package com.mdmoney

import com.mdmoney.data.AppSettings
import com.mdmoney.data.VaultRepository
import com.mdmoney.importer.ImportPlatform
import com.mdmoney.importer.LocalLlm
import com.mdmoney.importer.PickedPdf
import com.mdmoney.platform.JvmPrefs
import com.mdmoney.platform.JvmVaultStorage
import com.mdmoney.ui.ImportPhase
import com.mdmoney.ui.StatementImportModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The review → import flow end to end, with the PDF reader faked: what is unticked must not be written. */
class StatementImportModelTest {

    private val vault = Files.createTempDirectory("mdmoney-model").toFile().also { File(it, "nubank").mkdirs() }

    @AfterTest
    fun tearDown() {
        vault.deleteRecursively()
    }

    private val text = """
        August 22, 2026 through  September 22, 2026
        WITHDRAWALS
        08/24 Recurring Card Transaction  Blue Stream      4899  954-753-0100  FL 33065    3914 84.94
        09/14 Card Purchase  Sunpass*acc1442  4784  888-865-5352  FL 33434    3914 10.00
        09/15 Card Purchase  Padaria Central  4784  888-865-5352  FL 33434    3914 7.50
        Total Withdrawals  ${'$'}102.44
    """.trimIndent()

    private val platform = object : ImportPlatform {
        override suspend fun pickPdf() = PickedPdf("extrato.pdf", ByteArray(0))
        override suspend fun pdfPages(bytes: ByteArray) = listOf(text)
        override fun modelPath(fileName: String) = "none"
        override fun fileSize(path: String): Long? = null
        override suspend fun deleteFile(path: String) {}
        override suspend fun download(url: String, path: String, onProgress: (Long, Long) -> Unit) {}
        override suspend fun sha256(path: String) = ""
        override val engineAvailable = false
        override suspend fun openLlm(path: String): LocalLlm = error("no engine")
    }

    private val settings = object : AppSettings {
        override fun language(): String? = null
        override fun setLanguage(code: String?) {}
        override fun decimalSeparator(): String? = null
        override fun setDecimalSeparator(code: String?) {}
        override fun importModel(): String? = null
        override fun setImportModel(value: String?) {}
    }

    @Test
    fun unticked_lines_are_not_written() = runBlocking {
        val repo = VaultRepository(JvmVaultStorage(JvmPrefs(), vault))
        val importer = StatementImportModel(platform, settings, repo, CoroutineScope(Dispatchers.Default), { 2026 }, {})
        importer.start("nubank")
        importer.pickAndRead(emptyList())
        withTimeout(20_000) { while (importer.state.value.phase !is ImportPhase.Review) delay(20) }

        val rows = importer.state.value.rows
        assertEquals(3, rows.size, rows.joinToString())
        assertTrue(rows.all { it.selected }, "spending starts ticked")

        val padaria = rows.single { "Padaria" in it.description }
        importer.toggle(rows.single { "Blue Stream" in it.description }.id)
        importer.toggle(rows.single { "Sunpass" in it.description }.id)
        assertEquals(listOf(padaria.id), importer.state.value.rows.filter { it.selected }.map { it.id })

        importer.import(emptyList(), "Importado", "Entradas")
        withTimeout(20_000) { while (importer.state.value.phase !is ImportPhase.Done) delay(20) }

        val done = importer.state.value.phase as ImportPhase.Done
        assertEquals(1, done.result.added)
        val written = vault.walkTopDown().filter { it.isFile && it.extension == "md" }.joinToString("\n") { it.readText() }
        assertTrue("Padaria" in written, written)
        assertTrue("Blue Stream" !in written && "Sunpass" !in written, "unticked lines must stay out: $written")
    }
}
