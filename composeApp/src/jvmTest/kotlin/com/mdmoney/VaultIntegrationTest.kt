package com.mdmoney

import com.mdmoney.data.CacheDb
import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end over the real Nubank vault files: reads them through the storage + parser + mapper,
 * writes a change back, and checks the on-disk result preserves unknown frontmatter and migrates
 * the legacy `fev` key to `feb`.
 */
class VaultIntegrationTest {

    private fun projectRoot(): File = File(System.getProperty("mdmoney.projectRoot") ?: ".")

    private fun makeVaultWithNubank(): File {
        val nubankSrc = File(projectRoot(), "nubank")
        check(nubankSrc.isDirectory) { "nubank folder not found at ${nubankSrc.absolutePath}" }
        val vault = Files.createTempDirectory("mdmoney-vault").toFile()
        val account = File(vault, "Nubank").apply { mkdirs() }
        nubankSrc.listFiles { f -> f.name.endsWith(".md") }!!.forEach { src ->
            src.copyTo(File(account, src.name), overwrite = true)
        }
        return vault
    }

    @Test
    fun reads_nubank_and_round_trips_a_change() = runBlocking<Unit> {
        val vault = makeVaultWithNubank()
        val storage = JvmVaultStorage(JvmPrefs(), vault)
        val repo = VaultRepository(storage)

        assertTrue("Nubank" in repo.accounts())

        val expenses = repo.loadExpenses("Nubank", 2026)
        assertEquals(19, expenses.size, "expected the 19 Nubank notes")

        val contador = expenses.first { it.title == "Contador" }
        // February value comes from the legacy `fev` key; it was paid.
        assertEquals(390.0, contador.amount(Month.FEB))
        assertTrue(contador.isPaid(Month.FEB))
        assertFalse(contador.isPaid(Month.AUG))

        // Mark August paid and persist.
        val updated = contador.copy(paid = contador.paid.toMutableMap().apply { put(Month.AUG, true) })
        repo.save(contador, updated)

        val raw = File(File(vault, "Nubank"), "Collegato - Contador.md").readText()
        assertTrue(raw.contains("billing_cycle: monthly"), raw)
        assertTrue(raw.contains("renewal_date: 2026-05-04"), raw)
        assertTrue(raw.contains("feb: 390"), raw)
        assertTrue(raw.contains("feb-paid: true"), raw)
        assertFalse(Regex("(?m)^fev:").containsMatchIn(raw), raw)
        assertTrue(raw.contains("aug-paid: true"), raw)

        val reread = repo.loadExpenses("Nubank", 2026).first { it.title == "Contador" }
        assertTrue(reread.isPaid(Month.AUG))
        assertTrue(reread.isPaid(Month.FEB))
        assertEquals(390.0, reread.amount(Month.FEB))

        vault.deleteRecursively()
    }

    @Test
    fun syncs_into_cache_and_aggregates() = runBlocking<Unit> {
        val vault = makeVaultWithNubank()
        val storage = JvmVaultStorage(JvmPrefs(), vault)
        val repo = VaultRepository(storage, CacheDb(":memory:"))

        // First paint would be empty; sync populates the cache from disk.
        assertTrue(repo.cachedExpenses("Nubank").isEmpty())
        repo.syncAccount("Nubank", 2026)

        val cached = repo.cachedExpenses("Nubank")
        assertEquals(19, cached.size, "cache should hold all 19 notes")

        // paidTotal (SQL SUM) must equal the paid amounts summed in memory.
        val expectedPaid = cached
            .filter { it.year == 2026 }
            .sumOf { e -> Month.ALL.sumOf { m -> if (e.isPaid(m)) (e.amount(m) ?: 0.0) else 0.0 } }
        assertEquals(expectedPaid, repo.paidTotal("Nubank", 2026), 0.001)

        // An edit updates the cache and rewrites the markdown, preserving unknown keys.
        val contador = cached.first { it.title == "Contador" }
        assertFalse(contador.isPaid(Month.AUG))
        val updated = contador.copy(paid = contador.paid.toMutableMap().apply { put(Month.AUG, true) })
        val resolved = repo.cacheSave(updated)
        repo.persist(contador, resolved)

        assertEquals(
            expectedPaid + contador.amount(Month.AUG)!!,
            repo.paidTotal("Nubank", 2026),
            0.001,
        )
        val raw = File(File(vault, "Nubank"), "Collegato - Contador.md").readText()
        assertTrue(raw.contains("billing_cycle: monthly"), raw)
        assertTrue(raw.contains("aug-paid: true"), raw)

        // Account metadata: opening balance round-trips through the vault-root file.
        repo.setInitialBalance("Nubank", 2026, 1000.0)
        assertEquals(1000.0, repo.accountMeta("Nubank").initialBalance(2026))
        assertTrue(File(vault, "Nubank.md").readText().contains("initial-2026: 1000"))

        vault.deleteRecursively()
    }

    @Test
    fun cache_keys_on_folder_name_not_conta_frontmatter() = runBlocking<Unit> {
        // Folder is lowercase `nubank` while every note declares `conta: Nubank`. The cache must key
        // on the folder (what the app lists and looks up), or the account paints empty.
        val nubankSrc = File(projectRoot(), "nubank")
        val vault = Files.createTempDirectory("mdmoney-case").toFile()
        val account = File(vault, "nubank").apply { mkdirs() }
        nubankSrc.listFiles { f -> f.name.endsWith(".md") }!!.forEach { src ->
            src.copyTo(File(account, src.name), overwrite = true)
        }

        val storage = JvmVaultStorage(JvmPrefs(), vault)
        val repo = VaultRepository(storage, CacheDb(":memory:"))

        assertTrue("nubank" in repo.accounts())
        repo.syncAccount("nubank", 2026)

        // Look up by the folder name the app actually uses — must not be empty.
        val cached = repo.cachedExpenses("nubank")
        assertEquals(19, cached.size, "notes must be cached under the folder name, not `conta`")
        assertTrue(cached.all { it.account == "nubank" }, "account identity is the folder")
        assertTrue(repo.paidTotal("nubank", 2026) > 0.0, "aggregation must find the folder's rows")

        // Saving a note preserves the original `conta: Nubank` casing rather than clobbering it.
        val contador = cached.first { it.title == "Contador" }
        repo.save(contador, contador.copy(paid = contador.paid.toMutableMap().apply { put(Month.AUG, true) }))
        val raw = File(account, "Collegato - Contador.md").readText()
        assertTrue(raw.contains("conta: Nubank"), raw)

        vault.deleteRecursively()
    }

    @Test
    fun income_is_read_aggregated_apart_from_expenses_and_round_trips() = runBlocking<Unit> {
        val vault = Files.createTempDirectory("mdmoney-income").toFile()
        val acct = File(vault, "Nubank").apply { mkdirs() }
        File(acct, "Salario.md").writeText(
            """
            ---
            title: Salário
            category: trabalho
            conta: Nubank
            year: 2026
            type: income
            jan: 5000
            jan-paid: true
            feb: 5200
            feb-paid: false
            ---
            """.trimIndent() + "\n",
        )
        File(acct, "Luz.md").writeText(
            """
            ---
            title: Luz
            conta: Nubank
            year: 2026
            type: recurring-variable
            jan: 300
            jan-paid: true
            feb: 250
            feb-paid: false
            ---
            """.trimIndent() + "\n",
        )

        val repo = VaultRepository(JvmVaultStorage(JvmPrefs(), vault), CacheDb(":memory:"))
        repo.syncAccount("Nubank", 2026)

        val salary = repo.cachedExpenses("Nubank").first { it.title == "Salário" }
        assertEquals(ExpenseType.INCOME, salary.type)
        assertTrue(salary.isIncome)

        // Only the *flagged* months count, and the two sides never bleed into each other.
        assertEquals(5000.0, repo.receivedTotal("Nubank", 2026), 0.001)
        assertEquals(300.0, repo.paidTotal("Nubank", 2026), 0.001)

        // Balance = opening + received - paid.
        repo.setInitialBalance("Nubank", 2026, 1000.0)
        val initial = repo.accountMeta("Nubank").initialBalance(2026)!!
        assertEquals(5700.0, initial + repo.receivedTotal("Nubank", 2026) - repo.paidTotal("Nubank", 2026), 0.001)

        // Marking February received moves only the income side.
        val updated = salary.copy(paid = salary.paid.toMutableMap().apply { put(Month.FEB, true) })
        repo.save(salary, updated)
        assertEquals(10200.0, repo.receivedTotal("Nubank", 2026), 0.001)
        assertEquals(300.0, repo.paidTotal("Nubank", 2026), 0.001)

        val raw = File(acct, "Salario.md").readText()
        assertTrue(raw.contains("type: income"), raw)
        assertTrue(raw.contains("feb-paid: true"), raw)

        vault.deleteRecursively()
    }

    @Test
    fun one_offs_group_into_one_ledger_note_per_month_and_category() = runBlocking<Unit> {
        val vault = Files.createTempDirectory("mdmoney-ledger").toFile()
        val acct = File(vault, "nubank").apply { mkdirs() }
        val repo = VaultRepository(JvmVaultStorage(JvmPrefs(), vault), CacheDb(":memory:"))

        // Three coffees and a parking ticket must become two files, not four.
        repo.addOneOff("nubank", 2026, Month.JUL, "Alimentação", "food", LedgerEntry("20260714", "Starbucks", 11.23))
        repo.addOneOff("nubank", 2026, Month.JUL, "Alimentação", "food", LedgerEntry("20260715", "Starbucks", 10.23))
        repo.addOneOff("nubank", 2026, Month.JUL, "Alimentação", "food", LedgerEntry("20260715", "Seven Eleven", 5.32))
        repo.addOneOff("nubank", 2026, Month.JUL, "Transporte", "car", LedgerEntry("20260715", "Estacionamento", 8.0))

        val files = acct.listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("2026 Jul - Alimentação.md", "2026 Jul - Transporte.md"), files)

        val raw = File(acct, "2026 Jul - Alimentação.md").readText()
        assertTrue(raw.contains("month: July"), raw)
        assertTrue(raw.contains("category: food"), raw)
        assertTrue(raw.contains("total: 26.78"), raw)
        assertTrue(raw.contains("| 20260715 | Starbucks    | 10.23  |"), raw)
        assertTrue(raw.contains("| 20260714 | Starbucks    | 11.23  |"), raw)

        // A different month is a different file.
        repo.addOneOff("nubank", 2026, Month.AUG, "Alimentação", "food", LedgerEntry("20260801", "Padaria", 4.0))
        assertTrue(File(acct, "2026 Aug - Alimentação.md").exists())

        // The ledger shows up as a normal expense row: its month carries the total, already paid.
        repo.syncAccount("nubank", 2026)
        val cached = repo.cachedExpenses("nubank")
        val food = cached.first { it.id == "2026 Jul - Alimentação" }
        assertTrue(food.ledger)
        assertEquals(26.78, food.amount(Month.JUL)!!, 0.001)
        assertTrue(food.isPaid(Month.JUL), "one-offs are money already spent")
        assertEquals(null, food.amount(Month.AUG))

        // ...so it counts against the balance without any special case (26.78 + 8.00 + 4.00).
        assertEquals(38.78, repo.paidTotal("nubank", 2026), 0.001)

        // Editing it through the generic expense writer must never clobber the table.
        repo.persist(food, food.copy(amounts = food.amounts.toMutableMap().apply { put(Month.JUL, 999.0) }))
        assertEquals(raw, File(acct, "2026 Jul - Alimentação.md").readText())

        // Removing a row rewrites the table and the total.
        val ledger = repo.loadLedger("nubank", "2026 Jul - Alimentação", 2026)!!
        repo.saveLedger(ledger.copy(entries = ledger.entries.filterNot { it.note == "Seven Eleven" }))
        val after = File(acct, "2026 Jul - Alimentação.md").readText()
        assertTrue(after.contains("total: 21.46"), after)
        assertFalse(after.contains("Seven Eleven"), after)

        vault.deleteRecursively()
    }

    @Test
    fun reads_imported_regions_note() = runBlocking<Unit> {
        val vault = Files.createTempDirectory("mdmoney-regions").toFile()
        val regions = File(vault, "Regions").apply { mkdirs() }
        // Exactly what scripts/import_regions.py produces for Água/Lixo 2026.
        File(regions, "Água - Lixo - 2026.md").writeText(
            """
            ---
            title: Água / Lixo
            category: casa
            conta: Regions
            year: 2026
            type: recurring-variable
            subStatus: Active
            projected: 1200
            jan: 94.03
            jan-paid: false
            feb: 175.36
            feb-paid: false
            mar: 89.13
            mar-paid: false
            ---
            """.trimIndent() + "\n",
        )

        val repo = VaultRepository(JvmVaultStorage(JvmPrefs(), vault))
        val e = repo.loadExpenses("Regions", 2026).single()

        assertEquals("Água / Lixo", e.title)
        assertEquals(2026, e.year)
        assertEquals(com.mdmoney.domain.ExpenseType.RECURRING_VARIABLE, e.type)
        assertEquals(1200.0, e.projected)
        assertEquals(94.03, e.amount(Month.JAN))
        assertEquals(175.36, e.amount(Month.FEB))

        vault.deleteRecursively()
    }
}
