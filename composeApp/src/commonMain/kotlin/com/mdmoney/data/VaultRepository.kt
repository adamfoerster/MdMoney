package com.mdmoney.data

import com.mdmoney.domain.Expense
import com.mdmoney.domain.Ledger
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads and writes [Expense] items through a [VaultStorage], with a [CacheDb] in front for fast
 * reads and aggregation. Markdown files stay the source of truth: the UI reads from the cache and
 * edits are written to the cache immediately and flushed to the files afterwards (see [AppModel]).
 * All logic here is common code; the only platform-specific piece is the storage itself.
 */
class VaultRepository(
    private val storage: VaultStorage,
    private val cache: CacheDb = CacheDb(":memory:"),
) {
    // Serializes file writes so rapid edits flush in order and each preserves the prior result.
    private val fileLock = Mutex()

    suspend fun accounts(): List<String> = storage.listAccounts()

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
    ): Ledger = fileLock.withLock {
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

        storage.write(account, "$id.md", LedgerMapper.write(existingContent, ledger))
        cache.upsertAll(listOf(ledger.toExpense()))
        ledger
    }

    /** Rewrites a ledger's rows (used when removing an entry); the total follows automatically. */
    suspend fun saveLedger(ledger: Ledger): Ledger = fileLock.withLock {
        val existingContent = runCatching { storage.read(ledger.account, "${ledger.id}.md") }.getOrNull()
        storage.write(ledger.account, "${ledger.id}.md", LedgerMapper.write(existingContent, ledger))
        cache.upsertAll(listOf(ledger.toExpense()))
        ledger
    }

    /** Instant read straight from the cache (may be empty until [syncAccount] runs). */
    suspend fun cachedExpenses(account: String): List<Expense> = cache.expenses(account)

    /** Re-reads every note from disk into the cache and prunes deleted ones; returns the fresh list. */
    suspend fun syncAccount(account: String, fallbackYear: Int): List<Expense> {
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
        fileLock.withLock {
            val originalContent = original?.takeIf { it.id.isNotBlank() }
                ?.let { runCatching { storage.read(it.account, "${it.id}.md") }.getOrNull() }
            val content = ExpenseMapper.write(originalContent, original, resolved)
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
