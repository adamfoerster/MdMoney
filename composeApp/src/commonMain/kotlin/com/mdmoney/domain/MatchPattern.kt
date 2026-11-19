package com.mdmoney.domain

/**
 * One entry of a note's `match:` list: a pattern a statement line's description is tested against,
 * so the import can file the line where lines like it went before.
 *
 * Two forms, chosen by how the text is written:
 *
 * - **Glob** (the default and what the app learns): the whole description must match, ignoring
 *   case and runs of spaces. `*` stands for any run of characters (none included) and `?` for one.
 *   `Card Purchase Publix*` takes every line that *starts* with that; `*publix*` takes any line
 *   that mentions it. Every other character is literal, and since `*` and `?` also match
 *   themselves, a description copied as it is always matches its own line.
 * - **Regex**, written between slashes: `/^pin purchase (wal-mart|walmart)/`. Searched anywhere in
 *   the description, ignoring case, for what a glob can't say. A regex that doesn't compile matches
 *   nothing rather than breaking the import.
 */
class MatchPattern private constructor(val text: String, private val regex: Regex?, private val glob: Boolean) {

    /** Whether [description] is a line this pattern takes. */
    fun matches(description: String): Boolean {
        val r = regex ?: return false
        val d = normalize(description)
        return if (glob) r.matches(d) else r.containsMatchIn(d)
    }

    /**
     * How specific the pattern is — its count of literal characters — so the most precise of
     * several matching patterns wins. A regex counts its own length, as there is no better measure.
     */
    val specificity: Int = if (glob) normalize(text).count { it != '*' && it != '?' } else text.length - 2

    override fun equals(other: Any?): Boolean = other is MatchPattern && normalize(other.text) == normalize(text)

    override fun hashCode(): Int = normalize(text).hashCode()

    override fun toString(): String = text

    companion object {
        /** Reads one `match:` item; null when it is blank. */
        fun of(text: String): MatchPattern? {
            val t = text.trim()
            if (t.isEmpty()) return null
            if (t.length > 2 && t.startsWith("/") && t.endsWith("/")) {
                val regex = runCatching { Regex(t.substring(1, t.length - 1), RegexOption.IGNORE_CASE) }.getOrNull()
                return MatchPattern(t, regex, glob = false)
            }
            val source = buildString {
                for (c in normalize(t)) when (c) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(c.toString()))
                }
            }
            return MatchPattern(t, Regex(source), glob = true)
        }

        /** Lower case, trimmed, every run of whitespace one space: what both sides are compared as. */
        fun normalize(s: String): String = s.trim().lowercase().replace(Regex("""\s+"""), " ")

        /**
         * The pattern the app writes after filing [description] somewhere: the description up to the
         * first part that changes from one purchase to the next, then `*`.
         *
         * Statements put the merchant first and the variable details after it — a store number, a
         * category code, a city and ZIP, a card's last digits, a reference after `*`. So the
         * description is cut at the first word that is a number (`5411`, `#1899`, `98109`) or that
         * carries a digit after a `*` (`Mktpl*8y` keeps `Mktpl*`, `Sunpass*acc1442` keeps
         * `Sunpass*`). A word like `7-Eleven` has letters around its digit and stays.
         *
         * `Card Purchase Publix #1899 5411 Palm Bay FL 32907 3914` → `Card Purchase Publix*`.
         * A description with nothing to cut still gets the `*`, so a later line that only adds a
         * reference at the end matches too. Null for a blank description.
         */
        fun learnFrom(description: String): String? {
            val words = description.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            val kept = mutableListOf<String>()
            for (word in words) {
                if (isNumber(word)) break
                // Before the code check: `Sunpass*acc1442` keeps the merchant in front of its `*`.
                val star = word.indexOf('*')
                if (star >= 0 && word.substring(star + 1).any { it.isDigit() }) {
                    if (word.substring(0, star).any { it.isLetter() }) kept += word.substring(0, star + 1)
                    break
                }
                if (isCode(word)) break
                kept += word
            }
            // A sign left dangling before the cut (`Trader Joe S #`, `City Arts Mkt @`) says nothing.
            while (kept.isNotEmpty() && kept.last().none { it.isLetterOrDigit() }) kept.removeLast()
            // Cut before anything that names a merchant (a description that opens with a number):
            // keep it whole, or the pattern would be a bare `*` that takes every line.
            val head = (if (kept.any { w -> w.any { it.isLetter() } }) kept else words).joinToString(" ")
            return head.trimEnd('*').trimEnd() + "*"
        }

        /** `5411`, `#1899`, `98109`, `12/03`, `1,234.56`: digits with at most a sign or separator. */
        private fun isNumber(word: String): Boolean =
            word.any { it.isDigit() } && word.all { it.isDigit() || it in "#*.,/-:" }

        /**
         * A store number or reference spelled with letters: `F319`, `T-2547`, `4j`, `337u4`,
         * `It3qqlkc4`. Digits with at most two letters, or letters and digits run together. A name
         * that only joins a number to a word with a hyphen, like `7-Eleven`, is not one.
         */
        private fun isCode(word: String): Boolean {
            val letters = word.count { it.isLetter() }
            val digits = word.count { it.isDigit() }
            return digits > 0 && (letters <= 2 || (letters > 0 && '-' !in word))
        }
    }
}
