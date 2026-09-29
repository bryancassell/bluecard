# BlueCard architecture

This is the high-level technical design for BlueCard. It explains how the app is
structured to meet [`PRD.md`](PRD.md). It is a map, not a detailed spec: feature
issues fill in the details and may update this document when a decision changes.

Sources were checked on 2026-09-28. "Recommended" in this document means Android's
own documentation says so, and each such claim links to the page.

## Overview

BlueCard is a single-activity Android app built with Jetpack Compose. A scout
enters their name and unit number once, browses and searches the merit badge
catalog, and records progress on each badge: counselor details, per-requirement
completion dates and comments, larger trackers such as exercise logs, or a
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
| A new tracker needs only catalog data (req. 7) | A test renders and stores a tracker defined only in test catalog data. |
| The code stays testable as it grows (req. 8) | Every class, except generated code and `@Preview` functions, has at least 80% line coverage from local tests **(CI)**. Every ViewModel, use case, repository and mapper has a unit test, no test uses `@Ignore`, and no mocking library is used **(CI)**. Each repository fake passes the same contract tests as the real implementation. |
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
  [Data model](#data-model)) rather than use cases.
- **One Gradle module (`:app`).** Android's
  [modularization guide](https://developer.android.com/topic/modularization)
  says modularizing pays off mainly for reuse, strict visibility or large
  codebases, none of which apply yet. Code is organized by package instead.

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

### UI layer

- **Screens are composables with a screen-level ViewModel.** Each ViewModel
  exposes one `uiState: StateFlow`, built with `stateIn(WhileSubscribed(5000))`
  when it comes from a flow, and the screen collects it with
  `collectAsStateWithLifecycle()`. This matches the
  [UI layer guide](https://developer.android.com/topic/architecture/ui-layer)
  and the recommendations page.
- **UI state is immutable** and models each state the screen can be in (for
  example loading, content, empty). ViewModels handle events by updating state,
  not by sending one-off events to the UI, as the recommendations page advises.
- **Load failures are a UI state.** When stored data can't be read, a repository
  throws an `IOException` (see [Data layer](#data-layer)). Each ViewModel that
  loads data turns it into a `LoadFailed` state with `catchLoadFailure`
  (`ui/LoadFailure.kt`), and the screen shows a message in place of its content.
  When the profile can't be read, the navigation root shows the message in place
  of the whole app. This follows "Show errors on the screen" in the UI layer
  guide, which keeps errors in UI state. Any other exception is a bug and still
  crashes the app. The app has no crash reporting of its own, so a crash is the
  only way a bug reaches the developer without a scout reporting it: Google
  Play's [Android vitals](https://developer.android.com/topic/performance/vitals)
  reports crashes from users who allow it, but not caught exceptions. To keep it
  simple, the message has no "Try again" button; it asks the scout to close and
  reopen the app, which loads everything again. Revisit this if crash reporting
  is added ([#63](https://github.com/bryancassell/bluecard/issues/63)).
- **Navigation uses [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)**,
  which the recommendations page names for single-activity apps with more than
  one screen. Each destination is a `@Serializable` key, and ViewModels are
  scoped to back stack entries with `lifecycle-viewmodel-navigation3`.
- **Screens navigate with `rememberNavigateFrom`** (`ui/navigation/`), which
  ignores a tap unless the tapping screen is on top of the screens `NavDisplay`
  is showing. A screen that is animating out still takes taps, but it's no
  longer on top, so a double tap can't open a screen twice. Taps on the
  incoming screen work straight away.
- **Launch:** Home is the fixed start destination. Until a profile is saved, the
  navigation root shows Onboarding in place of the back stack, because the
  [navigation principles](https://developer.android.com/guide/navigation/principles#fixed_start_destination)
  say one-time setup screens "should not be considered start destinations".
  The splash screen stays up until the saved profile loads, using
  [core-splashscreen](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate)'s
  `setKeepOnScreenCondition`, so the wrong screen never flashes first. The
  [splash screen guide](https://developer.android.com/develop/ui/views/launch/splash-screen)
  suggests holding the first frame for loading "a small amount of data, such as
  loading in-app settings from a local disk".
- **Material 3** components and the existing `BlueCardTheme`.

### Data layer

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
- **Stored data that can't be read is reported as an `IOException`.** The
  [data layer guide](https://developer.android.com/topic/architecture/data-layer)
  ("Expose errors") says the data layer can expose errors "using custom
  exceptions". DataStore and the asset manager already throw `IOException`, and
  `RoomProgressRepository` wraps Room's `SQLiteException` in one. ViewModels then
  catch it without depending on a storage API (req. 9). Room's writes aren't
  wrapped yet: the first screen that saves progress wraps them the same way.

### Dependency injection

[Hilt](https://developer.android.com/training/dependency-injection/hilt-android),
using constructor injection. The
[recommendations page](https://developer.android.com/topic/architecture/recommendations)
says to use Hilt once an app has "multiple screens with ViewModels" or
ViewModels scoped to the navigation back stack; BlueCard will have both. Hilt
modules bind each repository interface to its implementation and provide the
Room database, DataStore and a coroutine dispatcher (injected so tests can
replace it).

### Package layout

```
io.github.bryancassell.bluecard
├── ui/                 One package per screen: screen composable, ViewModel, UI state
│   ├── onboarding/
│   ├── home/
│   ├── badges/         Browse and search
│   ├── badge/          Badge detail and requirement sub-pages
│   ├── data/           Clear, export, import
│   ├── navigation/     Navigation 3 keys and the NavDisplay
│   └── theme/
├── data/
│   ├── profile/        ProfileRepository + DataStore
│   ├── catalog/        CatalogRepository + JSON models
│   ├── progress/       ProgressRepository + Room entities and DAOs
│   ├── report/         ReportRepository (PDF)
│   └── backup/         BackupRepository (export/import format)
└── di/                 Hilt modules
```

## Screens

| Screen | PRD journey |
|---|---|
| **Onboarding** | First launch: ask for name and unit number. Shown until the profile is saved. |
| **Home** | Name, unit, and a progress summary: how many badges are completed and in progress, and Eagle-required progress. Links to Badges and Data management. |
| **Badges** | Browse all current badges and search by name or description. One screen: the list filters as the scout types. |
| **Badge detail** | Summary, Eagle-required flag, link to the official page, counselor details, requirement list with completion state, "mark completed on a prior date", and "generate report" once complete. |
| **Requirement detail** | Sub-page for a requirement with sub-requirements or a tracker: its sub-requirements with their completion state, and its tracker. |
| **Data management** | Clear all progress, export, import. Clearing a single badge or a single requirement's progress lives on the badge and requirement screens. |

The PRD asks that requirements be understandable "without extensive
scrolling", so the badge detail page lists only the top-level requirements. Each
is one row: its official number, our one-line summary, "Do N of M" when only
some of its sub-requirements are needed, and its completion state. A requirement
with sub-requirements opens the requirement detail sub-page, which lists its
sub-requirements the same way, and a sub-requirement with more of its own opens
a sub-page in turn. A requirement with a tracker will open one too, once the
sub-page shows trackers ([#40](https://github.com/bryancassell/bluecard/issues/40)). Each page shows one level of the requirement tree.
Both pages show the requirements version the badge was started on, or the newest
version for a badge the scout hasn't started.

## Merit badge catalog

**Content.** For each badge: a stable ID, name, our own short summary, whether
it is Eagle-required (and which Eagle "one of" group it belongs to, such as
Cycling / Hiking / Swimming), the URL of its official page, and its
requirements. Each requirement has its official number (such as `4b`), which
also identifies it, our own one-line summary, its children, how many children
are required ("do two of the following"), and an optional tracker definition. No text is copied
from Scouting America, and no badge images or logos are included, because
Scouting America's
[trademarks](https://licensingbsa.org/trademarks/) and terms of use require
written permission.

**Links.** The official page URL is stored per badge rather than built from the
name, because the URLs don't always match (American Indian Culture lives at
`/merit-badges/indian-lore/`). Official pages have no per-requirement anchors,
so requirement links go to the badge page.

**Trackers.** Requirements such as Personal Fitness's 12-week exercise log or
Personal Management's 13-week budget need repeated entries. The catalog
describes a tracker generically (its columns and their types, such as date,
number and text, and optionally a number of rows or weeks), and the app renders
and stores any tracker the same way. New trackers then need only catalog data,
not new code.

**Versions.** Scouting America updates many badges each January 1 and can make
safety changes at any time
([announcement](https://www.scouting.org/program-updates/important-update-merit-badge-requirements-moving-online/)).
The Guide to Advancement (section 7.0.4.3, quoted on the
[2026 update page](https://www.scouting.org/program-updates/scouts-bsa-advancement-updates-effective-january-1-2026/))
says scouts who start a badge after a change must use the new requirements,
while scouts who started before it may finish on the previous requirements or
switch to the new ones.

- **Each badge has requirement versions**, identified by effective date.
- **The first release ships only current requirements.** Each badge starts
  with a single version: its requirements when the release is prepared. Older
  requirements aren't written for it. From then on, when a badge's requirements
  change, the app update adds the new version and keeps the old one, so existing
  users can stay on theirs.
- **Each badge the scout works on records its version.** It defaults to the
  newest version. When the catalog has an older version of the badge, the scout
  can pick it, for example if they started the badge before a change.
- **A badge stays on its version** when an app update brings newer
  requirements, so recorded progress keeps matching its requirements. The scout
  can switch it to the newest version themselves.
- **Switching versions** on a badge that already has requirement progress asks
  for confirmation and starts that badge's requirement progress fresh (counselor
  details stay). Requirement numbers can mean different things in different
  versions, so old entries aren't carried over automatically. Carrying progress
  over is a possible future improvement
  ([#29](https://github.com/bryancassell/bluecard/issues/29)).
- **Versions offered vs. versions kept.** The picker offers the newest version
  and the one before it (when the catalog has one), which covers scouts who
  started shortly before a change. The catalog keeps every version it has ever shipped, so a badge
  already on an older version never loses its requirements.

**Shipping and updates.** The catalog is a JSON file bundled in `assets/` and
loaded into memory at startup (about 140 badges, small enough that search is a
simple in-memory filter). It is updated by releasing a new app version. It is
kept separate from the progress database, so catalog updates never require
database migrations.

**Authoring.** The project writes all the summaries itself. That is about 140
badges, a content project of its own, so the catalog grows in stages, in no
particular order; the app treats whatever is in the file as the full list.
Before the first release, each badge gets only its current requirements; after
it, a requirements change adds a version and keeps the ones already shipped
(see Versions above). A unit test
validates the file (unique IDs, valid structure, a URL for every badge). The
format and authoring rules are in [`docs/catalog.md`](docs/catalog.md).

**Discontinued badges.** Not handled yet: the Badges list shows every badge in
the catalog. Once shipped, a badge can't be removed, because progress is stored
against it, so hiding discontinued badges from scouts who haven't started them
is tracked in [#55](https://github.com/bryancassell/bluecard/issues/55).

**Requirement IDs.** A requirement is identified by its official number (such
as `4c(1)`), which is unique within a requirements version. Progress is stored
against the badge ID, its version and the requirement number; because switching
versions starts requirement progress fresh, numbers only need to be unique
within one version.

## Data model

At a high level. Exact fields are decided in the feature issues.

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
    completion date (optional), comment (optional).
  - `TrackerEntry`: an ID, badge ID, requirement number, and the row's values
    keyed by the catalog's column IDs.

**Completion is derived, not stored**
(`data/progress/Completion.kt`), from requirement progress and the catalog:

- A requirement without children is complete when the scout marked it complete.
  One with children is complete when all of them are, or its "N of these"
  count is.
- A badge is complete when all its top-level requirements are, or when it was
  marked completed on a prior date.
- The completion date is when the last requirement it needed was completed. It
  is the prior date for a badge marked that way. It is unknown if a needed
  requirement has no date.

Because nothing about completion is saved, editing or clearing progress can't
leave a stale completion state behind.

A badge's status (not started, in progress or completed) is derived the same
way, in `data/progress/BadgeStatus.kt`, so every screen that shows it agrees.
Which requirements version a badge is worked on (the one it was started on, or
the newest for a badge not started yet) comes from `data/progress/BadgeVersion.kt`.

## Key flows

- **First launch.** `MainActivityViewModel` reads `ProfileRepository`. While there
  is no profile, the navigation root shows Onboarding instead of the back stack;
  once the profile is saved, it shows the back stack, which starts at Home. If
  the profile can't be read, it shows the load-failed message instead.
- **Home summary.** The Home ViewModel combines the profile, the catalog and the
  scout's progress. It counts badges completed and in progress, and
  Eagle-required progress against the Eagle-required badges in the catalog.
  Each Eagle "one of" group (such as Cycling, Hiking and Swimming) counts once,
  with the status of its furthest-along badge, because earning any of them meets
  the requirement. Progress on a badge that isn't in the catalog isn't counted.
- **Browse and search.** The Badges ViewModel combines the catalog with the
  scout's progress, to show each badge's status from
  `data/progress/BadgeStatus.kt`. Search
  ([#36](https://github.com/bryancassell/bluecard/issues/36)) adds the search
  query to it.
- **Recording progress.** Badge and requirement screens call `ProgressRepository`
  functions (set completed date, set comment, add tracker row, set counselor,
  mark badge completed on a date); the screens observe progress as a `Flow`, so
  they update as soon as data is saved.
- **PDF report.** `ReportRepository` draws the profile, badge, counselor,
  requirement summaries, dates, comments and tracker data onto `PdfDocument`
  pages and writes the file to the app's cache directory. The scout can:
  - **Share** it through the
    [Android Sharesheet](https://developer.android.com/training/sharing/send)
    (`ACTION_SEND` with a
    [`FileProvider`](https://developer.android.com/training/secure-file-sharing/setup-sharing)
    content URI and a read permission grant), or
  - **Save** it to a location they choose with the
    [system file picker](https://developer.android.com/training/data-storage/shared/documents-files)
    (`ActivityResultContracts.CreateDocument`). Neither needs storage
    permissions.

  `androidx.pdf` is not used: it is for viewing PDFs, is still in beta, and
  requires API 28 (BlueCard's minimum is 26).
- **Clearing data.** `ProgressRepository` deletes all progress, one badge's
  progress, or one requirement's progress, each after a confirmation dialog.
  Clearing progress does not clear the profile.
- **Export and import.** Export writes a single JSON document (a format
  version, the profile and all progress) to a user-chosen file with
  `ActivityResultContracts.CreateDocument`. Import reads one with
  `ActivityResultContracts.OpenDocument`, checks the format version and
  validates it before changing anything. Import **replaces all current data**
  (profile and progress), after a warning that says so. Merging an import
  with existing data is a possible future improvement
  ([#28](https://github.com/bryancassell/bluecard/issues/28)).
- **Backup.** Android [Auto Backup](https://developer.android.com/identity/data/autobackup)
  stays on, so the Room database and DataStore file are backed up to the
  scout's Google Drive (end-to-end encrypted on Android 9+ with a screen lock)
  and restored on a new phone. This is Android's system backup, not app sync,
  and the scout can turn it off in system settings. The manifest sets
  `android:allowBackup` explicitly, as the Auto Backup docs
  [recommend](https://developer.android.com/identity/data/autobackup#EnablingAutoBackup),
  and its backup rules include only the databases directory (the Room database
  and its write-ahead log files) and the DataStore directory, for both cloud
  backup and device-to-device transfer (`res/xml/data_extraction_rules.xml` on
  Android 12 and higher, `res/xml/backup_rules.xml` on Android 11 and lower).
  Other files, such as libraries' state files in `files/`, are left out. A
  library that stores data in either directory would be backed up too, so check
  new dependencies for that. Backup isn't limited to phones that can
  encrypt it end to end (`disableIfNoEncryptionCapabilities`), so every scout
  who keeps backup on can move their records. The cache directory where PDFs
  are generated needs no rule: Auto Backup always excludes it.

## Testing approach

How the architecture supports the testing rules in `CLAUDE.md`:

- **Fakes, not mocks.** Every repository is an interface, and tests use
  `Fake…Repository` classes. Android's
  [test doubles guide](https://developer.android.com/training/testing/fundamentals/test-doubles)
  says fakes "are preferred"; the recommendations page marks "prefer fakes to
  mocks" as strongly recommended.
- **ViewModel tests** are local JVM tests against fake repositories, using
  `kotlinx-coroutines-test` and a `MainDispatcherRule`, as in the
  [coroutines testing guide](https://developer.android.com/kotlin/coroutines/test).
  They check each UI state and each event.
- **Hilt in tests.** Tests that launch a Hilt activity use `HiltAndroidRule` and
  Hilt's test application, and `@TestInstallIn` modules replace production
  bindings such as the coroutine dispatcher. A test class that needs fakes
  removes the modules that bind those repositories with `@UninstallModules` and
  supplies the fakes with `@BindValue`, as `MainActivityTest` does.
  `ProfileModule` binds only the profile repository. `DataModule` binds the
  catalog and progress repositories together, so a test that fakes one of them
  supplies both.
- **Room repository tests use Robolectric** with an in-memory database. The
  [Room testing guide](https://developer.android.com/training/data-storage/room/testing-db)
  recommends plain JVM tests with Room's Kotlin Multiplatform setup instead.
  That needs a JVM target, where the in-memory builder takes no `Context`. This
  module is Android-only, and every in-memory builder in Room's Android artifact
  takes a `Context`, so the tests use Robolectric to supply one (checked with
  Room 2.8.5).
- **Contract tests keep fakes honest.** A repository's behavior is written once
  as an abstract test class (for example `ProgressRepositoryContract`). The real
  implementation's test and the fake's test both extend it, so the fake used by
  other features' tests behaves like the real repository.
- **Load failures in tests.** Each fake has a `failLoads` switch that makes its
  reads throw an `IOException`, for testing each screen's `LoadFailed` state. The
  profile and progress contract tests check that the real repositories throw one
  too, using a folder where the DataStore file or the Room database should be.
- **Compose UI tests** run locally with Robolectric, one test per UI state and
  interaction, fed by fake repositories or fixed UI state.
- **Catalog tests** parse the bundled JSON file and validate its structure.
- **Report and backup tests** check the generated PDF's content (page count,
  text) and that export followed by import restores the same data.
- **Coverage.** Classes that Hilt and Room generate (for example `Hilt_*`,
  `*_Factory`, `*_Impl`) are excluded from the per-class 80% coverage rule.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Architecture | UI and data layers; no domain layer yet | Android's recommendations; the domain layer is optional |
| Modules | Single `:app` module | The modularization guide's reasons don't apply at this size |
| Navigation | Navigation 3 | Named by the recommendations page and used by Now in Android; stable since 1.0.0 |
| Persistence | Room 2.8 for progress; Preferences DataStore for the profile | DataStore guide's own criteria; Room 2.8 over Room 3 because BlueCard doesn't need Kotlin Multiplatform |
| Dependency injection | Hilt | Recommended once there are multiple screens with ViewModels |
| Catalog | Our own summaries in a bundled JSON file, linking to official pages; no official text or images | Scouting America's terms of use and trademarks |
| Requirement versions | Newest by default; the scout can pick the previous version when the catalog has one; a badge stays on its version until the scout switches it | Scouting America's advancement rules allow finishing on the previous requirements; keeps recorded progress matched to its requirements |
| Versions in the first release | Current requirements only; versions are kept from the first release on | Project decision for the initial app; keeping every version from then on protects existing users' recorded progress |
| PDF | Framework `PdfDocument` | `androidx.pdf` is a viewer, in beta, and needs API 28 |
| Save, share, export, import | System file picker, Sharesheet, FileProvider; JSON via kotlinx.serialization | No storage permissions needed; kotlinx.serialization JSON is stable and Kotlin's official library |
| Backup | Android Auto Backup on, with rules that include only the databases and DataStore directories; not limited to phones that can encrypt the backup | Scouts keep their records across phone changes; this is system backup, not app sync |
| Badge completion | Derived from requirement progress and the catalog, never stored | Nothing to keep in sync when progress is edited or cleared |
| Load failures | A screen that can't read stored data (`IOException`) shows a message in place of its content; any other exception crashes | The UI layer guide keeps errors in UI state. Crashes reach Android vitals, while caught exceptions would go unreported because the app has no crash reporting of its own; revisit with [#63](https://github.com/bryancassell/bluecard/issues/63) |
| Requirement IDs | A requirement's official number, unique within its requirements version | Less to author and easy to check against the official page; switching versions starts progress fresh, so IDs don't need to match across versions |
| Catalog authoring | The project writes every summary, in no particular order | All badges get covered eventually; order doesn't affect the design |
| Import | Replaces all current data, after a warning | Simplest correct behavior; merging is tracked in [#28](https://github.com/bryancassell/bluecard/issues/28) |
| Switching requirement versions | Resets the badge's requirement progress, after a confirmation; counselor details stay | Avoids attaching entries to the wrong requirement; carrying progress over is tracked in [#29](https://github.com/bryancassell/bluecard/issues/29) |
| Requirement changes | Shipped with regular app updates, with no urgency or monitoring process | The app doesn't need to reflect Scouting America's changes immediately. Scouts who already started may keep the previous requirements anyway. Until an update ships, a scout starting a changed badge sees the previous requirements in the app; the linked official page has the current ones |
| Links to scouting.org | Link each badge to its official page | The PRD asks for links. Scouting America's [terms of use](https://www.scouting.org/legal/terms-and-conditions/), read literally, restrict linking without permission; resolve before release ([#26](https://github.com/bryancassell/bluecard/issues/26)) |
| Official wording | Use Scouting America's terms, such as "Merit Badge" and "Eagle Scout", and official badge names | Clearest for scouts. These are Scouting America [trademarks](https://licensingbsa.org/trademarks/); resolve before release ([#27](https://github.com/bryancassell/bluecard/issues/27)) |
