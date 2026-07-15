package com.mdmoney.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mdmoney.data.VaultStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens the system folder picker (SAF) and yields the chosen tree Uri, or null if cancelled. */
interface TreePicker {
    suspend fun pick(): Uri?
}

/**
 * Android storage backed by the Storage Access Framework. The user picks the vault folder once; the
 * tree Uri is persisted (with a persistable permission) so it survives restarts. All IO goes through
 * [DocumentFile]/ContentResolver since app-external folders aren't reachable as plain files.
 */
class AndroidVaultStorage(
    private val context: Context,
    private val picker: TreePicker,
) : VaultStorage {

    private val prefs = context.getSharedPreferences("mdmoney", Context.MODE_PRIVATE)
    private var treeUri: Uri? = prefs.getString(KEY_TREE, null)?.let { Uri.parse(it) }

    override fun vaultLabel(): String? = treeUri?.let { Uri.decode(it.lastPathSegment) }

    override fun hasVault(): Boolean = treeUri != null && root() != null

    override suspend fun pickVault(): Boolean {
        val uri = picker.pick() ?: return false
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        treeUri = uri
        prefs.edit().putString(KEY_TREE, uri.toString()).apply()
        return true
    }

    override suspend fun listAccounts(): List<String> = withContext(Dispatchers.IO) {
        root()?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }?.sorted().orEmpty()
    }

    override suspend fun createAccount(name: String) {
        withContext(Dispatchers.IO) { root()?.createDirectory(name.trim()) }
    }

    override suspend fun listNotes(account: String): List<String> = withContext(Dispatchers.IO) {
        accountDir(account)?.listFiles()
            ?.filter { it.isFile && (it.name?.endsWith(".md") == true) }
            ?.mapNotNull { it.name }?.sorted().orEmpty()
    }

    override suspend fun read(account: String, fileName: String): String = withContext(Dispatchers.IO) {
        val file = accountDir(account)?.findFile(fileName) ?: error("Note not found: $account/$fileName")
        context.contentResolver.openInputStream(file.uri)!!.use { it.readBytes().decodeToString() }
    }

    override suspend fun write(account: String, fileName: String, content: String) = withContext(Dispatchers.IO) {
        val dir = accountDir(account) ?: root()?.createDirectory(account) ?: error("No vault")
        val file = dir.findFile(fileName) ?: dir.createFile("text/markdown", fileName)
        ?: error("Cannot create $fileName")
        context.contentResolver.openOutputStream(file.uri, "wt")!!.use { it.write(content.encodeToByteArray()) }
    }

    override suspend fun readRootFile(fileName: String): String? = withContext(Dispatchers.IO) {
        val file = root()?.findFile(fileName)?.takeIf { it.isFile } ?: return@withContext null
        context.contentResolver.openInputStream(file.uri)?.use { it.readBytes().decodeToString() }
    }

    override suspend fun writeRootFile(fileName: String, content: String) = withContext(Dispatchers.IO) {
        val dir = root() ?: error("No vault")
        val file = dir.findFile(fileName) ?: dir.createFile("text/markdown", fileName)
        ?: error("Cannot create $fileName")
        context.contentResolver.openOutputStream(file.uri, "wt")!!.use { it.write(content.encodeToByteArray()) }
    }

    private fun root(): DocumentFile? = treeUri?.let { DocumentFile.fromTreeUri(context, it) }

    private fun accountDir(account: String): DocumentFile? =
        root()?.findFile(account)?.takeIf { it.isDirectory }

    private companion object {
        const val KEY_TREE = "vault.tree.uri"
    }
}
