package com.mdmoney.tools

import com.mdmoney.data.MatchPatterns
import com.mdmoney.importer.PatternSeeding
import java.io.File

/**
 * Fills the `match:` lists of one account's notes from the statement lines they already hold — see
 * [PatternSeeding]. Run it with:
 *
 * ```
 * ./gradlew :composeApp:seedPatterns -Pvault=/path/to/vault -Paccount=Regions           # preview
 * ./gradlew :composeApp:seedPatterns -Pvault=/path/to/vault -Paccount=Regions -Papply   # write
 * ```
 *
 * Only the `match:` key is touched; patterns already there are kept as written.
 */
fun main(args: Array<String>) {
    val (path, account) = args.takeIf { it.size >= 2 } ?: fail("usage: seedPatterns <vault path> <account> [--apply]")
    val apply = args.contains("--apply")
    val folder = File(path, account)
    if (!folder.isDirectory) fail("not a folder: ${folder.absolutePath}")

    val files = folder.listFiles { f -> f.isFile && f.name.endsWith(".md") }.orEmpty().sortedBy { it.name }
    val notes = files.map { PatternSeeding.Note(it.name.removeSuffix(".md"), it.readText()) }
    val plan = PatternSeeding.plan(notes)

    for ((id, patterns) in plan) {
        println("$id.md")
        patterns.forEach { println("    + $it") }
        if (apply) {
            val f = File(folder, "$id.md")
            f.writeText(MatchPatterns.withAdded(f.readText(), patterns))
        }
    }
    println("\n${plan.size} of ${notes.size} notes, ${plan.values.sumOf { it.size }} patterns " + if (apply) "written." else "(preview; -Papply writes them).")
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    kotlin.system.exitProcess(1)
}
