package com.mdmoney.data

import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import com.mdmoney.domain.parseVaultLink

/**
 * Converts between the on-disk markdown note and the [Expense] domain model.
 *
 * Reading is tolerant (accepts the legacy Portuguese `fev` key, and a `category:` written either as
 * a `"[[casa|Casa]]"` link or as the plain `casa` that predates links). Writing standardizes month
 * keys to English (`feb`), preserves every frontmatter key the app doesn't manage and the markdown
 * body, and only reformats a month's amount when its value actually changed — so untouched numbers
 * keep their exact original text.
 */
object ExpenseMapper {

    fun read(id: String, account: String, content: String, fallbackYear: Int): Expense {
        val note = FrontmatterParser.parse(content)

        val amounts = Month.ALL.associateWith { month ->
            val key = month.aliases.firstOrNull { note.hasKey(it) }
            key?.let { parseAmount(note.scalar(it)) }
        }
        val paid = Month.ALL.associateWith { month ->
            val paidKey = month.aliases.map { "$it-paid" }.firstOrNull { note.hasKey(it) }
            paidKey?.let { note.scalar(it) == "true" } ?: false
        }

        val period = note.scalar("period")
        val type = ExpenseType.fromId(note.scalar("type")) ?: inferType(amounts, period)

        return Expense(
            id = id,
            // The account is the folder the note lives in — its identity for the cache and for
            // writes. The `conta:` frontmatter is just preserved metadata and may differ in casing
            // (e.g. folder `nubank` vs `conta: Nubank`); trusting it here would key the cache under a
            // name the app never looks up, hiding every note in the account.
            account = account,
            title = note.scalar("title") ?: id,
            // The link's target is the category's identity (its note's name); the title half is
            // presentation and is read from that note, not from here.
            category = parseVaultLink(note.scalar("category"))?.target,
            year = note.scalar("year")?.toIntOrNull() ?: fallbackYear,
            type = type,
            period = period,
            subStatus = note.scalar("subStatus"),
            projected = parseAmount(note.scalar("projected")),
            tags = readTags(note),
            amounts = amounts,
            paid = paid,
        )
    }

    /**
     * Renders [updated] back to markdown. Pass the note's [originalContent] and the [original]
     * (as previously read) to preserve untouched numbers and unknown frontmatter; pass null for a
     * brand-new note. [links] supplies the display titles for the category/account links.
     */
    fun write(
        originalContent: String?,
        original: Expense?,
        updated: Expense,
        links: VaultLinks = VaultLinks.Empty,
    ): String {
        val note = originalContent?.let { FrontmatterParser.parse(it) } ?: MarkdownNote(mutableListOf(), "")

        // Migrate legacy month keys (fev -> feb) in place, keeping their original value text.
        for (month in Month.ALL) {
            canonicalizeKey(note, month.key, month.aliases.filter { it != month.key })
            canonicalizeKey(note, month.paidKey, month.aliases.filter { it != month.key }.map { "$it-paid" })
        }

        // Managed classification fields (short, stable strings).
        note.setScalar("title", updated.title)
        // Category and account are links into their metadata notes, so the note is navigable in
        // Obsidian and every category collects its own backlinks.
        note.setLink("category", updated.category) { links.category(it) }
        // `conta:` is the legacy plain-text ancestor of this key. It is left exactly as the user
        // wrote it (its casing may differ from the folder) but never added to a new note: the
        // account's identity has always been the folder, and now `account:` says so as a link.
        note.setLink("account", updated.account, after = "conta") { links.account(it) }
        note.setScalar("year", updated.year.toString())
        note.setScalar("type", updated.type.id)
        if (updated.period != null || note.hasKey("period")) note.setScalar("period", updated.period)
        if (updated.subStatus != null || note.hasKey("subStatus")) note.setScalar("subStatus", updated.subStatus)
        if (updated.projected != null) note.setScalar("projected", formatAmount(updated.projected))

        // Months: only reformat a value that changed, so untouched amounts keep their exact text.
        for (month in Month.ALL) {
            val newAmount = updated.amounts[month]
            val oldAmount = original?.amounts?.get(month)
            if (original == null || newAmount != oldAmount || !note.hasKey(month.key)) {
                note.setScalar(month.key, newAmount?.let { formatAmount(it) })
            }
            val newPaid = updated.paid[month] ?: false
            val oldPaid = original?.paid?.get(month) ?: false
            if (original == null || newPaid != oldPaid || !note.hasKey(month.paidKey)) {
                note.setScalar(month.paidKey, newPaid.toString())
            }
        }

        return FrontmatterParser.serialize(note)
    }

    /** Renames the first present alias to [canonical] in place, preserving its value; drops extras. */
    private fun canonicalizeKey(note: MarkdownNote, canonical: String, aliases: List<String>) {
        if (aliases.isEmpty()) return
        if (note.hasKey(canonical)) {
            aliases.forEach { note.removeKey(it) }
            return
        }
        val idx = note.entries.indexOfFirst { it.key in aliases }
        if (idx < 0) return
        val entry = note.entries[idx]
        if (entry is FmEntry.Scalar) {
            note.entries[idx] = FmEntry.Scalar(canonical, entry.value)
        }
        for (i in note.entries.indices.reversed()) {
            if (i != idx && note.entries[i].key in aliases) note.entries.removeAt(i)
        }
    }

    private fun inferType(amounts: Map<Month, Double?>, period: String?): ExpenseType {
        val nonZero = Month.ALL.mapNotNull { amounts[it] }.filter { it != 0.0 }
        val hasPeriod = !period.isNullOrBlank()
        return when {
            nonZero.isEmpty() -> if (hasPeriod) ExpenseType.RECURRING_VARIABLE else ExpenseType.EVENTUAL
            nonZero.size == 1 && !hasPeriod -> ExpenseType.EVENTUAL
            nonZero.distinct().size == 1 -> ExpenseType.RECURRING_FIXED
            else -> ExpenseType.RECURRING_VARIABLE
        }
    }

    private fun readTags(note: MarkdownNote): List<String> {
        val entry = note.entries.firstOrNull { it.key == "tags" } ?: return emptyList()
        return when (entry) {
            is FmEntry.Raw -> entry.lines.drop(1)
                .map { it.trim() }
                .filter { it.startsWith("-") }
                .map { it.removePrefix("-").trim() }
                .filter { it.isNotEmpty() }
            is FmEntry.Scalar -> entry.value.trim().removeSurrounding("[", "]")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
