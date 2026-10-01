# BlueCard architecture

This is the high-level technical design for BlueCard. It explains how the app is
structured to meet [`PRD.md`](PRD.md). It is a map, not a detailed spec: it
records the technical decisions, the conventions new code must follow, and why.
How each part works is in the comments of the code that builds it; for a part
not built yet, such as export and import, its feature issue fills in the
details. Choices about how the app looks and behaves are in
[`PRD.md`](PRD.md#design-decisions).

Sources were checked on 2026-09-28. "Recommended" in this document means Android's
own documentation says so, and each such claim links to the page.

## Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Success criteria](#success-criteria)
- [Architecture approach](#architecture-approach)
- [UI layer](#ui-layer)
  - [Screen state](#screen-state)
  - [Load and save failures](#load-and-save-failures)
  - [Text fields](#text-fields)
  - [Navigation](#navigation)
  - [Double taps](#double-taps)
  - [Language and layout direction](#language-and-layout-direction)
  - [Theme](#theme)
- [Data layer](#data-layer)
  - [Repositories](#repositories)
  - [Writes](#writes)
  - [Storage errors](#storage-errors)
  - [Dependency injection](#dependency-injection)
- [Package layout](#package-layout)
- [Screens](#screens)
- [Merit badge catalog](#merit-badge-catalog)
  - [Content and links](#content-and-links)
  - [Trackers](#trackers)
  - [Requirement versions](#requirement-versions)
  - [Requirement IDs](#requirement-ids)
  - [Shipping and authoring](#shipping-and-authoring)
- [Data model](#data-model)
  - [Completion](#completion)
- [Key flows](#key-flows)
  - [First launch](#first-launch)
  - [Home summary](#home-summary)
  - [Browse and search](#browse-and-search)
  - [Recording progress](#recording-progress)
  - [PDF report](#pdf-report)
  - [Clearing data](#clearing-data)
  - [Export and import](#export-and-import)
  - [Backup](#backup)
- [Testing approach](#testing-approach)
  - [Fakes and contract tests](#fakes-and-contract-tests)
  - [ViewModel tests](#viewmodel-tests)
  - [Hilt in tests](#hilt-in-tests)
  - [Room and migration tests](#room-and-migration-tests)
  - [Compose UI and screenshot tests](#compose-ui-and-screenshot-tests)
  - [Catalog, report and backup tests](#catalog-report-and-backup-tests)
  - [Coverage](#coverage)
- [Decisions](#decisions)

## Overview

BlueCard is a single-activity Android app built with Jetpack Compose. A scout
enters their name and unit number once, browses and searches the merit badge
catalog, and records progress on each badge: counselor details, per-requirement
completion dates and notes, larger trackers such as exercise logs, or a
whole badge completed on a prior date. A finished badge can be turned into a PDF
report to save or share. All progress can be cleared, exported and imported.

## Requirements

What the architecture must provide, beyond the features listed in
[`PRD.md`](PRD.md). The rest of this document describes how the design meets
them.

1. **All data stays on the device.** There is no server, account or app sync.
   Android's own Auto Backup, which the scout can turn off, is the only
   automatic copy off the device (see req. 4). The only network use is opening
   official Scouting America pages in the browser, which the browser does, so
   the app itself needs no internet access.
2. **The merit badge catalog is our own data.** Scouting America's
   [terms of use](https://www.scouting.org/legal/terms-and-conditions/) forbid
   reusing or compiling their content without written permission, so the app
   ships its own short summaries of each requirement and links to the official
   page for the full text (see [Merit badge catalog](#merit-badge-catalog)).
3. **Recorded progress survives app updates.** Catalog changes, including new
   requirement versions, ship with app updates and never lose a scout's
   progress or attach it to the wrong requirements.
4. **Recorded progress survives a phone change**, through Android Auto Backup
   and the app's own export and import.
5. **No special permissions.** Saving, sharing, exporting and importing go
   through the system file picker and Sharesheet, so the app requests no
   storage or other runtime permissions.
6. **Runs on the project's `minSdk`** (currently API 26). Raising it is decided
   case by case, when a feature or API needs it.
7. **New catalog content needs no new code.** Adding a badge, a requirements
   version or a tracker is a change to the catalog file only.
8. **Testable with local tests and fakes.** Every class with logic can be
   tested on the JVM against fake dependencies, so the app can meet the testing
   rules in `CLAUDE.md` and the build's check of 80% line coverage for each
   class.
9. **Follows Android's architecture guidance**, meaning the items its
   recommendations page marks "strongly recommended" (see
   [Architecture approach](#architecture-approach)).

## Success criteria

The architecture is working when these outcomes hold. Each outcome lists the
checks that show it. Checks marked **(CI)** already run in `./gradlew build` on
every pull request; the others are added by the feature issue that builds that
part of the app.

| Outcome | Checks |
|---|---|
| Everything works with no network connection, and the app itself sends no data off the device; data leaves only when the scout shares or exports it, or through Android's system backup (req. 1) | The merged manifest declares no `INTERNET` permission. |
| The catalog contains only our own content (req. 2) | Catalog validation test requires an official page URL for every badge **(CI)**. Catalog changes are reviewed against the authoring rules in [`docs/catalog.md`](docs/catalog.md). No badge images or logos in the app's resources. |
| An app update never loses or mismatches recorded progress (req. 3) | Each database version's schema is committed in `app/schemas/`, and every schema change comes with a migration test. From the first release on, a catalog test checks that every badge ID and requirements version shipped before is still in the file. |
| A scout can move their records to a new phone (req. 4) | An export followed by an import restores the same profile and progress. Backup rules include the database and DataStore files **(CI)**. |
| The app never asks for a runtime permission (req. 5) | The merged manifest declares no dangerous permissions. |
| The app runs on every Android version from `minSdk` up, and any bump is a deliberate decision (req. 6) | Android lint, which flags APIs newer than `minSdk`, fails the build on warnings **(CI)**. |
| A new tracker needs only catalog data (req. 7) | Tests render and store trackers defined only in test catalog data (`TrackerEntryViewModelTest`, `MainActivityTest`) **(CI)**. |
| The code stays testable as it grows (req. 8) | Every class, except generated code, `@Preview` functions and `PdfDocumentWriter` (which only runs on a device), has at least 80% line coverage from local tests **(CI)**. Every ViewModel, use case, repository and mapper has a unit test, no test uses `@Ignore`, and no mocking library is used **(CI)**. Each repository fake passes the same contract tests as the real implementation. |
| The layers stay separate (req. 9) | Composables and ViewModels depend only on repository interfaces, never on Room, DataStore or file APIs. Checked in code review. |

## Architecture approach

The app follows Android's
[guide to app architecture](https://developer.android.com/topic/architecture) and
the items its [recommendations page](https://developer.android.com/topic/architecture/recommendations)
marks "strongly recommended":

- **Two layers: UI and data.** Composables and ViewModels never talk to a data
  source directly; they go through repositories, and each repository is the
  single source of truth for its data.
- **Unidirectional data flow.** State flows down from repositories to ViewModels
  to composables; user events flow up as ViewModel function calls.
- **Coroutines and flows** connect the layers. Repositories expose `Flow`s for
  data that changes and `suspend` functions for one-off work.
- **No domain layer for now.** The architecture guide calls it
  [optional](https://developer.android.com/topic/architecture/domain-layer), for
  logic that is complex or reused across ViewModels. Add use cases only when that
  happens. Small calculations that several screens share, such as a badge's
  status, are plain functions next to the data they read (see
  [Completion](#completion)) rather than use cases.
- **One Gradle module (`:app`).** Android's
  [modularization guide](https://developer.android.com/topic/modularization)
  says modularizing pays off mainly for reuse, strict visibility or large
  codebases, none of which apply yet. Code is organized by package instead (see
  [Package layout](#package-layout)).

```
UI layer        Compose screens  ──events──▶  ViewModels (uiState: StateFlow)
                       ▲                            │
                       └──────── state ─────────────┘
                                                    │ calls / collects
Data layer      Repositories: Profile · Catalog · Progress · Report · Backup
                       │            │           │          │         │
Data sources    DataStore     JSON asset      Room      PdfDocument  JSON files via
                (profile)     (catalog)    (progress)                system file picker
```

## UI layer

### Screen state

- **Screens are composables with a screen-level ViewModel.** Each ViewModel
  exposes one `uiState: StateFlow`, built with `stateIn(WhileSubscribed(5000))`
  when it comes from a flow, and the screen collects it with
  `collectAsStateWithLifecycle()`. This matches the
  [UI layer guide](https://developer.android.com/topic/architecture/ui-layer)
  and the recommendations page.
- **UI state is immutable** and models each state the screen can be in (for
  example loading, content, empty). ViewModels handle events by updating state,
  not by sending one-off events to the UI, as the recommendations page advises.
  A page that closes itself once its save succeeds does so from UI state too
  (see [Recording progress](#recording-progress)).

### Load and save failures

- **Load failures are a UI state.** When stored data can't be read, a repository
  throws an `IOException` (see [Storage errors](#storage-errors)). Each
  ViewModel that loads data turns it into a `LoadFailed` state with
  `catchLoadFailure` (`ui/LoadFailure.kt`), and the screen shows a message in
  place of its content. When the profile can't be read, the navigation root
  shows the message in place of the whole app. This follows "Show errors on the
  screen" in the UI layer guide, which keeps errors in UI state. The message has
  no "Try again" button, which limits when a screen loads again
  (`LoadFailedMessage`).
- **Save failures are a UI state too.** When something can't be saved, a
  repository throws an `IOException`. ViewModels that save progress launch each
  write with a `SaveRunner` (`ui/SaveFailure.kt`), which puts a `SaveFailure` in
  the screen's state, and the screen shows "Couldn't save. Try again." in a
  snackbar. That's the pattern in the UI layer guide's
  [Handle ViewModel events](https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events),
  which says ViewModel events "should always result in a UI state update". The
  screen keeps showing what's stored, so a change that failed visibly didn't
  happen. Onboarding predates this and shows its own message under its button.
- **Any other exception is a bug and still crashes the app.** The app has no
  crash reporting of its own, so a crash is the only way a bug reaches the
  developer without a scout reporting it: Google Play's
  [Android vitals](https://developer.android.com/topic/performance/vitals)
  reports crashes from users who allow it, but not caught exceptions. Revisit
  this if crash reporting is added
  ([#63](https://github.com/bryancassell/bluecard/issues/63)). Until then, each
  caught load or save failure is logged with `Log.w`, so logcat and bug reports
  show which data failed and why.

### Text fields

- **Text fields are state-based.** A text field edits a `TextFieldState` that
  its screen's ViewModel holds. The
  [text field guide](https://developer.android.com/develop/ui/compose/text/user-input)
  recommends state-based fields over `value` and `onValueChange`, which invite
  async updates, and encourages keeping `TextFieldState` in ViewModels.
- **The text survives the system stopping the app.** The ViewModel creates the
  state with `SavedStateHandle.textFieldState` (`ui/TextFieldSavedState.kt`),
  which keeps the text with a
  [saved state provider](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate#non-parcelable).
  Fields that start as stored text, such as the requirement notes and the
  counselor's fields, use `StoredTextFields` from the same file.
- **Every text field has a length limit** (`TextLengthLimit`), because saved
  state has a size limit. Each stored field's limit is a constant, with its
  reason, beside the data it limits (for example `NOTES_MAX_LENGTH` in
  `data/progress/Progress.kt`), because import holds a file to the same limits
  (see [Export and import](#export-and-import)). Single-line text fields also
  replace a pasted line break with a space (`LineBreaksAsSpaces`); number
  fields reject one.

### Navigation

- **Navigation uses [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)**,
  which the recommendations page names for single-activity apps with more than
  one screen. Each destination is a `@Serializable` key, and ViewModels are
  scoped to back stack entries with `lifecycle-viewmodel-navigation3`.
- **Home is the fixed start destination.** Until a profile is saved, the
  navigation root shows Onboarding in place of the back stack, because the
  [navigation principles](https://developer.android.com/guide/navigation/principles#fixed_start_destination)
  say one-time setup screens "should not be considered start destinations".
  The splash screen stays up until the saved profile loads, using
  [core-splashscreen](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate)'s
  `setKeepOnScreenCondition`, so the wrong screen never flashes first. The
  [splash screen guide](https://developer.android.com/develop/ui/views/launch/splash-screen)
  suggests holding the first frame for loading "a small amount of data, such as
  loading in-app settings from a local disk".
- **Screens' `SavedStateHandle`s don't start with the launching intent's
  extras.** `MainActivity` is exported, and `ComponentActivity` passes the
  intent's extras to every screen's ViewModel as default arguments, so any app
  could fill a screen's saved state. BlueCard uses neither intent extras nor
  default arguments, so `MainActivity` overrides
  `defaultViewModelCreationExtras` to leave them empty
  ([#83](https://github.com/bryancassell/bluecard/issues/83)). That covers
  every ViewModel created with the activity's creation extras, as Hilt and
  Navigation 3 create them. The activity's default factory still passes the
  extras to a ViewModel created without creation extras, so create none that
  way; overriding the factory instead would replace Hilt's.
- **Pages slide the full width of their area, side by side**
  (`ui/navigation/PageTransitions.kt`), as Navigation 3's
  [animation guide](https://developer.android.com/guide/navigation/navigation-3/animate-destinations)
  shows. A back swipe doesn't move the pages: the navigation root handles Back
  with its own `BackHandler`, added before the screens so that a handler a
  screen adds goes first (`BlueCardNavDisplay.kt`). Why the pages move this
  way, against Material 3's advice, is in
  [`PRD.md`](PRD.md#design-decisions).

### Double taps

The other app or the next screen takes a moment to cover the one tapped, so
both taps of a double tap can reach it.

- **Screens open other screens with `rememberNavigateFrom`**
  (`ui/navigation/`), which ignores a tap unless the tapping screen is on top,
  and doesn't reopen a screen still animating out (`DrawnScreens`). Screens
  also ignore touches while they animate out, and for the double-tap timeout as
  they animate in (`rememberIgnoreTouchesNavEntryDecorator`), so the second tap
  doesn't press anything on the new screen
  ([#61](https://github.com/bryancassell/bluecard/issues/61)).
- **Screens start other apps with one `OtherAppStarter`** from
  `rememberStartOtherApp` (`ui/`), shared among the screen's controls that open
  another app. After a tap, it ignores taps for the double-tap timeout, so a
  browser doesn't open two tabs, or an email app two drafts
  ([#97](https://github.com/bryancassell/bluecard/issues/97)).

### Language and layout direction

- **Text inside a string follows the strings' language, not the device's.**
  `strings_language` in `strings.xml` names the language of the strings the app
  shows, which differs from the device's when the app has no strings for it.
  `BlueCardApp` provides `LocalResources` in that language
  (`ProvideStringsLanguageResources`), so every `stringResource` and
  `pluralStringResource` follows it. Lists inside a string
  (`rememberBadgeNameListFormatter`), numbers and dates
  (`rememberCompletionDateFormatter`) are formatted in it too, so a sentence
  never mixes two languages: on a Persian phone, English strings read "Do 2 of
  3", not "Do ۲ of ۳". Code outside Compose, such as the PDF report, uses
  `stringsLanguageResources` and `completionDateFormatter` (`text/`).
  `StringsLanguageTagTest` checks that each `strings.xml` names its own
  language: a wrong tag, such as "en" left in a translation, would replace the
  whole translation with English.
- **Screens are laid out in the strings' language's direction, not the
  device's** ([#66](https://github.com/bryancassell/bluecard/issues/66)).
  `MainActivity` sets it in `attachBaseContext`, so on a Persian or Arabic
  phone the English strings are laid out left-to-right, as on an English phone;
  mirrored, they would read as broken English. The manifest keeps
  `android:supportsRtl="true"`, so a right-to-left translation would be laid
  out right-to-left with no other change.
- **Text the scout types keeps its own direction** (`ui/TypedText.kt`). Fields
  the scout types in use `typedTextFieldStyle()`. Typed text shown on screen,
  alone or inside one of the app's strings, goes through `typedText()`, which
  wraps it with
  [`BidiFormatter.unicodeWrap`](https://developer.android.com/training/basics/supporting-devices/languages#FormatText);
  code outside Compose uses `text.typedText(text, locale)`.

### Theme

- **Material 3** components, themed by `BlueCardTheme` with one light color
  scheme (`BlueCardColorScheme` in `ui/theme/Color.kt`), as
  [`PRD.md`](PRD.md#design-decisions)'s Colors row chooses.
- **Don't follow dark mode** until the app has a dark scheme
  ([#108](https://github.com/bryancassell/bluecard/issues/108)).
  `isSystemInDarkTheme()` still reports the system's dark mode, and `-night`
  resources still apply in it. Nothing but the window theme should use either:
  it would put dark-mode colors, images or bar icons on the light app.
- **`BlueCardColorSchemeTest` checks the PRD's contrast rule** for every text
  color on every surface.

## Data layer

### Repositories

| Repository | Owns | Data source |
|---|---|---|
| `ProfileRepository` | Scout name and unit number; first-launch setup is done once they are saved | [Preferences DataStore](https://developer.android.com/topic/libraries/architecture/datastore) |
| `CatalogRepository` | Merit badges, requirements, requirement versions (read-only) | JSON file in `assets/`, parsed with [kotlinx.serialization](https://kotlinlang.org/docs/serialization.html) |
| `ProgressRepository` | Everything the scout records | [Room](https://developer.android.com/training/data-storage/room) database |
| `ReportRepository` | Building a badge's PDF report | Framework [`PdfDocument`](https://developer.android.com/reference/android/graphics/pdf/PdfDocument) |
| `BackupRepository` | Export and import of all user data | JSON written to or read from a user-chosen file |

- **Storage choice:** the DataStore guide says it is "ideal for small datasets"
  and to "consider using Room" for larger or relational data. The profile is two
  values; recorded progress is relational (badges → requirements → tracker rows).
- **Room 2.8** (`androidx.room`), not Room 3. The Room guide now shows Room 3, a
  rewrite focused on Kotlin Multiplatform, which BlueCard doesn't need. Room 2.8
  is still maintained and is what Google's
  [Now in Android](https://github.com/android/nowinandroid) sample uses. Room
  and Hilt both generate code, using the KSP Gradle plugin.
- **Repositories are interfaces** with one production implementation each, so
  tests can substitute fakes.

### Writes

- **Writes outlive the screen.** `RoomProgressRepository` runs each write in an
  app-lifetime scope and waits for it (`runOutlivingCaller`), so leaving a
  screen cancels only the wait, not the write. Saving a PDF report, exporting
  and importing do too.
  That's the pattern in the data layer guide's
  [Make an operation live longer than the screen](https://developer.android.com/topic/architecture/data-layer#make_an_operation_live_longer_than_the_screen).
  A storage failure after the scout has left the screen goes unreported, but a
  bug still crashes the app.
- **Writes happen in the order they're made.** Each takes a first-come,
  first-served lock (`writing` in `RoomProgressRepository`), so a new write
  must go through it too: otherwise it could run ahead of writes already
  queued, such as a save of notes ahead of a clear.

### Storage errors

- **Stored data that can't be read or saved is reported as an `IOException`.**
  The
  [data layer guide](https://developer.android.com/topic/architecture/data-layer)
  ("Expose errors") says the data layer can expose errors "using custom
  exceptions". DataStore and the asset manager already throw `IOException`, and
  `RoomProgressRepository` wraps Room's `SQLiteException` in one, for its reads
  and its writes. ViewModels then catch it without depending on a storage API
  (req. 9) and show it as a UI state (see
  [Load and save failures](#load-and-save-failures)).
- **Errors that can only come from a mistake in the app's code aren't
  wrapped,** so they still crash as bugs. `RoomProgressRepository` says which
  SQLite errors count.
- **A corrupted database is lost.** The SQLite library deletes it. If a read
  finds the corruption, the scout sees the load-failed message, and the app
  reopens with no progress. If opening the database finds it, the progress is
  gone without any message
  ([#70](https://github.com/bryancassell/bluecard/issues/70)).

### Dependency injection

[Hilt](https://developer.android.com/training/dependency-injection/hilt-android),
using constructor injection. The
[recommendations page](https://developer.android.com/topic/architecture/recommendations)
says to use Hilt once an app has "multiple screens with ViewModels" or
ViewModels scoped to the navigation back stack; BlueCard has both. Hilt
modules bind each repository interface to its implementation and provide the
Room database, DataStore, a coroutine dispatcher, an app-lifetime
`CoroutineScope` (`@ApplicationScope`) and a `Clock` for today's date (the
dispatcher and clock injected so tests can replace them).

## Package layout

```
io.github.bryancassell.bluecard
├── ui/                 One package per screen: screen composable, ViewModel, UI state
│   ├── onboarding/
│   ├── home/
│   ├── badges/         Browse and search, and the badge rows and Eagle labels other screens share
│   ├── badge/          Badge detail, its requirement sub-pages and Edit counselor
│   ├── data/           Clear, export, import
│   ├── navigation/     Navigation 3 keys and the NavDisplay
│   └── theme/
├── data/
│   ├── profile/        ProfileRepository + DataStore
│   ├── catalog/        CatalogRepository + JSON models
│   ├── progress/       ProgressRepository + Room entities and DAOs
│   ├── report/         ReportRepository (PDF)
│   └── backup/         BackupRepository and the export format
├── text/               The strings' locales, and dates and typed text formatted in them, for the
│                       screens and for code outside Compose, such as the PDF report
└── di/                 Hilt modules
```

## Screens

| Screen | PRD journey |
|---|---|
| **Onboarding** | First launch: ask for name and unit number. Shown until the profile is saved. |
| **Home** | Name, unit, and a progress summary: how many badges are completed and in progress, and Eagle-required progress. Below the summary, each badge in progress, in the same row as on Badges, opening its Badge detail. Links to Badges and Data management. |
| **Badges** | Browse all current badges and search by name or description. One screen: the list filters as the scout types. |
| **Badge detail** | Summary, Eagle-required flag, link to the official page, "Share report" and "Save report" once complete, counselor details (tapping the phone or email opens the phone or email app), requirement list with completion state, each opening the requirement's page, and "mark completed on a prior date". |
| **Requirement detail** | Every requirement's own page: whether it's complete, with a checkbox and completion date for one the scout marks complete by hand, its sub-requirements with their completion state, its tracker's rows, and the scout's notes. At the bottom, once anything is recorded, a button clears its progress and that of the requirements under it. |
| **Tracker entry** | One row of a requirement's tracker, to fill in, change or delete: a field for each of the tracker's columns. |
| **Edit counselor** | The badge's merit badge counselor: name, phone and email, each optional. Opened from Badge detail; closes once saved. |
| **Data management** | Clear all progress, export, import. Clearing a single badge or a single requirement's progress lives on the badge and requirement screens. |

Badge detail and each requirement's page show one level of the requirement
tree (see [`PRD.md`](PRD.md#design-decisions)'s Requirement list). Both show
the requirements version the badge was started on, or the newest version for a
badge the scout hasn't started (`data/progress/BadgeVersion.kt`).

## Merit badge catalog

The catalog's file format and authoring rules are in
[`docs/catalog.md`](docs/catalog.md). This section explains why it works this
way.

### Content and links

- **Our own words only.** For each badge, the catalog has our own short
  summary, and for each requirement our own one-line summary. No text is copied
  from Scouting America, and no badge images or logos are included, because
  Scouting America's [trademarks](https://licensingbsa.org/trademarks/) and
  terms of use require written permission.
- **The official page URL is stored per badge** rather than built from the
  name, because the URLs don't always match (American Indian Culture lives at
  `/merit-badges/indian-lore/`). Official pages have no per-requirement
  anchors, so requirement links go to the badge page.

### Trackers

Requirements such as Personal Fitness's 12-week exercise log or Personal
Management's 13-week budget need repeated entries. The catalog describes a
tracker generically (its columns and their types, such as date, number and
text, and optionally a number of rows or weeks), and the app renders and stores
any tracker the same way. New trackers then need only catalog data, not new
code (req. 7).

### Requirement versions

Scouting America updates many badges each January 1 and can make safety changes
at any time
([announcement](https://www.scouting.org/program-updates/important-update-merit-badge-requirements-moving-online/)).
The Guide to Advancement (section 7.0.4.3, quoted on the
[2026 update page](https://www.scouting.org/program-updates/scouts-bsa-advancement-updates-effective-january-1-2026/))
says scouts who start a badge after a change must use the new requirements,
while scouts who started before it may finish on the previous requirements or
switch to the new ones.

- **Each badge has requirement versions**, identified by effective date.
- **The first release ships only current requirements.** From then on, when a
  badge's requirements change, the app update adds the new version and keeps
  every version it has shipped, so a badge already on an older version never
  loses its requirements.
- **Each badge the scout works on records its version**: the newest, or the
  one before it, as the PRD's journeys ask. Older versions stay in the catalog
  for badges already on them, but aren't offered.
- **A badge stays on its version** when an app update brings newer
  requirements, so recorded progress keeps matching its requirements. The scout
  can switch it to the newest version themselves, which starts its requirement
  progress fresh (see [`PRD.md`](PRD.md#design-decisions)).

### Requirement IDs

A requirement is identified by its official number (such as `4c(1)`), which is
unique within a requirements version. Progress is stored against the badge ID,
its version and the requirement number; because switching versions starts
requirement progress fresh, numbers only need to be unique within one version.

### Shipping and authoring

- **The catalog is a JSON file bundled in `assets/`**, loaded into memory at
  startup (about 140 badges, small enough that search is a simple in-memory
  filter). It is updated by releasing a new app version. It is kept separate
  from the progress database, so catalog updates never require database
  migrations.
- **The project writes all the summaries itself.** That is about 140 badges, a
  content project of its own, so the catalog grows in stages, in no particular
  order; the app treats whatever is in the file as the full list. A unit test
  validates the file (unique IDs, valid structure, a URL for every badge).
- **Discontinued badges aren't handled yet:** the Badges list shows every badge
  in the catalog. Once shipped, a badge can't be removed, because progress is
  stored against it, so hiding discontinued badges from scouts who haven't
  started them is tracked in
  [#55](https://github.com/bryancassell/bluecard/issues/55).

## Data model

At a high level. The exact fields are in the code.

- **Profile** (DataStore): name, unit number.
- **Catalog** (JSON, read-only): `MeritBadge` → `RequirementsVersion` →
  `Requirement` (a tree) → optional `TrackerDefinition`.
- **Progress** (Room, database file `bluecard.db`), keyed by catalog IDs
  (strings), so progress survives catalog updates. Only a started badge has
  progress; its requirement progress and tracker entries are deleted with it.
  - `BadgeProgress`: badge ID, requirements version (its effective date, recorded
    when the badge is started), started date, counselor (name, phone, email, all
    optional), and the date it was marked completed on a prior date, if any.
  - `RequirementProgress`: badge ID, requirement number, whether it is complete,
    completion date (optional), notes (`comment`, optional).
  - `TrackerEntry`: an ID that only grows, badge ID, requirement number, the row
    it fills in a tracker with a fixed number of rows (null in a log), the date
    it was first saved, and the row's values keyed by the catalog's column IDs,
    all stored as text (a date as `YYYY-MM-DD`).

### Completion

Completion is derived, not stored (`data/progress/Completion.kt`), from
requirement progress, tracker entries and the catalog:

- A requirement with children is complete when enough of them are, even if it
  also has a tracker. One without children but with a fixed-row tracker is
  complete when every row has an entry. Any other
  requirement, including one with a log, is complete when the scout marked it
  complete.
- A badge is complete when all its top-level requirements are, or when it was
  marked completed on a prior date.
- The completion date is when the last requirement it needed was completed, or
  the prior date for a badge marked that way.

Because nothing about completion is saved, editing or clearing progress can't
leave a stale completion state behind.

A badge's status (not started, in progress or completed) is derived the same
way, in `data/progress/BadgeStatus.kt`, so every screen that shows it agrees.
Which requirements version a badge is worked on (the one it was started on, or
the newest for a badge not started yet) comes from `data/progress/BadgeVersion.kt`.

## Key flows

### First launch

`MainActivityViewModel` reads `ProfileRepository`. While there is no profile,
the navigation root shows Onboarding instead of the back stack; once the profile
is saved, it shows the back stack, which starts at Home. If the profile can't be
read, it shows the load-failed message instead (see
[Load and save failures](#load-and-save-failures)).

### Home summary

The Home ViewModel combines the profile, the catalog and the scout's progress.
It counts badges completed and in progress, and Eagle-required progress against
the Eagle-required badges in the catalog, counting each Eagle "one of" group
once (see [`PRD.md`](PRD.md#design-decisions)). It also lists every badge in
progress, in the row Badges uses
(`ui/badges/BadgeRow.kt`).

### Browse and search

The Badges ViewModel combines the catalog, the scout's progress and the search
text, to list the matching badges with each one's status from
`data/progress/BadgeStatus.kt`. Matching is in `ui/badges/BadgeSearch.kt`, and
how screen readers hear the number of matches is in `BadgesScreen.kt`
(`MatchCount`).

### Recording progress

- **Screens call `ProgressRepository` functions** (set completed date, set
  notes, add tracker row, set counselor, mark badge completed on a date) and
  observe progress as a `Flow`, so they update as soon as data is saved.
- **Recording anything starts the badge**, on the requirements version its
  pages show until then (the newest), dated today
  (`ui/badge/ProgressRecorder.kt`). There's no separate "start" step.
  `markRequirementCompleted`, `setRequirementComment`, `addTrackerEntry` and
  `setCounselor` take a `BadgeStart`, and `ProgressRepository` starts the badge
  in the same transaction as the write, so a save that fails doesn't leave the
  badge started. Other functions that record progress should take one when a
  screen first calls them.
- **The repository cleans up what the scout types:** it trims spaces around
  each value and drops blank ones (`normalizedText`, `normalizedTrackerValues`,
  `Counselor.normalized`).
- **A page that closes once its save succeeds closes itself from UI state**
  (`saved` or `done` in its UI state), following the UI layer guide's
  [example](https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events)
  of navigating from UI state, so a save that fails keeps the page open with the
  scout's edit. Edit counselor and Tracker entry work this way; Requirement
  detail stays open after its notes are saved.

### PDF report

Once a badge is complete, Badge detail offers "Share report" and "Save report".
`PdfReportRepository` reads the profile, the catalog and the badge's progress
(`data/report/BadgeReport.kt`) and lays out the pages with `StaticLayout`
(`ReportLayout.kt`), in the strings' language and direction (see
[Language and layout direction](#language-and-layout-direction)).
`PdfDocumentWriter` draws them onto framework `PdfDocument` pages and writes the
PDF. `androidx.pdf` is not used: it is for viewing PDFs, is still in beta, and
requires API 28 (BlueCard's minimum is 26).

- **Share** it through the
  [Android Sharesheet](https://developer.android.com/training/sharing/send)
  (`ACTION_SEND` with a
  [`FileProvider`](https://developer.android.com/training/secure-file-sharing/setup-sharing)
  content URI to a file in the cache directory's `reports/` folder).
- **Save** it to a location the scout chooses with the
  [system file picker](https://developer.android.com/training/data-storage/shared/documents-files)
  (`ActivityResultContracts.CreateDocument`). The save runs in the app's scope,
  as progress writes do (see [Writes](#writes)), so it finishes if the scout
  leaves Badge detail.

Neither needs storage permissions. A report that can't be created or saved
shows a snackbar, as a failed save does (`SaveRunner`).

### Clearing data

`ProgressRepository` deletes all progress, one badge's progress, or the progress
of a requirement and every one under it (`clearRequirements`), each in one
transaction, after a confirmation dialog. Every removal of what the scout
recorded asks first with the shared `ConfirmDialog` (`ui/ConfirmDialog.kt`),
opened by a button with `removalButtonColors`. Clearing progress does not clear the
profile, and clearing a requirement leaves its badge started.

### Export and import

Export writes a single JSON document (a format version, the profile and all
progress) to a user-chosen file with `ActivityResultContracts.CreateDocument`,
as Save report does (`data/Documents.kt`). Import reads one with
`ActivityResultContracts.OpenDocument`, asking only for documents that can be
opened as a file (`CATEGORY_OPENABLE`), checks the format version and validates
it before changing anything, since import replaces all current data (see
[`PRD.md`](PRD.md#design-decisions)).

- **Any change to the format needs a new format version**
  (`BACKUP_FORMAT_VERSION` in `data/backup/BackupFormat.kt`), even an added
  field. Every field is required and unknown fields are rejected, so an older
  app turns away a newer file rather than importing it without what it doesn't
  know. So does raising a field's length limit, which import holds a file to.
  A file's version is read first, since a newer format may lay the rest out
  differently.
- **Import checks the whole file before it changes anything:** that it's JSON
  in this format, and that it holds only what this version of the app could
  have recorded. Each badge and requirements version must be in the catalog: a
  newer app's catalog can add some without a new format version, so a file
  with one the catalog doesn't have is reported as from a newer version too.
  Each requirement, tracker row and column must be in that version, and no text
  longer than its field takes, so none is cut short when the scout edits it.
- **The file is decoded as it's read, never into a tree of the whole file,**
  and its size is capped, so a large or deeply nested file picked by mistake
  can't use up the app's memory or stack.
- **Progress is replaced in one transaction, then the profile is saved**
  (`ProgressRepository.replaceAll`). The profile is in DataStore, so the two
  can't share a transaction. If replacing progress fails, nothing has changed;
  if saving the profile then fails, the progress is already the file's, and
  importing again replaces both.
- **The file has no tracker entry IDs.** Entries are listed in the order they
  were added, and an import gives them new IDs in that order, so each log keeps
  its order and IDs keep growing.

### Backup

Android [Auto Backup](https://developer.android.com/identity/data/autobackup)
stays on, so the Room database and DataStore file are backed up to the scout's
Google Drive (end-to-end encrypted on Android 9+ with a screen lock) and
restored on a new phone. This is Android's system backup, not app sync, and the
scout can turn it off in system settings.

- **The manifest sets `android:allowBackup` explicitly,** as the Auto Backup
  docs
  [recommend](https://developer.android.com/identity/data/autobackup#EnablingAutoBackup).
- **Backup rules include only the databases directory and the DataStore
  directory,** for both cloud backup and device-to-device transfer
  (`res/xml/data_extraction_rules.xml` on Android 12 and higher,
  `res/xml/backup_rules.xml` on Android 11 and lower). A library that stores
  data in either directory would be backed up too, so check new dependencies
  for that.
- **Backup isn't limited to phones that can encrypt it end to end**
  (`disableIfNoEncryptionCapabilities`), so every scout who keeps backup on can
  move their records.

## Testing approach

How the architecture supports the testing rules in `CLAUDE.md`.

### Fakes and contract tests

- **Fakes, not mocks.** Every repository is an interface, and tests use
  `Fake…Repository` classes. Android's
  [test doubles guide](https://developer.android.com/training/testing/fundamentals/test-doubles)
  says fakes "are preferred"; the recommendations page marks "prefer fakes to
  mocks" as strongly recommended.
- **Contract tests keep fakes honest.** A repository's behavior is written once
  as an abstract test class (for example `ProgressRepositoryContract`). The real
  implementation's test and the fake's test both extend it, so the fake used by
  other features' tests behaves like the real repository.
- **Failures in tests.** Fakes have switches that make their reads
  (`failLoads`: catalog, profile, progress; `failReads`: backup files) or
  writes (`failSaves`: profile, progress, report, backup) throw an
  `IOException`, for testing each screen's failure states. The profile and
  progress contract tests check that the real repositories throw one too.

### ViewModel tests

- **ViewModel tests** are local JVM tests against fake repositories, using
  `kotlinx-coroutines-test` and a `MainDispatcherRule`, as in the
  [coroutines testing guide](https://developer.android.com/kotlin/coroutines/test).
  They check each UI state and each event.
- **They run with Robolectric,** because their load- and save-failure tests
  reach `android.util.Log`, whose methods throw in plain local tests. The
  alternative, `returnDefaultValues`, makes every Android method return null or
  zero instead; the
  [local tests guide](https://developer.android.com/training/testing/local-tests)
  says it "might allow failing tests to pass" and adds: "Only use it as a last
  resort."
- **Saved state** is tested with `ViewModelScenario` from
  `lifecycle-viewmodel-testing`, whose `recreate()` saves state, passes it
  through a `Parcel` and restores it into a new ViewModel
  (`TextFieldSavedStateTest`).

### Hilt in tests

Tests that launch a Hilt activity use `HiltAndroidRule` and Hilt's test
application, and `@TestInstallIn` modules replace production bindings such as
the coroutine dispatcher. A test class that needs fakes removes the modules
that bind those repositories with `@UninstallModules` and supplies the fakes
with `@BindValue`, as `MainActivityTest` does. `ProfileModule` binds only the
profile repository. `DataModule` binds the catalog and progress repositories
together, so a test that fakes one of them supplies both. `BackupModule` binds
only the backup repository, so such a test still exports and imports through
the real one.

### Room and migration tests

- **Room repository tests use Robolectric** with an in-memory database. The
  [Room testing guide](https://developer.android.com/training/data-storage/room/testing-db)
  recommends plain JVM tests with Room's Kotlin Multiplatform setup instead.
  That needs a JVM target, where the in-memory builder takes no `Context`. This
  module is Android-only, and every in-memory builder in Room's Android artifact
  takes a `Context`, so the tests use Robolectric to supply one (checked with
  Room 2.8.5).
- **Migration tests** (`MigrationTest`) run locally too, with Room's
  `MigrationTestHelper`. Debug builds carry the schemas as assets for them
  (`app/build.gradle.kts`); release builds don't.

### Compose UI and screenshot tests

- **Compose UI tests** run locally with Robolectric, one test per UI state and
  interaction, fed by fake repositories or fixed UI state. A test that depends
  on how text is measured, such as whether a long label wraps, uses
  Robolectric's native graphics (`@GraphicsMode(NATIVE)`), since its default
  graphics measure every character as 1px wide.
- **Screenshot tests** ([Roborazzi](https://github.com/takahirom/roborazzi))
  check looks that semantics can't tell apart, such as a requirement row's
  number box in each state (`RequirementRowScreenshotTest`). They run locally
  with Robolectric's native graphics on a fixed screen (`w360dp-h640dp-xhdpi`)
  and on SDK 36 (`@Config(sdk = [36])`), because on SDK 37 Robolectric 4.17
  draws only a class's first screenshot and leaves the rest blank. Every test
  run, `./gradlew check` and
  CI included, compares them against the reference images committed in
  `app/src/test/screenshots/`. After an intended change,
  `./gradlew recordRoborazziDebug` records them again, and the new images are
  reviewed in the diff. A failed comparison writes the new image and one
  comparing the two to `app/build/outputs/roborazzi/`, which CI uploads.

### Catalog, report and backup tests

- **Catalog tests** parse the bundled JSON file and validate its structure.
- **Report tests are split, because `PdfDocument` doesn't run under
  Robolectric.** Its native code isn't there, so it throws "document is
  closed!" (checked with Robolectric 4.17). Local tests check the layout and
  drawing with Robolectric's native graphics (`ReportLayoutTest`), and
  `PdfReportRepositoryTest` uses a fake PDF writer. An instrumented test
  (`PdfDocumentWriterTest`) writes a real PDF and reads it back; run it on an
  emulator with `./gradlew connectedAndroidTest`. CI has no emulator, so it
  doesn't run there.
- **Backup tests:** `BackupFormatTest` pins the export format and checks each
  of import's rules. `JsonBackupRepositoryTest` writes and reads documents
  through a test documents provider, and checks that an export imported into an
  empty Room database restores the same data.

### Coverage

Classes that Hilt and Room generate (for example `Hilt_*`, `*_Factory`,
`*_Impl`) are excluded from the per-class 80% coverage rule, and so is
`PdfDocumentWriter`, which only runs on a device.

## Decisions

Technical decisions, each linked to the section that explains it. Choices about
how the app looks and behaves are in [`PRD.md`](PRD.md#design-decisions).

| Decision | Choice | Why |
|---|---|---|
| [Architecture](#architecture-approach) | UI and data layers; no domain layer yet | Android's recommendations; the domain layer is optional |
| [Modules](#architecture-approach) | Single `:app` module | The modularization guide's reasons don't apply at this size |
| [Navigation](#navigation) | Navigation 3 | Named by the recommendations page and used by Now in Android; stable since 1.0.0 |
| [Persistence](#repositories) | Room 2.8 for progress; Preferences DataStore for the profile | DataStore guide's own criteria; Room 2.8 over Room 3 because BlueCard doesn't need Kotlin Multiplatform |
| [Dependency injection](#dependency-injection) | Hilt | Recommended once there are multiple screens with ViewModels |
| [Catalog](#merit-badge-catalog) | Our own summaries in a bundled JSON file, linking to official pages; no official text or images | Scouting America's terms of use and trademarks |
| [Requirement versions](#requirement-versions) | Every shipped version stays in the catalog; each started badge records its version and stays on it until the scout switches | Scouting America's advancement rules allow finishing on the previous requirements; keeps recorded progress matched to its requirements |
| [Requirement IDs](#requirement-ids) | A requirement's official number, unique within its requirements version | Less to author and easy to check against the official page; switching versions starts progress fresh, so IDs don't need to match across versions |
| [Badge completion](#completion) | Derived from requirement progress and the catalog, never stored | Nothing to keep in sync when progress is edited or cleared |
| [Text fields](#text-fields) | State-based (`TextFieldState`), held in the ViewModel; its text kept in `SavedStateHandle` by a saved state provider | The text field guide recommends state-based fields and holding their state in ViewModels. The provider reads the text only when the system saves state, so it keeps every change without anything collecting the screen's state. `SavedStateHandle.saveable` would too, but it's experimental |
| [Load failures](#load-and-save-failures) | A screen that can't read stored data (`IOException`) shows a message in place of its content; any other exception crashes | The UI layer guide keeps errors in UI state. Crashes reach Android vitals, while caught exceptions would go unreported because the app has no crash reporting of its own; revisit with [#63](https://github.com/bryancassell/bluecard/issues/63) |
| [Save failures](#load-and-save-failures) | A snackbar from UI state; what's on screen keeps showing what's stored | The UI layer guide's pattern for messages from the ViewModel |
| [PDF](#pdf-report) | Framework `PdfDocument`, laid out with `StaticLayout` | `androidx.pdf` is a viewer, in beta, and needs API 28 |
| [Save, share](#pdf-report), [export, import](#export-and-import) | System file picker, Sharesheet, FileProvider; JSON via kotlinx.serialization | No storage permissions needed; kotlinx.serialization JSON is stable and Kotlin's official library |
| [Backup](#backup) | Android Auto Backup on, with rules that include only the databases and DataStore directories; not limited to phones that can encrypt the backup | Scouts keep their records across phone changes; this is system backup, not app sync |
| [Screenshot tests](#compose-ui-and-screenshot-tests) | Roborazzi under Robolectric, compared against committed images on every test run, only for looks that semantics can't show | `CLAUDE.md` asks for screenshot tests where semantics can't tell states apart. They run with the other local tests, with no device or emulator |
| [PDF report tests](#catalog-report-and-backup-tests) | Layout and drawing tested locally with Robolectric's native graphics. `PdfDocumentWriter` tested on a device, outside CI and the coverage check | `PdfDocument` doesn't run under Robolectric, and CI has no emulator |
