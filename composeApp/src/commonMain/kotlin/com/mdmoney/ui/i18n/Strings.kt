package com.mdmoney.ui.i18n

import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month

/** Supported UI languages. [code] is the two-letter ISO code matched against the system language. */
enum class Language(val code: String, val displayName: String) {
    EN("en", "English"),
    PT("pt", "Português"),
    ES("es", "Español");

    companion object {
        fun fromCode(code: String?): Language? = entries.firstOrNull { it.code == code }
    }
}

/**
 * All user-facing strings. Hand-rolled (no resource library) so runtime language switching is a
 * plain state change and the whole thing stays trivially portable to wasmJs later.
 */
interface Strings {
    val appName: String
    val settings: String
    val language: String
    val systemDefault: String
    val selectVault: String
    val changeVault: String
    val vaultNotSelected: String
    val vaultNotSelectedHint: String
    val accounts: String
    val addAccount: String
    val accountName: String
    val noAccounts: String
    val create: String
    val cancel: String
    val save: String
    val add: String
    val year: String
    val total: String
    val projected: String
    val paid: String
    val markPaid: String
    val markUnpaid: String
    val addExpense: String
    val editExpense: String
    val titleLabel: String
    val categoryLabel: String
    val typeLabel: String
    val periodLabel: String
    val amountLabel: String
    val fixedAmountHint: String
    val variableAmountHint: String
    val noExpenses: String
    val loading: String
    val home: String
    val annual: String
    val reports: String
    val balance: String
    val initialBalance: String
    val setInitialBalance: String
    val addOneOff: String
    val thisMonth: String
    val reportsComingSoon: String
    val switchAccount: String
    val nothingThisMonth: String
    val decimalSeparator: String
    val separatorDot: String
    val separatorComma: String
    val incomeSection: String
    val expenseSection: String
    val recurringSection: String
    val oneOffSection: String
    val net: String
    val addIncome: String
    val received: String
    val incomeAmountHint: String
    val groupLabel: String
    val groupHint: String
    val dateLabel: String
    val noteLabel: String
    val entries: String
    val noEntries: String
    val addEntry: String
    val remove: String
    val oneOffHint: String
    fun typeName(type: ExpenseType): String
    fun month(m: Month): String
    fun monthShort(m: Month): String
}

fun stringsFor(language: Language): Strings = when (language) {
    Language.EN -> EnStrings
    Language.PT -> PtStrings
    Language.ES -> EsStrings
}
