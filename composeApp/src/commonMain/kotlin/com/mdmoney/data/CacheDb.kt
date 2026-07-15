package com.mdmoney.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.mdmoney.domain.Expense
import com.mdmoney.domain.ExpenseType
import com.mdmoney.domain.Month
import com.mdmoney.platform.ioDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A read/index cache over the markdown vault, backed by a single bundled-SQLite connection.
 *
 * Markdown files remain the source of truth; this table is populated by syncing the vault and is
 * served to the UI for instant reads and cheap aggregation (`SUM(...)`). Every call is confined to
 * [ioDispatcher] and serialized by a [Mutex] so the one connection is never touched concurrently.
 *
 * Each row is one expense keyed by `(account, id)`, with the twelve monthly amounts flattened into
 * `m1..m12` (nullable) and their paid flags into `p1..p12` (0/1).
 */
class CacheDb(private val path: String) {

    private val mutex = Mutex()
    private var connection: SQLiteConnection? = null

    private fun conn(): SQLiteConnection {
        connection?.let { return it }
        val c = BundledSQLiteDriver().open(path)
        // Every row here is derived from the vault, so a schema change just rebuilds from disk
        // rather than migrating: drop the table and let the next sync repopulate it.
        val version = c.prepare("PRAGMA user_version").useStmt { if (it.step()) it.getLong(0) else 0L }
        if (version != SCHEMA_VERSION) {
            c.execSQL("DROP TABLE IF EXISTS expense")
            c.execSQL("PRAGMA user_version = $SCHEMA_VERSION")
        }
        c.execSQL(CREATE_SQL)
        connection = c
        return c
    }

    private suspend fun <T> withConn(block: (SQLiteConnection) -> T): T =
        withContext(ioDispatcher) { mutex.withLock { block(conn()) } }

    suspend fun upsertAll(expenses: List<Expense>) = withConn { c ->
        c.prepare(UPSERT_SQL).useStmt { st ->
            for (e in expenses) {
                bindExpense(st, e)
                st.step()
                st.reset()
                st.clearBindings()
            }
        }
    }

    /** Removes rows for [account] whose id is not in [keepIds] (notes deleted on disk). */
    suspend fun deleteMissing(account: String, keepIds: Set<String>) = withConn { c ->
        val ids = mutableListOf<String>()
        c.prepare("SELECT id FROM expense WHERE account = ?").useStmt { st ->
            st.bindText(1, account)
            while (st.step()) ids.add(st.getText(0))
        }
        val stale = ids.filter { it !in keepIds }
        if (stale.isNotEmpty()) {
            c.prepare("DELETE FROM expense WHERE account = ? AND id = ?").useStmt { st ->
                for (id in stale) {
                    st.bindText(1, account); st.bindText(2, id)
                    st.step(); st.reset(); st.clearBindings()
                }
            }
        }
    }

    suspend fun expenses(account: String): List<Expense> = withConn { c ->
        val out = mutableListOf<Expense>()
        c.prepare("$SELECT_COLS WHERE account = ? ORDER BY LOWER(title)").useStmt { st ->
            st.bindText(1, account)
            while (st.step()) out.add(readExpense(st))
        }
        out
    }

    suspend fun ids(account: String): List<String> = withConn { c ->
        val out = mutableListOf<String>()
        c.prepare("SELECT id FROM expense WHERE account = ?").useStmt { st ->
            st.bindText(1, account)
            while (st.step()) out.add(st.getText(0))
        }
        out
    }

    /** Sum of expense amounts marked paid in [account]/[year] — money actually spent. */
    suspend fun paidTotal(account: String, year: Int): Double = flaggedTotal(account, year, income = false)

    /** Sum of income amounts marked received in [account]/[year] — money actually in. */
    suspend fun receivedTotal(account: String, year: Int): Double = flaggedTotal(account, year, income = true)

    /**
     * Sum of the amounts whose month is flagged (paid/received) for the income or expense side of
     * [account]/[year]. The checkbook model: only flagged months have moved money.
     */
    private suspend fun flaggedTotal(account: String, year: Int, income: Boolean): Double = withConn { c ->
        val expr = (1..12).joinToString(" + ") { "CASE WHEN p$it = 1 THEN COALESCE(m$it, 0) ELSE 0 END" }
        val side = if (income) "type = ?" else "type != ?"
        c.prepare("SELECT COALESCE(SUM($expr), 0) FROM expense WHERE account = ? AND year = ? AND $side").useStmt { st ->
            st.bindText(1, account); st.bindLong(2, year.toLong()); st.bindText(3, ExpenseType.INCOME.id)
            if (st.step()) st.getDouble(0) else 0.0
        }
    }

    /** Column totals per month for [account]/[year], indexed by [Month]. */
    suspend fun monthTotals(account: String, year: Int): Map<Month, Double> = withConn { c ->
        val cols = (1..12).joinToString(", ") { "COALESCE(SUM(m$it), 0)" }
        c.prepare("SELECT $cols FROM expense WHERE account = ? AND year = ?").useStmt { st ->
            st.bindText(1, account); st.bindLong(2, year.toLong())
            if (st.step()) Month.ALL.associateWith { st.getDouble(it.number - 1) } else emptyMap()
        }
    }

    // --- row mapping ---

    private fun bindExpense(st: SQLiteStatement, e: Expense) {
        st.bindText(1, e.account)
        st.bindText(2, e.id)
        st.bindLong(3, e.year.toLong())
        st.bindText(4, e.title)
        e.category?.let { st.bindText(5, it) } ?: st.bindNull(5)
        st.bindText(6, e.type.id)
        e.period?.let { st.bindText(7, it) } ?: st.bindNull(7)
        e.subStatus?.let { st.bindText(8, it) } ?: st.bindNull(8)
        e.projected?.let { st.bindDouble(9, it) } ?: st.bindNull(9)
        st.bindText(10, e.tags.joinToString(","))
        Month.ALL.forEach { m ->
            val amount = e.amounts[m]
            if (amount == null) st.bindNull(10 + m.number) else st.bindDouble(10 + m.number, amount)
            st.bindLong(22 + m.number, if (e.paid[m] == true) 1L else 0L)
        }
        st.bindLong(35, if (e.ledger) 1L else 0L)
    }

    private fun readExpense(st: SQLiteStatement): Expense {
        val account = st.getText(0)
        val amounts = Month.ALL.associateWith { m ->
            val col = 9 + m.number // m1 starts at column index 10
            if (st.isNull(col)) null else st.getDouble(col)
        }
        val paid = Month.ALL.associateWith { m -> st.getLong(21 + m.number) == 1L }
        val tags = st.getText(9).split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return Expense(
            id = st.getText(1),
            account = account,
            title = st.getText(3),
            category = if (st.isNull(4)) null else st.getText(4),
            year = st.getLong(2).toInt(),
            type = ExpenseType.fromId(st.getText(5)) ?: ExpenseType.RECURRING_VARIABLE,
            period = if (st.isNull(6)) null else st.getText(6),
            subStatus = if (st.isNull(7)) null else st.getText(7),
            projected = if (st.isNull(8)) null else st.getDouble(8),
            tags = tags,
            amounts = amounts,
            paid = paid,
            ledger = st.getLong(34) == 1L,
        )
    }

    private inline fun <R> SQLiteStatement.useStmt(block: (SQLiteStatement) -> R): R =
        try { block(this) } finally { close() }

    private companion object {
        /** Bump whenever the table shape changes; the cache is dropped and rebuilt from the vault. */
        const val SCHEMA_VERSION = 2L

        val CREATE_SQL = buildString {
            append("CREATE TABLE IF NOT EXISTS expense (")
            append("account TEXT NOT NULL, id TEXT NOT NULL, year INTEGER NOT NULL, ")
            append("title TEXT NOT NULL, category TEXT, type TEXT NOT NULL, period TEXT, ")
            append("sub_status TEXT, projected REAL, tags TEXT NOT NULL, ")
            append((1..12).joinToString(", ") { "m$it REAL" })
            append(", ")
            append((1..12).joinToString(", ") { "p$it INTEGER NOT NULL DEFAULT 0" })
            append(", ledger INTEGER NOT NULL DEFAULT 0")
            append(", PRIMARY KEY(account, id))")
        }

        // Column order: account,id,year,title,category,type,period,sub_status,projected,tags,
        //               m1..m12, p1..p12, ledger
        private val ALL_COLS =
            "account, id, year, title, category, type, period, sub_status, projected, tags, " +
                (1..12).joinToString(", ") { "m$it" } + ", " +
                (1..12).joinToString(", ") { "p$it" } + ", ledger"

        val SELECT_COLS = "SELECT $ALL_COLS FROM expense"

        val UPSERT_SQL: String = run {
            val placeholders = (1..35).joinToString(", ") { "?" }
            "INSERT OR REPLACE INTO expense ($ALL_COLS) VALUES ($placeholders)"
        }
    }
}
