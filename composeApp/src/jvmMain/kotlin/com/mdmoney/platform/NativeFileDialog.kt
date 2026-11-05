package com.mdmoney.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * The operating system's own open dialog where the JDK can reach it: [FileDialog] is the native
 * Explorer dialog on Windows and the native sheet on macOS, whereas [JFileChooser] draws the
 * cross-platform "Metal" one. Linux (and a Windows folder pick, which AWT has no native call for)
 * fall back to [JFileChooser] under the system look and feel.
 *
 * Every function blocks until the dialog closes, so call them on the Swing thread.
 */
object NativeFileDialog {

    private val osName = System.getProperty("os.name").orEmpty().lowercase()
    private val isMac = osName.contains("mac")
    private val isWindows = osName.contains("windows")

    /** One file with the given [extension] (no dot), or null when cancelled. */
    fun openFile(title: String, label: String, extension: String, startDir: File?): File? {
        if (!isMac && !isWindows) return swingOpen(title, JFileChooser.FILES_ONLY, startDir, label, extension)
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            startDir?.takeIf { it.isDirectory }?.let { directory = it.absolutePath }
            // Windows reads a wildcard in `file` as the type filter and ignores filenameFilter; macOS
            // is the other way round. Setting both keeps each platform's picker to the one type.
            if (isWindows) file = "*.$extension"
            filenameFilter = FilenameFilter { dir, name ->
                name.endsWith(".$extension", ignoreCase = true) || File(dir, name).isDirectory
            }
        }
        return dialog.pick()?.takeIf { it.extension.equals(extension, ignoreCase = true) }
    }

    /** One folder, or null when cancelled. */
    fun openDirectory(title: String, startDir: File?): File? {
        if (!isMac) return swingOpen(title, JFileChooser.DIRECTORIES_ONLY, startDir, null, null)
        val key = "apple.awt.fileDialogForDirectories"
        val previous = System.getProperty(key)
        System.setProperty(key, "true")
        try {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
                startDir?.takeIf { it.isDirectory }?.let { directory = it.absolutePath }
            }
            return dialog.pick()
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }

    private fun FileDialog.pick(): File? {
        isVisible = true
        val name = file ?: return null
        return File(directory.orEmpty(), name)
    }

    private fun swingOpen(
        title: String,
        mode: Int,
        startDir: File?,
        label: String?,
        extension: String?,
    ): File? {
        // Must be set before the chooser is created. The system look and feel is the closest Swing
        // gets to native; if it is unavailable the default one is kept.
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = mode
            if (label != null && extension != null) fileFilter = FileNameExtensionFilter(label, extension)
            startDir?.takeIf { it.isDirectory }?.let { currentDirectory = it }
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
}
