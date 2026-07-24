package com.mdmoney.ui

import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Ledger
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthLinesTest {

    /** A plain note as the vault holds one: all twelve slots present, most of them empty. */
    private fun note(
        title: String,
        type: ExpenseType,
        amounts: Map<Month, Double> = emptyMap(),
        year: Int = 2026,
    ) = Expense.empty(account = "Regions", year = year, type = type)
        .copy(id = title, title = title, amounts = Month.ALL.associateWith { amounts[it] })

    /**
     * The reported bug: `Seguro residencial - 2026.md` is a yearly bill (`type: eventual`) whose
     * amount sits in January, so from February on Home dropped it entirely.
     */
    @Test
    fun yearly_eventual_note_shows_in_a_month_it_has_no_value_in() {
        val seguro = note("Seguro residencial", ExpenseType.EVENTUAL, mapOf(Month.JAN to 2302.55))

        val july = monthLines(listOf(seguro), 2026, Month.JUL)

        assertEquals(listOf("Seguro residencial"), july.eventual.map { it.title })
        assertEquals(null, july.eventual.single().amount(Month.JUL), "and it carries no July amount")
    }

    @Test
    fun recurring_note_shows_in_a_month_it_has_no_value_in() {
        val condominio = note("Condomínio", ExpenseType.RECURRING_FIXED, mapOf(Month.JAN to 300.0))

        val july = monthLines(listOf(condominio), 2026, Month.JUL)

        assertEquals(listOf("Condomínio"), july.recurring.map { it.title })
    }

    /** A ledger is the record of one month's purchases — it must not leak into other months. */
    @Test
    fun ledger_shows_only_in_its_own_month() {
        val june = Ledger(
            id = "2026 Jun - Alimentação",
            account = "Regions",
            title = "Alimentação",
            category = "food",
            year = 2026,
            month = Month.JUN,
            entries = listOf(LedgerEntry("20260614", "Padaria", 93.01)),
        ).toExpense()

        assertEquals(emptyList(), monthLines(listOf(june), 2026, Month.JUL).eventual)
        assertEquals(listOf("Alimentação"), monthLines(listOf(june), 2026, Month.JUN).eventual.map { it.title })
    }

    @Test
    fun splits_income_recurring_and_eventual_and_ignores_other_years() {
        val rows = listOf(
            note("Deel Payments", ExpenseType.INCOME, mapOf(Month.JUL to 2348.46)),
            note("Luz", ExpenseType.RECURRING_VARIABLE),
            note("Condomínio", ExpenseType.RECURRING_FIXED),
            note("Property Tax", ExpenseType.EVENTUAL),
            note("Seguro residencial", ExpenseType.EVENTUAL, mapOf(Month.JAN to 1718.46), year = 2025),
        )

        val july = monthLines(rows, 2026, Month.JUL)

        assertEquals(listOf("Deel Payments"), july.income.map { it.title })
        assertEquals(listOf("Condomínio", "Luz"), july.recurring.map { it.title }, "sorted by title")
        assertEquals(listOf("Property Tax"), july.eventual.map { it.title }, "2025's note is another year")
        assertTrue(!july.isEmpty)
    }
}
