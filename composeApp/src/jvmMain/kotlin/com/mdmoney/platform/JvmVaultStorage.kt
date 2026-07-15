package com.mdmoney.platform

import com.mdmoney.data.VaultStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

/** Desktop storage: a plain folder on disk, chosen via a Swing directory picker. */
class JvmVaultStorage(
    private val prefs: JvmPrefs,
    initialRoot: File? = null,
) : VaultStorage {

    private var root: File? = initialRoot?.takeIf { it.isDirectory }
        ?: prefs.get(KEY_VAULT)?.let { File(it) }?.takeIf { it.isDirectory }

    override fun vaultLabel(): String? = root?.absolutePath

    override fun hasVault(): Boolean = root != null

    override suspend fun pickVault(): Boolean = withContext(Dispatchers.Swing) {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Selecione a pasta do vault"
            root?.let { currentDirectory = it }
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val chosen = chooser.selectedFile
            root = chosen
            prefs.set(KEY_VAULT, chosen.absolutePath)
            true
        } else {
            false
        }
    }

    override suspend fun listAccounts(): List<String> = withContext(Dispatchers.IO) {
        requireRoot().listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.map { it.name }?.sorted().orEmpty()
    }

    override suspend fun createAccount(name: String) = withContext(Dispatchers.IO) {
        File(requireRoot(), name).mkdirs()
        Unit
    }

    override suspend fun listNotes(account: String): List<String> = withContext(Dispatchers.IO) {
        File(requireRoot(), account).listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.map { it.name }?.sorted().orEmpty()
    }

    override suspend fun read(account: String, fileName: String): String = withContext(Dispatchers.IO) {
        File(File(requireRoot(), account), fileName).readText()
    }

    override suspend fun write(account: String, fileName: String, content: String) = withContext(Dispatchers.IO) {
        val dir = File(requireRoot(), account).apply { mkdirs() }
        File(dir, fileName).writeText(content)
    }

    override suspend fun readRootFile(fileName: String): String? = withContext(Dispatchers.IO) {
        File(requireRoot(), fileName).takeIf { it.isFile }?.readText()
    }

    override suspend fun writeRootFile(fileName: String, content: String) = withContext(Dispatchers.IO) {
        File(requireRoot(), fileName).writeText(content)
    }

    private fun requireRoot(): File = root ?: error("No vault selected")

    private companion object {
        const val KEY_VAULT = "vault.path"
    }
}
