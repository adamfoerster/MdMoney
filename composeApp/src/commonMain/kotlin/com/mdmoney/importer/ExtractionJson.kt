package com.mdmoney.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reads the model's JSON answers. The grammar already fixes their shape, but a model can still be
 * cut off at the token limit or be swapped for one run without a grammar, so this never throws:
 * whatever doesn't make sense is dropped and the rest is kept.
 */
object ExtractionJson {

    private val json = Json { isLenient = true }

    /**
     * The model's verdict on each marked line of a page (see [StatementGrammar.lines]), made into
     * transactions. The amount, the date and the description come from the line itself; the model
     * contributes whether it is a transaction, which way the money went (unless a `+` is printed),
     * its kind and category — and the date only for a line that had none. A line the model calls a
     * transaction but leaves without a usable date is dropped. Keyed by line number.
     */
    fun lineAnswers(text: String, candidates: List<CandidateLine>): Map<Int, RawTransaction> {
        val root = parse(text) as? JsonObject ?: return emptyMap()
        val items = root["lines"] as? JsonArray ?: return emptyMap()
        val byNumber = candidates.associateBy { it.number }
        return items.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            if (o.primitive("tx")?.contentOrNull != "true") return@mapNotNull null
            val candidate = byNumber[o.int("n")] ?: return@mapNotNull null
            val line = candidate.line
            if (!line.hasWords) return@mapNotNull null
            val date = line.effectiveDate ?: run {
                val day = o.int("day") ?: return@mapNotNull null
                val month = o.int("month") ?: return@mapNotNull null
                DateParts(null, month, day)
            }
            if (date.month !in 1..12 || date.day !in 1..31) return@mapNotNull null
            val direction = if (line.sign == Direction.CREDIT) Direction.CREDIT else Direction.fromId(o.string("dir")) ?: Direction.DEBIT
            val description = line.description
            candidate.number to RawTransaction(
                year = date.year,
                month = date.month,
                day = date.day,
                description = description,
                amount = candidate.amount,
                direction = direction,
                kind = TxKind.fromId(o.string("kind")) ?: HeuristicStatementParser.guessKind(description, direction),
                category = o.string("cat")?.takeIf { it.isNotBlank() && it != StatementGrammar.NO_CATEGORY },
            )
        }.toMap()
    }

    /** The first page's facts; fields the model couldn't read stay null. */
    fun header(text: String): StatementHeader {
        val o = parse(text) as? JsonObject ?: return StatementHeader()
        val start = SimpleDate.parseIso(o.string("start"))
        val end = SimpleDate.parseIso(o.string("end"))
        return StatementHeader(
            period = if (start != null && end != null && start <= end) StatementPeriod(start, end) else null,
            currency = o.string("currency")?.uppercase()?.takeIf { it.length == 3 && it.all(Char::isLetter) },
            declaredDebits = o.number("debits")?.let { kotlin.math.abs(it) },
            declaredCredits = o.number("credits")?.let { kotlin.math.abs(it) },
        )
    }

    /**
     * Parses [text], or — when it was cut off mid-array — its longest prefix that closes cleanly, so
     * one truncated page still yields every complete line before the cut.
     */
    private fun parse(text: String): JsonElement? {
        runCatching { return json.parseToJsonElement(text.trim()) }
        val cut = text.lastIndexOf("},")
        if (cut < 0) return null
        return runCatching { json.parseToJsonElement(text.substring(0, cut + 1) + "]}") }.getOrNull()
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

    private fun JsonObject.string(key: String): String? = primitive(key)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        primitive(key)?.let { it.intOrNull ?: it.contentOrNull?.trim()?.toIntOrNull() }

    private fun JsonObject.number(key: String): Double? =
        primitive(key)?.let { p -> p.doubleOrNull ?: p.contentOrNull?.let { StatementText.parseAmount(it) } }
}
