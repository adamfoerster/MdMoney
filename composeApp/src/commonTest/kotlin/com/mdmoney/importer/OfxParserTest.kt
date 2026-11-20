package com.mdmoney.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The OFX reader over both dialects banks send. Totals below were added up by hand from the fixtures. */
class OfxParserTest {

    /** OFX 1.x: SGML, no closing tags on leaf elements, header lines before the root. */
    private val sgml = """
        OFXHEADER:100
        DATA:OFXSGML
        VERSION:102
        CHARSET:1252

        <OFX>
        <BANKMSGSRSV1><STMTTRNRS><STMTRS>
        <CURDEF>BRL
        <BANKTRANLIST>
        <DTSTART>20260801000000[-3:BRT]
        <DTEND>20260831000000[-3:BRT]
        <STMTTRN>
        <TRNTYPE>DEBIT
        <DTPOSTED>20260805120000[-3:BRT]
        <TRNAMT>-50.25
        <FITID>1
        <NAME>MERCADO CENTRAL
        <MEMO>COMPRA CARTAO
        </STMTTRN>
        <STMTTRN>
        <TRNTYPE>CREDIT
        <DTPOSTED>20260810
        <TRNAMT>1200.00
        <FITID>2
        <MEMO>SALARIO
        </STMTTRN>
        <STMTTRN>
        <TRNTYPE>SRVCHG
        <DTPOSTED>20260815
        <TRNAMT>-12.90
        <FITID>3
        <NAME>TARIFA MENSAL
        </STMTTRN>
        </BANKTRANLIST>
        <LEDGERBAL><BALAMT>1136.85<DTASOF>20260831</LEDGERBAL>
        </STMTRS></STMTTRNRS></BANKMSGSRSV1>
        </OFX>
    """.trimIndent()

    @Test
    fun reads_sgml_statement_with_signed_amounts_period_and_currency() {
        val r = OfxParser.parse(sgml)
        assertTrue(r.fromOfx)
        assertFalse(r.usedModel)
        assertEquals("BRL", r.header.currency)
        assertEquals(StatementPeriod(SimpleDate(2026, 8, 1), SimpleDate(2026, 8, 31)), r.header.period)
        assertEquals(3, r.transactions.size)

        val (market, salary, fee) = r.transactions
        assertEquals(SimpleDate(2026, 8, 5), market.date)
        assertEquals(50.25, market.amount)
        assertEquals(Direction.DEBIT, market.direction)
        assertEquals(TxKind.PURCHASE, market.kind)
        assertEquals("MERCADO CENTRAL - COMPRA CARTAO", market.description)

        assertEquals(1200.0, salary.amount)
        assertEquals(Direction.CREDIT, salary.direction)
        assertEquals(TxKind.INCOME, salary.kind)
        assertEquals("SALARIO", salary.description)

        assertEquals(TxKind.FEE, fee.kind)
        assertEquals(12.9, fee.amount)

        // Debits 50.25 + 12.90 = 63.15; credits 1200.00. Every OFX line starts ticked-worthy.
        assertEquals(63.15, r.reconciliation.debits)
        assertEquals(1200.0, r.reconciliation.credits)
        assertTrue(r.transactions.all { it.verified })
    }

    @Test
    fun reads_xml_statement_with_closing_tags_and_entities() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS>
            <CURDEF>USD</CURDEF>
            <BANKTRANLIST>
            <DTSTART>20260101</DTSTART><DTEND>20260131</DTEND>
            <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260102</DTPOSTED><TRNAMT>-9.99</TRNAMT><NAME>AT&amp;T WIRELESS</NAME></STMTTRN>
            <STMTTRN><TRNTYPE>CREDIT</TRNTYPE><DTPOSTED>20260120</DTPOSTED><TRNAMT>25.00</TRNAMT><NAME>REFUND STORE</NAME></STMTTRN>
            </BANKTRANLIST>
            </CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1></OFX>
        """.trimIndent()
        val r = OfxParser.parse(xml)
        assertEquals("USD", r.header.currency)
        assertEquals(2, r.transactions.size)
        assertEquals("AT&T WIRELESS", r.transactions[0].description)
        assertEquals(TxKind.REFUND, r.transactions[1].kind)
        assertEquals(25.0, r.reconciliation.credits)
    }

    @Test
    fun a_transaction_without_closing_tag_ends_at_the_next_one() {
        val text = "<OFX><STMTTRN><DTPOSTED>20260301<TRNAMT>-1.00<NAME>A<STMTTRN><DTPOSTED>20260302<TRNAMT>-2.00<NAME>B</OFX>"
        val r = OfxParser.parse(text)
        assertEquals(listOf("A", "B"), r.transactions.map { it.description })
        assertEquals(listOf(1.0, 2.0), r.transactions.map { it.amount })
    }

    @Test
    fun skips_zero_amounts_and_impossible_dates() {
        val text = """
            <OFX><STMTTRN><DTPOSTED>20260301<TRNAMT>0.00<NAME>ZERO</STMTTRN>
            <STMTTRN><DTPOSTED>20260231<TRNAMT>-5.00<NAME>FEB 31</STMTTRN>
            <STMTTRN><DTPOSTED>20260302<TRNAMT>-5.00<NAME>OK</STMTTRN></OFX>
        """.trimIndent()
        assertEquals(listOf("OK"), OfxParser.parse(text).transactions.map { it.description })
    }

    @Test
    fun parses_comma_and_thousands_separators() {
        assertEquals(-1234.56, OfxParser.parseAmount("-1.234,56"))
        assertEquals(-12.5, OfxParser.parseAmount("-12,50"))
        assertEquals(1234.56, OfxParser.parseAmount("1,234.56"))
        assertEquals(7.0, OfxParser.parseAmount("7"))
        assertNull(OfxParser.parseAmount("abc"))
    }

    @Test
    fun date_ignores_time_and_zone() {
        assertEquals(SimpleDate(2026, 8, 5), OfxParser.parseDate("20260805120000.000[-3:BRT]"))
        assertNull(OfxParser.parseDate("2026"))
    }

    @Test
    fun decodes_windows_1252_when_not_utf8() {
        // "AÇÃO" with Ç = 0xC7 and Ã = 0xC3, which is not valid UTF-8.
        val body = "<OFX><STMTTRN><DTPOSTED>20260301<TRNAMT>-3.00<NAME>A".encodeToByteArray() +
            byteArrayOf(0xC7.toByte(), 0xC3.toByte()) + "O</STMTTRN></OFX>".encodeToByteArray()
        assertEquals("AÇÃO", OfxParser.parse(body).transactions.single().description)
    }

    @Test
    fun recognises_ofx_by_content_not_by_name() {
        assertTrue(OfxParser.looksLikeOfx(sgml.encodeToByteArray()))
        assertTrue(OfxParser.looksLikeOfx("<?xml version=\"1.0\"?>\n<?OFX OFXHEADER=\"200\"?>\n<ofx></ofx>".encodeToByteArray()))
        assertFalse(OfxParser.looksLikeOfx("%PDF-1.7\n1 0 obj".encodeToByteArray()))
    }
}
