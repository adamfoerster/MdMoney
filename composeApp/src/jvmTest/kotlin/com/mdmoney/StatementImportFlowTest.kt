package com.mdmoney

import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import com.mdmoney.importer.ImportEntry
import com.mdmoney.importer.StatementExtractor
import com.mdmoney.platform.JvmImportPlatform
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Statement import against a real (temporary) vault and real PDF bytes. */
class StatementImportFlowTest {

    private val account = "nubank"
    private val vault = Files.createTempDirectory("mdmoney-import").toFile().also { File(it, "nubank").mkdirs() }
    private val repo = VaultRepository(JvmVaultStorage(JvmPrefs(), vault))

    @AfterTest
    fun tearDown() {
        vault.deleteRecursively()
    }

    private fun spend(day: Int, note: String, amount: Double, title: String = "Alimentação", category: String? = "food") =
        ImportEntry(2026, Month.AUG, title, category, income = false, entry = LedgerEntry("202608${day.toString().padStart(2, '0')}", note, amount))

    private fun received(day: Int, note: String, amount: Double) =
        ImportEntry(2026, Month.AUG, "Entradas", null, income = true, entry = LedgerEntry("202608${day.toString().padStart(2, '0')}", note, amount))

    private val statement = listOf(
        spend(1, "Mercado", 50.0),
        spend(2, "Padaria", 7.5),
        spend(2, "Tarifa", 12.9, title = "Importado", category = null),
        spend(6, "Pedágio", 10.0, title = "Transporte", category = "transport"),
        spend(6, "Pedágio", 10.0, title = "Transporte", category = "transport"),
        received(9, "Pix recebido", 104.0),
    )

    @Test
    fun lines_group_into_one_note_per_month_and_title() = runBlocking {
        val result = repo.importEntries(account, statement)
        assertEquals(6, result.added)
        assertEquals(0, result.skipped)
        assertEquals(4, result.notes)

        val food = File(vault, "nubank/2026 Aug - Alimentação.md").readText()
        assertTrue(food.contains("total: 57.5"), food)
        val transport = File(vault, "nubank/2026 Aug - Transporte.md").readText()
        assertTrue(transport.contains("total: 20"), "both honest tolls are kept: $transport")
        assertTrue(File(vault, "categories/transport.md").isFile, "a new category gets its note")
    }

    @Test
    fun importing_the_same_statement_twice_adds_nothing() = runBlocking {
        repo.importEntries(account, statement)
        val before = vault.walkTopDown().filter { it.isFile }.associate { it.path to it.readText() }

        val again = repo.importEntries(account, statement)
        assertEquals(0, again.added)
        assertEquals(6, again.skipped)
        val after = vault.walkTopDown().filter { it.isFile }.associate { it.path to it.readText() }
        assertEquals(before, after, "not a byte changed")
    }

    @Test
    fun a_line_filed_under_another_category_is_still_recognised() = runBlocking {
        repo.importEntries(account, listOf(spend(1, "Mercado", 50.0)))
        val moved = repo.importEntries(account, listOf(spend(1, "Mercado", 50.0, title = "Casa", category = "casa")))
        assertEquals(1, moved.skipped)
        assertTrue(!File(vault, "nubank/2026 Aug - Casa.md").exists())
    }

    @Test
    fun received_money_counts_as_income() = runBlocking {
        repo.importEntries(account, statement)
        val income = File(vault, "nubank/2026 Aug - Entradas.md").readText()
        assertTrue(income.contains("type: income"), income)

        repo.syncAccount(account, 2026)
        assertEquals(104.0, repo.receivedTotal(account, 2026), 0.001)
        assertEquals(90.4, repo.paidTotal(account, 2026), 0.001, "50 + 7.50 + 12.90 + 10 + 10")
    }

    private val walmart = """
        ---
        title: Walmart
        account: "[[nubank|Nubank]]"
        year: 2026
        type: recurring-variable
        renewal_date: 2027-01-01
        jul: 100
        jul-paid: false
        aug: 20.5
        aug-paid: false
        ---

        Compras do mês, sempre no cartão.
    """.trimIndent() + "\n"

    private fun inWalmart(day: Int, note: String, amount: Double) =
        ImportEntry(2026, Month.AUG, "Walmart", null, income = false, LedgerEntry("202608${day.toString().padStart(2, '0')}", note, amount), noteId = "Walmart")

    @Test
    fun lines_filed_under_a_plain_note_grow_its_month_and_keep_what_it_had() = runBlocking {
        File(vault, "nubank/Walmart.md").writeText(walmart)
        val result = repo.importEntries(account, listOf(inWalmart(3, "Compra A", 10.25), inWalmart(9, "Compra B", 4.0)))

        assertEquals(2, result.added)
        assertEquals(1, result.notes)
        val text = File(vault, "nubank/Walmart.md").readText()
        assertTrue("aug: 34.75" in text, "20.50 + 10.25 + 4.00: $text")
        assertTrue("aug-paid: true" in text, text)
        assertTrue("jul: 100" in text && "jul-paid: false" in text, "other months untouched: $text")
        assertTrue("renewal_date: 2027-01-01" in text, "unknown keys survive: $text")
        assertTrue("Compras do mês, sempre no cartão." in text, "prose survives: $text")
        assertTrue(Regex("""\|\s*20260803\s*\|\s*Compra A\s*\|\s*10\.25\s*\|""").containsMatchIn(text), "the rows are kept: $text")
        assertTrue(!vault.walkTopDown().any { it.name.startsWith("2026 Aug - Walmart") }, "no ledger was made")
    }

    @Test
    fun importing_into_a_plain_note_twice_adds_nothing() = runBlocking {
        File(vault, "nubank/Walmart.md").writeText(walmart)
        val lines = listOf(inWalmart(3, "Compra A", 10.25))
        repo.importEntries(account, lines)
        val once = File(vault, "nubank/Walmart.md").readText()

        val again = repo.importEntries(account, lines)
        assertEquals(0, again.added)
        assertEquals(1, again.skipped)
        assertEquals(once, File(vault, "nubank/Walmart.md").readText(), "not a byte changed")
    }

    @Test
    fun a_second_import_extends_the_same_table() = runBlocking {
        File(vault, "nubank/Walmart.md").writeText(walmart)
        repo.importEntries(account, listOf(inWalmart(3, "Compra A", 10.0)))
        repo.importEntries(account, listOf(inWalmart(9, "Compra B", 5.0)))
        val text = File(vault, "nubank/Walmart.md").readText()
        assertEquals(1, Regex("""\| Date""").findAll(text).count(), "one table, not two: $text")
        assertTrue("aug: 35.5" in text, text)
        assertTrue("Compra A" in text && "Compra B" in text, text)
    }

    @Test
    fun a_vanished_plain_note_falls_back_to_a_ledger() = runBlocking {
        val result = repo.importEntries(account, listOf(inWalmart(3, "Compra A", 10.0)))
        assertEquals(1, result.added)
        assertTrue(File(vault, "nubank/2026 Aug - Walmart.md").isFile)
    }

    @Test
    fun pdf_text_keeps_each_row_on_one_line() {
        // Laid out as a statement is: date, description and amount drawn separately, right column first.
        val bytes = PDDocument().use { doc ->
            val page = PDPage().also { doc.addPage(it) }
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            PDPageContentStream(doc, page).use { cs ->
                fun text(x: Float, y: Float, s: String) {
                    cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(x, y); cs.showText(s); cs.endText()
                }
                text(500f, 700f, "170,40")
                text(500f, 680f, "166,92")
                text(40f, 700f, "25 AGO")
                text(40f, 680f, "26 AGO")
                text(120f, 700f, "Oticas Exemplo")
                text(120f, 680f, "Mercado Bella")
            }
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        val lines = JvmImportPlatform.pdfPagesOf(bytes).single().lines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(listOf("25 AGO Oticas Exemplo 170,40", "26 AGO Mercado Bella 166,92"), lines)
    }

    /** One page per entry of [rows], each row drawn as a statement line. */
    private fun statementPdf(vararg rows: String): ByteArray = PDDocument().use { doc ->
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        rows.forEach { row ->
            val page = PDPage().also { doc.addPage(it) }
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(40f, 700f); cs.showText(row); cs.endText()
            }
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    @Test
    fun each_line_remembers_the_page_it_was_printed_on() = runBlocking {
        val bytes = statementPdf("25/08/2026 Mercado Bella 166,92", "27/08/2026 Oticas Exemplo 170,40")
        val result = StatementExtractor(null, emptyList()).extract(JvmImportPlatform.pdfPagesOf(bytes), fallbackYear = 2026)
        assertEquals(listOf("Mercado Bella" to 0, "Oticas Exemplo" to 1), result.transactions.map { it.description to it.page })
    }

    @Test
    fun a_page_is_drawn_at_the_width_asked_for() {
        val bytes = statementPdf("25/08/2026 Mercado Bella 166,92", "27/08/2026 Oticas Exemplo 170,40")
        val image = ImageIO.read(ByteArrayInputStream(JvmImportPlatform.renderPageOf(bytes, 1, 400)))
        assertEquals(400, image.width)
        // A letter page is 612 × 792 points.
        assertEquals(518, image.height)
        val inked = (0 until image.width).any { x -> (0 until image.height).any { y -> image.getRGB(x, y) and 0xFFFFFF != 0xFFFFFF } }
        assertTrue(inked, "the page's text is drawn, not a blank")
    }
}
