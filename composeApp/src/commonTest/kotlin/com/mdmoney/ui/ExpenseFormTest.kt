package com.mdmoney.ui

import com.mdmoney.domain.Category
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpenseFormTest {

    private fun expense(category: String?, amounts: Map<Month, Double> = emptyMap()) =
        Expense.empty("Regions", 2026, ExpenseType.RECURRING_FIXED)
            .copy(category = category, amounts = Month.ALL.associateWith { amounts[it] })

    @Test
    fun fixed_amount_lands_only_in_the_picked_months() {
        val amounts = fixedAmounts(99.9, setOf(Month.SEP, Month.OCT))

        assertEquals(99.9, amounts[Month.SEP])
        assertEquals(99.9, amounts[Month.OCT])
        assertEquals(null, amounts[Month.JAN])
        assertEquals(2, amounts.values.count { it != null })
    }

    @Test
    fun year_total_is_amount_times_months() {
        assertEquals(1198.8, yearTotal(99.9, Month.ALL.toSet()), 0.001)
        assertEquals(0.0, yearTotal(null, Month.ALL.toSet()))
    }

    @Test
    fun a_new_bill_opens_on_the_whole_year_and_an_existing_one_on_its_months() {
        assertEquals(12, repeatMonths(expense(null)).size)
        assertEquals(setOf(Month.MAR, Month.APR), repeatMonths(expense(null, mapOf(Month.MAR to 10.0, Month.APR to 10.0))))
    }

    @Test
    fun most_used_categories_rank_by_count_then_name() {
        val rows = listOf("casa", "mercado", "casa", "carro", "mercado", "casa").map { expense(it) } + expense(null)

        assertEquals(listOf("casa", "mercado"), mostUsedCategories(rows, limit = 2))
    }

    @Test
    fun filter_matches_titles_ignoring_case_and_blank_shows_all() {
        val all = listOf(Category("casa", "Casa"), Category("mercado", "Mercado"))

        assertEquals(listOf("Mercado"), filterCategories(all, " MERC ").map { it.title })
        assertEquals(2, filterCategories(all, "").size)
    }
}
