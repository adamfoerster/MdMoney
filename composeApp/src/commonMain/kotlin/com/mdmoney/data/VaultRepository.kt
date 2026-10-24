package com.mdmoney.data

import com.mdmoney.domain.Category
import com.mdmoney.domain.Currency
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Ledger
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import com.mdmoney.importer.EntryKey
import com.mdmoney.importer.ImportDedup
import com.mdmoney.importer.ImportEntry
import com.mdmoney.importer.ImportResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.round

/**
 * Reads and writes [Expense] items through a [VaultStorage], with a [CacheDb] in front for fast
 * reads and aggregation. Markdown files stay the source of truth: the UI reads from the cache and
 * edits are written to the cache immediately and flushed to the files afterwards (see [AppModel]).
 * All logic here is common code; the only platform-specific piece is the storage itself.
 */
class VaultRepository(
    internal val storage: VaultStorage,
    private val cache: CacheDb = CacheDb(":memory:"),
) {
    // Serializes file writes so rapid edits flush in order and each preserves the prior result.
    private val fileLock = Mutex()

    /** Display titles for the links written into notes; refreshed by [syncAccount]. */
    private var links: VaultLinks = VaultLinks.Empty

    /** The category notes as of the last [refreshLinks]; see [cachedCategories]. */
    private var catalog: List<Category> = emptyList()

    /**
     * The vault's accounts: every root folder except the one holding category notes, which is a
     * sibling of the accounts on disk but is not one — an account is a place money moves through.
     */
    suspend fun accounts(): List<String> =
        storage.listAccounts().filterNot { it.equals(CategoryMeta.FOLDER, ignoreCase = true) }

    suspend fun createAccount(name: String) = storage.createAccount(name.trim())

    /**
     * Direct file read of every note in [account] (the slow path; used by [syncAccount] and tests).
     *
     * Ledger notes (one-off spending grouped per month) are projected onto the same [Expense] shape
     * as everything else, so listing, aggregation and the balance need no special case.
     */
    suspend fun loadExpenses(account: String, fallbackYear: Int): List<Expense> =
        storage.listNotes(account).map { fileName ->
            val id = fileName.removeSuffix(".md")
            val content = storage.read(account, fileName)
            LedgerMapper.read(id, account, content, fallbackYear)?.toExpense()
                ?: ExpenseMapper.read(id, account, content, fallbackYear)
        }

    /** Reads a single ledger note (the one-off detail view), or null when [id] isn't one. */
    suspend fun loadLedger(account: String, id: String, fallbackYear: Int): Ledger? {
        val content = runCatching { storage.read(account, "$id.md") }.getOrNull() ?: return null
        return LedgerMapper.read(id, account, content, fallbackYear)
    }

    /**
     * Records one-off spending: finds or creates `<year> <Mon> - <title>.md` for the account and
     * appends [entry] to its table, recomputing the total. Returns the saved ledger.
     */
    suspend fun addOneOff(
        account: String,
        year: Int,
        month: Month,
        title: String,
        category: String?,
        entry: LedgerEntry,
    ): Ledger {
        // A category the user just typed may not have a note yet; give it one before linking to it.
        category?.let { ensureCategories(listOf(it)) }
        return fileLock.withLock { writeOneOff(account, year, month, title, category, entry) }
    }

    private suspend fun writeOneOff(
        account: String,
        year: Int,
        month: Month,
        title: String,
        category: String?,
        entry: LedgerEntry,
    ): Ledger {
        val id = Ledger.idFor(year, month, title)
        val existingContent = runCatching { storage.read(account, "$id.md") }.getOrNull()
        val existing = existingContent?.let { LedgerMapper.read(id, account, it, year) }
        val ledger = (
            existing ?: Ledger(
                id = id,
                account = account,
                title = Ledger.sanitizeTitle(title),
                category = category,
                year = year,
                month = month,
                entries = emptyList(),
            )
            ).let { it.copy(entries = it.entries + entry, category = category ?: it.category) }

        storage.write(account, "$id.md", LedgerMapper.write(existingContent, ledger, links))
        cache.upsertAll(listOf(ledger.toExpense()))
        return ledger
    }

    /**
     * Writes reviewed statement lines into their ledger notes — one read-modify-write per note, not
     * per line — skipping every line the vault already holds (see [ImportDedup]), so importing the
     * same statement twice adds nothing the second time.
     */
    suspend fun importEntries(account: String, entries: List<ImportEntry>): ImportResult {
        ensureCategories(entries.mapNotNull { it.category })
        return fileLock.withLock {
            val months = entries.map { it.year to it.month }.toSet()
            val present = ImportDedup.alreadyPresent(
                entries.map { EntryKey.of(it.entry) },
                ImportDedup.count(entriesIn(account, months)),
            )
            val fresh = entries.filterIndexed { i, _ -> !present[i] }

            // Lines filed under a plain note (recurring or income) grow that note's month instead.
            var plainNotes = 0
            val ledgerLines = mutableListOf<ImportEntry>()
            for ((noteId, group) in fresh.filter { it.noteId != null }.groupBy { it.noteId }) {
                val content = runCatching { storage.read(account, "$noteId.md") }.getOrNull()
                if (content == null || LedgerMapper.isLedger(content)) {
                    ledgerLines += group // the note is gone or not a plain one: fall back to a ledger
                    continue
                }
                val expense = ExpenseMapper.read(noteId!!, account, content, group.first().year)
                val updated = expense.withAddedLines(group)
                val text = LedgerMapper.withAppendedRows(
                    ExpenseMapper.write(content, expense, updated, links),
                    group.map { it.entry },
                )
                storage.write(account, "$noteId.md", text)
                cache.upsertAll(listOf(updated))
                plainNotes++
            }
            // Lines filed under no group each become a note of their own.
            val alone = fresh.filter { it.standalone }
            for (line in alone) {
                writeStandalone(account, line.year, line.month, line.income, line.entry.note, line.category, line.entry)
            }
            ledgerLines += fresh.filter { it.noteId == null && !it.standalone }
            val groups = ledgerLines.groupBy { Ledger.idFor(it.year, it.month, it.title) }
            for ((id, group) in groups) {
                val first = group.first()
                val existingContent = runCatching { storage.read(account, "$id.md") }.getOrNull()
                val existing = existingContent?.let { LedgerMapper.read(id, account, it, first.year) }
                val ledger = (
                    existing ?: Ledger(
                        id = id,
                        account = account,
                        title = Ledger.sanitizeTitle(first.title),
                        category = first.category,
                        year = first.year,
                        month = first.month,
                        entries = emptyList(),
                        income = first.income,
                    )
                    ).let { it.copy(entries = it.entries + group.map { e -> e.entry }) }
                storage.write(account, "$id.md", LedgerMapper.write(existingContent, ledger, links))
                cache.upsertAll(listOf(ledger.toExpense()))
            }
            ImportResult(added = fresh.size, skipped = entries.size - fresh.size, notes = groups.size + plainNotes + alone.size)
        }
    }

    // --- moving a purchase into or out of a group ---

    /**
     * Moves [entry] from the group [from] into the group titled [toTitle] of the same month, which
     * is created when the month has none by that name. Returns the two ledgers as saved, or null
     * when the destination is [from] itself.
     */
    suspend fun moveEntry(from: Ledger, entry: LedgerEntry, toTitle: String): Pair<Ledger, Ledger>? = fileLock.withLock {
        val id = Ledger.idFor(from.year, from.month, toTitle)
        if (id == from.id || toTitle.isBlank()) return@withLock null
        val source = from.copy(entries = from.entries.minusFirst(entry))
        val existingContent = runCatching { storage.read(from.account, "$id.md") }.getOrNull()
        val existing = existingContent?.let { LedgerMapper.read(id, from.account, it, from.year) }
        val target = (
            existing ?: from.copy(id = id, title = Ledger.sanitizeTitle(toTitle), entries = emptyList())
            ).let { it.copy(entries = it.entries + entry) }
        writeLedger(source)
        writeLedger(target, existingContent)
        source to target
    }

    /**
     * Takes [entry] out of the group [from] and files it as a note of its own — an eventual expense
     * (or income) in that month, already paid. The date stays in a table in the note's body, so the
     * line is still recognised as imported. Returns the group as saved and the new note.
     */
    suspend fun ungroupEntry(from: Ledger, entry: LedgerEntry): Pair<Ledger, Expense> = fileLock.withLock {
        val source = from.copy(entries = from.entries.minusFirst(entry))
        writeLedger(source)
        val note = writeStandalone(from.account, from.year, from.month, from.income, entry.note.ifBlank { from.title }, from.category, entry)
        source to note
    }

    /**
     * Folds the plain one-off [expense] into the group titled [toTitle] of its month and removes its
     * note. Returns the group, or null — leaving the note exactly as it was — when the note isn't a
     * single-month one-off or holds anything (prose, keys of its own) that the move would lose.
     */
    suspend fun groupExpense(expense: Expense, toTitle: String): Ledger? = fileLock.withLock {
        val month = expense.groupableMonth ?: return@withLock null
        val content = runCatching { storage.read(expense.account, "${expense.id}.md") }.getOrNull() ?: return@withLock null
        if (!ExpenseMapper.holdsNothingToLose(content) || toTitle.isBlank()) return@withLock null

        val amount = expense.amounts.getValue(month) ?: return@withLock null
        val kept = LedgerMapper.tableEntries(content)
        val entries = if (kept.isNotEmpty() && round(kept.sumOf { it.amount } * 100) == round(amount * 100)) {
            kept
        } else {
            listOf(LedgerEntry(LedgerEntry.dateOf(expense.year, month.number, 1), expense.title, amount))
        }
        val id = Ledger.idFor(expense.year, month, toTitle)
        val existingContent = runCatching { storage.read(expense.account, "$id.md") }.getOrNull()
        val existing = existingContent?.let { LedgerMapper.read(id, expense.account, it, expense.year) }
        val target = (
            existing ?: Ledger(
                id = id,
                account = expense.account,
                title = Ledger.sanitizeTitle(toTitle),
                category = expense.category,
                year = expense.year,
                month = month,
                entries = emptyList(),
                income = expense.isIncome,
            )
            ).let { it.copy(entries = it.entries + entries) }
        writeLedger(target, existingContent)
        storage.delete(expense.account, "${expense.id}.md")
        cache.deleteMissing(expense.account, cache.ids(expense.account).toSet() - expense.id)
        target
    }

    private suspend fun writeLedger(ledger: Ledger, existingContent: String? = null): Ledger {
        val current = existingContent ?: runCatching { storage.read(ledger.account, "${ledger.id}.md") }.getOrNull()
        storage.write(ledger.account, "${ledger.id}.md", LedgerMapper.write(current, ledger, links))
        cache.upsertAll(listOf(ledger.toExpense()))
        return ledger
    }

    /** Writes one purchase as a note of its own; its name never collides with a note already there. */
    private suspend fun writeStandalone(
        account: String,
        year: Int,
        month: Month,
        income: Boolean,
        title: String,
        category: String?,
        entry: LedgerEntry,
    ): Expense {
        val taken = runCatching { storage.listNotes(account) }.getOrDefault(emptyList()).map { it.removeSuffix(".md") }.toSet()
        val base = sanitize(title).ifBlank { "Despesa" }
        var id = base
        var n = 2
        while (id in taken) id = "$base ${n++}"
        val note = Expense.empty(account, year, if (income) ExpenseType.INCOME else ExpenseType.EVENTUAL).copy(
            id = id,
            title = title.trim().ifBlank { base },
            category = category,
            amounts = Month.ALL.associateWith { if (it == month) entry.amount else null },
            paid = Month.ALL.associateWith { it == month },
        )
        val text = LedgerMapper.withAppendedRows(ExpenseMapper.write(null, null, note, links), listOf(entry))
        storage.write(account, "$id.md", text)
        cache.upsertAll(listOf(note))
        return note
    }

    private fun <T> List<T>.minusFirst(item: T): List<T> {
        val i = indexOf(item)
        return if (i < 0) this else toMutableList().apply { removeAt(i) }
    }

    /** [this] with each line's amount added to its month, and those months marked paid/received. */
    private fun Expense.withAddedLines(lines: List<ImportEntry>): Expense {
        val sums = lines.groupBy { it.month }.mapValues { (_, l) -> l.sumOf { it.entry.amount } }
        return copy(
            amounts = amounts + sums.mapValues { (m, s) -> round(((amounts[m] ?: 0.0) + s) * 100) / 100 },
            // Statement lines are money that has already moved.
            paid = paid + sums.mapValues { true },
        )
    }

    /** How many times each row already sits in any of the account's notes for [months]. */
    suspend fun existingEntryCounts(account: String, months: Set<Pair<Int, Month>>): Map<EntryKey, Int> =
        ImportDedup.count(entriesIn(account, months))

    /**
     * The rows the account already holds: every ledger of [months], plus the rows kept in the body
     * of plain notes (see [LedgerMapper.withAppendedRows]) — a key includes the date, so those need
     * no month filter.
     */
    private suspend fun entriesIn(account: String, months: Set<Pair<Int, Month>>): List<LedgerEntry> {
        if (months.isEmpty()) return emptyList()
        val files = runCatching { storage.listNotes(account) }.getOrDefault(emptyList())
        return files.flatMap { file ->
            val content = runCatching { storage.read(account, file) }.getOrNull() ?: return@flatMap emptyList()
            val ledger = LedgerMapper.read(file.removeSuffix(".md"), account, content, months.first().first)
            when {
                ledger != null -> if ((ledger.year to ledger.month) in months) ledger.entries else emptyList()
                else -> LedgerMapper.tableEntries(content)
            }
        }
    }

    /** Rewrites a ledger's rows (used when removing an entry); the total follows automatically. */
    suspend fun saveLedger(ledger: Ledger): Ledger = fileLock.withLock {
        val existingContent = runCatching { storage.read(ledger.account, "${ledger.id}.md") }.getOrNull()
        storage.write(ledger.account, "${ledger.id}.md", LedgerMapper.write(existingContent, ledger, links))
        cache.upsertAll(listOf(ledger.toExpense()))
        ledger
    }

    /** Instant read straight from the cache (may be empty until [syncAccount] runs). */
    suspend fun cachedExpenses(account: String): List<Expense> = cache.expenses(account)

    /** Re-reads every note from disk into the cache and prunes deleted ones; returns the fresh list. */
    suspend fun syncAccount(account: String, fallbackYear: Int): List<Expense> {
        refreshLinks(account)
        val fresh = loadExpenses(account, fallbackYear)
        cache.upsertAll(fresh)
        cache.deleteMissing(account, fresh.map { it.id }.toSet())
        return fresh
    }

    suspend fun paidTotal(account: String, year: Int): Double = cache.paidTotal(account, year)

    suspend fun receivedTotal(account: String, year: Int): Double = cache.receivedTotal(account, year)

    /** Writes [updated] to the cache immediately (fast), assigning an id when it is new. */
    suspend fun cacheSave(updated: Expense): Expense {
        val id = updated.id.ifBlank { uniqueId(updated) }
        val resolved = updated.copy(id = id)
        cache.upsertAll(listOf(resolved))
        return resolved
    }

    /**
     * Flushes [resolved] to its markdown file, preserving unknown frontmatter/body from [original].
     *
     * Ledger-backed rows are skipped: their amount is the sum of a table this writer knows nothing
     * about, so rewriting them as a plain note would silently drop every purchase. They are edited
     * through [addOneOff]/[saveLedger] instead.
     */
    suspend fun persist(original: Expense?, resolved: Expense) {
        if (resolved.ledger) return
        resolved.category?.let { ensureCategories(listOf(it)) }
        fileLock.withLock {
            val originalContent = original?.takeIf { it.id.isNotBlank() }
                ?.let { runCatching { storage.read(it.account, "${it.id}.md") }.getOrNull() }
            val content = ExpenseMapper.write(originalContent, original, resolved, links)
            storage.write(resolved.account, "${resolved.id}.md", content)
        }
    }

    /** Convenience: cache + file in one call (used by tests and non-UI paths). */
    suspend fun save(original: Expense?, updated: Expense): Expense {
        val resolved = cacheSave(updated)
        persist(original, resolved)
        return resolved
    }

    // --- account metadata (vault-root file) ---

    suspend fun accountMeta(account: String): AccountMeta =
        AccountMeta.read(account, runCatching { storage.readRootFile("$account.md") }.getOrNull())

    suspend fun setInitialBalance(account: String, year: Int, value: Double?): AccountMeta {
        val updated = accountMeta(account).withInitialBalance(year, value)
        storage.writeRootFile("$account.md", updated.serialize())
        return updated
    }

    suspend fun setAccountTitle(account: String, title: String): AccountMeta {
        val updated = accountMeta(account).withTitle(title)
        storage.writeRootFile("$account.md", updated.serialize())
        return updated
    }

    suspend fun setCurrency(account: String, currency: Currency): AccountMeta {
        val updated = accountMeta(account).withCurrency(currency)
        storage.writeRootFile("$account.md", updated.serialize())
        return updated
    }

    /** Edits the account's display title and currency together, in one read-modify-write. */
    suspend fun updateAccount(account: String, title: String, currency: Currency): AccountMeta {
        val updated = accountMeta(account).withTitle(title).withCurrency(currency)
        storage.writeRootFile("$account.md", updated.serialize())
        return updated
    }

    // --- categories (notes in the `categories/` folder) ---

    /** Every category note in the vault, ordered as they should read. */
    suspend fun categories(): List<Category> {
        val files = runCatching { storage.listNotes(CategoryMeta.FOLDER) }.getOrDefault(emptyList())
        return files.mapNotNull { fileName ->
            val slug = fileName.removeSuffix(".md")
            val content = runCatching { storage.read(CategoryMeta.FOLDER, fileName) }.getOrNull()
            content?.let { CategoryMeta.read(slug, it).toCategory() }
        }.sortedBy { it.title.lowercase() }
    }

    suspend fun categoryMeta(slug: String): CategoryMeta =
        CategoryMeta.read(slug, runCatching { storage.read(CategoryMeta.FOLDER, "$slug.md") }.getOrNull())

    /**
     * Gives every one of [slugs] a note if it doesn't have one, so no link the app writes dangles.
     *
     * An existing note is never touched: its title and description are the user's, and re-running
     * this must not stamp a derived title over one they wrote.
     */
    suspend fun ensureCategories(slugs: Collection<String>): List<Category> {
        val existing = runCatching { storage.listNotes(CategoryMeta.FOLDER) }.getOrDefault(emptyList())
            .map { it.removeSuffix(".md") }
            .toSet()
        val created = mutableListOf<Category>()
        for (slug in slugs.distinct().filter { it.isNotBlank() && it !in existing }) {
            val meta = CategoryMeta.create(slug)
            runCatching { storage.write(CategoryMeta.FOLDER, "$slug.md", meta.serialize()) }
                .onSuccess { created.add(meta.toCategory()) }
        }
        if (created.isNotEmpty()) catalog = (catalog + created).sortedBy { it.title.lowercase() }
        return created
    }

    /**
     * The category notes as of the last [refreshLinks] — enough to put a title on screen without
     * re-reading the folder after every keystroke. The notes remain the source of truth; this is the
     * same stale-while-revalidate bargain the expense cache makes.
     */
    fun cachedCategories(): List<Category> = catalog

    /** The titles the app stamps onto the links it writes, read from the metadata notes. */
    suspend fun refreshLinks(account: String): VaultLinks {
        catalog = runCatching { categories() }.getOrDefault(emptyList())
        val title = runCatching { accountMeta(account).title }.getOrNull() ?: account
        return VaultLinks.of(catalog, mapOf(account to title)).also { links = it }
    }

    private suspend fun uniqueId(expense: Expense): String {
        val base = sanitize(expense.title).ifBlank { "Despesa" }
        val existing = cache.ids(expense.account).toSet()
        if (base !in existing) return base
        var n = 2
        while ("$base $n" in existing) n++
        return "$base $n"
    }

    private fun sanitize(name: String): String =
        name.trim().replace(Regex("""[\\/:*?"<>|]"""), "-").trim()
}
