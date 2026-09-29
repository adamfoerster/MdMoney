package com.mdmoney.importer

import com.mdmoney.domain.Category
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatementExtractorTest {

    /** Answers the header question and then each page from a script, recording what it was asked. */
    private class FakeLlm(private val header: String, private val pages: List<String>) : LocalLlm {
        val grammars = mutableListOf<String>()
        val messages = mutableListOf<String>()
        private var page = 0
        override suspend fun complete(system: String, user: String, grammar: String, maxTokens: Int): String {
            grammars.add(grammar)
            messages.add(user)
            return if (system == StatementPrompt.headerSystem) header else pages[page++]
        }
        override fun close() {}
    }

    private val page1 = """
        FATURA 01 OUT 2026 EMISSÃO E ENVIO 24 SET 2026
        TRANSAÇÕES DE 25 AGO A 24 SET
        Jane Doe R$ 337,32
        25 AGO •••• 9723 Oticas Exemplo - Parcela 2/10 R$ 170,40
        26 AGO •••• 3054 Mercado Bella R$ 166,92
        Estorno Loja + R$ 20,00
        Limite total R$ 8.735,41
    """.trimIndent()

    private fun candidates(page: String) = CandidateLines.of(StatementLines.analyze(listOf(page), monthFirst = false))

    @Test
    fun amount_lines_are_numbered_for_the_model() {
        val c = candidates(page1)
        assertEquals(listOf(337.32, 170.40, 166.92, 20.0, 8735.41), c.map { it.amount })
        assertEquals(listOf(true, false, false, true, true), c.map { it.needsDate }, "only lines with no date of their own or above")
        assertEquals(Direction.CREDIT, c[3].line.sign)
        assertEquals("Oticas Exemplo - Parcela 2/10", c[1].line.description, "date, card mask and amount taken out")
        val marked = CandidateLines.mark(StatementLines.analyze(listOf(page1), monthFirst = false)).lines()
        assertEquals("    FATURA 01 OUT 2026 EMISSÃO E ENVIO 24 SET 2026", marked[0])
        assertEquals("[2] 25 AGO •••• 9723 Oticas Exemplo - Parcela 2/10 R$ 170,40", marked[3])
        assertEquals("[5] Limite total R$ 8.735,41", marked[6])
    }

    @Test
    fun the_model_classifies_lines_and_everything_else_comes_from_the_page() = runBlocking {
        val llm = FakeLlm(
            header = """{"start":"2026-08-25","end":"2026-09-24","currency":"BRL","debits":337.32,"credits":999.99}""",
            pages = listOf(
                """{"lines":[""" +
                    """{"n":1,"tx":false},""" +
                    """{"n":2,"tx":true,"dir":"debit","kind":"purchase","cat":"saude"},""" +
                    """{"n":3,"tx":true,"dir":"debit","kind":"purchase","cat":"none"},""" +
                    // Undated, so the model gives a date; it says debit, but the line prints a `+`.
                    """{"n":4,"tx":true,"day":2,"month":3,"dir":"debit","kind":"refund","cat":"none"},""" +
                    """{"n":5,"tx":false}""" +
                    """]}""",
            ),
        )
        val result = StatementExtractor(llm, listOf(Category("saude", "Saúde"))).extract(listOf(page1), fallbackYear = 1999)

        assertTrue(result.usedModel)
        assertEquals(3, result.transactions.size)
        val (glasses, market, refund) = result.transactions
        assertEquals(SimpleDate(2026, 8, 25), glasses.date, "date from the line, year from the detected period")
        assertEquals(170.40, glasses.amount, "amount from the line")
        assertEquals("Oticas Exemplo - Parcela 2/10", glasses.description)
        assertEquals("saude", glasses.category)
        assertNull(market.category, "'none' means no category")
        assertEquals(Direction.CREDIT, refund.direction, "the printed + wins")
        assertEquals(20.0, refund.amount)
        assertEquals(Doubt.READERS_DISAGREE, refund.doubt, "the rules find no date for it, so they don't take it for a transaction")
        assertTrue(glasses.verified && market.verified)

        assertEquals(337.32, result.header.declaredDebits, "printed on the page: kept")
        assertNull(result.header.declaredCredits, "999.99 appears nowhere in the statement: dropped")
        assertEquals(337.32, result.reconciliation.debits, "170.40 + 166.92")
        assertEquals(true, result.reconciliation.debitsMatch)

        val grammar = llm.grammars.last()
        assertTrue(grammar.contains("\"\\\"saude\\\"\""), "the vault's categories are the only ones allowed")
        assertTrue(grammar.contains("\"{\\\"n\\\":5,\\\"tx\\\":\" ansdated"), "exactly one answer per numbered line")
        assertFalse(grammar.contains("\\\"n\\\":6,"))
        assertTrue(llm.messages.last().contains("[4] Estorno Loja + R$ 20,00"), llm.messages.last())
    }

    @Test
    fun a_failing_model_falls_back_to_the_simple_parser() = runBlocking {
        val broken = object : LocalLlm {
            override suspend fun complete(system: String, user: String, grammar: String, maxTokens: Int): String =
                throw IllegalStateException("out of memory")
            override fun close() {}
        }
        val result = StatementExtractor(broken, emptyList()).extract(listOf(page1), fallbackYear = 1999)
        assertFalse(result.usedModel)
        assertEquals(2, result.transactions.size, "the simple parser only trusts dated lines")
        assertEquals(337.32, result.reconciliation.debits, "170.40 + 166.92")
    }

    @Test
    fun a_page_without_amounts_is_not_sent_to_the_model() = runBlocking {
        val llm = FakeLlm(header = "{}", pages = listOf("""{"lines":[]}"""))
        StatementExtractor(llm, emptyList()).extract(listOf(page1, "Terms and conditions, no numbers."), fallbackYear = 2026)
        assertEquals(2, llm.grammars.size, "one header call, one page call")
    }

    @Test
    fun model_json_is_read_defensively() {
        val c = candidates(page1)
        assertEquals(emptyMap(), ExtractionJson.lineAnswers("not json", c))
        assertEquals(emptyMap(), ExtractionJson.lineAnswers("""{"other":1}""", c))
        // Cut off at the token limit mid-object: the complete answers before the cut survive.
        val truncated = """{"lines":[{"n":2,"tx":true,"dir":"debit","kind":"purchase","cat":"none"},{"n":3,"tx":true,"di"""
        assertEquals(listOf(170.40), ExtractionJson.lineAnswers(truncated, c).values.map { it.amount })
        // A line number that doesn't exist, an undated line left without a date, an unknown kind.
        val odd = """{"lines":[{"n":9,"tx":true},{"n":1,"tx":true,"dir":"debit"},{"n":3,"tx":true,"dir":"debit","kind":"??"}]}"""
        val read = ExtractionJson.lineAnswers(odd, c).values.toList()
        assertEquals(1, read.size)
        assertEquals(166.92, read[0].amount)
        assertEquals(TxKind.PURCHASE, read[0].kind, "guessed from the words")
    }

    @Test
    fun the_model_and_the_rules_vote_on_every_line() {
        // A statement laid out by day headings, with sections saying which way money went.
        val page = """
            01 AGO 2026 Total de saídas - 169,90
            Transferência enviada pelo Pix Maria 100,00
            09 AGO 2026 Total de entradas + 254,00
            Transferência recebida pelo Pix Pedro 104,00
        """.trimIndent()
        val lines = StatementLines.analyze(listOf(page), monthFirst = false).filter { it.amount != null }
        fun model(line: StatementLine, dir: Direction, kind: TxKind) =
            RawTransaction(2026, 8, 1, line.description, line.amount!!, dir, kind, category = "casa")
        val (heading, sent, _, received) = lines

        // Regression: the 1.5B model took every daily total for a transaction, burying the review
        // under unticked "Total de saídas" lines. A total is never one.
        assertNull(StatementExtractor.vote(heading, model(heading, Direction.DEBIT, TxKind.PURCHASE)))
        // Both agree, but the model has the direction backwards: the section heading wins, and a
        // kind that contradicts it is re-guessed. The model's category is kept.
        val agreed = assertNotNull(StatementExtractor.vote(sent, model(sent, Direction.CREDIT, TxKind.INCOME)))
        assertFalse(agreed.disputed)
        assertEquals(Direction.DEBIT, agreed.direction)
        assertEquals(TxKind.TRANSFER, agreed.kind)
        assertEquals("casa", agreed.category)
        // The model misses a line the rules are sure of: still shown, unticked.
        val missed = assertNotNull(StatementExtractor.vote(received, null))
        assertTrue(missed.disputed)
        assertEquals(Direction.CREDIT, missed.direction)
    }

    @Test
    fun a_row_of_figures_is_never_a_transaction_whatever_the_model_says() {
        // Regression: the model called a statement's summary band a credit of US$ 13,17.
        val summary = "Ending balance Fees charged Total spent Starting balance\nUS$ 201,67 US$ 0,00 US$ 847,50 US$ 13,17"
        val c = candidates(summary)
        assertEquals(1, c.size)
        val answer = """{"lines":[{"n":1,"tx":true,"day":20,"month":7,"dir":"credit","kind":"income","cat":"none"}]}"""
        assertEquals(emptyMap(), ExtractionJson.lineAnswers(answer, c))
    }

    @Test
    fun the_header_ignores_what_it_cannot_use() {
        val h = ExtractionJson.header("""{"start":"2026-09-01","end":"2026-08-01","currency":"R$","debits":null,"credits":12}""")
        assertNull(h.period, "an end before the start is no period")
        assertNull(h.currency, "not an ISO code")
        assertNull(h.declaredDebits)
        assertEquals(12.0, h.declaredCredits)
    }
}
