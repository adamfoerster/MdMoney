package com.mdmoney.domain

/**
 * One line in a [Ledger] — a single purchase (a coffee, a parking ticket).
 *
 * [date] is kept as the raw `yyyyMMdd` string the file carries, so it round-trips exactly and sorts
 * correctly as plain text.
 */
data class LedgerEntry(
    val date: String,
    val note: String,
    val amount: Double,
) {
    companion object {
        fun dateOf(year: Int, month: Int, day: Int): String =
            year.toString().padStart(4, '0') +
                month.toString().padStart(2, '0') +
                day.toString().padStart(2, '0')
    }
}

/**
 * A month's worth of one-off spending grouped under one heading — one markdown note per
 * account/year/month/title, e.g. `2026 Jul - Alimentação.md`.
 *
 * Individual one-offs (each coffee) would otherwise each become their own file; they are rows in
 * [entries] instead, and the note's `total` is their sum. Money here is already spent by
 * definition — you record the purchase after making it — so it always counts against the balance.
 */
data class Ledger(
    val id: String,
    val account: String,
    val title: String,
    val category: String?,
    val year: Int,
    val month: Month,
    val entries: List<LedgerEntry>,
) {
    val total: Double get() = entries.sumOf { it.amount }

    /** Newest first, matching how the table is written out. */
    fun sorted(): List<LedgerEntry> = entries.sortedByDescending { it.date }

    /**
     * How the ledger appears everywhere expenses are listed or summed: a one-off landing in its own
     * month, already paid — the money left the account when the purchase was made.
     */
    fun toExpense(): Expense = Expense(
        id = id,
        account = account,
        title = title,
        category = category,
        year = year,
        type = ExpenseType.EVENTUAL,
        period = null,
        subStatus = null,
        projected = null,
        tags = emptyList(),
        amounts = Month.ALL.associateWith { if (it == month) total else null },
        paid = Month.ALL.associateWith { it == month },
        ledger = true,
    )

    companion object {
        /** The note's file name (without extension): `2026 Jul - Alimentação`. */
        fun idFor(year: Int, month: Month, title: String): String =
            "$year ${month.englishShort} - ${sanitizeTitle(title)}"

        /** Strips only what a file name cannot hold; accents and spaces are kept as typed. */
        fun sanitizeTitle(title: String): String =
            title.trim().replace(Regex("""[\\/:*?"<>|]"""), "-").trim()
    }
}
