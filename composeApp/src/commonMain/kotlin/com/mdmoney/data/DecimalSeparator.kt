package com.mdmoney.data

/**
 * How the cents separator is rendered in the UI. Storage (the markdown frontmatter) always uses a
 * dot; this only affects display and editable-field text. The default is [DOT].
 */
enum class DecimalSeparator(val code: String, val char: Char) {
    DOT("dot", '.'),
    COMMA("comma", ',');

    companion object {
        val DEFAULT = DOT

        fun fromCode(code: String?): DecimalSeparator =
            entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}
