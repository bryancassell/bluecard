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

Two constraints shape the design:

- **All data stays on the device.** There is no server, account or sync. The only
  network use is opening official Scouting America pages in the browser.
- **The merit badge catalog is our own data.** Scouting America's
  [terms of use](https://www.scouting.org/legal/terms-and-conditions/) forbid
  reusing or compiling their content without written permission, so the app
  ships its own short summaries of each requirement and links to the official
  page for the full text (see [Merit badge catalog](#merit-badge-catalog)).

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
  happens (for example, if progress calculations end up shared by several screens).
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
- **Navigation uses [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)**,
  which the recommendations page names for single-activity apps with more than
  one screen. Each destination is a `@Serializable` key, and ViewModels are
  scoped to back stack entries with `lifecycle-viewmodel-navigation3`.
- **Material 3** components and the existing `BlueCardTheme`.

### Data layer

| Repository | Owns | Data source |
|---|---|---|
| `ProfileRepository` | Scout name and unit number; whether first-launch setup is done | [Preferences DataStore](https://developer.android.com/topic/libraries/architecture/datastore) |
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
| **Home** | Name, unit, and a progress summary (for example badges started, completed, and Eagle-required progress). |
| **Badges** | Browse all current badges and search by name or description. One screen: the list filters as the scout types. |
| **Badge detail** | Summary, Eagle-required flag, link to the official page, counselor details, requirement list with completion state, "mark completed on a prior date", and "generate report" once complete. |
| **Requirement detail** | Sub-page for requirements that need more room: trackers, long lists of choices, or many sub-requirements. |
| **Data management** | Clear all progress, export, import. Clearing a single badge or a single requirement's progress lives on the badge and requirement screens. |

Keeping the badge detail page short (the PRD asks that requirements be
understandable "without extensive scrolling") is a UI concern for the feature
issues: each requirement shows a one-line summary and its state, and anything
larger opens the requirement detail sub-page.

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
- **Each badge the scout works on records its version.** It defaults to the
  newest version. The scout can pick an older one, for example if they started
  the badge before using the app.
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
  and the one before it, which covers scouts who started shortly before a
  change. The catalog keeps every version it has ever shipped, so a badge
  already on an older version never loses its requirements.

**Shipping and updates.** The catalog is a JSON file bundled in `assets/` and
loaded into memory at startup (about 140 badges, small enough that search is a
simple in-memory filter). It is updated by releasing a new app version. It is
kept separate from the progress database, so catalog updates never require
database migrations.

**Authoring.** The project writes all the summaries itself. That is about 140
badges, a content project of its own, so the catalog grows in stages, in no
particular order; the app treats whatever is in the file as the full list. Each badge
whose requirements changed recently also needs its previous version written,
so scouts who started before the change can pick it. A unit test
validates the file (unique IDs, valid structure, a URL for every badge). The
format and authoring rules are in [`docs/catalog.md`](docs/catalog.md).

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
- **Progress** (Room), keyed by catalog IDs (strings), so progress survives
  catalog updates:
  - `BadgeProgress`: badge ID, requirements version, counselor name and contact
    details, started date, completed date, and whether it was marked complete
    on a prior date without per-requirement detail.
  - `RequirementProgress`: badge ID, requirement ID, completed date (optional),
    comment (optional).
  - `TrackerEntry`: badge ID, requirement ID, row, and the values for the
    tracker's columns.

A badge's completion state is derived from its requirement progress and the
catalog's "N of M children required" rules, or from an explicit prior
completion date.

## Key flows

- **First launch.** The navigation root reads `ProfileRepository`: with no
  profile it shows Onboarding, otherwise Home. Saving the profile moves to Home.
- **Browse and search.** The Badges ViewModel combines the catalog with the
  search query and the scout's progress (to show state on each badge).
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
  and the scout can turn it off in system settings. The manifest will set
  backup rules explicitly, as the Auto Backup docs recommend, and exclude the
  cache directory where PDFs are generated.

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
  bindings such as the coroutine dispatcher.
- **Room DAO and repository tests** use an in-memory database in local tests.
  The [Room testing guide](https://developer.android.com/training/data-storage/room/testing-db)
  recommends plain JVM tests (Room's Kotlin Multiplatform setup with the bundled
  SQLite driver) over Robolectric. Whether that works in this Android-only
  module, or needs Robolectric after all, is to be confirmed in the first
  persistence issue.
- **Compose UI tests** run locally with Robolectric, one test per UI state and
  interaction, fed by fake repositories or fixed UI state.
- **Catalog tests** parse the bundled JSON file and validate its structure.
- **Report and backup tests** check the generated PDF's content (page count,
  text) and that export followed by import restores the same data.
- **Coverage.** Hilt and Room generate classes (for example `Hilt_*`,
  `*_Factory`, `*_Impl`) that the per-class 80% coverage rule will need to
  exclude, alongside the existing generated-code exclusions.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Architecture | UI and data layers; no domain layer yet | Android's recommendations; the domain layer is optional |
| Modules | Single `:app` module | The modularization guide's reasons don't apply at this size |
| Navigation | Navigation 3 | Named by the recommendations page and used by Now in Android; stable since 1.0.0 |
| Persistence | Room 2.8 for progress; Preferences DataStore for the profile | DataStore guide's own criteria; Room 2.8 over Room 3 because BlueCard doesn't need Kotlin Multiplatform |
| Dependency injection | Hilt | Recommended once there are multiple screens with ViewModels |
| Catalog | Our own summaries in a bundled JSON file, linking to official pages; no official text or images | Scouting America's terms of use and trademarks |
| Requirement versions | Newest by default; the scout can pick the previous version; a badge stays on its version until the scout switches it | Scouting America's advancement rules allow finishing on the previous requirements; keeps recorded progress matched to its requirements |
| PDF | Framework `PdfDocument` | `androidx.pdf` is a viewer, in beta, and needs API 28 |
| Save, share, export, import | System file picker, Sharesheet, FileProvider; JSON via kotlinx.serialization | No storage permissions needed; kotlinx.serialization JSON is stable and Kotlin's official library |
| Backup | Android Auto Backup on, with explicit rules | Scouts keep their records across phone changes; this is system backup, not app sync |
| Requirement IDs | A requirement's official number, unique within its requirements version | Less to author and easy to check against the official page; switching versions starts progress fresh, so IDs don't need to match across versions |
| Catalog authoring | The project writes every summary, in no particular order | All badges get covered eventually; order doesn't affect the design |
| Import | Replaces all current data, after a warning | Simplest correct behavior; merging is tracked in [#28](https://github.com/bryancassell/bluecard/issues/28) |
| Switching requirement versions | Resets the badge's requirement progress, after a confirmation; counselor details stay | Avoids attaching entries to the wrong requirement; carrying progress over is tracked in [#29](https://github.com/bryancassell/bluecard/issues/29) |
| Requirement changes | Shipped with regular app updates, with no urgency or monitoring process | The app doesn't need to reflect Scouting America's changes immediately. Scouts who already started may keep the previous requirements anyway. Until an update ships, a scout starting a changed badge sees the previous requirements in the app; the linked official page has the current ones |
| Links to scouting.org | Link each badge to its official page | The PRD asks for links. Scouting America's [terms of use](https://www.scouting.org/legal/terms-and-conditions/), read literally, restrict linking without permission; resolve before release ([#26](https://github.com/bryancassell/bluecard/issues/26)) |
| Official wording | Use Scouting America's terms, such as "Merit Badge" and "Eagle Scout", and official badge names | Clearest for scouts. These are Scouting America [trademarks](https://licensingbsa.org/trademarks/); resolve before release ([#27](https://github.com/bryancassell/bluecard/issues/27)) |
