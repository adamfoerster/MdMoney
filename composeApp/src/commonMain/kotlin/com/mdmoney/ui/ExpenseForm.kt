package com.mdmoney.ui

import com.mdmoney.domain.Category
import com.mdmoney.domain.Expense
import com.mdmoney.domain.Month

/**
 * The twelve slots of a fixed bill: [amount] in each of [months], nothing in the rest. "Whole year"
 * is simply all twelve months picked.
 */
fun fixedAmounts(amount: Double?, months: Set<Month>): Map<Month, Double?> =
    Month.ALL.associateWith { if (it in months) amount else null }

/** What a fixed bill adds up to over the year: its amount times the months it repeats in. */
fun yearTotal(amount: Double?, months: Set<Month>): Double = (amount ?: 0.0) * months.size

/**
 * The months a fixed bill already carries an amount in — the selection the form opens with. A new
 * bill (no amounts yet) starts on the whole year.
 */
fun repeatMonths(expense: Expense): Set<Month> =
    Month.ALL.filter { expense.amount(it) != null }.toSet().ifEmpty { Month.ALL.toSet() }

/** Category slugs ordered by how many of [expenses] file under each, most used first. */
fun mostUsedCategories(expenses: List<Expense>, limit: Int = 4): List<String> =
    expenses.mapNotNull { it.category?.takeIf(String::isNotBlank) }
        .groupingBy { it }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit).map { it.key }

/** The categories whose title contains [query], ignoring case; all of them for a blank query. */
fun filterCategories(categories: List<Category>, query: String): List<Category> {
    val q = query.trim()
    return if (q.isEmpty()) categories else categories.filter { it.title.contains(q, ignoreCase = true) }
}
