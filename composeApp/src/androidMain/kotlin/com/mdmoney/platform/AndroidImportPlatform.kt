package com.mdmoney.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mdmoney.importer.ImportPlatform
import com.mdmoney.importer.LocalLlm
import com.mdmoney.importer.PickedPdf
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Bridges the system document picker (an Activity result) into a suspend call. */
fun interface DocumentPicker {
    suspend fun pick(mimeType: String): Uri?
}

/**
 * Android statement import: the system picker for PDFs, PdfBox-Android for their text, and models
 * in the no-backup files folder (a gigabyte has no business in a cloud backup). The engine is the
 * arm64 `libjllama.so` packaged as a jniLib; on other ABIs, or a build made without the NDK, it
 * isn't there and the simple parser takes over.
 */
class AndroidImportPlatform(
    private val context: Context,
    private val picker: DocumentPicker,
) : ImportPlatform {

    private val modelsDir = File(context.noBackupFilesDir, "models")

    override suspend fun pickPdf(): PickedPdf? {
        val uri = picker.pick("application/pdf") ?: return null
        return withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            PickedPdf(displayName(uri) ?: "statement.pdf", bytes)
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    override suspend fun pdfPages(bytes: ByteArray): List<String> = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(bytes).use { doc ->
            // Sorted by position for the same reason as on desktop: a row's parts stay on one line.
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            (1..doc.numberOfPages).map { page ->
                stripper.startPage = page
                stripper.endPage = page
                stripper.getText(doc)
            }
        }
    }

    override fun modelPath(fileName: String): String = File(modelsDir, fileName).absolutePath

    override fun fileSize(path: String): Long? = JavaImportFiles.size(path)

    override suspend fun deleteFile(path: String) = JavaImportFiles.delete(path)

    override suspend fun download(url: String, path: String, onProgress: (Long, Long) -> Unit) =
        JavaImportFiles.download(url, path, onProgress)

    override suspend fun sha256(path: String): String = JavaImportFiles.sha256(path)

    override val engineAvailable: Boolean by lazy {
        runCatching { System.loadLibrary("jllama") }.isSuccess
    }

    override suspend fun openLlm(path: String): LocalLlm = withContext(Dispatchers.IO) {
        // A smaller context than on desktop: the phone's memory is shared with everything else.
        JavaLlamaLlm(path, contextSize = 6_144)
    }
}
