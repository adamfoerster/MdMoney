package com.mdmoney.importer

import com.mdmoney.domain.Category

/**
 * The instructions the model reads. Written in English, which small models follow most reliably,
 * while the statements themselves may be in any language.
 */
object StatementPrompt {

    /** Roughly what a page may take before the prompt no longer fits the context window. */
    const val MAX_PAGE_CHARS = 14_000

    val headerSystem: String = """
        You read the first page of a bank statement or a credit card bill and report facts printed on it.
        - start, end: the period the statement covers, as YYYY-MM-DD.
        - currency: the ISO 4217 code of its amounts (BRL for R$, USD for $ or US$, EUR for €).
        - debits: the printed total of money out (withdrawals, purchases, "total de saídas"). For a card bill, the total of this bill's purchases.
        - credits: the printed total of money in (deposits, credits, "total de entradas").
        Amounts use a dot for decimals (1.234,56 is 1234.56). Answer null for anything not printed.
    """.trimIndent()

    fun transactionsSystem(header: StatementHeader, monthFirst: Boolean, categories: List<Category>): String {
        val period = header.period?.let { "${iso(it.start)} to ${iso(it.end)}" } ?: "unknown"
        val dateOrder = if (monthFirst) "MM/DD (month first)" else "DD/MM (day first)"
        val categoryList = if (categories.isEmpty()) {
            "none"
        } else {
            categories.joinToString("; ") { "${it.slug} (${it.title})" } + "; or none"
        }
        return """
            You read one page of a bank statement or credit card bill, taken from a PDF.
            Every line that prints an amount is marked [n]. Answer for every marked line, in order.
            tx: true when the line is one transaction on the account: a purchase, a fee, a payment, a transfer sent or received, a deposit, a refund.
            tx: false for everything else: balances, totals and subtotals (including daily totals such as "Total de saídas"), credit limits, interest rates, installment or minimum-payment offers, summaries.
            For a transaction:
            - dir: debit when money left the account, credit when money came in (deposits, transfers received, refunds). A section heading such as "Deposits" or "Total de entradas" tells you.
            - kind: purchase, fee, income, transfer (between accounts, Pix, wire), card_payment (paying a credit card bill) or refund.
            - cat: the category that fits best: $categoryList.
            - day, month: only asked for when the date isn't printed where the program could find it; read it from the page. The statement covers $period. Numeric dates are $dateOrder.
        """.trimIndent()
    }

    /** The page with its amount lines numbered (see [CandidateLines.mark]). */
    fun pageMessage(pageLines: List<StatementLine>, index: Int, count: Int): String =
        "Page ${index + 1} of $count:\n\n${CandidateLines.mark(pageLines)}"

    fun headerMessage(firstPage: String): String = compact(firstPage)

    /**
     * Collapses the long runs of spaces a layout-preserving extraction pads columns with — they cost
     * tokens and tell the model nothing a few spaces don't — and caps the page's length.
     */
    fun compact(page: String): String = page
        .replace(Regex("""[ \t]{4,}"""), "   ")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
        .take(MAX_PAGE_CHARS)

    private fun iso(d: SimpleDate) =
        "${d.year}-${d.month.toString().padStart(2, '0')}-${d.day.toString().padStart(2, '0')}"
}
