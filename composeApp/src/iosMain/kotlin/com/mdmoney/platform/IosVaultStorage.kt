package com.mdmoney.platform

import com.mdmoney.data.VaultStorage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkResolutionWithoutUI
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS storage backed by a user-picked folder. The chosen folder is persisted as a security-scoped
 * bookmark (base64 in NSUserDefaults) so access survives restarts; all IO uses [NSFileManager].
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosVaultStorage : VaultStorage {

    private val defaults = NSUserDefaults.standardUserDefaults
    private val fileManager = NSFileManager.defaultManager
    private var rootUrl: NSURL? = resolveBookmark()
    private var pickerDelegate: FolderPickerDelegate? = null

    override fun vaultLabel(): String? = rootUrl?.lastPathComponent

    override fun hasVault(): Boolean = rootUrl != null

    override suspend fun pickVault(): Boolean {
        val url = presentPicker() ?: return false
        url.startAccessingSecurityScopedResource()
        val bookmark = url.bookmarkDataWithOptions(0u, null, null, null)
        if (bookmark != null) {
            defaults.setObject(bookmark.base64EncodedStringWithOptions(0u), KEY_BOOKMARK)
            rootUrl = url
            return true
        }
        return false
    }

    override suspend fun listAccounts(): List<String> =
        childDirectories(requireRoot()).mapNotNull { it.lastPathComponent }.sorted()

    override suspend fun createAccount(name: String) {
        val dir = requireRoot().URLByAppendingPathComponent(name.trim(), isDirectory = true) ?: return
        fileManager.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
    }

    override suspend fun listNotes(account: String): List<String> {
        val dir = accountUrl(account) ?: return emptyList()
        return childUrls(dir)
            .mapNotNull { it.lastPathComponent }
            .filter { it.endsWith(".md") }
            .sorted()
    }

    override suspend fun read(account: String, fileName: String): String {
        val url = accountUrl(account)?.URLByAppendingPathComponent(fileName) ?: error("Note not found")
        return NSString_stringWithContentsOfURL(url) ?: error("Cannot read $fileName")
    }

    override suspend fun write(account: String, fileName: String, content: String) {
        val dir = accountUrl(account) ?: run {
            val created = requireRoot().URLByAppendingPathComponent(account, isDirectory = true)!!
            fileManager.createDirectoryAtURL(created, withIntermediateDirectories = true, attributes = null, error = null)
            created
        }
        val url = dir.URLByAppendingPathComponent(fileName) ?: error("Bad path")
        NSString_writeToURL(content, url)
    }

    override suspend fun readRootFile(fileName: String): String? {
        val url = requireRoot().URLByAppendingPathComponent(fileName) ?: return null
        return NSString_stringWithContentsOfURL(url)
    }

    override suspend fun writeRootFile(fileName: String, content: String) {
        val url = requireRoot().URLByAppendingPathComponent(fileName) ?: error("Bad path")
        NSString_writeToURL(content, url)
    }

    // --- helpers ---

    private fun requireRoot(): NSURL = rootUrl ?: error("No vault selected")

    private fun accountUrl(account: String): NSURL? =
        childDirectories(requireRoot()).firstOrNull { it.lastPathComponent == account }

    private fun childUrls(dir: NSURL): List<NSURL> {
        @Suppress("UNCHECKED_CAST")
        val contents = fileManager.contentsOfDirectoryAtURL(dir, null, 0u, null) as? List<NSURL>
        return contents.orEmpty()
    }

    private fun childDirectories(dir: NSURL): List<NSURL> =
        childUrls(dir).filter { it.hasDirectoryPath }

    private fun resolveBookmark(): NSURL? {
        val base64 = defaults.stringForKey(KEY_BOOKMARK)
        if (base64 != null) {
            val data = NSData.create(base64EncodedString = base64, options = 0u)
            val url = data?.let {
                NSURL.URLByResolvingBookmarkData(it, NSURLBookmarkResolutionWithoutUI, null, null, null)
            }
            if (url != null) {
                url.startAccessingSecurityScopedResource()
                return url
            }
        }
        // Fallback: a plain filesystem path (e.g. a vault synced into the app's own Documents).
        val path = defaults.stringForKey(KEY_PATH)
        if (path != null && NSFileManager.defaultManager.fileExistsAtPath(path)) {
            return NSURL.fileURLWithPath(path, isDirectory = true)
        }
        return null
    }

    private suspend fun presentPicker(): NSURL? = suspendCancellableCoroutine { cont ->
        val delegate = FolderPickerDelegate(cont)
        pickerDelegate = delegate
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder))
        picker.delegate = delegate
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (root == null) {
            cont.resume(null)
        } else {
            root.presentViewController(picker, animated = true, completion = null)
        }
    }

    // NSString bridging helpers kept in one place.
    private fun NSString_stringWithContentsOfURL(url: NSURL): String? =
        platform.Foundation.NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null)

    private fun NSString_writeToURL(content: String, url: NSURL) {
        (content as platform.Foundation.NSString).writeToURL(url, true, NSUTF8StringEncoding, null)
    }

    private companion object {
        const val KEY_BOOKMARK = "vault.bookmark"
        const val KEY_PATH = "vault.path"
    }
}

@OptIn(ExperimentalForeignApi::class)
private class FolderPickerDelegate(
    private val cont: CancellableContinuation<NSURL?>,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (cont.isActive) cont.resume(url)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        if (cont.isActive) cont.resume(null)
    }
}
