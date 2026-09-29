package com.mdmoney

import com.mdmoney.platform.LlamaNativeLibrary
import de.kherud.llama.LlamaModel
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression: java-llama.cpp extracts its DLL to a fixed `%TEMP%\jllama.dll`, deleting the previous
 * copy first. With that file held by another process — here, an open handle stands in for a second
 * MdMoney that has it loaded — the engine failed to load (`UnsatisfiedLinkError`) and every import
 * quietly used the simple parser even with a model downloaded.
 */
class LlamaNativeLibraryTest {

    private val base = Files.createTempDirectory("mdmoney-native").toFile()

    @AfterTest
    fun tearDown() {
        base.deleteRecursively()
    }

    @Test
    fun the_engine_loads_while_the_shared_temp_copy_is_held() {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            assertNull(LlamaNativeLibrary.prepare(base), "only Windows locks a loaded library")
            return
        }
        val shared = File(System.getProperty("java.io.tmpdir"), "jllama.dll")
        // Only hold it when it's there already: this test must not plant files in the real temp folder.
        val held = shared.takeIf { it.isFile }?.let { FileInputStream(it) }
        try {
            val dir = assertNotNull(LlamaNativeLibrary.prepare(base))
            assertTrue(File(dir, "jllama.dll").isFile)
            // Any call into the engine loads the native library; this one needs no model.
            val grammar = LlamaModel.jsonSchemaToGrammar("""{"type":"boolean"}""")
            assertTrue(grammar.contains("root"), grammar)
        } finally {
            held?.close()
        }
    }

    @Test
    fun an_identical_copy_is_left_alone_and_a_damaged_one_replaced() {
        val target = File(base, "x/jllama.dll")
        LlamaNativeLibrary.install(byteArrayOf(1, 2, 3), target)
        val written = target.lastModified()
        Thread.sleep(20)
        LlamaNativeLibrary.install(byteArrayOf(1, 2, 3), target)
        assertEquals(written, target.lastModified(), "same bytes: not rewritten, so a loaded copy is never touched")

        target.writeBytes(byteArrayOf(9))
        LlamaNativeLibrary.install(byteArrayOf(1, 2, 3), target)
        assertTrue(target.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals(listOf("jllama.dll"), target.parentFile.list()!!.toList(), "no partial files left behind")
    }
}
