package com.mdmoney.importer

/**
 * The fallback extractor for when no model can run: fixed rules over [StatementLines]. It knows no
 * bank in particular, but its rules are words (`total`, `saldo`, `deposits`…) that a new layout or
 * language can slip past — which is what the model is for — and the review screen says when it ran.
 *
 * A line is a transaction when it ends in an amount, has a date of its own or sits under a dated
 * heading (`01 AGO 2026 Total de saídas`), isn't itself a heading or a total, and has some text
 * besides numbers.
 */
object HeuristicStatementParser {

    private val creditWords = Regex(
        """(entrada|recebid|dep[oó]sito|deposit|cr[eé]dito\b|credit|transfer(ência)? from|estorno|refund|reembolso|devolu)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(pages: List<String>, header: StatementHeader, fallbackYear: Int): List<RawTransaction> {
        val monthFirst = StatementText.isMonthFirst(pages.joinToString("\n"), header.currency)
        return StatementLines.analyze(pages, monthFirst).mapNotNull { decide(it) }
    }

    /** The rules' verdict on one line: the transaction it is, or null when it isn't one. */
    fun decide(line: StatementLine): RawTransaction? {
        val amount = line.amount ?: return null
        if (line.isHeading || line.isSummary) return null
        val date = line.effectiveDate ?: return null
        if (!line.hasWords) return null
        val direction = directionOf(line) ?: if (creditWords.containsMatchIn(line.description)) Direction.CREDIT else Direction.DEBIT
        return RawTransaction(
            year = date.year,
            month = date.month,
            day = date.day,
            description = line.description,
            amount = amount,
            direction = direction,
            kind = guessKind(line.description, direction),
        )
    }

    /**
     * The direction the page itself states for [line] — a printed sign or the section it sits in —
     * or null when it states none.
     */
    fun directionOf(line: StatementLine): Direction? = when {
        // Under a dated heading the heading's "entradas"/"saídas" is the reliable signal: a dash
        // before the amount there is as likely a separator in the counterparty's name.
        line.date == null && line.section != null -> line.section
        line.sign != null -> line.sign
        else -> line.section
    }

    /** A best guess at the kind from the words alone; the review screen lets the user fix it. */
    fun guessKind(description: String, direction: Direction): TxKind {
        val d = description.lowercase()
        return when {
            Regex("""estorno|refund|reembolso|devolu""").containsMatchIn(d) -> TxKind.REFUND
            Regex("""pagamento (de fatura|recebido)|payment received|pago de tarjeta|autopay|card payment""").containsMatchIn(d) -> TxKind.CARD_PAYMENT
            Regex("""\b(tarifa|fee|iof|anuidade|juros|interest|multa|comiss|service (assessment|charge))""").containsMatchIn(d) -> TxKind.FEE
            direction == Direction.CREDIT && Regex("""transfer|pix|ted|doc\b""").containsMatchIn(d) -> TxKind.TRANSFER
            direction == Direction.CREDIT -> TxKind.INCOME
            Regex("""transfer[eê]ncia enviada|transfer to|transfer\b|\bted\b""").containsMatchIn(d) -> TxKind.TRANSFER
            else -> TxKind.PURCHASE
        }
    }
}
