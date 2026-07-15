package com.mdmoney

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.mdmoney.platform.AndroidAppSettings
import com.mdmoney.platform.AndroidVaultStorage
import com.mdmoney.platform.TreePicker
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {

    // Bridges the SAF folder-picker (an Activity result) into a suspend function the storage calls.
    private var pending: CancellableContinuation<Uri?>? = null
    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        pending?.let { if (it.isActive) it.resume(uri) }
        pending = null
    }

    private val picker = object : TreePicker {
        override suspend fun pick(): Uri? = suspendCancellableCoroutine { cont ->
            pending = cont
            openTree.launch(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val storage = AndroidVaultStorage(applicationContext, picker)
        val settings = AndroidAppSettings(applicationContext)
        val dbPath = applicationContext.getDatabasePath("mdmoney-cache.db")
            .also { it.parentFile?.mkdirs() }.absolutePath
        setContent {
            App(storage, settings, dbPath)
        }
    }
}
