# Release notes

All notable changes to MdMoney live here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the version scheme is
[Semantic Versioning](https://semver.org/). Because the vault is the user's own Markdown, treat
**anything that changes the on-disk format as breaking** — a note this app rewrites must stay
readable by the Obsidian vault it came from.

The version in `gradle.properties` is the single source of truth; the top entry here must match it
(`VersionTest` fails the build otherwise). Put day-to-day work under **Unreleased** and rename that
heading when you cut a release.

## [Unreleased]

## [0.1.0] — 2026-07-15

First tagged version: the app as it stands after the initial build-out.

### Added

- **Markdown-as-database.** An account is a vault subfolder; each line item is one note whose
  frontmatter carries twelve monthly amounts plus `*-paid` flags. Unknown keys and the note body
  survive a round-trip, so Obsidian-side edits are never clobbered.
- **Four kinds of line item** over the same twelve slots: `eventual`, `recurring-fixed`,
  `recurring-variable`, and `income` (money in, whose `*-paid` reads as *received*). A note with no
  `type:` is read as an expense, so existing vaults are unaffected.
- **Ledger notes for one-off spending** — one note per account/month/group
  (`2026 Jul - Alimentação.md`) holding a `Date | Note | Amount` table whose `total` is recomputed
  from the rows, so a month of coffees is one file instead of thirty.
- **Home tab**: balance (opening balance + received − paid) and the month's lines grouped into
  income, recurring, and one-off sections, each with its own month total; check off bills or edit a
  variable amount in place.
- **Annual tab**: the yearly grid, with income and expenses totalled in separate sections and
  reconciled by a net row.
- **SQLite read cache** (`androidx.sqlite:sqlite-bundled`) so screens paint instantly and
  aggregations run as `SUM(...)`; Markdown stays authoritative and is written back asynchronously.
- **Reino Eterno design system** — parchment/ink/brass, bundled Spectral / Hanken Grotesk / IBM Plex
  Mono, square corners and hairline rules. Light-only by design.
- **Portuguese, English, and Spanish**, defaulting to the system language.
- **Cents separator** setting (dot or comma), defaulting to the dot. Display-only: files always store
  a dot, so the vault stays portable.
- Android, iOS, and desktop (JVM) targets over one `VaultStorage` interface — SAF on Android,
  security-scoped bookmarks on iOS, `java.io.File` on desktop.
- Importers: `scripts/import_regions.py` (spreadsheet → notes) and `scripts/import_statement.py`
  (bank statement → ledger notes, idempotent on re-run).


### Fixed

- **Accounts could paint empty.** The cache keyed notes on the `conta:` frontmatter while the app
  looks accounts up by folder name, so a folder `nubank` holding `conta: Nubank` hid every note in
  it. The folder is now the account's identity, and an existing `conta:` is preserved verbatim
  rather than restamped.
