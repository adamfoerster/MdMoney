package com.mdmoney.importer

/**
 * A line the model is asked about: one that ends in an amount. [number] is how the model refers to
 * it (1-based, per page).
 */
data class CandidateLine(val number: Int, val line: StatementLine) {
    val amount: Double get() = line.amount!!

    /** No date on the line or above it: the model has to read one. */
    val needsDate: Boolean get() = line.effectiveDate == null
}

/**
 * Picks a page's amount lines out of [StatementLines] and marks them for the model.
 *
 * This is what keeps the model honest: it never writes an amount, a date it can be given, or a
 * description — it only says which of these lines are transactions and what kind. The amount is the
 * one printed on the line, so it can't be invented, misread as a card code, or repeated.
 */
object CandidateLines {

    fun of(pageLines: List<StatementLine>): List<CandidateLine> =
        pageLines.filter { it.amount != null }.mapIndexed { i, line -> CandidateLine(i + 1, line) }

    /**
     * The page as the model sees it: every candidate tagged `[n]`, every other line indented, so a
     * date heading or a description wrapped onto the next line is still there to read.
     */
    fun mark(pageLines: List<StatementLine>): String {
        var n = 0
        return pageLines.joinToString("\n") { line ->
            if (line.amount != null) "[${++n}] ${line.text}" else "    ${line.text}"
        }
    }
}
