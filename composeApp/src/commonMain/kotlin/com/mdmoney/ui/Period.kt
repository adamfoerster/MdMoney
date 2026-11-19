package com.mdmoney.ui

import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Ledger
import com.mdmoney.importer.SimpleDate

/** A calendar month of a given year, sortable as `yyyyMM`. */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    val ordinal: Int get() = year * 100 + month
    override fun compareTo(other: YearMonth): Int = ordinal.compareTo(other.ordinal)
}

/**
 * What Home covers when it is not looking at a single month: a hand-picked list of months (at most
 * [MAX_MONTHS], not necessarily contiguous) or a first and last day.
 *
 * Every day is an `yyyyMMdd` integer, so ranges compare as plain numbers.
 */
sealed interface Period {
    /** First day covered, `yyyyMMdd`. */
    val firstDay: Int

    /** Last day covered, `yyyyMMdd`. */
    val lastDay: Int

    /** Whether the `yyyyMMdd` [date] is part of what the user picked. */
    fun contains(date: Int): Boolean

    data class Months(val months: Set<YearMonth>) : Period {
        init {
            require(months.isNotEmpty()) { "a period needs at least one month" }
            require(months.size <= MAX_MONTHS) { "a period holds at most $MAX_MONTHS months" }
        }

        override val firstDay: Int get() = months.min().let { it.ordinal * 100 + 1 }
        override val lastDay: Int get() = months.max().let { it.ordinal * 100 + SimpleDate.daysIn(it.year, it.month) }
        override fun contains(date: Int): Boolean = YearMonth(date / 10_000, date / 100 % 100) in months
    }

    data class Range(val start: SimpleDate, val end: SimpleDate) : Period {
        init {
            require(start <= end) { "a range cannot end before it starts" }
        }

        override val firstDay: Int get() = start.ordinal
        override val lastDay: Int get() = end.ordinal
        override fun contains(date: Int): Boolean = date in firstDay..lastDay
    }

    companion object {
        const val MAX_MONTHS = 12
    }
}

/** Adds [ym] to the picked months, or removes it when already there; a 13th month is refused. */
fun Set<YearMonth>.toggled(ym: YearMonth): Set<YearMonth> = when {
    ym in this -> this - ym
    size >= Period.MAX_MONTHS -> this
    else -> this + ym
}

/**
 * The two taps that make a range: the first sets the start, the second the end (swapped when it is
 * earlier). A third tap starts over.
 */
data class RangeDraft(val start: SimpleDate? = null, val end: SimpleDate? = null) {
    fun tap(day: SimpleDate): RangeDraft = when {
        start == null || end != null -> RangeDraft(start = day)
        day < start -> RangeDraft(start = day, end = start)
        else -> RangeDraft(start = start, end = day)
    }

    /** A single tapped day is a one-day range. Null until something is tapped. */
    fun toPeriod(): Period.Range? = start?.let { Period.Range(it, end ?: it) }

    fun covers(day: SimpleDate): Boolean = start != null && day >= start && day <= (end ?: start)
}

/** One line of the period view: everything a title came to across the period. */
data class PeriodLine(val title: String, val category: String?, val amount: Double)

data class PeriodLines(
    val income: List<PeriodLine>,
    val recurring: List<PeriodLine>,
    val eventual: List<PeriodLine>,
) {
    val isEmpty: Boolean get() = income.isEmpty() && recurring.isEmpty() && eventual.isEmpty()
}

data class PeriodBalance(val start: Double, val end: Double)

/** One movement of money, dated. [amount] is positive; whether it came in is the row's own business. */
private class Movement(val date: Int, val amount: Double, val paid: Boolean, val row: Expense)

/**
 * Every dated movement [expenses] carries. The vault stores plain notes by month, so a note's month
 * lands on the 1st; a ledger purchase lands on its own day — when [ledgers] holds the ledger's rows
 * for it, and on the 1st of its month (as its total) when not.
 */
private fun movements(expenses: List<Expense>, ledgers: Map<String, Ledger>): List<Movement> =
    expenses.flatMap { e ->
        val ledger = if (e.ledger) ledgers[e.id] else null
        if (ledger != null) {
            ledger.entries.map { entry ->
                val date = entry.date.toIntOrNull()
                    ?: (e.year * 10_000 + ledger.month.number * 100 + 1)
                Movement(date, entry.amount, paid = true, row = e)
            }
        } else {
            e.amounts.mapNotNull { (month, amount) ->
                amount?.let { Movement(e.year * 10_000 + month.number * 100 + 1, it, e.isPaid(month), e) }
            }
        }
    }

/** What each title came to inside [period], whether or not it has been paid yet. */
fun periodLines(expenses: List<Expense>, ledgers: Map<String, Ledger>, period: Period): PeriodLines {
    val inside = movements(expenses, ledgers).filter { period.contains(it.date) }

    fun section(match: (Expense) -> Boolean): List<PeriodLine> =
        inside.filter { match(it.row) }
            .groupBy { it.row.title.trim().lowercase() }
            .map { (_, rows) ->
                PeriodLine(
                    title = rows.first().row.title,
                    category = rows.firstNotNullOfOrNull { it.row.category },
                    amount = rows.sumOf { it.amount },
                )
            }
            .sortedBy { it.title.lowercase() }

    return PeriodLines(
        income = section { it.isIncome },
        recurring = section { it.type == ExpenseType.RECURRING_FIXED || it.type == ExpenseType.RECURRING_VARIABLE },
        eventual = section { !it.isIncome && it.type == ExpenseType.EVENTUAL },
    )
}

/**
 * The checkbook balance as [period] begins and as it ends, from the [initial] balance of the year the
 * period starts in: only money marked paid / received has moved. The end covers everything up to the
 * period's last day, so months left out of a hand-picked list still count towards the balance — they
 * only stay out of [periodLines].
 */
fun periodBalance(expenses: List<Expense>, ledgers: Map<String, Ledger>, period: Period, initial: Double): PeriodBalance {
    val yearStart = period.firstDay / 10_000 * 10_000 + 101
    val moved = movements(expenses, ledgers).filter { it.paid }
    fun net(from: Int, to: Int) = moved.filter { it.date in from..to }
        .sumOf { if (it.row.isIncome) it.amount else -it.amount }
    return PeriodBalance(
        start = initial + net(yearStart, period.firstDay - 1),
        end = initial + net(yearStart, period.lastDay),
    )
}
