package com.mdmoney.data

/**
 * Minimal, dependency-free reader/writer for the small subset of YAML that Obsidian frontmatter
 * uses in this vault: `key: value` scalars, block lists (`key:` followed by indented `- item`
 * lines), empty values, and a free-form markdown body after the closing `---`.
 *
 * It does not attempt to be a general YAML implementation — it preserves what it doesn't manage
 * rather than interpreting it, which is exactly what round-tripping a user's notes requires.
 */
object FrontmatterParser {

    private const val DELIMITER = "---"

    fun parse(content: String): MarkdownNote {
        val normalized = content.replace("\r\n", "\n")
        val lines = normalized.split("\n")

        // A frontmatter block requires the very first line to be exactly `---`.
        if (lines.isEmpty() || lines[0].trim() != DELIMITER) {
            return MarkdownNote(mutableListOf(), normalized)
        }

        var closingIndex = -1
        for (i in 1 until lines.size) {
            if (lines[i].trim() == DELIMITER) {
                closingIndex = i
                break
            }
        }
        if (closingIndex == -1) {
            // No closing delimiter: treat the whole thing as body to avoid destroying content.
            return MarkdownNote(mutableListOf(), normalized)
        }

        val fmLines = lines.subList(1, closingIndex)
        val entries = parseEntries(fmLines)

        // Body is everything after the closing delimiter line, preserved verbatim.
        val body = if (closingIndex + 1 <= lines.lastIndex) {
            lines.subList(closingIndex + 1, lines.size).joinToString("\n")
        } else {
            ""
        }
        return MarkdownNote(entries, body)
    }

    private fun parseEntries(fmLines: List<String>): MutableList<FmEntry> {
        val entries = mutableListOf<FmEntry>()
        var i = 0
        while (i < fmLines.size) {
            val line = fmLines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            val colon = topLevelColon(line)
            if (colon == -1) {
                // Not a recognizable key line; keep verbatim under a synthetic empty key.
                entries.add(FmEntry.Raw("", listOf(line)))
                i++
                continue
            }
            val key = line.substring(0, colon).trim()
            val rest = line.substring(colon + 1)
            if (rest.isNotBlank()) {
                entries.add(FmEntry.Scalar(key, rest.trim()))
                i++
            } else {
                // Empty value: gather following indented lines (block list / continuation).
                val block = mutableListOf(line)
                var j = i + 1
                while (j < fmLines.size && fmLines[j].isNotEmpty() && fmLines[j][0].isWhitespace()) {
                    block.add(fmLines[j])
                    j++
                }
                if (block.size > 1) {
                    entries.add(FmEntry.Raw(key, block))
                } else {
                    entries.add(FmEntry.Scalar(key, ""))
                }
                i = j
            }
        }
        return entries
    }

    /** Index of the key/value colon (a top-level line, so the first colon), or -1. */
    private fun topLevelColon(line: String): Int {
        if (line.isEmpty() || line[0].isWhitespace() || line[0] == '-') return -1
        return line.indexOf(':')
    }

    fun serialize(note: MarkdownNote): String {
        val sb = StringBuilder()
        sb.append(DELIMITER).append('\n')
        for (entry in note.entries) {
            when (entry) {
                is FmEntry.Scalar -> {
                    if (entry.value.isEmpty()) {
                        sb.append(entry.key).append(':').append('\n')
                    } else {
                        sb.append(entry.key).append(": ").append(entry.value).append('\n')
                    }
                }
                is FmEntry.Raw -> {
                    for (l in entry.lines) sb.append(l).append('\n')
                }
            }
        }
        sb.append(DELIMITER).append('\n')
        sb.append(note.body)
        return sb.toString()
    }
}
