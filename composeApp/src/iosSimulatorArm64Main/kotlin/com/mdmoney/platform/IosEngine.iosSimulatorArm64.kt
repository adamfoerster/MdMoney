package com.mdmoney.platform

import com.mdmoney.importer.LocalLlm

// llama.cpp's official xcframework ships no simulator slice, so the simulator build imports with
// the simple parser only.
internal actual val iosEngineAvailable: Boolean = false

internal actual fun openIosLlm(path: String): LocalLlm = error("no model engine in the simulator build")
