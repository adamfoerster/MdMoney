package com.mdmoney.platform

import de.kherud.llama.LlamaModel
import java.io.File
import java.security.MessageDigest

/**
 * Puts java-llama.cpp's native library somewhere this app owns before the engine first loads.
 *
 * Left to itself the library extracts to one fixed path, `%TEMP%\jllama.dll`, deleting the previous
 * copy first. On Windows a DLL that any process has loaded can't be deleted, so while one process
 * holds it — a second MdMoney window, a hung process — every new one fails with
 * `UnsatisfiedLinkError` and statement import silently falls back to the simple parser.
 *
 * Instead the library is written once to `<base>/<content hash>/`, never deleted, and the loader is
 * pointed there (`de.kherud.llama.lib.path`): processes share the file, and a different library
 * version gets a different folder. Only Windows needs this — elsewhere a loaded library can be
 * replaced — and on macOS the loader must keep extracting its Metal shader alongside.
 */
object LlamaNativeLibrary {

    private const val LIB_PATH_PROPERTY = "de.kherud.llama.lib.path"

    /** Prepares the library under [base] and returns the folder used, or null when not needed/possible. */
    @Synchronized
    fun prepare(base: File, osName: String = System.getProperty("os.name"), arch: String = System.getProperty("os.arch")): File? {
        if (!osName.startsWith("Windows", ignoreCase = true)) return null
        System.getProperty(LIB_PATH_PROPERTY)?.let { return File(it) }
        val archFolder = when (arch.lowercase()) {
            "amd64", "x86_64" -> "x86_64"
            "x86", "i386", "i686" -> "x86"
            else -> return null
        }
        val name = "jllama.dll"
        val bytes = LlamaModel::class.java.getResourceAsStream("/de/kherud/llama/Windows/$archFolder/$name")
            ?.use { it.readBytes() } ?: return null
        val dir = File(base, sha256(bytes).take(16))
        install(bytes, File(dir, name))
        System.setProperty(LIB_PATH_PROPERTY, dir.absolutePath)
        return dir
    }

    /**
     * Writes [bytes] to [target] unless an identical file is already there. Written beside it and
     * renamed into place, so another process never sees a half-written library.
     */
    internal fun install(bytes: ByteArray, target: File) {
        if (target.isFile && target.length() == bytes.size.toLong() && target.readBytes().contentEquals(bytes)) return
        target.parentFile.mkdirs()
        val partial = File.createTempFile(target.name, ".part", target.parentFile)
        partial.writeBytes(bytes)
        target.delete() // a damaged copy; renameTo won't replace on Windows
        if (!partial.renameTo(target)) {
            partial.delete()
            // Another process won the race and its copy is in use: fine, as long as it's the same file.
            check(target.isFile && target.readBytes().contentEquals(bytes)) { "could not install $target" }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
