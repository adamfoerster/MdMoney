package com.mdmoney.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatementGrammarTest {

    @Test
    fun categories_are_the_only_choices_and_none_is_always_one() {
        val g = StatementGrammar.lines(listOf(true, false, false), listOf("food", "saúde", "casa nova", "bad\"slug", "none"))
        val cat = g.lines().single { it.startsWith("cat ::= ") }
        assertEquals(
            """cat ::= "\"none\"" | "\"food\"" | "\"saúde\"" | "\"casa nova\""""",
            cat,
            "none first, accents and spaces kept, a slug that can't be a JSON literal left out",
        )
    }

    @Test
    fun each_line_gets_exactly_one_answer_and_a_date_only_when_it_needs_one() {
        val root = StatementGrammar.lines(listOf(false, true, false), emptyList()).lines().first()
        assertEquals(
            """root ::= "{\"lines\":[" "{\"n\":1,\"tx\":" ans "," "{\"n\":2,\"tx\":" ansdated "," "{\"n\":3,\"tx\":" ans "]}"""",
            root,
        )
    }

    @Test
    fun every_kind_and_direction_is_offered() {
        val g = StatementGrammar.lines(listOf(true), emptyList())
        TxKind.entries.forEach { assertTrue(g.contains("\"\\\"${it.id}\\\"\""), it.id) }
        Direction.entries.forEach { assertTrue(g.contains("\"\\\"${it.id}\\\"\""), it.id) }
    }

    @Test
    fun every_rule_used_is_defined() {
        for (grammar in listOf(StatementGrammar.lines(listOf(false, true), listOf("food")), StatementGrammar.header())) {
            val defined = grammar.lines().map { it.substringBefore(" ::=") }.toSet()
            val bodies = grammar.lines().joinToString(" ") { referencesIn(it.substringAfter("::=")) }
            val used = Regex("""[a-z]+""").findAll(bodies).map { it.value }.toSet()
            assertTrue(defined.containsAll(used), "undefined: ${used - defined}")
            assertTrue("root" in defined)
            assertFalse(grammar.contains("\r"))
        }
    }

    /** A rule body with its "literals" and [classes] blanked out, leaving the rule names it refers to. */
    private fun referencesIn(body: String): String = buildString {
        var i = 0
        while (i < body.length) {
            val close = when (body[i]) {
                '"' -> '"'
                '[' -> ']'
                else -> null
            }
            if (close == null) {
                append(body[i++])
                continue
            }
            i++
            while (i < body.length && body[i] != close) i += if (body[i] == '\\') 2 else 1
            i++
            append(' ')
        }
    }
}
