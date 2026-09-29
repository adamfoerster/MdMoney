package com.mdmoney.importer

/** A date as printed on a line: the year is often left out. */
data class DateParts(val year: Int?, val month: Int, val day: Int)

/**
 * One printed line of a statement, taken apart deterministically: the amount it ends in, the date
 * it carries (or the dated heading it sits under), and the description left once those are gone.
 *
 * Both extractors start here. The simple parser decides with fixed rules which lines are
 * transactions; the model decides that instead, but reads its amounts, dates and descriptions from
 * these same fields, so it has nothing to invent.
 */
data class StatementLine(
    val page: Int,
    val text: String,
    /** The amount the line ends in, always positive; null when it ends in none. */
    val amount: Double?,
    /** Direction printed right against the amount (`+ R$ 254,00`, `- US$ 17,04`, `CR`), if any. */
    val sign: Direction?,
    /** The line's own date, at its start or (with its year) mid-line. */
    val date: DateParts?,
    /** The last dated heading above this line (`01 AGO 2026 Total de saídas`), across pages. */
    val headingDate: DateParts?,
    /** The direction the current section implies ("Deposits", "Total de entradas"), if any. */
    val section: Direction?,
    val description: String,
    /** A date with no amount after it, or a dated total: it heads the lines below rather than being one. */
    val isHeading: Boolean,
    /** Carries a word only totals and balances use (total, saldo, balance, limite…). */
    val isSummary: Boolean,
) {
    /** The date that applies: the line's own, else its heading's. */
    val effectiveDate: DateParts? get() = date ?: headingDate

    /**
     * Whether any words remain once amounts and currency markers are gone. A row of figures (a
     * summary band, a daily-balance table) has no one to have paid, whoever says otherwise.
     */
    val hasWords: Boolean
        get() = description.replace(currencyMarker, " ").count { it.isLetter() } >= 2

    private companion object {
        val currencyMarker = Regex("""\b[A-Z]{1,3}\$|R\$|US\$|€|\b(BRL|USD|EUR)\b""")
    }
}

object StatementLines {

    private const val MONTH_WORD = """[A-Za-zÀ-ÿ]{3,10}\.?"""
    private val numericDate = Regex("""^\s*(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    private val wordDate = Regex("""^\s*(\d{1,2})\s+($MONTH_WORD)(?:\s+(\d{4}))?\b""")
    private val inlineDate = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{4})\b""")
    private val clockTime = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM|am|pm)?""")
    private val trailingAmount = Regex(
        """([-+−]\s*)?(?:[A-Z]{1,3}\$|\$|€)?\s*([-+−]\s*)?(\d{1,3}(?:[.,]\d{3})*[.,]\d{2})\s*([-+]|CR|DB|D|C)?\s*$""",
    )
    private val cardMask = Regex("""[•*·●]{2,}\s*\d{4}""")
    private val summaryWords = Regex(
        """\b(total|saldo|balance|subtotal|limite|limit|resumo|summary|juros|rendimento)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val incomingSection = Regex("""total de entradas|deposits|cr[eé]ditos""", RegexOption.IGNORE_CASE)
    private val outgoingSection = Regex("""total de sa[ií]das|withdrawals|d[eé]bitos""", RegexOption.IGNORE_CASE)

    /** A date found on a line and where it sits; only a date at the start can head other lines. */
    private class Found(val parts: DateParts, val range: IntRange, val leading: Boolean)

    /** Every non-blank line of [pages], in order. Numeric dates read month-first when [monthFirst]. */
    fun analyze(pages: List<String>, monthFirst: Boolean): List<StatementLine> {
        val out = mutableListOf<StatementLine>()
        var headingDate: DateParts? = null
        var section: Direction? = null
        pages.forEachIndexed { pageIndex, page ->
            for (raw in page.lines()) {
                if (raw.isBlank()) continue
                val line = raw.trim()
                when {
                    incomingSection.containsMatchIn(line) -> section = Direction.CREDIT
                    outgoingSection.containsMatchIn(line) -> section = Direction.DEBIT
                }
                val amountMatch = trailingAmount.find(line)
                val end = amountMatch?.range?.first ?: line.length
                val found = dateOn(line, monthFirst, before = end)
                // Whatever surrounds the date, up to the amount: `Purchase X 07/25/2026 - 6:25 PM` -> `Purchase X`.
                val body = if (found == null) {
                    line.substring(0, end)
                } else {
                    line.substring(0, found.range.first) + " " + line.substring((found.range.last + 1).coerceAtMost(end), end)
                }
                val amount = amountMatch?.let { StatementText.parseAmount(it.groupValues[3]) }?.takeIf { it != 0.0 }
                val isSummary = summaryWords.containsMatchIn(body)
                val isHeading = found != null && found.leading && (amount == null || isSummary)
                out.add(
                    StatementLine(
                        page = pageIndex,
                        text = line,
                        amount = amount,
                        sign = amountMatch?.let { signOf(it) },
                        date = found?.parts,
                        headingDate = headingDate,
                        section = section,
                        description = clean(body),
                        isHeading = isHeading,
                        isSummary = isSummary,
                    ),
                )
                if (isHeading) headingDate = found?.parts
            }
        }
        return out
    }

    private fun signOf(m: MatchResult): Direction? {
        val s = (m.groupValues[1] + m.groupValues[2] + m.groupValues[4]).trim()
        return when {
            s.startsWith("+") || s == "CR" || s == "C" -> Direction.CREDIT
            s.startsWith("-") || s.startsWith("−") || s == "DB" || s == "D" -> Direction.DEBIT
            else -> null
        }
    }

    /**
     * The date a line starts with, or failing that a full `a/b/yyyy` date anywhere before [before]
     * (the amount) — some statements print the description first. Only a date with its year is
     * trusted mid-line, so `Parcela 2/10` is never read as the 2nd of October.
     */
    private fun dateOn(line: String, monthFirst: Boolean, before: Int): Found? {
        numericDate.find(line)?.let { m -> return numeric(m, monthFirst, leading = true) }
        wordDate.find(line)?.let { m ->
            val month = StatementText.monthNumber(m.groupValues[2]) ?: return null
            val day = m.groupValues[1].toInt()
            if (day !in 1..31) return null
            return Found(DateParts(m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt(), month, day), m.range, leading = true)
        }
        return inlineDate.findAll(line).firstOrNull { it.range.last < before }?.let { numeric(it, monthFirst, leading = false) }
    }

    private fun numeric(m: MatchResult, monthFirst: Boolean, leading: Boolean): Found? {
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        val (month, day) = if (monthFirst) a to b else b to a
        if (month !in 1..12 || day !in 1..31) return null
        val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
        return Found(DateParts(year, month, day), m.range, leading)
    }

    private fun clean(text: String): String =
        text.replace(cardMask, " ").replace(clockTime, " ").replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '–', '·', '|')
            .trim()
}
