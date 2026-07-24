package com.mdmoney.ui

import com.mdmoney.domain.Category
import com.mdmoney.domain.Expense
import com.mdmoney.domain.Month
import com.mdmoney.domain.prettifySlug

/** The slug standing in for "no category at all", so money is never missing from a report. */
const val UNCATEGORIZED = ""

/**
 * What one category cost over a year: the twelve figures the chart draws, and the notes they came
 * from.
 *
 * Income is left out. A category collects spending, and a chart mixing a salary in with the bills it
 * pays would be summing two different things.
 */
data class CategoryReport(
    val category: Category,
    val monthly: Map<Month, Double>,
    val rows: List<Expense>,
) {
    val total: Double get() = Month.ALL.sumOf { monthly[it] ?: 0.0 }

    /** The tallest month, which is what the chart scales against. */
    val peak: Double get() = Month.ALL.maxOf { monthly[it] ?: 0.0 }

    val slug: String get() = category.slug
}

/**
 * Every category's spending in [year], heaviest first — the order that answers "where is the money
 * going" without reading anything.
 *
 * [categories] supplies the titles; a slug with no note of its own still reports under a readable
 * name rather than vanishing, since a report that hides money is worse than an unnamed row.
 */
fun categoryReports(
    expenses: List<Expense>,
    year: Int,
    categories: List<Category>,
    uncategorizedTitle: String,
): List<CategoryReport> =
    expenses
        .filter { it.year == year && !it.isIncome }
        .groupBy { it.category ?: UNCATEGORIZED }
        .map { (slug, rows) -> report(slug, rows, categories, uncategorizedTitle) }
        .sortedWith(compareByDescending<CategoryReport> { it.total }.thenBy { it.category.title.lowercase() })

/** One category's report, or null when nothing in [year] belongs to it. */
fun categoryReport(
    slug: String,
    expenses: List<Expense>,
    year: Int,
    categories: List<Category>,
    uncategorizedTitle: String,
): CategoryReport? {
    val rows = expenses.filter { it.year == year && !it.isIncome && (it.category ?: UNCATEGORIZED) == slug }
    if (rows.isEmpty()) return null
    return report(slug, rows, categories, uncategorizedTitle)
}

private fun report(
    slug: String,
    rows: List<Expense>,
    categories: List<Category>,
    uncategorizedTitle: String,
): CategoryReport = CategoryReport(
    category = resolve(slug, categories, uncategorizedTitle),
    monthly = Month.ALL.associateWith { month -> rows.sumOf { it.amount(month) ?: 0.0 } },
    rows = rows.sortedWith(compareByDescending<Expense> { it.total }.thenBy { it.title.lowercase() }),
)

private fun resolve(slug: String, categories: List<Category>, uncategorizedTitle: String): Category = when {
    slug == UNCATEGORIZED -> Category(UNCATEGORIZED, uncategorizedTitle)
    else -> categories.firstOrNull { it.slug == slug } ?: Category(slug, prettifySlug(slug))
}
