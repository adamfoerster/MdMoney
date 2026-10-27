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
    val pendingBills: String
    val nameLabel: String
    val change: String
    val chooseCategory: String
    val searchOrCreate: String
    val mostUsed: String
    val newCategory: String
    val amountPerMonth: String
    val repeatsIn: String
    val wholeYear: String
    val pickMonths: String
    val saveExpense: String
    val oneOffNewHint: String
    val variableNewHint: String
    val close: String
    fun typeHint(type: ExpenseType): String
    fun monthsSelected(n: Int): String
    fun everyMonthSummary(year: Int, total: String): String
    fun createCategory(name: String): String
    val noOpeningBalanceYet: String
    val variableEnterAmount: String
    val hideAmounts: String
    val showAmounts: String
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
    val groupFollowsCategory: String
    val groupHint: String
    val dateLabel: String
    val numberLabel: String
    val noteLabel: String
    val entries: String
    val noEntries: String
    val addEntry: String
    val editEntry: String
    val groupNone: String
    val moveToGroup: String
    val moveOutOfGroup: String
    val newGroupLabel: String
    val cannotGroupNotice: String
    val remove: String
    val oneOffHint: String
    val deleteEntry: String
    val fixedTag: String
    val variableTag: String
    val moveToAnother: String
    val oneOrRecurring: String
    val moveEntryTitle: String
    val back: String
    val destOneOff: String
    val destRecurring: String
    val newOneOffTitle: String
    val newOneOffSub: String
    val notSet: String
    val editGroup: String
    val tapEntryHint: String
    val cannotMoveNotice: String
    val useAsValueHint: String
    val addToValueHint: String
    fun entryFrom(date: String, title: String): String
    fun entryIn(date: String, title: String): String
    fun searchOneOffs(month: Month): String
    fun oneOffListHead(month: Month, year: Int): String
    fun recurringListHead(month: Month): String
    fun becomes(title: String, amount: String): String
    fun becomesEmpty(title: String): String
    fun moveTo(title: String): String
    fun continueWith(title: String): String
    fun recurringMoveHint(month: Month): String
    fun movingTo(title: String, month: Month, year: Int): String
    fun alreadyHas(title: String, amount: String, month: Month): String
    fun useAsValue(month: Month): String
    fun addToValue(month: Month): String
    fun markedPaid(title: String, month: Month): String
    fun noEntriesLeft(title: String): String
    fun moveSet(amount: String): String
    fun moveAdd(amount: String): String
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
    val reviewAmounts: String
    val reviewAmountsHint: String
    val confirmLine: String
    val pdfUnavailable: String
    fun reviewPosition(n: Int, total: Int): String
    fun plusVariable(n: Int): String
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
