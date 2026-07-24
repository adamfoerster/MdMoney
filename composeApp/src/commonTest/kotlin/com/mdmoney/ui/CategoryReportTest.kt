package com.mdmoney.ui

import com.mdmoney.domain.Category
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryReportTest {

    private fun note(
        title: String,
        category: String?,
        amounts: Map<Month, Double> = emptyMap(),
        type: ExpenseType = ExpenseType.RECURRING_VARIABLE,
        year: Int = 2026,
    ) = Expense.empty(account = "Regions", year = year, type = type)
        .copy(id = title, title = title, category = category, amounts = Month.ALL.associateWith { amounts[it] })

    private val categories = listOf(
        Category("casa", "Casa", "Tudo o que a casa consome"),
        Category("car", "Carro"),
    )

    private val expenses = listOf(
        note("Luz", "casa", mapOf(Month.JAN to 100.0, Month.FEB to 150.0)),
        note("Água", "casa", mapOf(Month.JAN to 50.0)),
        note("Gasolina", "car", mapOf(Month.JAN to 300.0)),
        note("Salário", "casa", mapOf(Month.JAN to 5000.0), type = ExpenseType.INCOME),
        note("Antigo", "casa", mapOf(Month.JAN to 999.0), year = 2025),
        note("Perdido", null, mapOf(Month.MAR to 7.0)),
    )

    private fun reports() = categoryReports(expenses, 2026, categories, "Sem categoria")

    @Test
    fun sums_a_category_month_by_month() {
        val casa = reports().first { it.slug == "casa" }

        // Hand-computed: Luz 100 + Água 50 in January, Luz 150 alone in February.
        assertEquals(150.0, casa.monthly[Month.JAN])
        assertEquals(150.0, casa.monthly[Month.FEB])
        assertEquals(0.0, casa.monthly[Month.MAR])
        assertEquals(300.0, casa.total)
        assertEquals(150.0, casa.peak, "the chart scales against the tallest month")
    }

    @Test
    fun reads_the_title_and_description_from_the_category_note() {
        val casa = reports().first { it.slug == "casa" }
        assertEquals("Casa", casa.category.title)
        assertEquals("Tudo o que a casa consome", casa.category.description)
    }

    /** A salary is not a cost, and charting it as one would make the category read as spending. */
    @Test
    fun income_is_not_spending() {
        val casa = reports().first { it.slug == "casa" }
        assertEquals(300.0, casa.total, "the 5000 salário in `casa` must not be counted")
        assertTrue(casa.rows.none { it.isIncome })
        assertTrue(casa.rows.map { it.title }.containsAll(listOf("Luz", "Água")))
    }

    @Test
    fun another_year_is_another_report() {
        assertEquals(300.0, reports().first { it.slug == "casa" }.total, "2025's 999 stays in 2025")
        assertEquals(999.0, categoryReports(expenses, 2025, categories, "Sem categoria").single().total)
    }

    /** A report that hides money is worse than one with an unnamed row. */
    @Test
    fun money_with_no_category_still_shows_up() {
        val none = reports().first { it.slug == UNCATEGORIZED }
        assertEquals(7.0, none.total)
        assertEquals("Sem categoria", none.category.title)
    }

    @Test
    fun heaviest_first_is_the_order_that_answers_the_question() {
        assertEquals(listOf("car", "casa", UNCATEGORIZED), reports().map { it.slug })
        assertEquals(listOf(300.0, 300.0, 7.0), reports().map { it.total })
        // car and casa tie at 300; the tie breaks on title so the order can't wobble between runs.
        assertEquals(listOf("Carro", "Casa"), reports().take(2).map { it.category.title })
    }

    @Test
    fun a_category_with_no_note_reports_under_a_readable_name() {
        val orphan = categoryReports(
            listOf(note("Algo", "adam shopping", mapOf(Month.JAN to 10.0))),
            2026,
            categories,
            "Sem categoria",
        ).single()
        assertEquals("adam shopping", orphan.slug, "the identity is still the slug")
        assertEquals("Adam Shopping", orphan.category.title)
    }

    @Test
    fun one_category_drills_down_to_the_notes_behind_it() {
        val casa = categoryReport("casa", expenses, 2026, categories, "Sem categoria")!!
        assertEquals(300.0, casa.total)
        assertEquals(listOf("Luz", "Água"), casa.rows.map { it.title }, "biggest first")
        assertNull(categoryReport("casa", expenses, 2030, categories, "Sem categoria"), "a year with nothing in it")
        assertNull(categoryReport("nada", expenses, 2026, categories, "Sem categoria"))
    }
}
