package com.mdmoney.platform

import com.mdmoney.importer.LocalLlm
import de.kherud.llama.InferenceParameters
import de.kherud.llama.LlamaModel
import de.kherud.llama.ModelParameters
import de.kherud.llama.Pair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [LocalLlm] over java-llama.cpp, shared by desktop and Android (which load the same Java API over
 * their own native library). CPU only, so it behaves the same on every machine.
 */
class JavaLlamaLlm(path: String, contextSize: Int = 8_192) : LocalLlm {

    private val model = LlamaModel(
        ModelParameters()
            .setModel(path)
            .setCtxSize(contextSize)
            .setThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 8))
            .setGpuLayers(0)
            .disableLog(),
    )

    /** One generation at a time: the context is shared. */
    private val lock = Mutex()

    override suspend fun complete(system: String, user: String, grammar: String, maxTokens: Int): String =
        lock.withLock {
            withContext(Dispatchers.Default) {
                val prompt = model.applyTemplate(InferenceParameters("").setMessages(system, listOf(Pair("user", user))))
                val params = InferenceParameters(prompt)
                    .setGrammar(grammar)
                    .setNPredict(maxTokens)
                    .setTemperature(0f)
                    .setTopK(1)
                    .setCachePrompt(false)
                val iterator = model.generate(params).iterator()
                val out = StringBuilder()
                while (iterator.hasNext()) {
                    if (!currentCoroutineContext().isActive) {
                        iterator.cancel()
                        break
                    }
                    out.append(iterator.next().text)
                }
                out.toString()
            }
        }

    override fun close() = model.close()
}
