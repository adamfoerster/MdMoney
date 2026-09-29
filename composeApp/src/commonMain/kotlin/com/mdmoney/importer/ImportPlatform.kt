package com.mdmoney.importer

/** A statement the user picked: its file name (for display) and contents. */
class PickedPdf(val name: String, val bytes: ByteArray)

/**
 * What statement import needs from the platform: a file picker, a PDF text reader, somewhere to
 * keep a downloaded model, and the model engine itself. Passed into `App` beside the vault storage.
 *
 * Models live in the app's own data folder — never in the vault, which is the user's notes and is
 * often synced to other devices.
 */
interface ImportPlatform {
    /** Lets the user choose a PDF; null when cancelled. */
    suspend fun pickPdf(): PickedPdf?

    /** The text of each page, in order, with a line per printed line where the reader can tell. */
    suspend fun pdfPages(bytes: ByteArray): List<String>

    /** Where a model named [fileName] is (or would be) stored. */
    fun modelPath(fileName: String): String

    /** The file's size in bytes, or null when there is no such file. */
    fun fileSize(path: String): Long?

    suspend fun deleteFile(path: String)

    /**
     * Downloads [url] to [path], resuming a partial `.part` file from an earlier attempt.
     * [onProgress] gets bytes done and the total (0 when unknown).
     */
    suspend fun download(url: String, path: String, onProgress: (done: Long, total: Long) -> Unit)

    /** Lowercase hex SHA-256 of the file at [path]. */
    suspend fun sha256(path: String): String

    /** False when this build or device has no model engine (the simple parser is used instead). */
    val engineAvailable: Boolean

    /** Loads the model at [path]; throws when it can't (corrupt file, not enough memory). */
    suspend fun openLlm(path: String): LocalLlm

    /** True where [pickModelFile] can offer a picker (desktop). */
    val supportsModelFile: Boolean get() = false

    /** Lets the user point at a `.gguf` they already have; null where unsupported or cancelled. */
    suspend fun pickModelFile(): String? = null
}
