package com.mdmoney.data

/**
 * Platform-agnostic access to the Obsidian vault folder that backs the app.
 *
 * An "account" is a direct subfolder of the chosen vault root; a note is a `.md` file inside it.
 * Implementations own folder picking and persisting the chosen location (SAF tree URI on Android,
 * a security-scoped bookmark on iOS, a plain path on desktop).
 */
interface VaultStorage {
    /** Human-readable label of the chosen vault (a path or folder name), or null if none chosen. */
    fun vaultLabel(): String?

    fun hasVault(): Boolean

    /** Opens the platform folder picker; returns true if a vault was chosen and persisted. */
    suspend fun pickVault(): Boolean

    suspend fun listAccounts(): List<String>

    suspend fun createAccount(name: String)

    /** File names (with `.md`) of the notes directly inside [account]. */
    suspend fun listNotes(account: String): List<String>

    suspend fun read(account: String, fileName: String): String

    suspend fun write(account: String, fileName: String, content: String)

    /** Reads a file directly at the vault root (e.g. an account metadata file), or null if absent. */
    suspend fun readRootFile(fileName: String): String?

    suspend fun writeRootFile(fileName: String, content: String)
}
