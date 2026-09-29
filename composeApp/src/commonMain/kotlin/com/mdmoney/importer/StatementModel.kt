package com.mdmoney.importer

import com.mdmoney.domain.LedgerEntry

/** A calendar date without time or zone — all a statement line needs. */
data class SimpleDate(val year: Int, val month: Int, val day: Int) : Comparable<SimpleDate> {
    override fun compareTo(other: SimpleDate): Int = ordinal.compareTo(other.ordinal)

    /** Sortable integer `yyyyMMdd`. */
    val ordinal: Int get() = year * 10_000 + month * 100 + day

    /** The `yyyyMMdd` string a ledger row stores. */
    fun toLedgerDate(): String = LedgerEntry.dateOf(year, month, day)

    fun isValid(): Boolean = month in 1..12 && day in 1..daysIn(year, month)

    companion object {
        /** Parses `yyyy-MM-dd`; null for anything else or an impossible date. */
        fun parseIso(text: String?): SimpleDate? {
            val m = Regex("""^\s*(\d{4})-(\d{1,2})-(\d{1,2})\s*$""").find(text ?: return null) ?: return null
            val (y, mo, d) = m.destructured
            return SimpleDate(y.toInt(), mo.toInt(), d.toInt()).takeIf { it.isValid() }
        }

        fun daysIn(year: Int, month: Int): Int = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
}

/** The dates a statement or card bill covers, both inclusive. */
data class StatementPeriod(val start: SimpleDate, val end: SimpleDate)

/** Which way the money went, from the account holder's point of view. */
enum class Direction(val id: String) {
    DEBIT("debit"),
    CREDIT("credit");

    companion object {
        fun fromId(id: String?): Direction? = entries.firstOrNull { it.id == id?.trim()?.lowercase() }
    }
}

/**
 * What a statement line is. Only [PURCHASE] and [FEE] are ticked for import by default: the rest are
 * money moving between the user's own pockets (a card bill paid, a transfer) or money coming in,
 * which the user opts into line by line.
 */
enum class TxKind(val id: String) {
    PURCHASE("purchase"),
    FEE("fee"),
    INCOME("income"),
    TRANSFER("transfer"),
    CARD_PAYMENT("card_payment"),
    REFUND("refund");

    val preselected: Boolean get() = this == PURCHASE || this == FEE

    companion object {
        fun fromId(id: String?): TxKind? = entries.firstOrNull { it.id == id?.trim()?.lowercase() }
    }
}

/**
 * One line as the extractor (model or simple parser) read it. The year is often not printed at all
 * (`25 AGO`, `08/24`), so it may be null and is settled against the statement's period later.
 */
data class RawTransaction(
    val year: Int?,
    val month: Int,
    val day: Int,
    val description: String,
    /** Always positive; [direction] carries the sign. */
    val amount: Double,
    val direction: Direction,
    val kind: TxKind,
    /** A category slug the extractor suggested, or null. */
    val category: String? = null,
    /** Set when the model and the fixed rules disagree about this line (see [Doubt.READERS_DISAGREE]). */
    val disputed: Boolean = false,
)

/** A statement line with its full date settled, ready for review. */
data class StatementTransaction(
    val date: SimpleDate,
    val description: String,
    val amount: Double,
    val direction: Direction,
    val kind: TxKind,
    val category: String?,
    /** Why this line deserves a second look; such a line is shown but not ticked. Null when none. */
    val doubt: Doubt? = null,
) {
    val verified: Boolean get() = doubt == null
}

/** Why a line is shown unticked for the user to decide. */
enum class Doubt {
    /** The amount isn't printed on the page the line supposedly came from. */
    AMOUNT_NOT_FOUND,

    /** The date falls well outside the statement's period. */
    DATE_OUTSIDE_PERIOD,

    /** The model and the fixed rules disagree about whether this line is a transaction at all. */
    READERS_DISAGREE,
}

/** What the statement says about itself: its period, currency and printed totals. */
data class StatementHeader(
    val period: StatementPeriod? = null,
    /** ISO 4217 code (`BRL`, `USD`) when it could be told. */
    val currency: String? = null,
    val declaredDebits: Double? = null,
    val declaredCredits: Double? = null,
)

/**
 * The extracted lines checked against the statement's own totals — the one cheap, independent way to
 * know whether anything was missed or made up. A null declared total means the statement didn't say.
 */
data class Reconciliation(
    val debits: Double,
    val credits: Double,
    val declaredDebits: Double?,
    val declaredCredits: Double?,
) {
    val debitsMatch: Boolean? get() = declaredDebits?.let { centsEqual(it, debits) }
    val creditsMatch: Boolean? get() = declaredCredits?.let { centsEqual(it, credits) }

    private fun centsEqual(a: Double, b: Double) = kotlin.math.round(a * 100) == kotlin.math.round(b * 100)
}

/** Everything one PDF yielded. */
data class ExtractionResult(
    val header: StatementHeader,
    val transactions: List<StatementTransaction>,
    val reconciliation: Reconciliation,
    /** False when the simple parser did the work because no model could run. */
    val usedModel: Boolean,
    /** Why the model wasn't used although one was wanted (it failed to load or to answer); null otherwise. */
    val modelError: String? = null,
)
