package com.mdmoney.ui.i18n

import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import com.mdmoney.importer.Doubt
import com.mdmoney.importer.TxKind

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
    val switchAccount: String
    val nothingThisMonth: String
    val decimalSeparator: String
    val separatorDot: String
    val separatorComma: String
    val incomeSection: String
    val expenseSection: String
    val recurringSection: String
    val eventualSection: String
    val net: String
    val addIncome: String
    val received: String
    val incomeAmountHint: String
    val groupLabel: String
    val groupHint: String
    val dateLabel: String
    val numberLabel: String
    val noteLabel: String
    val entries: String
    val noEntries: String
    val addEntry: String
    val editEntry: String
    val remove: String
    val oneOffHint: String
    val categories: String
    val uncategorized: String
    val spendByMonth: String
    val allCategories: String
    val nothingSpentThisYear: String
    val shareOfYear: String
    val currency: String
    val currencyNone: String
    val currencyReal: String
    val currencyEuro: String
    val currencyDollar: String
    val currencyCustom: String
    val currencySymbolLabel: String
    val editAccount: String
    val importStatement: String
    val importIntro: String
    val choosePdf: String
    val readingPdf: String
    val loadingModel: String
    val simpleModeNotice: String
    val modelFailedToRun: String
    val modelMissingNotice: String
    val importAccount: String
    val statementPeriod: String
    val extractedDebits: String
    val extractedCredits: String
    val declaredTotal: String
    val totalsMatch: String
    val totalsDiffer: String
    val alreadyImported: String
    val selectAll: String
    val selectNone: String
    val importAnother: String
    val importFailed: String
    val nothingFound: String
    val importedExpenseTitle: String
    val importedIncomeTitle: String
    val importModelSection: String
    val importModelHint: String
    val modelLight: String
    val modelAccurate: String
    val modelCustom: String
    val chooseModelFile: String
    val downloadModel: String
    val cancelDownload: String
    val removeModel: String
    val modelReady: String
    val modelMissing: String
    val modelVerifying: String
    val modelFailed: String
    val modelUnsupported: String
    fun pageProgress(done: Int, total: Int): String
    fun readingTime(elapsed: String, remainingMinutes: Long?): String
    fun importSelected(count: Int): String
    fun importDone(added: Int, skipped: Int, notes: Int): String
    fun currencyMismatch(statement: String, account: String): String
    fun modelDownloading(percent: Int): String
    fun kindName(kind: TxKind): String
    fun doubtText(doubt: Doubt): String
    fun typeName(type: ExpenseType): String
    fun month(m: Month): String
    fun monthShort(m: Month): String
}

fun stringsFor(language: Language): Strings = when (language) {
    Language.EN -> EnStrings
    Language.PT -> PtStrings
    Language.ES -> EsStrings
}
