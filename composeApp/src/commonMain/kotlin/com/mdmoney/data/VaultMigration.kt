package com.mdmoney.data

/** What a run actually changed, so it can report rather than just claim success. */
data class MigrationReport(
    val notesScanned: Int = 0,
    val notesRewritten: List<String> = emptyList(),
    val categoriesCreated: List<String> = emptyList(),
    val accountsTitled: List<String> = emptyList(),
) {
    val changedNothing: Boolean
        get() = notesRewritten.isEmpty() && categoriesCreated.isEmpty() && accountsTitled.isEmpty()
}

/**
 * Brings an existing vault up to the current note format: a metadata note for every category, and
 * `category:`/`account:` on every expense and ledger written as links into those notes.
 *
 * It deliberately owns no formatting of its own — it reads each note with the app's reader and
 * writes it straight back with the app's writer, so there is exactly one definition of the format
 * and a migrated note is by construction a note the app would have written itself.
 *
 * Two properties make it safe to point at a real vault:
 *
 * - **Idempotent.** A link that already resolves to the right note is left as written, so a second
 *   run rewrites nothing and a title tuned in Obsidian is never stamped over.
 * - **Lossless.** Every writer here preserves unknown frontmatter, the body, and untouched amounts,
 *   which is the same guarantee that lets the app share the vault with Obsidian at all.
 */
class VaultMigration(
    private val repo: VaultRepository,
    private val fallbackYear: Int,
) {
    private val storage: VaultStorage get() = repo.storage

    suspend fun run(): MigrationReport {
        val accounts = repo.accounts()
        val scanned = accounts.associateWith { account ->
            storage.listNotes(account).map { fileName -> Scanned(fileName, storage.read(account, fileName)) }
        }

        // 1. Every category referenced anywhere gets a note, so no link the rewrite writes dangles.
        val slugs = scanned.values.flatten().mapNotNull { it.categorySlug(fallbackYear) }.distinct().sorted()
        val created = repo.ensureCategories(slugs).map { it.slug }

        // 2. An account's display title: what its notes already call it. `conta: Nubank` is the name
        //    the user chose for a folder named `nubank`, and it would otherwise be the one thing the
        //    move to `account:` links threw away.
        val titled = mutableListOf<String>()
        val accountTitles = mutableMapOf<String, String>()
        for (account in accounts) {
            val meta = repo.accountMeta(account)
            val existing = meta.title
            val fromNotes = scanned[account].orEmpty()
                .mapNotNull { it.conta }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
            if (existing == account && fromNotes != null && fromNotes != account) {
                repo.setAccountTitle(account, fromNotes)
                accountTitles[account] = fromNotes
                titled.add(account)
            } else {
                accountTitles[account] = existing
            }
        }

        // 3. Rewrite, now that every title a link needs is known.
        val links = VaultLinks.of(repo.categories(), accountTitles)
        val rewritten = mutableListOf<String>()
        for ((account, notes) in scanned) {
            for (note in notes) {
                val updated = note.rewrite(account, fallbackYear, links) ?: continue
                if (updated == note.content) continue
                storage.write(account, note.fileName, updated)
                rewritten.add("$account/${note.fileName}")
            }
        }

        return MigrationReport(
            notesScanned = scanned.values.sumOf { it.size },
            notesRewritten = rewritten,
            categoriesCreated = created,
            accountsTitled = titled,
        )
    }

    /** One note read once: scanning and rewriting both need its content, and files are the slow part. */
    private class Scanned(val fileName: String, val content: String) {
        private val id: String get() = fileName.removeSuffix(".md")

        /** The legacy plain-text account name, kept only to seed the account's display title. */
        val conta: String? get() = FrontmatterParser.parse(content).scalar("conta")

        fun categorySlug(fallbackYear: Int): String? =
            if (LedgerMapper.isLedger(content)) {
                LedgerMapper.read(id, "", content, fallbackYear)?.category
            } else {
                ExpenseMapper.read(id, "", content, fallbackYear).category
            }

        /**
         * The note as the app would write it. Reading and writing the *same* model means nothing is
         * being changed here except what the writers themselves normalise — chiefly the links.
         */
        fun rewrite(account: String, fallbackYear: Int, links: VaultLinks): String? =
            if (LedgerMapper.isLedger(content)) {
                LedgerMapper.read(id, account, content, fallbackYear)?.let { LedgerMapper.write(content, it, links) }
            } else {
                val expense = ExpenseMapper.read(id, account, content, fallbackYear)
                ExpenseMapper.write(content, expense, expense, links)
            }
    }
}
