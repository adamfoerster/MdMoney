package com.mdmoney.importer

import kotlin.math.abs
import kotlin.math.round

/**
 * Pure text helpers shared by both extractors: reading amounts and dates the way bank statements
 * print them, in Portuguese, English and Spanish, and settling what the statement leaves unsaid.
 */
object StatementText {

    /**
     * Month names and abbreviations in the three languages, lowercase and without accents.
     * Ambiguous abbreviations resolve the same in every language they appear in (`mar`, `jun`).
     */
    private val monthNames: Map<String, Int> = buildMap {
        val names = listOf(
            listOf("jan", "janeiro", "january", "ene", "enero"),
            listOf("fev", "fevereiro", "feb", "february", "febrero"),
            listOf("mar", "marco", "march", "marzo"),
            listOf("abr", "abril", "apr", "april"),
            listOf("mai", "maio", "may", "mayo"),
            listOf("jun", "junho", "june", "junio"),
            listOf("jul", "julho", "july", "julio"),
            listOf("ago", "agosto", "aug", "august"),
            listOf("set", "setembro", "sep", "sept", "september", "septiembre"),
            listOf("out", "outubro", "oct", "october", "octubre"),
            listOf("nov", "novembro", "november", "noviembre"),
            listOf("dez", "dezembro", "dec", "december", "dic", "diciembre"),
        )
        names.forEachIndexed { i, aliases -> aliases.forEach { put(it, i + 1) } }
    }

    /** The month number for a name or abbreviation in pt/en/es (`AGO`, `August`, `setembro`), or null. */
    fun monthNumber(name: String): Int? = monthNames[fold(name).trimEnd('.')]

    /** Lowercase without the accents Portuguese and Spanish month names carry (`março` -> `marco`). */
    private fun fold(text: String): String = text.lowercase()
        .replace('ç', 'c').replace('á', 'a').replace('â', 'a').replace('ã', 'a')
        .replace('é', 'e').replace('ê', 'e').replace('í', 'i').replace('ó', 'o').replace('ô', 'o')
        .replace('ú', 'u')

    private const val MONTH_WORD = """[A-Za-zÀ-ÿ]{3,10}\.?"""

    /**
     * Parses an amount as printed: `1.234,56`, `1,234.56`, `R$ 5.879,28`, `- US$ 17,04`, `(84.94)`,
     * `$5,069.33`. The last separator followed by one or two digits is the decimal point; every
     * other `.`/`,` groups thousands. Returns a signed value, or null when there are no digits.
     */
    fun parseAmount(raw: String): Double? {
        val text = raw.trim()
        if (text.none { it.isDigit() }) return null
        val negative = text.contains('-') || text.contains('−') || (text.startsWith("(") && text.endsWith(")"))
        val digits = text.filter { it.isDigit() || it == '.' || it == ',' }.trim('.', ',')
        val lastSep = digits.indexOfLast { it == '.' || it == ',' }
        val value = if (lastSep >= 0 && digits.length - lastSep - 1 in 1..2) {
            val whole = digits.substring(0, lastSep).filter { it.isDigit() }.ifEmpty { "0" }
            "$whole.${digits.substring(lastSep + 1)}".toDoubleOrNull()
        } else {
            digits.filter { it.isDigit() }.toDoubleOrNull()
        } ?: return null
        return if (negative) -value else value
    }

    /**
     * True when [amount] appears in [text] in any of the ways a statement could print it
     * (`1234.56`, `1.234,56`, `1,234.56`, `1234,56`) — the check that catches an amount the model
     * invented.
     */
    fun containsAmount(text: String, amount: Double): Boolean {
        val cents = round(abs(amount) * 100).toLong()
        val whole = (cents / 100).toString()
        val frac = (cents % 100).toString().padStart(2, '0')
        val grouped = whole.reversed().chunked(3).joinToString(".").reversed()
        val candidates = setOf(
            "$whole.$frac", "$whole,$frac",
            "$grouped,$frac", "${grouped.replace('.', ',')}.$frac",
        )
        return candidates.any { containsNumber(text, it) }
    }

    /**
     * [number] occurs in [text] as a whole number: `12.34` must not match inside `412.345`, nor
     * `234,56` inside `1.234,56`.
     */
    private fun containsNumber(text: String, number: String): Boolean {
        var from = 0
        while (true) {
            val i = text.indexOf(number, from)
            if (i < 0) return false
            val before = text.getOrNull(i - 1)
            val groupedBefore = (before == '.' || before == ',') && text.getOrNull(i - 2)?.isDigit() == true
            val after = text.getOrNull(i + number.length)
            if (before?.isDigit() != true && !groupedBefore && after?.isDigit() != true) return true
            from = i + 1
        }
    }

    // --- dates ---

    private val isoDate = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val slashDate = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{4})\b""")
    // 01 DE AGOSTO DE 2026 · 24 SET 2026 · 1st August 2026
    private val dayMonthYear = Regex("""\b(\d{1,2})(?:st|nd|rd|th|º)?\s+(?:de\s+)?($MONTH_WORD)\s+(?:de\s+)?(\d{4})\b""", RegexOption.IGNORE_CASE)
    // August 22, 2026 · July, 1st 2026
    private val monthDayYear = Regex("""\b($MONTH_WORD),?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})\b""", RegexOption.IGNORE_CASE)

    /** A full date found in text, with where it sits. */
    private data class Found(val date: SimpleDate, val start: Int, val end: Int)

    /**
     * Every date in [text] that carries its year. Numeric `a/b/yyyy` dates are read day-first unless
     * [monthFirst] says otherwise (a US statement), or the first number can't be a month.
     */
    private fun fullDates(text: String, monthFirst: Boolean): List<Found> {
        val out = mutableListOf<Found>()
        isoDate.findAll(text).forEach { m ->
            val (y, mo, d) = m.destructured
            add(out, SimpleDate(y.toInt(), mo.toInt(), d.toInt()), m.range)
        }
        slashDate.findAll(text).forEach { m ->
            val (a, b, y) = m.destructured
            val (mo, d) = if (monthFirst && a.toInt() <= 12 || b.toInt() > 12) a to b else b to a
            add(out, SimpleDate(y.toInt(), mo.toInt(), d.toInt()), m.range)
        }
        dayMonthYear.findAll(text).forEach { m ->
            val (d, name, y) = m.destructured
            monthNumber(name)?.let { add(out, SimpleDate(y.toInt(), it, d.toInt()), m.range) }
        }
        monthDayYear.findAll(text).forEach { m ->
            val (name, d, y) = m.destructured
            monthNumber(name)?.let { add(out, SimpleDate(y.toInt(), it, d.toInt()), m.range) }
        }
        return out.sortedBy { it.start }
    }

    private fun add(out: MutableList<Found>, date: SimpleDate, range: IntRange) {
        if (date.isValid()) out.add(Found(date, range.first, range.last + 1))
    }

    private val rangeWord = Regex("""^\s*[,.]?\s*(?:a|à|ate|até|to|through|thru|until|-|–|of|al|hasta)\s*$""", RegexOption.IGNORE_CASE)

    // "25 AGO a 24 SET" — a bill's cycle printed without years.
    private val shortRange = Regex(
        """\b(\d{1,2})\s+($MONTH_WORD)\s+(?:a|à|ate|até|to|-|–|al)\s+(\d{1,2})\s+($MONTH_WORD)(?!\s+\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The period [text] covers, read from the first two full dates joined by a range word
     * ("August 22, 2026 through September 22, 2026", "01 DE AGOSTO DE 2026 a 31 DE AGOSTO DE 2026").
     * A card bill that prints its cycle without years ("25 AGO a 24 SET") takes the year from the
     * nearest full date after it, usually the issue or due date.
     */
    fun detectPeriod(text: String, monthFirst: Boolean = false): StatementPeriod? {
        val dates = fullDates(text, monthFirst)
        for (i in 0 until dates.size - 1) {
            val a = dates[i]
            val b = dates[i + 1]
            if (b.start < a.end || b.start - a.end > 12) continue
            if (!rangeWord.matches(text.substring(a.end, b.start))) continue
            if (a.date <= b.date) return StatementPeriod(a.date, b.date)
        }
        val m = shortRange.find(text) ?: return null
        val (d1, n1, d2, n2) = m.destructured
        val m1 = monthNumber(n1) ?: return null
        val m2 = monthNumber(n2) ?: return null
        val anchor = dates.firstOrNull { it.start >= m.range.last } ?: dates.lastOrNull() ?: return null
        // The anchor (issue date) is at or just after the cycle's end.
        val endYear = if (m2 > anchor.date.month) anchor.date.year - 1 else anchor.date.year
        val startYear = if (m1 > m2) endYear - 1 else endYear
        val start = SimpleDate(startYear, m1, d1.toInt())
        val end = SimpleDate(endYear, m2, d2.toInt())
        return if (start.isValid() && end.isValid() && start <= end) StatementPeriod(start, end) else null
    }

    /**
     * The year for a `day/month` printed without one: the year in [period] that month falls in —
     * so a December purchase on a bill running December to January lands in the earlier year.
     * Without a period, [fallbackYear].
     */
    fun resolveYear(month: Int, period: StatementPeriod?, fallbackYear: Int): Int {
        if (period == null) return fallbackYear
        if (period.start.year == period.end.year) return period.start.year
        return if (month >= period.start.month) period.start.year else period.end.year
    }

    /**
     * A currency code from the symbols and words a statement uses. `US$` beats a bare `$`, and a
     * page mentioning `R$` is Brazilian however many dollar signs it has elsewhere.
     */
    fun detectCurrency(text: String): String? {
        val usd = Regex("""US\$|USD""").findAll(text).count()
        val brl = Regex("""R\$|BRL""").findAll(text).count()
        val eur = Regex("""€|EUR\b""").findAll(text).count()
        val bare = Regex("""(?<![A-Z])\$""").findAll(text).count()
        return when {
            brl > 0 && brl >= usd -> "BRL"
            usd > 0 -> "USD"
            eur > 0 -> "EUR"
            bare > 0 -> "USD"
            else -> null
        }
    }

    /**
     * True when numeric dates on this statement are month-first: US-dollar statements print
     * `08/24`, everyone else `24/08`. A date whose first number is above 12 settles it outright.
     */
    fun isMonthFirst(text: String, currency: String?): Boolean {
        val pairs = Regex("""\b(\d{1,2})/(\d{1,2})\b""").findAll(text).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toList()
        if (pairs.any { it.first > 12 && it.second <= 12 }) return false
        if (pairs.any { it.second > 12 && it.first <= 12 }) return true
        return currency == "USD"
    }
}
