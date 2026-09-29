package com.mdmoney.platform

import com.mdmoney.importer.LocalLlm
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import llama.llama_backend_init
import llama.llama_batch_get_one
import llama.llama_chat_apply_template
import llama.llama_chat_message
import llama.llama_context_default_params
import llama.llama_decode
import llama.llama_free
import llama.llama_get_memory
import llama.llama_init_from_model
import llama.llama_memory_clear
import llama.llama_model_chat_template
import llama.llama_model_default_params
import llama.llama_model_free
import llama.llama_model_get_vocab
import llama.llama_model_load_from_file
import llama.llama_n_batch
import llama.llama_sampler_chain_add
import llama.llama_sampler_chain_default_params
import llama.llama_sampler_chain_init
import llama.llama_sampler_free
import llama.llama_sampler_init_grammar
import llama.llama_sampler_init_greedy
import llama.llama_sampler_sample
import llama.llama_token_to_piece
import llama.llama_tokenize
import llama.llama_vocab_is_eog

/**
 * [LocalLlm] straight over llama.cpp's C API (the official xcframework), since java-llama.cpp has no
 * iOS build. The same steps the Java binding takes: chat template, tokenize, decode the prompt in
 * batches, then sample greedily under the grammar until end-of-generation.
 */
@OptIn(ExperimentalForeignApi::class)
class LlamaCppLlm(path: String, contextSize: Int = 6_144) : LocalLlm {

    private val model = run {
        llama_backend_init()
        llama_model_load_from_file(path, llama_model_default_params()) ?: error("could not load model")
    }
    private val context = llama_init_from_model(
        model,
        llama_context_default_params().copy {
            n_ctx = contextSize.toUInt()
            n_batch = 512u
            n_threads = 4
            n_threads_batch = 4
        },
    ) ?: run {
        llama_model_free(model)
        error("could not create context")
    }
    private val vocab = llama_model_get_vocab(model) ?: error("model has no vocabulary")
    private val lock = Mutex()

    override suspend fun complete(system: String, user: String, grammar: String, maxTokens: Int): String =
        lock.withLock {
            withContext(Dispatchers.Default) {
                llama_memory_clear(llama_get_memory(context), true)
                val prompt = tokenize(applyTemplate(system, user))
                val sampler = llama_sampler_chain_init(llama_sampler_chain_default_params()) ?: error("no sampler")
                try {
                    llama_sampler_chain_add(sampler, llama_sampler_init_grammar(vocab, grammar, "root"))
                    llama_sampler_chain_add(sampler, llama_sampler_init_greedy())
                    decode(prompt)
                    val out = ArrayList<Byte>(maxTokens * 4)
                    for (i in 0 until maxTokens) {
                        if (!currentCoroutineContext().isActive) break
                        val token = llama_sampler_sample(sampler, context, -1)
                        if (llama_vocab_is_eog(vocab, token)) break
                        out.addAll(piece(token).toList())
                        decode(intArrayOf(token))
                    }
                    // Decoded once at the end: a token may carry half of a multi-byte character.
                    out.toByteArray().decodeToString()
                } finally {
                    llama_sampler_free(sampler)
                }
            }
        }

    private fun applyTemplate(system: String, user: String): String = memScoped {
        val template = llama_model_chat_template(model, null)
        val messages = allocArray<llama_chat_message>(2)
        messages[0].role = "system".cstr.ptr
        messages[0].content = system.cstr.ptr
        messages[1].role = "user".cstr.ptr
        messages[1].content = user.cstr.ptr
        // Asked once with a generous buffer, and again at the size it reports if that wasn't enough.
        val guess = (system.length + user.length) * 2 + 256
        val first = allocArray<ByteVar>(guess)
        val n = llama_chat_apply_template(template, messages, 2u, true, first, guess)
        check(n >= 0) { "chat template failed" }
        if (n <= guess) {
            first.readBytes(n).decodeToString()
        } else {
            val second = allocArray<ByteVar>(n)
            llama_chat_apply_template(template, messages, 2u, true, second, n)
            second.readBytes(n).decodeToString()
        }
    }

    private fun tokenize(text: String): IntArray {
        val bytes = text.encodeToByteArray().size
        val capacity = bytes + 16
        val tokens = IntArray(capacity)
        val n = tokens.usePinned { p ->
            llama_tokenize(vocab, text, bytes, p.addressOf(0), capacity, true, true)
        }
        check(n >= 0) { "prompt too long to tokenize" }
        return tokens.copyOf(n)
    }

    private fun decode(tokens: IntArray) {
        val batch = llama_n_batch(context).toInt().coerceAtLeast(1)
        var start = 0
        while (start < tokens.size) {
            val count = minOf(batch, tokens.size - start)
            val result = tokens.usePinned { p -> llama_decode(context, llama_batch_get_one(p.addressOf(start), count)) }
            check(result == 0) { "decode failed ($result)" }
            start += count
        }
    }

    private fun piece(token: Int): ByteArray = memScoped {
        val buffer = allocArray<ByteVar>(PIECE_BUFFER)
        val n = llama_token_to_piece(vocab, token, buffer, PIECE_BUFFER, 0, false)
        if (n >= 0) {
            buffer.readBytes(n)
        } else {
            // A negative count is the size the piece needs.
            val larger = allocArray<ByteVar>(-n)
            larger.readBytes(llama_token_to_piece(vocab, token, larger, -n, 0, false).coerceAtLeast(0))
        }
    }

    private companion object {
        const val PIECE_BUFFER = 64
    }

    override fun close() {
        llama_free(context)
        llama_model_free(model)
    }
}
