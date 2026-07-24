package com.mdmoney.ui

import com.mdmoney.data.AccountMeta
import com.mdmoney.data.AppSettings
import com.mdmoney.data.CacheDb
import com.mdmoney.data.DecimalSeparator
import com.mdmoney.data.VaultRepository
import com.mdmoney.data.VaultStorage
import com.mdmoney.domain.Category
import com.mdmoney.domain.Currency
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Ledger
import com.mdmoney.domain.LedgerEntry
import com.mdmoney.domain.Month
import com.mdmoney.platform.currentDay
import com.mdmoney.platform.currentMonth
import com.mdmoney.platform.currentYear
import com.mdmoney.platform.ioDispatcher
import com.mdmoney.platform.systemLanguage
import com.mdmoney.ui.i18n.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Setup : Screen
    data object Settings : Screen
    data object Accounts : Screen

    /** The tabbed shell shown once an account is open (Home / Annual / Reports / Settings). */
    data object AccountShell : Screen
}

/** Bottom-navigation destinations inside an open account. */
enum class HomeTab { HOME, ANNUAL, REPORTS, SETTINGS }

/** The starting point + context for the add/edit expense sheet. */
data class EditorState(
    val isNew: Boolean,
    val original: Expense?,
    val initial: Expense,
    val defaultMonth: Month? = null,
)

/**
 * The add-one-off sheet: a single purchase to append to a month's ledger.
 *
 * [knownGroups] and [knownCategories] are what the account already uses, so recording another coffee
 * is a tap rather than retyping "Alimentação".
 */
data class OneOffState(
    val month: Month,
    val date: String,
    val group: String = "",
    val knownGroups: List<String> = emptyList(),
    val knownCategories: List<Category> = emptyList(),
    /**
     * True when the sheet was opened from a group's ledger (rather than the month's quick-add), so
     * saving returns to that ledger with the new purchase in it rather than dropping back to the list.
     */
    val reopenLedger: Boolean = false,
)

data class UiState(
    val language: Language,
    val decimalSeparator: DecimalSeparator,
    val followSystem: Boolean,
    val hasVault: Boolean,
    val vaultLabel: String?,
    val screen: Screen,
    val tab: HomeTab = HomeTab.HOME,
    val accounts: List<String> = emptyList(),
    val selectedAccount: String? = null,
    val year: Int,
    /** The open account's display title (from its root note); folder name when it has none. */
    val accountTitle: String? = null,
    val availableYears: List<Int> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val homeMonth: Month,
    val initialBalance: Double? = null,
    val balance: Double? = null,
    val loading: Boolean = false,
    val editor: EditorState? = null,
    val oneOff: OneOffState? = null,
    val ledger: Ledger? = null,
    /** The vault's category notes, which put a title on every `[[casa|Casa]]` the notes carry. */
    val categories: List<Category> = emptyList(),
    /** The open account's currency; drives the symbol shown on every figure. [Currency.NONE] shows none. */
    val currency: Currency = Currency.NONE,
    /** The category being read on the Reports tab; null is the list of all of them. */
    val openCategory: String? = null,
)

/**
 * Single app-wide state holder. Plain class exposing a [StateFlow] and intent methods (no AndroidX
 * ViewModel, no DI framework) so it compiles unchanged for Android, iOS, desktop, and later wasmJs.
 *
 * Reads are served from a SQLite cache for instant paint; edits update the cache synchronously and
 * flush to the markdown files asynchronously (files remain the source of truth).
 */
class AppModel(
    private val storage: VaultStorage,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
    dbPath: String,
    private val initialAccount: String? = null,
) {
    private val repo = VaultRepository(storage, CacheDb(dbPath))

    private val thisYear: Int = currentYear()
    private val thisMonth: Month = Month.ALL.first { it.number == currentMonth() }

    /** Parsed metadata for the open account (opening balances); null when none/unopened. */
    private var meta: AccountMeta? = null

    private val _state = MutableStateFlow(
        UiState(
            language = resolveLanguage(),
            decimalSeparator = DecimalSeparator.fromCode(settings.decimalSeparator()),
            followSystem = settings.language() == null,
            hasVault = storage.hasVault(),
            vaultLabel = storage.vaultLabel(),
            screen = if (storage.hasVault()) Screen.Accounts else Screen.Setup,
            year = thisYear,
            homeMonth = thisMonth,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (storage.hasVault()) scope.launch {
            _state.update { it.copy(loading = true) }
            val accounts = runCatching { repo.accounts() }.getOrDefault(emptyList())
            _state.update { it.copy(accounts = accounts, loading = false) }
            // Optional deep link: open a specific account straight away.
            initialAccount?.takeIf { it in accounts }?.let { openAccount(it) }
        }
    }

    private fun resolveLanguage(): Language =
        Language.fromCode(settings.language())
            ?: Language.fromCode(systemLanguage())
            ?: Language.EN

    // --- Navigation ---

    fun openSettings() = _state.update { it.copy(screen = Screen.Settings) }

    fun selectTab(tab: HomeTab) = _state.update { it.copy(tab = tab) }

    fun switchAccount() = _state.update {
        it.copy(screen = Screen.Accounts, selectedAccount = null, editor = null, openCategory = null)
    }

    // --- Reports ---

    /** Opens one category's year: everything filed under it, and what it cost month by month. */
    fun openCategory(slug: String) = _state.update { it.copy(openCategory = slug) }

    fun closeCategory() = _state.update { it.copy(openCategory = null) }

    fun back() = _state.update {
        val target = when (it.screen) {
            Screen.Settings -> if (it.hasVault) Screen.Accounts else Screen.Setup
            Screen.AccountShell -> Screen.Accounts
            else -> it.screen
        }
        it.copy(screen = target, editor = null)
    }

    // --- Vault ---

    fun pickVault() = scope.launch {
        if (storage.pickVault()) {
            _state.update {
                it.copy(
                    hasVault = true,
                    vaultLabel = storage.vaultLabel(),
                    screen = Screen.Accounts,
                )
            }
            refreshAccounts()
        }
    }

    private fun refreshAccounts() = scope.launch {
        _state.update { it.copy(loading = true) }
        val accounts = runCatching { repo.accounts() }.getOrDefault(emptyList())
        _state.update { it.copy(accounts = accounts, loading = false) }
    }

    fun createAccount(name: String, currency: Currency = Currency.NONE) = scope.launch {
        if (name.isBlank()) return@launch
        runCatching { repo.createAccount(name) }
        // Persist the chosen currency before opening, so the first paint already shows its symbol.
        if (currency.hasSymbol) runCatching { repo.setCurrency(name.trim(), currency) }
        refreshAccounts().join()
        openAccount(name.trim())
    }

    // --- Account shell ---

    /** Opens [account] into the tabbed shell: instant cache paint, then a background disk sync. */
    fun openAccount(account: String) = scope.launch {
        meta = runCatching { repo.accountMeta(account) }.getOrNull()
        _state.update {
            it.copy(
                selectedAccount = account,
                screen = Screen.AccountShell,
                tab = HomeTab.HOME,
                loading = true,
                openCategory = null,
                currency = meta?.currency ?: Currency.NONE,
                accountTitle = meta?.title ?: account,
            )
        }
        refreshFromCache(account)
        runCatching { repo.syncAccount(account, thisYear) }
        refreshFromCache(account)
        _state.update { it.copy(loading = false) }
    }

    fun setYear(year: Int) = scope.launch {
        _state.update { it.copy(year = year) }
        _state.value.selectedAccount?.let { recomputeBalance(it) }
    }

    fun setHomeMonth(month: Month) = _state.update { it.copy(homeMonth = month) }

    private suspend fun refreshFromCache(account: String) {
        val all = runCatching { repo.cachedExpenses(account) }.getOrDefault(emptyList())
        val years = (all.map { it.year } + thisYear).distinct().sortedDescending()
        _state.update { st ->
            st.copy(
                expenses = all,
                categories = repo.cachedCategories(),
                availableYears = years,
                year = if (st.year in years) st.year else (years.firstOrNull() ?: thisYear),
            )
        }
        recomputeBalance(account)
    }

    /** Checkbook balance: what you opened the year with, plus money received, minus money paid. */
    private suspend fun recomputeBalance(account: String) {
        val year = _state.value.year
        val initial = meta?.initialBalance(year)
        val paid = runCatching { repo.paidTotal(account, year) }.getOrDefault(0.0)
        val received = runCatching { repo.receivedTotal(account, year) }.getOrDefault(0.0)
        _state.update { it.copy(initialBalance = initial, balance = initial?.let { b -> b + received - paid }) }
    }

    fun setInitialBalance(value: Double?) = scope.launch {
        val account = _state.value.selectedAccount ?: return@launch
        val year = _state.value.year
        meta = runCatching { repo.setInitialBalance(account, year, value) }.getOrNull() ?: meta
        recomputeBalance(account)
    }

    /**
     * Edits the open account's display title and currency together. A blank title is ignored — the
     * account keeps the one it had — while the currency (including [Currency.NONE]) always applies.
     */
    fun editAccount(title: String, currency: Currency) = scope.launch {
        val account = _state.value.selectedAccount ?: return@launch
        val cleanTitle = title.trim().ifBlank { meta?.title ?: account }
        meta = runCatching { repo.updateAccount(account, cleanTitle, currency) }.getOrNull() ?: meta
        _state.update {
            it.copy(currency = meta?.currency ?: currency, accountTitle = meta?.title ?: cleanTitle)
        }
    }

    // --- Editing ---

    fun openAdd() = _state.update { st ->
        val account = st.selectedAccount ?: return@update st
        st.copy(editor = EditorState(isNew = true, original = null, initial = Expense.empty(account, st.year)))
    }

    /**
     * Record a one-off purchase in the month currently in view. It becomes a row in that month's
     * ledger note rather than a file of its own — a month of coffees is one note, not thirty.
     */
    fun openAddOneOff(group: String = "", month: Month? = null, reopenLedger: Boolean = false) = _state.update { st ->
        if (st.selectedAccount == null) return@update st
        // A modal sheet at a time: opening this from a ledger must dismiss the ledger, or the two
        // stack and the ledger — composed last — hides the one-off sheet entirely (App.kt).
        val m = month ?: st.homeMonth
        st.copy(
            ledger = null,
            oneOff = OneOffState(
                month = m,
                date = defaultDate(st.year, m),
                group = group,
                knownGroups = st.expenses.filter { it.ledger }.map { it.title }.distinct().sorted(),
                knownCategories = st.categories,
                reopenLedger = reopenLedger,
            ),
        )
    }

    fun closeOneOff() = _state.update { it.copy(oneOff = null) }

    /** Today when the month in view is the current one, otherwise that month's first day. */
    private fun defaultDate(year: Int, month: Month): String =
        if (year == thisYear && month == thisMonth) {
            LedgerEntry.dateOf(year, month.number, currentDay())
        } else {
            LedgerEntry.dateOf(year, month.number, 1)
        }

    fun saveOneOff(group: String, category: String?, date: String, note: String, amount: Double?) = scope.launch {
        val account = _state.value.selectedAccount ?: return@launch
        val oneOff = _state.value.oneOff
        val month = oneOff?.month ?: _state.value.homeMonth
        val reopenLedger = oneOff?.reopenLedger == true
        val year = _state.value.year
        _state.update { it.copy(oneOff = null) }
        if (group.isBlank() || amount == null) return@launch
        val saved = runCatching {
            repo.addOneOff(account, year, month, group, category?.ifBlank { null }, LedgerEntry(date, note.trim(), amount))
        }.getOrNull()
        refreshFromCache(account)
        // Came from the group's ledger: return to it, now carrying the purchase just recorded.
        if (reopenLedger && saved != null) _state.update { it.copy(ledger = saved) }
    }

    // --- ledger detail ---

    /** Opens the purchases behind a ledger row (its amount is their sum, not directly editable). */
    fun openLedger(expense: Expense) = scope.launch {
        val ledger = runCatching { repo.loadLedger(expense.account, expense.id, expense.year) }.getOrNull()
        if (ledger != null) _state.update { it.copy(ledger = ledger) }
    }

    fun closeLedger() = _state.update { it.copy(ledger = null) }

    fun removeLedgerEntry(entry: LedgerEntry) = scope.launch {
        val current = _state.value.ledger ?: return@launch
        val updated = current.copy(entries = current.entries - entry)
        runCatching { repo.saveLedger(updated) }
        _state.update { it.copy(ledger = updated) }
        _state.value.selectedAccount?.let { refreshFromCache(it) }
    }

    /**
     * Replaces one purchase in the open ledger with an edited version, keeping its position. Matched
     * by the first equal entry, so two byte-identical rows edit the earlier one — the same rule
     * [removeLedgerEntry] follows. A blank amount leaves the ledger untouched.
     */
    fun updateLedgerEntry(original: LedgerEntry, edited: LedgerEntry) = scope.launch {
        val current = _state.value.ledger ?: return@launch
        val index = current.entries.indexOf(original)
        if (index < 0) return@launch
        val updated = current.copy(
            entries = current.entries.toMutableList().apply { this[index] = edited },
        )
        runCatching { repo.saveLedger(updated) }
        _state.update { it.copy(ledger = updated) }
        _state.value.selectedAccount?.let { refreshFromCache(it) }
    }

    /** Add an income entry (salary, bonus); amounts are then entered per month like a variable bill. */
    fun openAddIncome() = _state.update { st ->
        val account = st.selectedAccount ?: return@update st
        st.copy(
            editor = EditorState(
                isNew = true,
                original = null,
                initial = Expense.empty(account, st.year, ExpenseType.INCOME),
                defaultMonth = st.homeMonth,
            ),
        )
    }

    fun openEdit(expense: Expense) = _state.update {
        it.copy(editor = EditorState(isNew = false, original = expense, initial = expense))
    }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    fun saveExpense(original: Expense?, draft: Expense) = scope.launch {
        val resolved = runCatching { repo.cacheSave(draft) }.getOrNull()
        _state.update { it.copy(editor = null) }
        _state.value.selectedAccount?.let { refreshFromCache(it) }
        if (resolved != null) persistAsync(original, resolved)
    }

    /** Edit a single grid cell (amount + paid) in one write. */
    fun updateCell(expense: Expense, month: Month, amount: Double?, paid: Boolean) = applyEdit(
        expense,
        expense.copy(
            amounts = expense.amounts.toMutableMap().apply { put(month, amount) },
            paid = expense.paid.toMutableMap().apply { put(month, paid) },
        ),
    )

    fun togglePaid(expense: Expense, month: Month) = applyEdit(
        expense,
        expense.copy(paid = expense.paid.toMutableMap().apply { put(month, !(expense.paid[month] ?: false)) }),
    )

    /** Home: flip the paid flag for the month currently in view. */
    fun togglePaidThisMonth(expense: Expense) = togglePaid(expense, _state.value.homeMonth)

    /** Home: set a variable expense's amount for the month currently in view. */
    fun setVariableAmountThisMonth(expense: Expense, amount: Double?) {
        val month = _state.value.homeMonth
        applyEdit(expense, expense.copy(amounts = expense.amounts.toMutableMap().apply { put(month, amount) }))
    }

    /** Cache-first edit: write the cache and repaint instantly, then flush the file asynchronously. */
    private fun applyEdit(original: Expense, updated: Expense) = scope.launch {
        val resolved = runCatching { repo.cacheSave(updated) }.getOrNull() ?: return@launch
        _state.value.selectedAccount?.let { refreshFromCache(it) }
        persistAsync(original, resolved)
    }

    private fun persistAsync(original: Expense?, resolved: Expense) {
        scope.launch(ioDispatcher) { runCatching { repo.persist(original, resolved) } }
    }

    // --- Settings ---

    fun setLanguage(language: Language?) {
        settings.setLanguage(language?.code)
        _state.update {
            it.copy(
                language = language ?: (Language.fromCode(systemLanguage()) ?: Language.EN),
                followSystem = language == null,
            )
        }
    }

    fun setDecimalSeparator(separator: DecimalSeparator) {
        settings.setDecimalSeparator(separator.code)
        _state.update { it.copy(decimalSeparator = separator) }
    }
}
