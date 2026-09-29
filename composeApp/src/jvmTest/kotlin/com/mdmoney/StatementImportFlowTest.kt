package com.mdmoney

import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import com.mdmoney.importer.ImportEntry
import com.mdmoney.platform.JvmImportPlatform
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
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
}
