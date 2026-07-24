package com.mdmoney

import com.mdmoney.data.CacheDb
import com.mdmoney.data.VaultMigration
import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.Month
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The migration against a vault shaped like the real one: plain-text categories, a folder whose
 * casing differs from its `conta:`, a ledger, and a note carrying keys the app knows nothing about.
 */
class VaultMigrationTest {

    private fun vault(): File = Files.createTempDirectory("mdmoney-migrate").toFile()

    private fun writeNote(vault: File, path: String, content: String) {
        val file = File(vault, path)
        file.parentFile.mkdirs()
        file.writeText(content.trimIndent() + "\n")
    }

    /** The folder is `nubank`; every note calls it `Nubank`. Both facts have to survive. */
    private fun sampleVault(): File = vault().also { v ->
        writeNote(
            v, "nubank/Contador.md",
            """
            ---
            tags:
              - subscription
            title: Contador
            category: empresa
            billing_cycle: monthly
            conta: Nubank
            year: 2026
            type: recurring-fixed
            jan: 390
            jan-paid: true
            feb: 390
            feb-paid: false
            ---

            Anotações sobre o [[Contador]].
            """,
        )
        writeNote(
            v, "nubank/Luz.md",
            """
            ---
            title: Luz
            category: casa
            conta: Nubank
            year: 2026
            type: recurring-variable
            jan: 300
            jan-paid: true
            ---
            """,
        )
        writeNote(
            v, "nubank/2026 Jul - Alimentação.md",
            """
            ---
            title: Alimentação
            category: food
            conta: Nubank
            year: 2026
            month: July
            total: 10.23
            ---

            | Date     | Note      | Amount |
            | -------- | --------- | ------ |
            | 20260715 | Starbucks | 10.23  |
            """,
        )
        // A note with no category at all: it must be left without one rather than invent a link.
        writeNote(
            v, "nubank/Sem categoria.md",
            """
            ---
            title: Sem categoria
            conta: Nubank
            year: 2026
            type: eventual
            mar: 50
            ---
            """,
        )
    }

    private fun repoFor(vault: File) = VaultRepository(JvmVaultStorage(JvmPrefs(), vault), CacheDb(":memory:"))

    @Test
    fun gives_every_category_a_note_and_links_every_expense_to_it() = runBlocking<Unit> {
        val v = sampleVault()
        val repo = repoFor(v)
        val report = VaultMigration(repo, 2026).run()

        assertEquals(4, report.notesScanned)
        assertEquals(listOf("casa", "empresa", "food"), report.categoriesCreated.sorted())

        // Each category is a real note in the vault, with the fields a category may carry.
        val casa = File(v, "categories/casa.md").readText()
        assertTrue(casa.contains("type: category"), casa)
        assertTrue(casa.contains("title: Casa"), casa)
        assertTrue(casa.contains("description:"), "the field is there waiting to be filled in")

        val contador = File(v, "nubank/Contador.md").readText()
        assertTrue(contador.contains("""category: "[[empresa|Empresa]]""""), contador)
        assertTrue(contador.contains("""account: "[[nubank|Nubank]]""""), contador)

        // ...and the round-trip promise holds through all of it.
        assertTrue(contador.contains("billing_cycle: monthly"), "unknown keys survive")
        assertTrue(contador.contains("tags:\n  - subscription"), "block lists survive")
        assertTrue(contador.contains("Anotações sobre o [[Contador]]."), "the body survives")
        assertTrue(contador.contains("jan: 390"), "untouched amounts keep their exact text")

        val ledger = File(v, "nubank/2026 Jul - Alimentação.md").readText()
        assertTrue(ledger.contains("""category: "[[food|Food]]""""), ledger)
        assertTrue(ledger.contains("""account: "[[nubank|Nubank]]""""), ledger)
        assertTrue(ledger.contains("| 20260715 | Starbucks | 10.23  |"), "the table is untouched")

        val none = File(v, "nubank/Sem categoria.md").readText()
        assertFalse(none.contains("category:"), "no category means no link, not an invented one")
        assertTrue(none.contains("""account: "[[nubank|Nubank]]""""), none)

        v.deleteRecursively()
    }

    /** `conta: Nubank` is the name the user gave a folder called `nubank`; the move must not lose it. */
    @Test
    fun keeps_the_name_the_notes_gave_the_account() = runBlocking<Unit> {
        val v = sampleVault()
        val repo = repoFor(v)
        val report = VaultMigration(repo, 2026).run()

        assertEquals(listOf("nubank"), report.accountsTitled)
        val meta = File(v, "nubank.md").readText()
        assertTrue(meta.contains("title: Nubank"), meta)
        assertEquals("Nubank", repo.accountMeta("nubank").title)
        // The folder is still the identity; only the display half of the link changed.
        assertTrue(File(v, "nubank/Luz.md").readText().contains("""account: "[[nubank|Nubank]]""""))

        v.deleteRecursively()
    }

    /** Pointing this at a real vault twice must be as safe as pointing it once. */
    @Test
    fun running_it_twice_changes_nothing_the_second_time() = runBlocking<Unit> {
        val v = sampleVault()
        VaultMigration(repoFor(v), 2026).run()
        val after = File(v, "nubank").listFiles()!!.associate { it.name to it.readText() }

        val second = VaultMigration(repoFor(v), 2026).run()

        assertTrue(second.changedNothing, "second run: ${second.notesRewritten} ${second.categoriesCreated}")
        assertEquals(after, File(v, "nubank").listFiles()!!.associate { it.name to it.readText() })

        v.deleteRecursively()
    }

    /**
     * A category note is the user's to edit, and renaming it must not touch a single expense.
     *
     * This is why the app resolves a category's title from its *note* rather than from the link
     * text: in Obsidian a `[[casa|Casa]]` alias is a per-link snapshot, so a rename that chased
     * every link would rewrite the whole vault and clobber aliases people tuned by hand.
     */
    @Test
    fun renaming_a_category_touches_nothing_but_its_own_note() = runBlocking<Unit> {
        val v = sampleVault()
        VaultMigration(repoFor(v), 2026).run()
        val luzBefore = File(v, "nubank/Luz.md").readText()

        writeNote(
            v, "categories/casa.md",
            """
            ---
            type: category
            title: Nossa casa
            description: Água, luz, seguro e manutenção.
            ---

            Prosa que eu escrevi.
            """,
        )

        val repo = repoFor(v)
        val report = VaultMigration(repo, 2026).run()

        // What the user wrote stands, untouched.
        val casa = File(v, "categories/casa.md").readText()
        assertTrue(casa.contains("title: Nossa casa"), casa)
        assertTrue(casa.contains("description: Água, luz, seguro e manutenção."), casa)
        assertTrue(casa.contains("Prosa que eu escrevi."), casa)

        // The expense still points at `casa` and was not rewritten...
        assertEquals(luzBefore, File(v, "nubank/Luz.md").readText())
        assertTrue(report.changedNothing, "a rename is not a migration: ${report.notesRewritten}")

        // ...and the app reads the new title from the note, which is where a title lives.
        assertEquals("Nossa casa", repo.categories().first { it.slug == "casa" }.title)
        assertEquals("Água, luz, seguro e manutenção.", repo.categories().first { it.slug == "casa" }.description)
        repo.syncAccount("nubank", 2026)
        assertEquals("casa", repo.cachedExpenses("nubank").first { it.title == "Luz" }.category)

        v.deleteRecursively()
    }

    @Test
    fun the_categories_folder_is_not_an_account() = runBlocking<Unit> {
        val v = sampleVault()
        val repo = repoFor(v)
        VaultMigration(repo, 2026).run()

        assertTrue(File(v, "categories").isDirectory, "the folder is a sibling of the accounts on disk")
        assertEquals(listOf("nubank"), repo.accounts(), "but it is not one of them")

        v.deleteRecursively()
    }

    /** Migrating must not disturb a single number: the app reads exactly what it read before. */
    @Test
    fun the_money_is_identical_afterwards() = runBlocking<Unit> {
        val v = sampleVault()
        val before = repoFor(v).let { repo ->
            repo.syncAccount("nubank", 2026)
            repo.cachedExpenses("nubank").associate { it.id to it.amounts } to repo.paidTotal("nubank", 2026)
        }
        // 390 (Contador jan) + 300 (Luz jan) + 10.23 (the July ledger, already spent) = 700.23
        assertEquals(700.23, before.second, 0.001)

        VaultMigration(repoFor(v), 2026).run()

        val repo = repoFor(v)
        repo.syncAccount("nubank", 2026)
        assertEquals(before.first, repo.cachedExpenses("nubank").associate { it.id to it.amounts })
        assertEquals(700.23, repo.paidTotal("nubank", 2026), 0.001)

        // The categories now resolve to real notes rather than bare strings.
        val food = repo.cachedExpenses("nubank").first { it.id == "2026 Jul - Alimentação" }
        assertEquals("food", food.category)
        assertEquals(10.23, food.amount(Month.JUL)!!, 0.001)
        assertContains(repo.categories().map { it.slug }, "food")

        v.deleteRecursively()
    }
}
