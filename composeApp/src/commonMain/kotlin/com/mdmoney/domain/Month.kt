package com.mdmoney.domain

/**
 * The twelve months, keyed by the exact frontmatter keys the app writes (English, lowercase).
 *
 * February also accepts the legacy Portuguese key `fev` used by the existing Nubank vault files.
 * Reading is tolerant of the alias; writing always uses [key] (so `fev` is migrated to `feb`).
 */
enum class Month(val key: String, val number: Int, val aliases: List<String>, val english: String) {
    JAN("jan", 1, listOf("jan"), "January"),
    FEB("feb", 2, listOf("feb", "fev"), "February"),
    MAR("mar", 3, listOf("mar"), "March"),
    APR("apr", 4, listOf("apr"), "April"),
    MAY("may", 5, listOf("may"), "May"),
    JUN("jun", 6, listOf("jun"), "June"),
    JUL("jul", 7, listOf("jul"), "July"),
    AUG("aug", 8, listOf("aug"), "August"),
    SEP("sep", 9, listOf("sep"), "September"),
    OCT("oct", 10, listOf("oct"), "October"),
    NOV("nov", 11, listOf("nov"), "November"),
    DEC("dec", 12, listOf("dec"), "December");

    val paidKey: String get() = "$key-paid"

    /** Three-letter English abbreviation used in ledger file names (`2026 Jul - Alimentação.md`). */
    val englishShort: String get() = english.take(3)

    companion object {
        val ALL: List<Month> = entries

        fun fromNumber(number: Int): Month? = entries.firstOrNull { it.number == number }

        /** Matches the `month:` frontmatter of a ledger note, tolerating case and abbreviations. */
        fun fromEnglish(name: String?): Month? {
            val t = name?.trim()?.lowercase() ?: return null
            if (t.isEmpty()) return null
            return entries.firstOrNull { it.english.lowercase() == t }
                ?: entries.firstOrNull { it.englishShort.lowercase() == t }
        }
    }
}
