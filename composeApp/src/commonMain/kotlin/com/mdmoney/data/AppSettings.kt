package com.mdmoney.data

/** Small persisted preferences that aren't tied to the vault contents. */
interface AppSettings {
    /** Stored language override (ISO code), or null to follow the system language. */
    fun language(): String?

    fun setLanguage(code: String?)

    /** Stored decimal-separator code ([DecimalSeparator.code]), or null to use the default (dot). */
    fun decimalSeparator(): String?

    fun setDecimalSeparator(code: String?)

    /**
     * The statement-import model: a preset id (`ImportModelPreset.id`), or a path to a `.gguf` the
     * user pointed at. Null means the default preset.
     */
    fun importModel(): String?

    fun setImportModel(value: String?)
}
