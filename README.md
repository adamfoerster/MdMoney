# MdMoney

A personal-finance app whose **database is plain Markdown** inside an Obsidian vault. Built with
**Kotlin Multiplatform + Compose Multiplatform** for **Android, iOS, and desktop (JVM)**, structured
to add a **wasmJs** web target later. Dependencies are kept to a minimum (no DI framework, no
database, no networking, no serialization library).

## Concept

An **account** is a subfolder of your vault. Each expense line item is one Markdown note whose YAML
frontmatter holds twelve monthly amounts plus a paid flag, matching the format already used in the
`nubank/` sample folder:

```yaml
---
title: Educbank - Escola Helen
category: educação
conta: Nubank
year: 2026
type: recurring-variable      # eventual | recurring-fixed | recurring-variable
period: Mensal
subStatus: Active
jan: 1232.5
jan-paid: true
feb: 1232.5
feb-paid: true
# … mar … dec, each with a *-paid flag
---
```

Four kinds of line item, all expressed over the same twelve monthly slots:

- **eventual** — a one-off amount in a single month (a legacy shape; new one-offs go to a ledger, see
  below).
- **recurring-fixed** — the same amount every month (auto-filled).
- **recurring-variable** — recurs, but you enter each month's amount as the bill arrives.
- **income** — money coming *in* (salary, bonus) rather than out. Amounts are entered per month like
  a variable bill, and a month's `*-paid` flag reads as **received**. Income carries no recurrence of
  its own, and a note without a `type:` is always read as an expense — so existing vaults are
  unaffected.

Reading is tolerant of the legacy Portuguese `fev` key; writing standardizes to `feb`. Unknown
frontmatter keys (e.g. `renewal_date`) and the note body are preserved on save.

### One-off spending: ledger notes

A coffee and a parking ticket shouldn't each become a file. One-offs are grouped into **one note per
account / month / group**, named `<year> <Mon> - <group>.md`, holding a table of purchases whose
`total` is their sum:

```yaml
---
title: Alimentação
category: food
conta: nubank
year: 2026
month: July
total: 26.78
---

| Date     | Note         | Amount |
| -------- | ------------ | ------ |
| 20260715 | Starbucks    | 10.23  |
| 20260715 | Seven Eleven | 5.32   |
| 20260714 | Starbucks    | 11.23  |
```

A note is read as a ledger when it has a `month:` key (a plain expense note never does), so existing
vaults keep working. Its `total` is always recomputed from the rows, and it shows up like any other
line item — a row carrying that total in its month, **already paid**, since you record a purchase
after making it. Prose and unknown keys around the table are preserved. The Annual grid and Home open
the ledger rather than a value editor, because the amount is derived from the table.

Each account also has an optional **metadata note at the vault root** (`<vault>/<Account>.md`) whose
frontmatter records the opening balance per year:

```yaml
type: account
account: Regions
initial-2026: 1200
```

The Home screen shows the account **balance = opening balance + income received − everything paid**
for the year (a checkbook: only settled months move the figure), and can set the opening balance in
place.

## Navigation & performance

Opening an account lands on a **bottom-tab shell**: **Home** (balance + this month's lines to check
off / edit, grouped into **income**, **recurring**, and **one-off** sections each carrying its own
month total, plus add-one-off-expense and add-income actions), **Annual** (the full yearly
grid, with income and expenses totalled in separate sections and reconciled by a net row),
**Reports** (placeholder), and **Settings**.

Reads are served from a small **SQLite cache** (`androidx.sqlite:sqlite-bundled`, hand-written SQL in
`data/CacheDb.kt`) so screens paint instantly and aggregations run as `SUM(...)` queries. Markdown
files remain the source of truth: an account is loaded from cache first, then re-synced from disk in
the background, and every edit is written to the cache immediately and flushed to its `.md` file
asynchronously.

## Project layout

```
composeApp/
  src/commonMain/    domain models, markdown parser/mapper, SQLite cache, repository, Compose UI, i18n (pt/en/es)
  src/jvmMain/        desktop entry point + java.io.File storage (Swing folder picker)
  src/androidMain/    Android host + SAF (DocumentFile) storage
  src/iosMain/        iOS entry point + UIDocumentPicker + security-scoped-bookmark storage
  src/commonTest/     frontmatter round-trip tests
  src/jvmTest/        end-to-end test over the real nubank/ files
iosApp/               SwiftUI host consuming the ComposeApp framework
scripts/import_regions.py   one-time xlsx → Markdown importer for the Regions account
nubank/               sample account data (existing Obsidian vault folder)
```

The storage layer is the only platform-specific code, behind the `VaultStorage` interface.

## Running

- **Desktop:** `./gradlew :composeApp:run` — pick your vault folder on first launch.
- **Android:** open in Android Studio and run the `composeApp` configuration, or
  `./gradlew :composeApp:installDebug`.
- **iOS:** open `iosApp/iosApp.xcodeproj` in Xcode and run (the build embeds the Kotlin framework).
- **Tests:** `./gradlew :composeApp:jvmTest`

## Importing the Regions spreadsheet

One-time conversion of `PalmBayHouse.xlsx` (sheets per year) into Markdown notes:

```
python3 scripts/import_regions.py /path/to/PalmBayHouse.xlsx /path/to/your/vault
```

It writes `<vault>/Regions/<title> - <year>.md` in the schema above and never overwrites existing
files. Requires `openpyxl` (`pip3 install --user openpyxl`).

## Importing a bank statement

Turns a statement (`Date | Description | Category | Valor`) into **ledger notes** — one per month and
category, matching what the app writes:

```
python3 scripts/import_statement.py statement.xlsx /path/to/vault Regions [--dry-run]
```

The statement's `Category` becomes the group (the note's title and file name); `CATEGORY_SLUG` in the
script maps it to the frontmatter `category`. Rows already present are skipped, so re-running merges
rather than duplicating. Dates are tolerated as date cells or `m/d/yyyy` text, and negative amounts
are stored positive.

## Design

The UI follows the **Reino Eterno** design system — a warm archival/editorial look: parchment
background, warm ink, a single brass accent; Spectral (serif titles), Hanken Grotesk (sans body),
IBM Plex Mono (uppercase tracked labels and numbers); square corners and hairline rules instead of
shadows. It is light-only by design. The three typefaces are bundled under
`composeApp/src/commonMain/composeResources/font/` (all OFL-licensed via Google Fonts). Tokens live
in `ui/theme/` (`Colors.kt`, `Type.kt`) and the reusable motifs (eyebrow, section band, index row,
buttons) in `ui/components/ReinoComponents.kt`.

## Language & number format

The UI ships in Portuguese, English, and Spanish. It defaults to the system language (falling back to
English) and can be changed in Settings.

Displayed amounts are always shown with two decimals; Settings lets you pick the **cents separator**
(dot `1234.50` or comma `1234,50`), defaulting to the dot. This is display-only — the markdown
frontmatter always stores amounts with a dot and no trailing zeros, so files stay portable.
