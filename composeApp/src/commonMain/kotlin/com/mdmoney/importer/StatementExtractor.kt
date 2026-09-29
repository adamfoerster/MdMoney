package com.mdmoney.importer

import com.mdmoney.domain.Category
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Turns a statement's pages into reviewable transactions.
 *
 * With a [llm] the model reads each page (constrained by [StatementGrammar]); without one — or when
 * it fails — [HeuristicStatementParser] does. Either way the deterministic part is the same and runs
 * last: the period and currency are read from the text itself where possible, missing years are
 * settled against the period, every amount is checked against the page it came from, and the
 * totals are reconciled with what the statement prints.
 */
class StatementExtractor(
    private val llm: LocalLlm?,
    private val categories: List<Category>,
) {
    /**
     * [onProgress] is told how many of the pages worth reading are done, out of how many.
     * Cancellation (leaving the screen) stops between and during pages.
     */
    suspend fun extract(
        pages: List<String>,
        fallbackYear: Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExtractionResult {
        val text = pages.joinToString("\n")
        val currency = StatementText.detectCurrency(text)
        val monthFirst = StatementText.isMonthFirst(text, currency)
        var header = StatementHeader(
            period = StatementText.detectPeriod(text, monthFirst),
            currency = currency,
        )
        // Every line taken apart once; the model then sees each page with its amount lines
        // numbered, and a page with no amount can't hold a transaction and is spared it.
        val compacted = pages.map { StatementPrompt.compact(it) }
        val lines = StatementLines.analyze(compacted, monthFirst).groupBy { it.page }
        val readable = compacted.indices
            .map { i -> Triple(i, lines[i].orEmpty(), CandidateLines.of(lines[i].orEmpty())) }
            .filter { it.third.isNotEmpty() }

        var raw: List<Pair<RawTransaction, String>>? = null
        var modelError: String? = null
        if (llm != null && readable.isNotEmpty()) {
            try {
                header = mergeHeader(header, readHeader(llm, pages.first()), text)
                val out = mutableListOf<Pair<RawTransaction, String>>()
                val system = StatementPrompt.transactionsSystem(header, monthFirst, categories)
                val slugs = categories.map { it.slug }
                onProgress(0, readable.size)
                readable.forEachIndexed { done, (index, pageLines, candidates) ->
                    currentCoroutineContext().ensureActive()
                    val answer = llm.complete(
                        system,
                        StatementPrompt.pageMessage(pageLines, index, pages.size),
                        StatementGrammar.lines(candidates.map { it.needsDate }, slugs),
                        maxTokens = TOKENS_PER_LINE * candidates.size + 16,
                    )
                    val model = ExtractionJson.lineAnswers(answer, candidates)
                    candidates.forEach { c ->
                        vote(c.line, model[c.number])?.let { out.add(it to compacted[index]) }
                    }
                    onProgress(done + 1, readable.size)
                }
                raw = out
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // The model failed (out of memory, a grammar it rejects…): fall back below, and say why.
                raw = null
                modelError = describe(e)
            }
        }
        val usedModel = raw != null
        val found = raw ?: HeuristicStatementParser.parse(compacted, header, fallbackYear).map { it to text }

        val transactions = found.mapNotNull { (t, page) -> settle(t, page, header, fallbackYear, verify = usedModel) }
        return ExtractionResult(header, transactions, reconcile(transactions, header), usedModel, modelError)
    }

    private suspend fun readHeader(llm: LocalLlm, firstPage: String): StatementHeader =
        ExtractionJson.header(
            llm.complete(StatementPrompt.headerSystem, StatementPrompt.headerMessage(firstPage), StatementGrammar.header(), MAX_HEADER_TOKENS),
        )

    companion object {
        /** A failure as one line the user can read or report: its type and message. */
        fun describe(e: Throwable): String =
            listOfNotNull(e::class.simpleName, e.message?.lineSequence()?.firstOrNull()?.take(200)).joinToString(": ")

        /** Room for one line's answer: its keys, a date where one is asked for, and slack. */
        private const val TOKENS_PER_LINE = 40
        private const val MAX_HEADER_TOKENS = 120

        /**
         * What the text says outright wins over what the model read. Printed totals only come from
         * the model, so each must actually appear in the statement to be believed.
         */
        fun mergeHeader(detected: StatementHeader, read: StatementHeader, text: String): StatementHeader =
            StatementHeader(
                period = detected.period ?: read.period,
                currency = detected.currency ?: read.currency,
                declaredDebits = read.declaredDebits?.takeIf { it > 0 && StatementText.containsAmount(text, it) },
                declaredCredits = read.declaredCredits?.takeIf { it > 0 && StatementText.containsAmount(text, it) },
            )

        /**
         * Gives [raw] its full date and checks it. With [verify], a line whose amount isn't in its
         * page, or whose date falls well outside the statement's period, is kept but marked
         * unverified; an impossible date drops the line.
         */
        fun settle(raw: RawTransaction, page: String, header: StatementHeader, fallbackYear: Int, verify: Boolean): StatementTransaction? {
            val year = raw.year ?: StatementText.resolveYear(raw.month, header.period, fallbackYear)
            val date = SimpleDate(year, raw.month, raw.day).takeIf { it.isValid() } ?: return null
            return StatementTransaction(
                date = date,
                description = raw.description.trim(),
                amount = raw.amount,
                direction = raw.direction,
                kind = raw.kind,
                category = raw.category,
                doubt = when {
                    raw.disputed -> Doubt.READERS_DISAGREE
                    !verify -> null
                    !StatementText.containsAmount(page, raw.amount) -> Doubt.AMOUNT_NOT_FOUND
                    !withinPeriod(date, header.period) -> Doubt.DATE_OUTSIDE_PERIOD
                    else -> null
                },
            )
        }

        /**
         * The model and the fixed rules each say whether [line] is a transaction; a line only one of
         * them believes in is kept but [RawTransaction.disputed], so it is shown unticked — which is
         * how a layout the rules don't know still surfaces what the model found, and a model's
         * misreading costs a glance rather than a wrong entry. When both agree, the direction the
         * page states outright (a sign, a section) beats the model's, and the kind is re-guessed if
         * it contradicts the direction; the category is the model's.
         */
        fun vote(line: StatementLine, model: RawTransaction?): RawTransaction? {
            val rule = HeuristicStatementParser.decide(line)
            return when {
                model == null && rule == null -> null
                // A line that says total/saldo/balance, or heads a day, is not a purchase however
                // sure the model is — small models love a daily total.
                rule == null && (line.isSummary || line.isHeading) -> null
                model == null -> rule!!.copy(disputed = true)
                rule == null -> model.copy(disputed = true)
                else -> {
                    val direction = HeuristicStatementParser.directionOf(line) ?: model.direction
                    val incoming = model.kind == TxKind.INCOME || model.kind == TxKind.REFUND
                    val outgoing = model.kind == TxKind.PURCHASE || model.kind == TxKind.FEE
                    val consistent = (direction == Direction.CREDIT && !outgoing) || (direction == Direction.DEBIT && !incoming)
                    model.copy(
                        direction = direction,
                        kind = if (consistent) model.kind else HeuristicStatementParser.guessKind(model.description, direction),
                    )
                }
            }
        }

        /** Inside the period, give or take a month (a card bill lists purchases from before its cycle). */
        private fun withinPeriod(date: SimpleDate, period: StatementPeriod?): Boolean {
            if (period == null) return true
            fun index(d: SimpleDate) = d.year * 12 + d.month
            return index(date) in index(period.start) - 1..index(period.end) + 1
        }

        /** Totals of the lines that start ticked-worthy (no [Doubt]), set against the printed ones. */
        fun reconcile(transactions: List<StatementTransaction>, header: StatementHeader): Reconciliation {
            fun sum(direction: Direction) = kotlin.math.round(
                transactions.filter { it.direction == direction && it.verified }.sumOf { it.amount } * 100,
            ) / 100
            return Reconciliation(
                debits = sum(Direction.DEBIT),
                credits = sum(Direction.CREDIT),
                declaredDebits = header.declaredDebits,
                declaredCredits = header.declaredCredits,
            )
        }
    }
}
