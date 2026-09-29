package com.mdmoney.platform

import com.mdmoney.importer.ImportPlatform
import com.mdmoney.importer.LocalLlm
import com.mdmoney.importer.PickedPdf
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUserDomainMask
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGSizeMake
import platform.PDFKit.PDFDisplayBox
import platform.PDFKit.PDFDocument
import platform.UIKit.UIApplication
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIScreen
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** True where this binary links llama.cpp (a device build; the simulator has no slice for it). */
internal expect val iosEngineAvailable: Boolean

internal expect fun openIosLlm(path: String): LocalLlm

/**
 * iOS statement import: the document picker for PDFs, PDFKit for their text, and models in
 * Application Support, marked to stay out of iCloud backups.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosImportPlatform : ImportPlatform {

    private val fileManager = NSFileManager.defaultManager
    private var pickerDelegate: NSObject? = null
    private var downloadDelegate: NSObject? = null

    private val modelsDir: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: error("no Application Support folder")
        val dir = "$base/models"
        fileManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        NSURL.fileURLWithPath(dir).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        dir
    }

    override suspend fun pickPdf(): PickedPdf? {
        val url = presentPicker() ?: return null
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url) ?: return null
            return PickedPdf(url.lastPathComponent ?: "statement.pdf", data.toByteArray())
        } finally {
            if (scoped) url.stopAccessingSecurityScopedResource()
        }
    }

    override suspend fun pdfPages(bytes: ByteArray): List<String> = withContext(Dispatchers.Default) {
        val doc = PDFDocument(bytes.toNSData()) ?: error("not a PDF")
        (0 until doc.pageCount.toInt()).map { i -> doc.pageAtIndex(i.toULong())?.string ?: "" }
    }

    override suspend fun renderPdfPage(bytes: ByteArray, page: Int, widthPx: Int): ByteArray? = withContext(Dispatchers.Default) {
        val doc = PDFDocument(bytes.toNSData()) ?: return@withContext null
        val p = doc.pageAtIndex(page.toULong()) ?: return@withContext null
        val (w, h) = p.boundsForBox(PDFDisplayBox.kPDFDisplayBoxCropBox).useContents { size.width to size.height }
        // A page turned sideways is drawn turned, so its printed height becomes the width.
        val (width, height) = if (p.rotation % 180L != 0L) h to w else w to h
        if (width <= 0.0) return@withContext null
        // The thumbnail is sized in points; dividing by the screen scale keeps it widthPx pixels wide.
        val scale = UIScreen.mainScreen.scale
        val points = widthPx / scale
        val image = p.thumbnailOfSize(CGSizeMake(points, points * height / width), forBox = PDFDisplayBox.kPDFDisplayBoxCropBox)
        UIImagePNGRepresentation(image)?.toByteArray()
    }

    override fun modelPath(fileName: String): String = "$modelsDir/$fileName"

    override fun fileSize(path: String): Long? {
        if (!fileManager.fileExistsAtPath(path)) return null
        val attributes = fileManager.attributesOfItemAtPath(path, error = null) ?: return null
        return (attributes[NSFileSize] as? NSNumber)?.longLongValue
    }

    override suspend fun deleteFile(path: String) {
        fileManager.removeItemAtPath(path, error = null)
    }

    /**
     * A background-friendly `NSURLSession` download. It restarts from zero rather than resuming — iOS
     * keeps its own resume data, which isn't worth persisting for a one-off download.
     */
    override suspend fun download(url: String, path: String, onProgress: (Long, Long) -> Unit) {
        val source = NSURL.URLWithString(url) ?: error("bad url")
        suspendCancellableCoroutine { cont ->
            val delegate = DownloadDelegate(path, onProgress, cont)
            downloadDelegate = delegate
            val session = NSURLSession.sessionWithConfiguration(
                NSURLSessionConfiguration.defaultSessionConfiguration,
                delegate = delegate,
                delegateQueue = NSOperationQueue(),
            )
            val task = session.downloadTaskWithURL(source)
            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }

    override suspend fun sha256(path: String): String = withContext(Dispatchers.Default) {
        val handle = NSFileHandle.fileHandleForReadingAtPath(path) ?: error("cannot read $path")
        memScoped {
            val ctx = alloc<CC_SHA256_CTX>()
            CC_SHA256_Init(ctx.ptr)
            while (true) {
                val chunk = handle.readDataOfLength(1uL shl 20)
                if (chunk.length.toInt() == 0) break
                CC_SHA256_Update(ctx.ptr, chunk.bytes, chunk.length.toUInt())
            }
            handle.closeFile()
            val digest = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)
            CC_SHA256_Final(digest, ctx.ptr)
            (0 until CC_SHA256_DIGEST_LENGTH).joinToString("") { digest[it].toString(16).padStart(2, '0') }
        }
    }

    override val engineAvailable: Boolean get() = iosEngineAvailable

    override suspend fun openLlm(path: String): LocalLlm = withContext(Dispatchers.Default) { openIosLlm(path) }

    private suspend fun presentPicker(): NSURL? = suspendCancellableCoroutine { cont ->
        val delegate = PdfPickerDelegate(cont)
        pickerDelegate = delegate
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF))
        picker.delegate = delegate
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (root == null) cont.resume(null) else root.presentViewController(picker, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PdfPickerDelegate(
    private val cont: CancellableContinuation<NSURL?>,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        if (cont.isActive) cont.resume(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        if (cont.isActive) cont.resume(null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class DownloadDelegate(
    private val path: String,
    private val onProgress: (Long, Long) -> Unit,
    private val cont: CancellableContinuation<Unit>,
) : NSObject(), NSURLSessionDownloadDelegateProtocol {

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        onProgress(totalBytesWritten, totalBytesExpectedToWrite.coerceAtLeast(0))
    }

    override fun URLSession(session: NSURLSession, downloadTask: NSURLSessionDownloadTask, didFinishDownloadingToURL: NSURL) {
        // The temporary file vanishes when this returns, so it is moved into place right here.
        val manager = NSFileManager.defaultManager
        manager.removeItemAtPath(path, error = null)
        val moved = manager.moveItemAtURL(didFinishDownloadingToURL, NSURL.fileURLWithPath(path), error = null)
        if (!moved && cont.isActive) cont.resumeWithException(IllegalStateException("could not move the download into place"))
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!cont.isActive) return
        if (didCompleteWithError != null) {
            cont.resumeWithException(IllegalStateException(didCompleteWithError.localizedDescription))
        } else {
            cont.resume(Unit)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.toULong())
}
