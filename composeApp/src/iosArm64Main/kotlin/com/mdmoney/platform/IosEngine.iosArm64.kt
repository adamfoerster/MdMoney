package com.mdmoney.platform

import com.mdmoney.importer.LocalLlm

internal actual val iosEngineAvailable: Boolean = true

internal actual fun openIosLlm(path: String): LocalLlm = LlamaCppLlm(path)
