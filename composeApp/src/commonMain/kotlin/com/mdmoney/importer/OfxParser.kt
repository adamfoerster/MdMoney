package com.mdmoney.importer

import kotlin.math.abs

/**
 * Reads an OFX statement (the file banks export as `.ofx`). Unlike a PDF there is nothing to guess:
 * every line carries its own date, signed amount and description, so no model or heuristics are
 * involved and nothing is marked doubtful.
 *
 * OFX 1.x is SGML — tags are rarely closed (`<TRNAMT>-12.50`) — while 2.x is XML with closing tags.
 * Both are read by the same tag scan, which only cares about the leaf tags it knows.
 */
object OfxParser {

    /** Whether [bytes] look like an OFX file, whatever its name: the header or the root tag is up front. */
    fun looksLikeOfx(bytes: ByteArray): Boolean {
        val head = String(CharArray(minOf(bytes.size, 4096)) { (bytes[it].toInt() and 0xFF).toChar() }).uppercase()
        return "OFXHEADER" in head || "<OFX>" in head
    }

    fun parse(bytes: ByteArray): ExtractionResult = parse(decode(bytes))

    fun parse(text: String): ExtractionResult {
        var currency: String? = null
        var start: SimpleDate? = null
        var end: SimpleDate? = null
        val transactions = mutableListOf<StatementTransaction>()
        var current: MutableMap<String, String>? = null

        fun finish() {
            current?.let { fields -> transaction(fields)?.let { transactions.add(it) } }
            current = null
        }

        for (m in TAG.findAll(text)) {
            val closing = m.groupValues[1] == "/"
            val tag = m.groupValues[2].uppercase()
            val value = unescape(m.groupValues[3].trim())
            when {
                tag == "STMTTRN" -> {
                    finish()
                    if (!closing) current = mutableMapOf()
                }
                // A closing aggregate ends the open transaction (SGML files have no </STMTTRN> at all).
                closing && tag in ENDS -> finish()
                closing -> {}
                current != null -> if (value.isNotEmpty()) current!!.putIfAbsent(tag, value)
                tag == "CURDEF" && value.isNotEmpty() -> currency = currency ?: value.uppercase()
                tag == "DTSTART" -> start = start ?: parseDate(value)
                tag == "DTEND" -> end = end ?: parseDate(value)
            }
        }
        finish()

        val period = if (start != null && end != null) StatementPeriod(start, end) else null
        val header = StatementHeader(period = period, currency = currency)
        return ExtractionResult(
            header = header,
            transactions = transactions,
            reconciliation = StatementExtractor.reconcile(transactions, header),
            usedModel = false,
            fromOfx = true,
        )
    }

    /** UTF-8 when the bytes are valid UTF-8, otherwise Latin-1 / Windows-1252 — what OFX 1.x banks send. */
    fun decode(bytes: ByteArray): String {
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        return try {
            bytes.decodeToString(start, bytes.size, throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            String(CharArray(bytes.size - start) { cp1252(bytes[start + it].toInt() and 0xFF) })
        }
    }

    private fun cp1252(b: Int): Char = when (b) {
        0x80 -> '€'
        0x91 -> '‘'
        0x92 -> '’'
        0x93 -> '“'
        0x94 -> '”'
        0x96 -> '–'
        0x97 -> '—'
        else -> b.toChar()
    }

    private fun transaction(f: Map<String, String>): StatementTransaction? {
        val signed = parseAmount(f["TRNAMT"] ?: return null) ?: return null
        if (signed == 0.0) return null
        val date = parseDate(f["DTPOSTED"] ?: f["DTUSER"] ?: return null) ?: return null
        val direction = if (signed < 0) Direction.DEBIT else Direction.CREDIT
        val description = describe(f["NAME"], f["MEMO"]).ifBlank { f["TRNTYPE"].orEmpty() }
        val kind = when (f["TRNTYPE"]?.uppercase()) {
            "FEE", "SRVCHG" -> TxKind.FEE
            "XFER" -> TxKind.TRANSFER
            else -> HeuristicStatementParser.guessKind(description, direction)
        }
        return StatementTransaction(
            date = date,
            description = description,
            amount = abs(signed),
            direction = direction,
            kind = kind,
            category = null,
        )
    }

    /** Name and memo both when they say different things (many banks repeat one in the other). */
    private fun describe(name: String?, memo: String?): String {
        val n = name?.trim().orEmpty()
        val m = memo?.trim().orEmpty()
        return when {
            n.isEmpty() -> m
            m.isEmpty() || m.equals(n, ignoreCase = true) -> n
            m.contains(n, ignoreCase = true) -> m
            n.contains(m, ignoreCase = true) -> n
            else -> "$n - $m"
        }
    }

    /** `yyyyMMdd` optionally followed by a time and `[offset:zone]`; only the day matters. */
    fun parseDate(value: String): SimpleDate? {
        val digits = value.takeWhile { it.isDigit() }
        if (digits.length < 8) return null
        return SimpleDate(digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt())
            .takeIf { it.isValid() }
    }

    /** A signed amount with a dot decimal as OFX defines — or a comma, which some banks write anyway. */
    fun parseAmount(value: String): Double? {
        var s = value.trim().replace(" ", "")
        val comma = s.lastIndexOf(',')
        val dot = s.lastIndexOf('.')
        s = when {
            comma > dot -> s.replace(".", "").replace(',', '.')
            else -> s.replace(",", "")
        }
        return s.toDoubleOrNull()
    }

    private fun unescape(s: String): String =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    /** `<TAG>value` up to the next `<`; group 1 is the slash of a closing tag. */
    private val TAG = Regex("""<(/?)([A-Za-z0-9._]+)>([^<]*)""")

    /** Closing tags that end a transaction list or statement, in case `</STMTTRN>` is missing. */
    private val ENDS = setOf("BANKTRANLIST", "STMTRS", "CCSTMTRS", "LEDGERBAL", "AVAILBAL")
}
