# Working on MdMoney

A personal-finance app whose database is the user's own **Obsidian vault of Markdown notes**, built
with Kotlin Multiplatform + Compose for Android, iOS, and desktop. `README.md` explains the concept
and file formats; read it before changing anything that touches the vault.

## Always bump the version

Every user-facing change ships a version bump. `mdmoney.version` in `gradle.properties` is the
**single source of truth** — Android's `versionName`/`versionCode`, the desktop package, and the
generated `AppVersion` all derive from it.

Bump it by [SemVer](https://semver.org/), judged from the user's point of view:

- **PATCH** — a fix that changes no behaviour anyone asked for.
- **MINOR** — a new capability, a new setting, a screen that gains a section.
- **MAJOR** — anything that breaks an existing vault: a frontmatter key that changes meaning, a note
  the app can no longer read, a rename that strands existing files. Prefer staying compatible: notes
  are the user's data and are edited in Obsidian too. A missing `type:` must keep reading as an
  expense.

Two places do not derive automatically and must be updated in the same change:

1. **`CHANGELOG.md`** — write the release notes as part of the work, not afterwards. Day-to-day work
   goes under `## [Unreleased]`; cutting a release renames that heading to the new version and date.
2. **`iosApp/iosApp.xcodeproj/project.pbxproj`** — `MARKETING_VERSION` (both Debug and Release), since
   Xcode can't read `gradle.properties`.

`VersionTest` asserts all four agree, so forgetting fails the build rather than shipping a lie.

One platform constraint to know: **`jpackage` refuses a leading zero** ("the first number in an
app-version cannot be zero"), so while the version is `0.x.y` the desktop `packageDmg`/`packageMsi`
tasks fail by design. `./gradlew :composeApp:run` is unaffected, and 0.x means pre-release anyway —
so cut a desktop package only from `1.0.0` onward, rather than hardcoding a fake package version.

## Always test new features

A feature is not done until something fails when it breaks. Tests live in
`composeApp/src/commonTest` (pure logic) and `composeApp/src/jvmTest` (anything touching real files),
and run with:

```
./gradlew :composeApp:jvmTest
```

What earns a test here:

- **Every vault format change** — round-trip it (`write(read(x)) == x`), with unknown frontmatter
  keys and body prose present in the fixture. Preserving what this app doesn't understand is the
  core promise; `FrontmatterRoundTripTest` and `LedgerMapperTest` are the models to copy.
- **Every bug fix** — the regression test must fail before the fix. `cache_keys_on_folder_name_not_conta_frontmatter`
  exists because that bug made whole accounts silently invisible.
- **Anything computing money** — totals, balances, aggregation, formatting.

Guard against tests that can't fail: no `?: return` that turns a missing fixture into a pass, no
assertion that holds trivially. Prefer asserting a hand-computed number over recomputing the
implementation's own logic. When a test needs the repo's own files, take the root from the
`mdmoney.projectRoot` system property that the build supplies.

Compose UI has no test harness here. Verify screens by running the desktop app
(`./gradlew :composeApp:run`) — and keep the logic out of the composable where it can be tested.

## Non-negotiables

- **The Markdown is the source of truth**, SQLite is only a cache — it's dropped and rebuilt on a
  schema change (`SCHEMA_VERSION` in `data/CacheDb.kt`), so never store anything there that isn't
  derivable from the vault.
- **An account's identity is its folder name**, not the `conta:` frontmatter, which is preserved
  as-written and may differ in casing.
- **Storage format is not display format.** Files always store amounts with a dot and no trailing
  zeros (`formatAmount`); the cents-separator setting only affects display (`formatMoney` /
  `formatInput`). Never let a display preference reach a file.
- **Never touch the user's real vault when verifying.** Run against a copy in the scratch directory,
  and redirect the app's home (`-Duser.home=…`) so it can't pick up the real vault path or cache.
- **Every user-facing string is in `ui/i18n/`** in all three languages (pt/en/es) — there are no
  literals in screens.
