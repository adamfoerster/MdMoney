package com.mdmoney.platform

import com.mdmoney.importer.ImportPlatform
import com.mdmoney.importer.LocalLlm
import com.mdmoney.importer.PickedPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop statement import: Swing pickers, PDFBox for text, models under `~/.mdmoney/models` and the
 * engine's native library under `~/.mdmoney/native` (see [LlamaNativeLibrary]).
 */
class JvmImportPlatform(
    private val modelsDir: File = File(System.getProperty("user.home"), ".mdmoney/models"),
    private val nativeDir: File = File(modelsDir.parentFile, "native"),
) : ImportPlatform {

    private var lastDir: File? = null

    override suspend fun pickPdf(): PickedPdf? {
        val file = choose("PDF", "pdf") ?: return null
        return withContext(Dispatchers.IO) { PickedPdf(file.name, file.readBytes()) }
    }

    override suspend fun pdfPages(bytes: ByteArray): List<String> = withContext(Dispatchers.IO) {
        pdfPagesOf(bytes)
    }

    override suspend fun renderPdfPage(bytes: ByteArray, page: Int, widthPx: Int): ByteArray = withContext(Dispatchers.IO) {
        renderPageOf(bytes, page, widthPx)
    }

    override fun modelPath(fileName: String): String = File(modelsDir, fileName).absolutePath

    override fun fileSize(path: String): Long? = JavaImportFiles.size(path)

    override suspend fun deleteFile(path: String) = JavaImportFiles.delete(path)

    override suspend fun download(url: String, path: String, onProgress: (Long, Long) -> Unit) =
        JavaImportFiles.download(url, path, onProgress)

    override suspend fun sha256(path: String): String = JavaImportFiles.sha256(path)

    override val engineAvailable: Boolean = true

    override suspend fun openLlm(path: String): LocalLlm = withContext(Dispatchers.IO) {
        LlamaNativeLibrary.prepare(nativeDir)
        JavaLlamaLlm(path)
    }

    override val supportsModelFile: Boolean = true

    override suspend fun pickModelFile(): String? = choose("GGUF", "gguf")?.absolutePath

    private suspend fun choose(label: String, extension: String): File? = withContext(Dispatchers.Swing) {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter(label, extension)
            lastDir?.let { currentDirectory = it }
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.also { lastDir = it.parentFile }
        } else {
            null
        }
    }

    companion object {
        /**
         * Page texts in reading order. Sorting by position keeps a row's date, description and
         * amount on one line where the PDF stores them apart, which is what both extractors need.
         */
        fun pdfPagesOf(bytes: ByteArray): List<String> = Loader.loadPDF(bytes).use { doc ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            (1..doc.numberOfPages).map { page ->
                stripper.startPage = page
                stripper.endPage = page
                stripper.getText(doc)
            }
        }

        /** Page [page] (0-based) drawn [widthPx] wide on white, as PNG. */
        fun renderPageOf(bytes: ByteArray, page: Int, widthPx: Int): ByteArray = Loader.loadPDF(bytes).use { doc ->
            val p = doc.getPage(page)
            // A page turned sideways is drawn turned, so its printed height becomes the width.
            val width = if (p.rotation % 180 != 0) p.cropBox.height else p.cropBox.width
            // Half a pixel over, since the renderer floors the size and 400 / 612 * 612 lands at 399.99.
            val image = PDFRenderer(doc).renderImage(page, (widthPx + 0.5f) / width, ImageType.RGB)
            ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        }
    }
}
