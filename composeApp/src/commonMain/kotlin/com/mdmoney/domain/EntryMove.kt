package com.mdmoney.domain

import kotlin.math.round

/** How a purchase lands in a recurring expense's month when that month already holds a value. */
enum class MergeMode { REPLACE, ADD }

/**
 * The value a recurring expense carries in a month after receiving a purchase of [entryAmount]:
 * [MergeMode.REPLACE] makes the purchase the value, [MergeMode.ADD] sums it onto what was there.
 * Nothing there yet reads the same either way. Rounded to cents so sums never leave float dust in a file.
 */
fun mergedAmount(existing: Double?, entryAmount: Double, mode: MergeMode): Double =
    when {
        existing == null || mode == MergeMode.REPLACE -> entryAmount
        else -> round((existing + entryAmount) * 100) / 100
    }

/** Whether moving into a month holding [existing] needs asking how to combine: only a real value is at stake. */
fun needsMergeChoice(existing: Double?): Boolean = existing != null && existing != 0.0

/**
 * The one-off groups a purchase of [from] could move to: the other ledgers of the same month and
 * kind (spending or income), by title.
 */
fun oneOffDestinations(expenses: List<Expense>, from: Ledger): List<Expense> =
    expenses.filter {
        it.ledger && it.year == from.year && it.amounts[from.month] != null &&
            it.isIncome == from.income && it.id != from.id
    }.sortedBy { it.title.lowercase() }

/** The recurring expenses of [year] a purchase could be filed under, by title. */
fun recurringDestinations(expenses: List<Expense>, year: Int): List<Expense> =
    expenses.filter {
        it.year == year && !it.ledger &&
            (it.type == ExpenseType.RECURRING_FIXED || it.type == ExpenseType.RECURRING_VARIABLE)
    }.sortedBy { it.title.lowercase() }
