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

## [0.5.0] — 2026-09-25

### Added

- **MdMoney has its own icon on every platform** — a brass coin on a balance scale, in a light and a
  dark version. Android shows it as an adaptive icon that fits any launcher shape, turns dark in
  night mode and follows the wallpaper under Android 13+ themed icons; iOS uses the dark version for
  its dark home screen; the desktop window, the taskbar/Dock and the Windows, macOS and Linux
  installers all carry it. Before this, Android and iOS showed the system's placeholder and desktop a
  Java cup. The artwork lives in `icon/*.svg`, and `java scripts/GenerateIcons.java` rebuilds every
  platform's icon from it.

### Changed

- **The app is built with JDK 21.** Kotlin's `jvmTarget` and Android's `compileOptions` moved from 17
  to 21, and a Gradle `jvmToolchain(21)` now pins the JDK that compiles desktop, Android and the
  tests — so the build no longer depends on whichever JDK happens to launch Gradle. Nothing about the
  app or the vault format changes; Android still targets API 35 with a minimum of 26.
- **Hot reload runs again on a machine without a JetBrains Runtime.** `:composeApp:hotRunJvm` needs a
  JBR (class redefinition is a JBR feature) and asks for version 21; with only a plain JDK around it
  stopped at "Failed to find suitable JetBrains Runtime 21 installation". Its fallback, Gradle's own
  toolchain provisioning, can't fill that gap either — this build declares no toolchain download
  repository — so `compose.reload.jbr.autoProvisioningEnabled` now lets the plugin fetch the JBR
  itself, once, into Gradle's cache. Compilation is unaffected: it still runs on the toolchain JDK.

- **The bank-statement importer writes the current note format.** `scripts/import_statement.py` was
  still writing the plain-text `conta:`/`category:` of before links existed; it now writes
  `account: "[[nubank|Nubank]]"` and `category: "[[food|Alimentação]]"` like the app, and creates the
  `categories/` note for any category that doesn't have one yet, so no link it writes dangles. A
  re-import also stopped rebuilding the note from scratch: unknown frontmatter keys, prose around the
  table, and a link title tuned in Obsidian now survive it, as they do everywhere else. Nothing about
  the app itself changed, and notes imported by the old version keep reading exactly as they did.

## [0.4.1] — 2026-08-24

### Fixed

- **An account whose notes picked up a `month:` key shows all twelve months again.** A template
  applied over a folder in Obsidian can stamp `month:` (and a checklist body) onto every note it
  touches — and `month:` was the single thing that told a one-off group apart from a yearly bill. So
  each stamped bill was read as that month's group of purchases, its total taken from a table that
  isn't there: the whole year collapsed into one R$ 0,00 line in the stamped month, and every other
  month of the account came up empty. A note that carries monthly amounts (`jan:` … `dec:`, or their
  paid flags) is now read as the bill it is, whatever else its frontmatter says; a real group keeps
  its money in its table and is unaffected. Nothing is rewritten — the stray key stays in the file,
  it just no longer decides what the note is.

### Added

- **Each account has a currency.** Pick one when creating an account, or later from the **Edit account**
  button beside the balance on the account's home page — Real (R$), Euro (€), Dollar ($), or a custom
  symbol you type. The symbol is then prefixed onto every figure the account shows: balances, month
  cells, ledgers, reports, and the amount fields you type into. It lives in the account's root note as
  a `currency:` key (a preset stored as its code, a custom one as the raw symbol); an account without
  the key shows plain numbers exactly as before, so older vaults read unchanged.
- **Rename an account.** The same **Edit account** dialog sets the account's display title, which now
  heads its home page. It's stored as `title:` in the root note; the folder name stays the account's
  identity, so renaming the title never strands a file.
- **Edit a purchase inside a group.** Tapping any row of a group's ledger now opens it for editing —
  its date, note and amount — or removal, instead of only offering the strike-through. The note's
  table and total are rewritten to match, and the other rows are left exactly as they were.

### Fixed

- **You can add a purchase to a group again.** The "add entry" button on a group's ledger opened the
  one-off sheet without closing the ledger, so the two modal sheets stacked and the ledger — drawn
  last — covered the sheet completely; the button looked dead. Opening the sheet now dismisses the
  ledger, defaults the purchase to the ledger's own month rather than the month Home happens to show,
  and saving returns you to the group with the new row already in it.
- **The one-off sheet's Save button is reachable.** The sheet's fields, category chips and button
  added up to more than a phone screen's height, and with nothing to scroll the Save button was
  simply clipped off the bottom — there appeared to be no way to confirm the purchase. The sheet now
  scrolls, like the ledger it came from.
- **The Settings screen scrolls.** Its sections ran past a short screen with nothing to scroll, so
  the vault section and version were clipped off the bottom. It scrolls now.

## [0.3.1] — 2026-07-15

### Fixed

- **Home's lines now hold a column.** Every figure sits in one fixed, right-aligned money column, so
  a typed amount and a printed one land on the same edge instead of drifting apart, and each block's
  subtotal sits directly over its own figures. A month with no value reads `—` rather than `·`.
- **Titles no longer shift with the kind of line.** Material's checkbox quietly drops its touch
  target when it has no callback, so read-only rows — ledgers, whose money is already spent — were
  indenting several pixels less than the rest of the list. The brand's own checkbox is a square
  hairline mark that fills brass with a ✓ (the design system's `TaskList`), and it occupies the same
  width whether or not it can be toggled.
- **Editable amounts are the design system's field**, not a Material box: no border, a dashed rule
  beneath, brass-tinted on hover, and on focus a `paper-deep` ground with the rule gone solid brass.
  The dash is meaning rather than decoration — the design system prints a legend for it ("campo
  editável") — so only a line you can actually type into carries one, and a fixed bill, a one-off or
  a ledger's sum reads as plain figures.

## [0.3.0] — 2026-07-15

Categories become real places in the vault instead of loose strings, and the Reports tab is built on
top of that.

### Added

- **A category is now a note of its own**, in the vault's new `categories/` folder, carrying a
  `title` and a `description`. Expenses and ledgers point at it with `category: "[[casa|Casa]]"`, so
  a category can be opened in Obsidian, described in prose, and shows the backlinks of everything
  filed under it. The folder is not an account, and the app doesn't list it as one.
- **`account:` on every expense and ledger**, likewise a link (`"[[nubank|Nubank]]"`) into the
  account's metadata note at the vault root. The account's identity is still its folder — the link
  only makes the relationship navigable.
- **The account note gained a `title`**, which is what the `account:` links display. Migration seeds
  it from what the notes already called the account, so a folder named `nubank` whose notes all said
  `conta: Nubank` still reads as "Nubank".
- **Reports is a real tab.** Every category of the year, heaviest first, with its share and its
  total; opening one shows its description, a chart of the twelve months, and every expense and
  ledger behind it — with a ledger row opening the purchases it sums. Money with no category is
  reported under "Sem categoria" rather than left out, because a report that hides money is worse
  than one with an unnamed row.
- **`./gradlew :composeApp:migrateVault -Pvault=<path> [-Papply]`** brings an existing vault up to
  this format. It previews by default, rewrites each note with the app's own writers rather than any
  formatting of its own, and is idempotent — a second run changes nothing.

### Changed

- **`category:` is read as a link, and a plain `category: casa` still means exactly what it always
  did** — a vault that is never migrated keeps working. The link's target is the category's identity;
  its title is read from the category's note, so renaming a category in Obsidian renames it
  everywhere without rewriting a single expense.
- **`conta:` is left exactly as written** and is no longer added to new notes, which carry `account:`
  instead. Nothing reads `conta:` any more; it stays because it is the user's text, and removing it
  is not something an app should do to a note it didn't write.
- The category field in the expense and one-off sheets now shows and accepts **titles**, resolving
  what you type onto an existing category rather than forking a second note meaning the same thing.

## [0.2.0] — 2026-07-15

Reviewed against the refreshed Reino Eterno design system, whose `Ledger` specimen gave the ledger a
shape to be held to.

### Changed

- **The ledger sheet is now laid out as the design system's `Ledger`**: a masthead closed by the
  heavy ink rule and counting its own entries (`03 LANÇAMENTOS`), mono column heads, and numbered
  rows — brass ordinal, serif description, mono meta, mono figure — separated by hairlines.
- **A ledger's total is carried by the brass band**, the system's signature motif, instead of a line
  of small brass text. Entry descriptions moved from sans to serif, the voice the system gives every
  row title.

### Fixed

- **Ledger entries showed their stored date** (`20260715`) rather than a readable one. They now read
  as mono meta in the app's language (`15 JUL`); the note's table keeps the sortable eight digits,
  since storage format is not display format. A date that isn't well-formed is shown as written
  rather than dropped.

### Fixed

- **Yearly bills vanished from Home.** A note typed `eventual` carries its amount in the one month
  it falls due, and Home listed a one-off only in a month it had a value in — so `Seguro residencial`
  (due in January) was invisible from February on, along with every other annual bill: Property Tax,
  the insurances, Ring, Jardinagem, HVAC Maintenance. What a month contains now turns on **ledger vs
  plain note**: a plain note is a plan for the year and shows every month, with or without a value
  there, while a ledger is the record of one month's purchases and still shows only in that month.
- Home's third section is now **Eventuais** ("One-off" / "Puntuales") rather than "Avulsos", because
  it holds planned yearly bills as well as already-spent purchases, and "avulso" means money already
  gone.

### Changed

- Home's month grouping moved out of the composable into `monthLines`, where it is covered by tests.

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
