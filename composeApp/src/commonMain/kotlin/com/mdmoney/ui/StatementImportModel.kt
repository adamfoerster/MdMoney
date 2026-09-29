package com.mdmoney.ui

import com.mdmoney.data.AppSettings
import com.mdmoney.data.VaultRepository
import com.mdmoney.domain.Category
import com.mdmoney.domain.Currency
import com.mdmoney.domain.Month
import com.mdmoney.importer.AccountGroups
import com.mdmoney.importer.EntryKey
import com.mdmoney.importer.ExtractionResult
import com.mdmoney.importer.GroupChoice
import com.mdmoney.importer.ImportModelPreset
import com.mdmoney.importer.ImportPlanner
import com.mdmoney.importer.ImportPlatform
import com.mdmoney.importer.ImportResult
import com.mdmoney.importer.LocalLlm
import com.mdmoney.importer.PickedPdf
import com.mdmoney.importer.ReviewRow
import com.mdmoney.importer.StatementExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Whether the statement-import model can be used, and what's happening to it. */
sealed interface ModelStatus {
    /** This build or device has no engine; the simple parser is all there is. */
    data object Unsupported : ModelStatus
    data object Missing : ModelStatus
    data class Downloading(val done: Long, val total: Long) : ModelStatus
    data object Verifying : ModelStatus
    data object Ready : ModelStatus

    /** The download failed or its checksum didn't match; the partial file is gone. */
    data class Failed(val reason: String) : ModelStatus
}

/** Which model is chosen: a downloadable preset, or a `.gguf` the user pointed at. */
data class ModelChoice(val preset: ImportModelPreset?, val customPath: String?)

sealed interface ImportPhase {
    data object Pick : ImportPhase
    data class Reading(
        val fileName: String,
        val done: Int,
        val total: Int,
        val loadingModel: Boolean,
        /** When page reading began; null while the PDF or the model is still loading. */
        val started: TimeMark? = null,
    ) : ImportPhase {
        /**
         * Seconds still to go, extrapolated from the pages done so far; null until one is done.
         * Pages vary in length, so it's an estimate — but it tells slow from stuck.
         */
        fun remainingSeconds(elapsedSeconds: Long): Long? =
            if (done <= 0 || total <= done) null else elapsedSeconds * (total - done) / done
    }
    data class Review(
        val fileName: String,
        val result: ExtractionResult,
        /** How many pages the PDF has, for the viewer beside the amount review. */
        val pageCount: Int = 0,
        /** The row being checked against the PDF (an index into the rows); null when not reviewing. */
        val reviewing: Int? = null,
    ) : ImportPhase
    data class Done(val result: ImportResult) : ImportPhase
    data class Failed(val fileName: String?, val reason: String) : ImportPhase
}

data class ImportUiState(
    val phase: ImportPhase = ImportPhase.Pick,
    val model: ModelChoice = ModelChoice(ImportModelPreset.LIGHT, null),
    val modelStatus: ModelStatus = ModelStatus.Missing,
    val rows: List<ReviewRow> = emptyList(),
    /** Where the reviewed lines will be written. */
    val account: String? = null,
    /** That account's currency, to warn when the statement is in another. */
    val accountCurrency: Currency = Currency.NONE,
    /** That account's existing groups, for filing lines under one of them. */
    val groups: AccountGroups = AccountGroups(),
    val importing: Boolean = false,
)

/**
 * The statement-import flow and the model it depends on: choosing and downloading the model (from
 * Settings), then pick a PDF → read it → review → write. The screen only renders this state; every
 * decision lives here or in [ImportPlanner].
 */
class StatementImportModel(
    private val platform: ImportPlatform,
    private val settings: AppSettings,
    private val repo: VaultRepository,
    private val scope: CoroutineScope,
    private val fallbackYear: () -> Int,
    private val onImported: suspend (account: String) -> Unit,
) {
    private val _state = MutableStateFlow(ImportUiState(model = readChoice()))
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var readJob: Job? = null
    private var downloadJob: Job? = null

    /** The statement under review, kept so its pages can be shown beside the lines read from it. */
    private var pdf: PickedPdf? = null

    /** What the chosen account already holds, for re-checking duplicates when an amount is corrected. */
    private var existing: Map<EntryKey, Int> = emptyMap()

    init {
        refreshModelStatus()
    }

    // --- model ---

    private fun readChoice(): ModelChoice {
        val stored = settings.importModel()
        val preset = ImportModelPreset.fromId(stored)
        return when {
            preset != null -> ModelChoice(preset, null)
            stored != null && stored.endsWith(".gguf", ignoreCase = true) -> ModelChoice(null, stored)
            else -> ModelChoice(ImportModelPreset.LIGHT, null)
        }
    }

    private fun modelPath(choice: ModelChoice = _state.value.model): String =
        choice.customPath ?: platform.modelPath((choice.preset ?: ImportModelPreset.LIGHT).fileName)

    private fun refreshModelStatus() {
        if (downloadJob?.isActive == true) return
        val status = when {
            !platform.engineAvailable -> ModelStatus.Unsupported
            platform.fileSize(modelPath()) != null -> ModelStatus.Ready
            else -> ModelStatus.Missing
        }
        _state.update { it.copy(modelStatus = status) }
    }

    fun choosePreset(preset: ImportModelPreset) {
        if (downloadJob?.isActive == true) return
        settings.setImportModel(preset.id)
        _state.update { it.copy(model = ModelChoice(preset, null)) }
        refreshModelStatus()
    }

    /** Whether this platform lets the user point at a model file (desktop). */
    val canPickModelFile: Boolean get() = platform.supportsModelFile

    /** Desktop: use a `.gguf` already on disk instead of downloading one. */
    fun chooseModelFile() = scope.launch {
        val path = platform.pickModelFile() ?: return@launch
        settings.setImportModel(path)
        _state.update { it.copy(model = ModelChoice(null, path)) }
        refreshModelStatus()
    }

    fun downloadModel() {
        val preset = _state.value.model.preset ?: return
        if (downloadJob?.isActive == true) return
        val path = platform.modelPath(preset.fileName)
        downloadJob = scope.launch {
            _state.update { it.copy(modelStatus = ModelStatus.Downloading(0, preset.sizeBytes)) }
            try {
                platform.download(preset.url, path) { done, total ->
                    _state.update { it.copy(modelStatus = ModelStatus.Downloading(done, total.takeIf { t -> t > 0 } ?: preset.sizeBytes)) }
                }
                _state.update { it.copy(modelStatus = ModelStatus.Verifying) }
                val sha = platform.sha256(path)
                if (sha != preset.sha256) {
                    platform.deleteFile(path)
                    _state.update { it.copy(modelStatus = ModelStatus.Failed("checksum")) }
                } else {
                    _state.update { it.copy(modelStatus = ModelStatus.Ready) }
                }
            } catch (e: CancellationException) {
                // Cancelled by the user: keep the partial file so the next attempt resumes.
                _state.update { it.copy(modelStatus = ModelStatus.Missing) }
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(modelStatus = ModelStatus.Failed(e.message ?: e::class.simpleName.orEmpty())) }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun removeModel() = scope.launch {
        val preset = _state.value.model.preset ?: return@launch
        platform.deleteFile(platform.modelPath(preset.fileName))
        refreshModelStatus()
    }

    // --- import flow ---

    /** Resets the flow for [account] (the open one) each time the screen is entered. */
    fun start(account: String) {
        readJob?.cancel()
        refreshModelStatus()
        pdf = null
        _state.update { it.copy(phase = ImportPhase.Pick, rows = emptyList(), account = account, importing = false) }
        scope.launch { loadCurrency(account) }
    }

    private suspend fun loadCurrency(account: String) {
        val currency = runCatching { repo.accountMeta(account).currency }.getOrDefault(Currency.NONE)
        // Read from the vault, not the open screen's list: the account being imported into needn't be
        // the open one, and that list only holds one year.
        val groups = runCatching { AccountGroups(repo.loadExpenses(account, fallbackYear())) }
            .getOrDefault(AccountGroups())
        _state.update { if (it.account == account) it.copy(accountCurrency = currency, groups = groups) else it }
    }

    fun cancel() {
        readJob?.cancel()
    }

    /** Changes where the lines go, re-checking which of them that account already holds. */
    fun setAccount(account: String) = scope.launch {
        // Groups belong to an account, so a pick made against the previous one no longer applies.
        _state.update { st -> st.copy(account = account, rows = st.rows.map { it.copy(group = null) }) }
        loadCurrency(account)
        val rows = _state.value.rows
        if (rows.isEmpty()) return@launch
        val months = rows.map { it.tx.date.year to Month.ALL.first { m -> m.number == it.tx.date.month } }.toSet()
        val existing = runCatching { repo.existingEntryCounts(account, months) }.getOrDefault(emptyMap())
        this@StatementImportModel.existing = existing
        _state.update { it.copy(rows = ImportPlanner.markExisting(it.rows, existing)) }
    }

    /** Picks a PDF and reads it, with the model when one is ready and the simple parser otherwise. */
    fun pickAndRead(categories: List<Category>) {
        readJob?.cancel()
        readJob = scope.launch {
            val pdf = platform.pickPdf() ?: return@launch
            var llm: LocalLlm? = null
            try {
                val useModel = _state.value.modelStatus == ModelStatus.Ready
                _state.update { it.copy(phase = ImportPhase.Reading(pdf.name, 0, 0, loadingModel = useModel)) }
                val pages = platform.pdfPages(pdf.bytes)
                var loadError: String? = null
                llm = if (useModel) {
                    try {
                        platform.openLlm(modelPath())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // An engine that won't load is an Error, not an Exception (UnsatisfiedLinkError).
                        loadError = StatementExtractor.describe(e)
                        null
                    }
                } else {
                    null
                }
                // The clock the screen counts from: a model can take a minute a page on a CPU, and a
                // screen that sits on "page 2 of 7" that long looks frozen.
                val started = TimeSource.Monotonic.markNow()
                _state.update { it.copy(phase = ImportPhase.Reading(pdf.name, 0, 0, loadingModel = false, started = started)) }
                val result = StatementExtractor(llm, categories).extract(pages, fallbackYear()) { done, total ->
                    _state.update { it.copy(phase = ImportPhase.Reading(pdf.name, done, total, loadingModel = false, started = started)) }
                }.let { if (loadError != null) it.copy(modelError = loadError) else it }
                val account = _state.value.account
                val months = result.transactions.map { it.date.year to Month.ALL.first { m -> m.number == it.date.month } }.toSet()
                val existing = account?.let { runCatching { repo.existingEntryCounts(it, months) }.getOrNull() }.orEmpty()
                this@StatementImportModel.pdf = pdf
                this@StatementImportModel.existing = existing
                _state.update {
                    it.copy(
                        phase = ImportPhase.Review(pdf.name, result, pageCount = pages.size),
                        rows = ImportPlanner.initialRows(result.transactions, existing),
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(phase = ImportPhase.Pick) }
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(phase = ImportPhase.Failed(pdf.name, e.message ?: e::class.simpleName.orEmpty())) }
            } finally {
                llm?.close()
            }
        }
    }

    fun toggle(id: Int) = updateRow(id) { it.copy(selected = !it.selected) }

    fun setDescription(id: Int, text: String) = updateRow(id) { it.copy(description = text) }

    fun setCategory(id: Int, slug: String?) = updateRow(id) { it.copy(category = slug) }

    fun setGroup(id: Int, group: GroupChoice?) = updateRow(id) { it.copy(group = group) }

    /** Ticks or unticks every row that isn't already in the vault. */
    fun selectAll(selected: Boolean) = _state.update { st ->
        st.copy(rows = st.rows.map { if (it.alreadyImported) it else it.copy(selected = selected) })
    }

    private fun updateRow(id: Int, change: (ReviewRow) -> ReviewRow) = _state.update { st ->
        st.copy(rows = st.rows.map { if (it.id == id) change(it) else it })
    }

    fun import(categories: List<Category>, uncategorizedTitle: String, incomeTitle: String) {
        val st = _state.value
        val account = st.account ?: return
        if (st.importing) return
        val entries = ImportPlanner.entries(st.rows, categories, uncategorizedTitle, incomeTitle)
        if (entries.isEmpty()) return
        _state.update { it.copy(importing = true) }
        scope.launch {
            try {
                val result = repo.importEntries(account, entries)
                onImported(account)
                _state.update { it.copy(phase = ImportPhase.Done(result), importing = false) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _state.update { it.copy(phase = ImportPhase.Failed(null, e.message ?: ""), importing = false) }
            }
        }
    }

    fun backToPick() {
        pdf = null
        _state.update { it.copy(phase = ImportPhase.Pick, rows = emptyList()) }
    }

    // --- amount review (when the totals disagree) ---

    /** Opens the PDF beside the first row, to check each amount against the page it was read from. */
    fun openAmountReview() = updateReview { phase, rows -> phase.copy(reviewing = 0.takeIf { rows.isNotEmpty() }) }

    fun closeAmountReview() = updateReview { phase, _ -> phase.copy(reviewing = null) }

    /** The row under review is right (or has been corrected): on to the next, or back when it was the last. */
    fun confirmAndNext() = updateReview { phase, rows ->
        phase.copy(reviewing = phase.reviewing?.let { ImportPlanner.nextReview(it, rows.size) })
    }

    /** Corrects row [id]'s amount and re-totals the statement, so the user sees whether it now matches. */
    fun setAmount(id: Int, amount: Double) = _state.update { st ->
        val phase = st.phase as? ImportPhase.Review ?: return@update st
        val rows = ImportPlanner.withAmount(st.rows, id, amount, existing)
        st.copy(
            rows = rows,
            phase = phase.copy(result = phase.result.copy(reconciliation = ImportPlanner.reconcile(rows, phase.result.header))),
        )
    }

    private fun updateReview(change: (ImportPhase.Review, List<ReviewRow>) -> ImportPhase.Review) = _state.update { st ->
        val phase = st.phase as? ImportPhase.Review ?: return@update st
        st.copy(phase = change(phase, st.rows))
    }

    /** Page [page] of the statement under review as PNG, [widthPx] wide; null when it can't be drawn. */
    suspend fun renderPage(page: Int, widthPx: Int): ByteArray? {
        val bytes = pdf?.bytes ?: return null
        return try {
            platform.renderPdfPage(bytes, page, widthPx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }
}
