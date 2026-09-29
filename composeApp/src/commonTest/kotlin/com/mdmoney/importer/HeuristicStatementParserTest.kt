package com.mdmoney.importer

import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The simple parser over excerpts of real statement layouts (as PDFBox reads them, names and
 * numbers changed). Every expected total below was added up by hand from the fixture.
 */
class HeuristicStatementParserTest {

    private fun parse(text: String, currency: String?, period: StatementPeriod?): List<StatementTransaction> {
        val header = StatementHeader(period = period, currency = currency)
        return HeuristicStatementParser.parse(listOf(text), header, fallbackYear = 2000)
            .mapNotNull { StatementExtractor.settle(it, text, header, 2000, verify = false) }
    }

    private fun List<StatementTransaction>.total(direction: Direction) =
        round(filter { it.direction == direction }.sumOf { it.amount } * 100) / 100

    // A US checking statement: MM/DD dates, sections for deposits and withdrawals, a daily-balance table.
    private val checking = """
        August 22, 2026 through  September 22, 2026
        SUMMARY
        Beginning Balance  ${'$'}1,113.24 Minimum Balance  ${'$'}887
        Deposits & Credits  ${'$'}5,070.88  +
         Withdrawals  ${'$'}112.51 -
        DEPOSITS & CREDITS
        08/25 'acme, Payroll Payments  Jane Doe 5,069.33
        09/08 Card Credit  Dl          *Ub  4121  Sao Paulo        05426    3914 1.55
        Total Deposits & Credits  ${'$'}5,070.88
        WITHDRAWALS
        08/24 Recurring Card Transaction  Blue Stream      4899  954-753-0100  FL 33065    3914 84.94
        09/14 Card Purchase  Sunpass*acc1442  4784  888-865-5352  FL 33434    3914 10.00
        09/14 Card Purchase  Sunpass*acc1442  4784  888-865-5352  FL 33434    3914 10.00
        Total Withdrawals  ${'$'}104.94
        FEES
        09/08 International Service  Assessment Dl          *Ub 7.57
        Total Fees  ${'$'}7.57
        08/24 6,056.51 09/09 7,336.82 09/16
    """.trimIndent()

    @Test
    fun a_us_checking_statement_reads_by_section() {
        val t = parse(checking, "USD", StatementPeriod(SimpleDate(2026, 8, 22), SimpleDate(2026, 9, 22)))
        assertEquals(6, t.size, t.joinToString("\n"))
        assertEquals(5070.88, t.total(Direction.CREDIT), "5069.33 + 1.55")
        assertEquals(112.51, t.total(Direction.DEBIT), "84.94 + 10 + 10 + 7.57")
        assertEquals(SimpleDate(2026, 8, 25), t.first().date, "month-first, year from the period")
        assertEquals(2, t.count { it.description.startsWith("Card Purchase Sunpass") }, "an honest duplicate stays")
        assertEquals(TxKind.FEE, t.last().kind)
    }

    // A Brazilian account statement: dated day headings with undated lines under them.
    private val account = """
        01 DE AGOSTO DE 2026 a 31 DE AGOSTO DE 2026 VALORES EM R$
        Saldo inicial 16.103,92
        R$ 13.365,35 Total de entradas +25.254,00
        Total de saídas -1.518,18
        Movimentações
        01 AGO 2026 Total de saídas - 169,90
        Transferência enviada pelo Pix Maria Silva - •••.113.159-•• - NU 100,00
        PAGAMENTOS - IP (0260) Agência: 1 Conta:
        Transferência enviada pelo Pix JOAO SOUZA - •••.857.339- 69,90
        03 AGO 2026 Total de saídas - 1.348,28
        Pagamento de boleto efetuado CONDOMINIO RESIDENCIAL 1.348,28
        09 AGO 2026 Total de entradas + 254,00
        Transferência recebida pelo Pix PEDRO ANDRADE - •••.366.366-•• - BCO 104,00
        Transferência recebida pelo Pix ANA LIMA - •••.352.189-•• - BCO DO 150,00
        14 AGO 2026 Total de entradas + 25.000,00
        Transferência recebida pelo Pix EMPRESA EXEMPLO LTDA - 25.000,00
        Extrato gerado dia 23 de setembro de 2026 às 17:47 1 de 7
    """.trimIndent()

    @Test
    fun a_brazilian_statement_reads_by_day_heading() {
        val t = parse(account, "BRL", StatementPeriod(SimpleDate(2026, 8, 1), SimpleDate(2026, 8, 31)))
        assertEquals(6, t.size, t.joinToString("\n"))
        assertEquals(1518.18, t.total(Direction.DEBIT), "100 + 69.90 + 1348.28")
        // The dash before 25.000,00 separates the company's name; the heading says money came in.
        assertEquals(25254.0, t.total(Direction.CREDIT), "104 + 150 + 25000")
        assertEquals(SimpleDate(2026, 8, 14), t.last().date)
        assertEquals(TxKind.TRANSFER, t.last().kind)
    }

    // A card bill: `25 AGO` with no year, installments, masked card numbers.
    private val bill = """
        FATURA 01 OUT 2026 EMISSÃO E ENVIO 24 SET 2026
        TRANSAÇÕES DE 25 AGO A 24 SET
        Jane Doe R$ 606,48
        25 AGO •••• 9723 Oticas Exemplo - Parcela 2/10 R$ 170,40
        26 AGO •••• 3054 Mercado Bella R$ 166,92
        29 AGO Transação de NuTag R$ 15,00
        04 SET •••• 3054 Netflix.Com R$ 85,70
        23 SET •••• 9723 Farmacia - Parcela 1/2 R$ 168,46
        Pagamentos R$ 0,00
        As informações de limite são referentes à data de emissão da fatura (24/09/2026) às 03:24. Para
    """.trimIndent()

    @Test
    fun a_card_bill_reads_line_by_line() {
        val period = StatementText.detectPeriod(bill)
        val t = parse(bill, "BRL", period)
        assertEquals(5, t.size, t.joinToString("\n"))
        assertEquals(606.48, t.total(Direction.DEBIT), "170.40 + 166.92 + 15 + 85.70 + 168.46 — the printed subtotal")
        assertEquals("Oticas Exemplo - Parcela 2/10", t.first().description, "card mask gone, installment kept")
        assertEquals(SimpleDate(2026, 9, 23), t.last().date)
        assertTrue(t.none { it.amount == 606.48 }, "the cardholder's subtotal is not a purchase")
    }

    // An account that prints the description first and the date mid-line.
    private val inline = """
        July, 1st 2026 of July, 31st 2026
        Transactions
        Purchase AIRPORT STORE C 130511 07/25/2026 - 6:25 PM - US$ 17,04
        Settled
        Purchase BARNES & NOBLE #3573 07/21/2026 - 8:30 PM - US$ 19,25
        Settled
        Transfer from Acme, IZ6AHK7NU 07/09/2026 - 4:02 AM + US$ 995,00
        Settled
    """.trimIndent()

    @Test
    fun a_date_in_the_middle_of_the_line_is_found() {
        val t = parse(inline, "USD", StatementText.detectPeriod(inline))
        assertEquals(3, t.size, t.joinToString("\n"))
        assertEquals(36.29, t.total(Direction.DEBIT), "17.04 + 19.25")
        assertEquals(995.0, t.total(Direction.CREDIT))
        assertEquals("Purchase AIRPORT STORE C 130511", t.first().description, "date and time cut out")
        assertEquals(SimpleDate(2026, 7, 25), t.first().date)
    }
}
