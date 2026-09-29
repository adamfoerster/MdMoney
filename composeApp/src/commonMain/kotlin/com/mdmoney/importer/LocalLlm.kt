package com.mdmoney.importer

/**
 * A language model running on this device (llama.cpp underneath on every platform). Nothing leaves
 * the machine: the statement's text goes in, grammar-constrained JSON comes out.
 */
interface LocalLlm {
    /**
     * One chat turn: the model's reply to [user] under [system], every token constrained by the GBNF
     * [grammar] and at most [maxTokens] long. Deterministic (greedy) so the same page reads the same
     * way twice. Cancelling the calling coroutine stops generation.
     */
    suspend fun complete(system: String, user: String, grammar: String, maxTokens: Int): String

    /** Frees the model's memory; the instance can't be used afterwards. */
    fun close()
}

/** A model the app offers to download, pinned by checksum. */
enum class ImportModelPreset(
    val id: String,
    val fileName: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
) {
    /** Small enough for a phone; the default. */
    LIGHT(
        id = "qwen2.5-1.5b",
        fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
        sizeBytes = 1_117_320_736,
    ),

    /** Reads awkward layouts better; best on a computer. */
    ACCURATE(
        id = "qwen2.5-3b",
        fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
        url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
        sha256 = "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d",
        sizeBytes = 2_104_932_768,
    );

    companion object {
        fun fromId(id: String?): ImportModelPreset? = entries.firstOrNull { it.id == id }
    }
}
