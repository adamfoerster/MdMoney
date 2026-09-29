package com.mdmoney.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** File plumbing for model downloads, the same on desktop and Android. */
object JavaImportFiles {

    fun size(path: String): Long? = File(path).takeIf { it.isFile }?.length()

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        File(path).delete()
        File("$path.part").delete()
        Unit
    }

    /**
     * Streams [url] into `<path>.part`, continuing a previous partial download with a range request,
     * and renames it into place only when complete — so a half-downloaded model is never mistaken
     * for a whole one.
     */
    suspend fun download(url: String, path: String, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val target = File(path)
        target.parentFile?.mkdirs()
        val part = File("$path.part")
        var connection = open(url, part.length())
        // A server that ignores the range sends everything again: start over rather than append.
        val resuming = part.length() > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
        if (!resuming && part.exists()) part.delete()
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            connection = open(url, 0)
        }
        check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
        val already = if (resuming) part.length() else 0L
        val total = connection.contentLengthLong.takeIf { it > 0 }?.plus(already) ?: 0L
        var done = already
        connection.inputStream.use { input ->
            FileOutputStream(part, resuming).use { output ->
                val buffer = ByteArray(1 shl 16)
                var lastReport = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    if (done - lastReport > 1 shl 20) {
                        onProgress(done, total)
                        lastReport = done
                    }
                }
            }
        }
        onProgress(done, total)
        target.delete()
        check(part.renameTo(target)) { "could not move the download into place" }
    }

    private fun open(url: String, from: Long): HttpURLConnection {
        var current = URL(url)
        // Follow redirects by hand: the model host sends us to a CDN on another domain.
        repeat(8) {
            val c = current.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 30_000
            c.readTimeout = 60_000
            if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location") ?: return c
                c.disconnect()
                current = URL(current, location)
            } else {
                return c
            }
        }
        error("too many redirects")
    }

    suspend fun sha256(path: String): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        File(path).inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
