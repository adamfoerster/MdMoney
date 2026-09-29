package com.mdmoney.tools.eval

import com.mdmoney.data.formatAmount
import com.mdmoney.importer.StatementExtractor
import com.mdmoney.platform.JavaLlamaLlm
import com.mdmoney.platform.JvmImportPlatform
import com.mdmoney.platform.LlamaNativeLibrary
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

/**
 * Runs statement extraction over every PDF in a folder and prints what came out, with the
 * reconciliation against each statement's printed totals — how to tell whether a prompt, model or
 * text-reading change made things better. Real statements carry personal data, so they are pointed
 * at, never copied into the repo:
 *
 *     ./gradlew :composeApp:evalStatements -Pdir=/path/to/pdfs [-Pmodel=/path/to/model.gguf] [-Ptext]
 *
 * Without a model the simple parser runs; `-Ptext` also prints each page's extracted text.
 */
fun main(args: Array<String>): Unit = runBlocking {
    val dir = File(args.getOrNull(0) ?: error("usage: EvalStatements <dir> [model.gguf|-] [--text]"))
    val modelPath = args.getOrNull(1)?.takeIf { it != "-" }
    val showText = "--text" in args
    val llm = modelPath?.let {
        LlamaNativeLibrary.prepare(File(System.getProperty("user.home"), ".mdmoney/native"))
        JavaLlamaLlm(it)
    }
    val pdfs = dir.listFiles { f -> f.extension.equals("pdf", ignoreCase = true) }.orEmpty().sortedBy { it.name }
    for (pdf in pdfs) {
        println("===== ${pdf.name}")
        val pages = JvmImportPlatform.pdfPagesOf(pdf.readBytes())
        if (showText) pages.forEachIndexed { i, p -> println("--- page ${i + 1}\n$p") }
        val extractor = StatementExtractor(llm, categories = emptyList())
        lateinit var result: com.mdmoney.importer.ExtractionResult
        val ms = measureTimeMillis {
            result = extractor.extract(pages, fallbackYear = 2026) { done, total -> print("\r  page $done/$total") }
        }
        println()
        val h = result.header
        println("  model=${result.usedModel} period=${h.period} currency=${h.currency} time=${ms / 1000}s")
        result.transactions.forEach { t ->
            val flag = when (t.doubt) { null -> " "; com.mdmoney.importer.Doubt.READERS_DISAGREE -> "~"; else -> "?" }
            println("  $flag ${t.date.toLedgerDate()}  ${t.direction.id.padEnd(6)} ${t.kind.id.padEnd(12)} ${formatAmount(t.amount).padStart(10)}  ${t.description}")
        }
        val r = result.reconciliation
        println("  lines=${result.transactions.size} debits=${formatAmount(r.debits)} (declared ${r.declaredDebits?.let(::formatAmount)}) credits=${formatAmount(r.credits)} (declared ${r.declaredCredits?.let(::formatAmount)})")
    }
    llm?.close()
    // llama.cpp leaves non-daemon threads behind; don't let them keep the JVM alive.
    exitProcess(0)
}
