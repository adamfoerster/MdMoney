package com.mdmoney.importer

/**
 * GBNF grammars (llama.cpp's constrained-decoding format) for the model's answers.
 *
 * A small model left to itself writes almost-JSON: a trailing comma, a note in prose, a made-up
 * field — or it loops, repeating one line until it runs out of tokens. Constraining every token to
 * the grammar makes the output parse every time, fixes how many answers there are, and lets the
 * category be chosen only from the vault's own categories. Built here rather than converted from a
 * JSON schema so that the same text drives the engine on every platform.
 */
object StatementGrammar {

    /** No category fits: the user picks one on review. */
    const val NO_CATEGORY = "none"

    /**
     * One answer per marked line, in order, one for each entry of [needsDate]:
     * `{"lines":[{"n":1,"tx":false},{"n":2,"tx":true,"dir":"debit","kind":"purchase","cat":"food"}]}`.
     *
     * The line numbers are written into the grammar, so the model can neither skip a line, repeat
     * one, nor keep going after the last. It writes no amount and no description — those come from
     * the line — and a date only where [needsDate] says the line and its headings carry none.
     */
    fun lines(needsDate: List<Boolean>, categorySlugs: List<String>): String {
        val answers = needsDate.withIndex().joinToString(" \",\" ") { (i, dated) ->
            "\"{\\\"n\\\":${i + 1},\\\"tx\\\":\" " + if (dated) "ansdated" else "ans"
        }
        val kinds = listOf(field("dir", "dir"), field("kind", "kind"), field("cat", "cat"))
        val dates = listOf(field("day", "day"), field("month", "month"))
        return rules(
            "root ::= \"{\\\"lines\\\":[\" ${answers.ifEmpty { "" }} \"]}\"",
            "ans ::= \"false}\" | \"true,\" " + kinds.joinToString(" \",\" ") + " \"}\"",
            "ansdated ::= \"false}\" | \"true,\" " + (dates + kinds).joinToString(" \",\" ") + " \"}\"",
            """day ::= [1-9] | [12] [0-9] | "3" [01]""",
            """month ::= [1-9] | "1" [0-2]""",
            "dir ::= " + Direction.entries.joinToString(" | ") { literal(it.id) },
            "kind ::= " + TxKind.entries.joinToString(" | ") { literal(it.id) },
            "cat ::= " + categoryChoices(categorySlugs).joinToString(" | ") { literal(it) },
        )
    }

    /** `{"start":"2026-08-22","end":"2026-09-22","currency":"USD","debits":3334.47,"credits":7565.88}` */
    fun header(): String = rules(
        "root ::= \"{\" " + listOf(
            field("start", "date"),
            field("end", "date"),
            field("currency", "currency"),
            field("debits", "total"),
            field("credits", "total"),
        ).joinToString(" \",\" ") + " \"}\"",
        """date ::= "\"" [0-9]{4} "-" [0-9]{2} "-" [0-9]{2} "\"" | "null"""",
        """currency ::= "\"" [A-Z]{3} "\"" | "null"""",
        """total ::= [0-9]{1,9} ( "." [0-9]{1,2} )? | "null"""",
    )

    /** [NO_CATEGORY] first, then every slug that can be written as a JSON string literal. */
    private fun categoryChoices(categorySlugs: List<String>): List<String> =
        listOf(NO_CATEGORY) + categorySlugs
            .filter { it.isNotBlank() && it != NO_CATEGORY && '"' !in it && '\\' !in it }
            .distinct()

    /** `"name":rule` — one key of an object, in the order the grammar fixes. */
    private fun field(name: String, rule: String) = "\"\\\"$name\\\":\" $rule"

    /** Joins [lines] with the shared string rules; a description is bounded so a confused model can't ramble. */
    private fun rules(vararg lines: String): String = (
        lines.toList() + listOf(
            """str ::= "\"" char{0,80} "\""""",
            """char ::= [^"\\\x00-\x1F] | "\\" ["\\/nt]""",
        )
        ).joinToString("\n")

    /** The JSON string `"text"` as a GBNF literal. Callers keep `"` and `\` out of [text]. */
    internal fun literal(text: String): String = "\"\\\"$text\\\"\""
}
