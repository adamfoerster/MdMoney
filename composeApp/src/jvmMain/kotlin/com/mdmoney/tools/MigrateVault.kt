package com.mdmoney.tools

import com.mdmoney.data.VaultMigration
import com.mdmoney.data.VaultRepository
import com.mdmoney.data.VaultStorage
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import com.mdmoney.platform.currentYear
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Rewrites a vault to the current note format — see [VaultMigration]. Run it with:
 *
 * ```
 * ./gradlew :composeApp:migrateVault -Pvault=/path/to/vault           # preview
 * ./gradlew :composeApp:migrateVault -Pvault=/path/to/vault -Papply   # write
 * ```
 *
 * The vault path is always explicit and previewing is the default, because the one thing this must
 * never do is rewrite a vault nobody asked it to.
 */
fun main(args: Array<String>) {
    val path = args.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: fail("usage: migrateVault <vault path> [--apply]")
    val apply = args.contains("--apply")

    val root = File(path)
    if (!root.isDirectory) fail("not a folder: ${root.absolutePath}")

    val storage: VaultStorage = JvmVaultStorage(JvmPrefs(), root).let { if (apply) it else DryRun(it) }
    val report = runBlocking { VaultMigration(VaultRepository(storage), currentYear()).run() }

    println((if (apply) "Migrated " else "Would migrate ") + root.absolutePath)
    println("  notes scanned:      ${report.notesScanned}")
    println("  notes rewritten:    ${report.notesRewritten.size}")
    report.notesRewritten.forEach { println("      $it") }
    println("  categories created: ${report.categoriesCreated.size}")
    report.categoriesCreated.forEach { println("      categories/$it.md") }
    println("  accounts titled:    ${report.accountsTitled.size}")
    report.accountsTitled.forEach { println("      $it.md") }
    if (report.changedNothing) println("  (already up to date)")
    if (!apply) println("\nNothing was written. Re-run with -Papply to write these changes.")
}

/** Reads pass through; writes are dropped, so a run can be previewed against a real vault. */
private class DryRun(private val delegate: VaultStorage) : VaultStorage by delegate {
    override suspend fun write(account: String, fileName: String, content: String) = Unit
    override suspend fun writeRootFile(fileName: String, content: String) = Unit
    override suspend fun createAccount(name: String) = Unit
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    kotlin.system.exitProcess(1)
}
