# BlueCard architecture

This is the high-level technical design for BlueCard. It explains how the app is
structured to meet [`PRD.md`](PRD.md). It is a map, not a detailed spec: it
records the technical decisions, the conventions new code must follow, and why.
How each part works is in the comments of the code that builds it, and
[`CLAUDE.md`](CLAUDE.md#what-goes-where) says what belongs here. Choices about
how the app looks and behaves are in [`PRD.md`](PRD.md#design-decisions).

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
  - [Screen reader labels](#screen-reader-labels)
  - [Live regions](#live-regions)
  - [Lists](#lists)
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
  - [Ranks](#ranks)
- [Data model](#data-model)
  - [Completion](#completion)
- [Key flows](#key-flows)
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
  - [Instrumented tests in CI](#instrumented-tests-in-ci)
- [Release build](#release-build)
- [Debug builds](#debug-builds)
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
| The catalog contains only our own content (req. 2) | Catalog validation test requires an official page URL for every badge and rank **(CI)**. Catalog changes are reviewed against the authoring rules in [`docs/catalog.md`](docs/catalog.md). No badge images or logos in the app's resources. |
| An app update never loses or mismatches recorded progress (req. 3) | Each database version's schema is committed in `app/schemas/`, and every schema change comes with a migration test. From the first release on, a catalog test checks that every badge and rank ID and requirements version shipped before is still in the file. |
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
- **A date that must be current when the scout acts is read then**, not held in
  UI state: screens pass their ViewModel's `today()` down, and the date picker
  calls it as it opens (`PickDate` in `ui/badge/CompletionDate.kt`).
- **What a page remembers survives the system stopping the app.** It's kept in
  the ViewModel's `SavedStateHandle`, as a text field's text is (see
  [Text fields](#text-fields)). A date, such as Badge detail's unmarked date, is
  kept as its epoch day and read with `dateFromEpochDay`
  (`ui/SavedStateDate.kt`). A key observed with
  `getStateFlow` is set to null to forget it, not removed: `remove` drops the
  flow, so the screen would stop following the key.

### Load and save failures

- **Load failures are a UI state.** When stored data can't be read, a repository
  throws an `IOException` (see [Storage errors](#storage-errors)). Each
  ViewModel that loads data turns it into a `LoadFailed` state with
  `catchLoadFailure` (`ui/LoadFailure.kt`), and the screen shows a message in
  place of its content. When the profile can't be read, the navigation root
  shows the message in place of the whole app. This follows "Show errors on the
  screen" in the UI layer guide, which keeps errors in UI state.
- **Save failures are a UI state too.** When something can't be saved, a
  repository throws an `IOException`. ViewModels that save progress launch each
  write with a `TaskRunner` (`ui/TaskFailure.kt`), which puts a `TaskFailure` in
  the screen's state, and the screen shows "Couldn't save. Try again." in a
  snackbar. That's the pattern in the UI layer guide's
  [Handle ViewModel events](https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events),
  which says ViewModel events "should always result in a UI state update". The
  screen keeps showing what's stored, so a change that failed visibly didn't
  happen. Data management, with several kinds of message, runs its own (`work`
  in `DataManagementViewModel`).
- **A message that takes a screen's place is a `ScreenMessage`**
  (`ui/ScreenMessage.kt`). It's a live region, so screen readers announce it as
  it appears ([#69](https://github.com/bryancassell/bluecard/issues/69)), but
  not again after the phone rotates (see [Live regions](#live-regions)).
- **Any other exception is a bug and still crashes the app**, so it reaches
  [Android vitals](https://developer.android.com/topic/performance/vitals) once
  BlueCard is on Google Play. That's the only automatic crash reporting, since
  a tool that sends reports itself needs the `INTERNET` permission, which
  requirement 1 rules out
  ([#63](https://github.com/bryancassell/bluecard/issues/63) weighs the
  others). Caught exceptions don't reach it, so each caught load or save
  failure is logged with `Log.w`.

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
  (see [Export and import](#export-and-import)). Single-line text fields take
  their limit from `singleLineInput` (`ui/SingleLineInput.kt`), which then
  replaces a pasted line break with a space (`LineBreaksAsSpaces`). Number
  fields instead chain `NumberInput`, which rejects a line break, with their
  `TextLengthLimit`.
- **The keyboard's action key moves through a page's fields.** On a page with
  more than one field, each one-line field sets `ImeAction.Next`, and the last
  text field `ImeAction.Done`; a multi-line field keeps Enter.
- **A page's last field and the Save button under it go in
  `KeepInViewWhileFocused`** (`ui/KeepInViewWhileFocused.kt`), as a tracker
  entry's last field and a requirement's notes do. Compose keeps only a focused
  field's cursor in view, which can leave Save behind the keyboard.

### Navigation

- **Navigation uses [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)**,
  which the recommendations page names for single-activity apps with more than
  one screen. Each destination is a `@Serializable` key, and ViewModels are
  scoped to back stack entries with `lifecycle-viewmodel-navigation3`.
- **The back stack is saved without reflection**, so R8 can't break it in the
  release build alone ([#203](https://github.com/bryancassell/bluecard/issues/203)).
  Every key implements the sealed `BlueCardNavKey`, whose compiler-written
  serializer saves the back stack (`rememberBackStack` in `NavKeys.kt`), and
  screens add keys with `rememberNavigateFrom`. `NavKeysTest` checks that every
  key is saved and restored, so a new key goes in its list.
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
  extras**, which any app could fill, since `MainActivity` is exported
  ([#83](https://github.com/bryancassell/bluecard/issues/83)). `MainActivity`
  leaves them out of its `defaultViewModelCreationExtras`, which covers every
  ViewModel Hilt and Navigation 3 create. So create no ViewModel without
  creation extras: the activity's default factory would still pass it them.
- **Pages slide side by side** (`ui/navigation/PageTransitions.kt`); why,
  against Material 3's advice, is in [`PRD.md`](PRD.md#design-decisions). The
  navigation root handles Back with its own `BackHandler`, added before the
  screens so that a handler a screen adds goes first (`BlueCardNavDisplay.kt`).
- **The navigation root decides whether Back asks before discarding unsaved
  changes** (`UnsavedChangesByPage`), from the back stack as it is when Back
  arrives. Pages report their changes with `ConfirmDiscardOnBack` and add no
  back handler of their own, which would turn on a frame or more behind the
  back stack.
- **Input focus doesn't pass to the first item of whichever page is shown.**
  Out of touch mode, when a focused item leaves or a page clears focus, Android
  asks the view to take focus again, and Compose gives it to that first item
  ([#285](https://github.com/bryancassell/bluecard/issues/285),
  [#297](https://github.com/bryancassell/bluecard/issues/297),
  [#301](https://github.com/bryancassell/bluecard/issues/301)). So a focus
  target around the pages (`BlueCardNavDisplay`) takes it then, and a page can
  close the keyboard with `FocusManager.clearFocus()`. A page sliding away
  can't take focus (`rememberRefuseFocusWhileLeavingNavEntryDecorator`).

### Double taps

The other app or the next screen takes a moment to cover the one tapped, so
both taps of a double tap can reach it.

- **Screens open other screens with `rememberNavigateFrom`**
  (`ui/navigation/`), which ignores a tap unless the tapping screen is on top,
  and doesn't reopen a screen still animating out (`DrawnScreens`). Screens
  also ignore touches while they animate out, and for the double-tap timeout as
  they animate in (`rememberIgnoreTouchesNavEntryDecorator`), so the second tap
  doesn't press anything on the new screen
  ([#61](https://github.com/bryancassell/bluecard/issues/61)). The merge's
  full-screen dialog does the same as it opens (`IgnoreTouchesAsItOpens`).
- **Screens start other apps with one `OtherAppStarter`** from
  `rememberOtherAppStarter` (`ui/`), shared among the screen's controls that
  open another app. After a tap, it ignores taps for the double-tap timeout
  ([#97](https://github.com/bryancassell/bluecard/issues/97)). A control beside
  them, such as Data management's Clear all, goes through it too
  (`OtherAppStarter.tap`), so its dialog doesn't open under the other app.

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
  `stringsLanguageResources` and `completionDateFormatter` (`text/`). Each
  translation's `strings.xml` must name its own language
  (`StringsLanguageTagTest`).
- **Punctuation that joins text comes from `strings.xml` too**
  ([#135](https://github.com/bryancassell/bluecard/issues/135)), such as the
  " · " between a tracker row's values or the ": " after a label, so a
  translation can change it.
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

### Screen reader labels

- **A label that replaces a button's text goes on the `Text` inside it**: the
  button's content is a `ButtonText` (`ui/ButtonText.kt`), never a description
  on the button's modifier, which TalkBack read as well as the text
  ([#166](https://github.com/bryancassell/bluecard/issues/166),
  [#256](https://github.com/bryancassell/bluecard/issues/256)). Tests check it
  with `assertButtonReadOnceAs` (`testing/ButtonText.kt`).
- **Something read as one, with lines one under another, that can be partly
  scrolled off screen has a label of its own**, set with `clearAndSetSemantics`,
  rather than merging its parts': Home's rank card, and list rows such as a
  badge's, a requirement's and a tracker row. The label is `readAsOneLabel`
  (`ui/ReadAsOne.kt`) of the text it shows, in reading order, but not what it
  reads as its state. Merged, TalkBack left out the parts scrolled off screen
  ([#305](https://github.com/bryancassell/bluecard/issues/305),
  [#312](https://github.com/bryancassell/bluecard/issues/312)). Something with
  one line of text, such as a button, merges its parts as usual: it loses its
  text only when nothing but its padding is on screen. Tests find such a node
  with `onReadAsOne` (`testing/ReadAsOne.kt`), and read it scrolled until only
  its last line shows (`readWithOnlyItsLastLineShown` in
  `testing/SpokenLabel.kt`).

### Live regions

- **A live region is set only when its text should be read out.** Compose
  reports a node's first layout, and each later change to its size or
  position, as a change to it, and TalkBack reads a live region on any change
  it's the source of. So Badges' count becomes one only when its text first
  changes (`MatchCount` in `BadgesScreen.kt`), and a message only for text
  other than what its place showed last, even before the phone rotated
  (`rememberIsNewText` in `ui/ScreenMessage.kt`).
- **Set a live region from a value read while composing, not from a state read
  in the `semantics` block.** Compose updates a `semantics` block as soon as a
  state it reads changes, before the next frame composes the new text, so
  TalkBack read out the old text.
- **In local tests, `LiveRegionReadouts` (`testing/LiveRegion.kt`) lists what
  TalkBack would read out**, as `BadgesScreenTest` uses it.

### Lists

- **A lazy list given a new `LazyListState` while it stays on screen sets its
  own `collectionInfo`** from the items it shows, since `LazyColumn` can keep
  the old state's count, which TalkBack reads as focus enters the list
  ([#304](https://github.com/bryancassell/bluecard/issues/304), Badges in
  `BadgesScreen.kt`). A list that keeps one state, such as Ranks, keeps
  Compose's count.

### Theme

- **Material 3** components, themed by `BlueCardTheme` with a light and a dark
  color scheme (`BlueCardLightColorScheme` and `BlueCardDarkColorScheme` in
  `ui/theme/Color.kt`), as [`PRD.md`](PRD.md#design-decisions)'s Colors row
  chooses. It follows the system's dark mode (`isSystemInDarkTheme()`), and
  takes no colors from the wallpaper (no dynamic color).
- **Dates are picked in our own dialog**, `CompletionDatePickerDialog`, not
  Material's `DatePickerDialog`, which is always as wide as its calendar
  ([#306](https://github.com/bryancassell/bluecard/issues/306)). Compare the two
  when updating Material 3.
- **Before Compose draws**, the window theme and splash screen follow dark mode
  through `values-night`: the dark scheme's background (`@color/background`,
  which must match each scheme's `background`), a dark window theme, and the
  splash screen's white system bar icons. Once the app draws,
  `enableEdgeToEdge()` picks the bar icons from dark mode.
- **The status bar's background is the Scaffold's top bar**
  (`StatusBarBackground` in `BlueCardApp`), as
  [`PRD.md`](PRD.md#design-decisions)'s Status bar row chooses, so no page pads
  itself for the status bar.
- **A full-screen dialog sets its own system bar icons**, since its window
  doesn't get the ones `enableEdgeToEdge()` gives the activity's
  ([#299](https://github.com/bryancassell/bluecard/issues/299)). `MergeDialog`
  calls `UseSystemBarIconsForTheme` (in `ui/data/ImportDialogs.kt`). Move it to
  `ui/` when a second full-screen dialog needs it.
- **Pick a color role that works in both schemes.** In the dark scheme the
  surface containers are lighter than the page, and `surfaceContainerLowest`
  is darker. A container that should stand out from the page, such as the
  status card, uses `surfaceBright`, the brightest surface in both.
- **`BlueCardColorSchemeTest` checks both schemes:** the PRD's contrast rule
  for every text color on every surface, that `surfaceBright` is the brightest
  surface, and that every other surface gets `onSurface` text, which one
  sharing `surfaceVariant`'s color wouldn't.

## Data layer

### Repositories

| Repository | Owns | Data source |
|---|---|---|
| `ProfileRepository` | Scout name and unit number; first-launch setup is done once they are saved | [Preferences DataStore](https://developer.android.com/topic/libraries/architecture/datastore) |
| `CatalogRepository` | Merit badges, requirements, requirement versions (read-only) | JSON file in `assets/`, parsed with [kotlinx.serialization](https://kotlinlang.org/docs/serialization.html) |
| `ProgressRepository` | Everything the scout records | [Room](https://developer.android.com/training/data-storage/room) database |
| `ReportRepository` | Building a badge's or rank's PDF report | Framework [`PdfDocument`](https://developer.android.com/reference/android/graphics/pdf/PdfDocument) |
| `BackupRepository` | Export and import of all user data | JSON written to or read from a user-chosen file |
| `DamagedProgressRepository` | Progress set aside because SQLite found the database damaged, and whether the scout has been told | Files in the app's no-backup directory |

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
  screen cancels only the wait, not the write. Saving the profile, saving a PDF
  report, exporting and importing do too.
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
- **A damaged database is set aside, and the scout is told**
  ([#70](https://github.com/bryancassell/bluecard/issues/70)). Android's
  default corruption handler, which Room 2.8 keeps, deletes the database.
  `SetAsideDamagedDatabaseFactory` gives Room one that moves it, with the files
  SQLite keeps beside it, to a folder of its own in `damaged-progress/`, in the
  no-backup directory (`FileDamagedProgressRepository`). Every copy is kept,
  since an earlier one may hold more progress. Nothing reads them yet; they're
  kept so their progress could be recovered later. The scout sees a notice
  until they dismiss it (see [`PRD.md`](PRD.md#design-decisions)). Damage found
  while the database is open leaves Room's connection closed, so the next read
  or write crashes the app, which starts again with a new database.

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
│   ├── badge/          Badge detail, Edit counselor, and the top of the page and requirement
│   │                   pages badges and ranks share
│   ├── ranks/          Ranks
│   ├── rank/           Rank detail
│   ├── data/           Clear, export, import
│   ├── profile/        Edit name and unit, and the name and unit fields Onboarding shares
│   ├── navigation/     Navigation 3 keys and the NavDisplay
│   └── theme/
├── data/
│   ├── profile/        ProfileRepository + DataStore
│   ├── catalog/        CatalogRepository + JSON models
│   ├── progress/       ProgressRepository + Room entities and DAOs
│   ├── report/         ReportRepository (PDF)
│   └── backup/         BackupRepository and the export format
├── text/               The strings' locales, dates and typed text formatted in them, and line
│                       breaks as spaces, for the screens and for code outside Compose, such as
│                       the PDF report and import
└── di/                 Hilt modules
```

## Screens

| Screen | PRD journey |
|---|---|
| **Onboarding** | First launch: ask for name and unit number. Shown until the profile is saved. |
| **Home** | Name, unit, and a card with the scout's rank, opening the rank in progress's Rank detail. A progress summary, and each badge in progress, opening its Badge detail. Links to Badges, Ranks and Data management. |
| **Badges** | Browse all current badges and search by name or description, with a progress bar on each badge in progress. One screen: the list filters as the scout types. |
| **Badge detail** | Summary, Eagle-required flag, official link, progress bar, a status card for marking it completed and for its report, counselor details, and the requirement list, each opening the requirement's page. Clear progress. |
| **Ranks** | The seven ranks, Scout through Eagle Scout, in the order they're earned, in the same rows as Badges, each with its status and progress bar ([Ranks](#ranks)). |
| **Rank detail** | Like Badge detail without the counselor or Eagle-required label: summary, official link, progress bar, a status card for marking it earned and for its report, the requirement list, and Clear progress. |
| **Requirement detail** | Every requirement's own page: whether it's complete and when, its sub-requirements, its tracker's rows, the scout's notes, and Clear progress. |
| **Tracker entry** | One row of a requirement's tracker, to fill in, change or delete: a field for each of the tracker's columns. |
| **Edit counselor** | The badge's merit badge counselor: name, phone and email, each optional. Opened from Badge detail; closes once saved. |
| **Data management** | Edit name and unit, export, import, and clearing all progress. |
| **Edit name and unit** | The scout's name and unit number, both required. Opened from Data management; closes once saved. |

Badge detail and each requirement's page show one level of the requirement
tree (see [`PRD.md`](PRD.md#design-decisions)'s Requirement list). Both show
the requirements version the badge was started on, or the newest version for a
badge the scout hasn't started (`data/progress/BadgeVersion.kt`). Requirement
detail and Tracker entry serve ranks' requirements too, and Rank detail shares
Badge detail's top of the page (`ui/badge/AdvancementHeader.kt`) and rows
([Ranks](#ranks)).

## Merit badge catalog

The catalog's file format and authoring rules are in
[`docs/catalog.md`](docs/catalog.md). This section explains why it works this
way.

### Content and links

- **Our own words only, and no badge images or logos.** Scouting America's
  [trademarks](https://licensingbsa.org/trademarks/) and terms of use require
  written permission to reuse them (req. 2). So the catalog has our own
  summaries, keeps official wording only where it's the plain way to say
  something ([what counts as copying](docs/catalog.md#what-counts-as-copying)),
  and links each badge and rank to its official page for the full text.

### Trackers

Requirements such as Personal Fitness's 12-week exercise log need repeated
entries. The catalog describes a tracker generically, by its columns and their
types and optionally its number of rows, and the app renders and stores any
tracker the same way, so a new tracker needs only catalog data (req. 7).

- **Completion doesn't read a log's totals or `rowsNeeded`.** It reads only
  what the scout marked and which rows are filled in. A log's requirement may
  ask for more than its rows or amount, and some rows may not count, such as
  Second Class 1a's activities that aren't outdoors, so the scout checks it
  off. Totals and `rowsNeeded` only guide the scout and fill the progress bar
  (`data/progress/FractionDone.kt`,
  [#193](https://github.com/bryancassell/bluecard/issues/193)).
- **A number column's values are stored as the scout typed them**, in any
  script's digits and with whichever decimal separator their keyboard offers,
  so code that reads one as a number uses `storedNumber`
  ([#233](https://github.com/bryancassell/bluecard/issues/233)).
- **Every count of a tracker's rows is out of `rowsOutOf`**, its fixed rows or
  `rowsNeeded`, so the screens and the report agree.

### Requirement versions

Scouting America updates many badges each January 1 and can make safety changes
at any time
([announcement](https://www.scouting.org/program-updates/important-update-merit-badge-requirements-moving-online/)).
Scouts who start a badge after a change must use the new requirements, while
those who started before it may finish on the previous ones (Guide to
Advancement 7.0.4.3, quoted on the
[2026 update page](https://www.scouting.org/program-updates/scouts-bsa-advancement-updates-effective-january-1-2026/)).

- **Each badge has requirement versions**, identified by effective date. The
  first release ships only current requirements. From then on, an app update
  that changes a badge's requirements adds the new version and keeps every
  version it has shipped, so a badge already on an older one never loses its
  requirements.
- **Each started badge records its version and stays on it** when an update
  brings newer requirements, so recorded progress keeps matching its
  requirements. Only the newest version and the one before it are offered, and
  switching to the newest starts the badge's requirement progress fresh (see
  [`PRD.md`](PRD.md#design-decisions)).

### Requirement IDs

A requirement is identified by its official number (such as `4c(1)`), which is
unique within a requirements version. Progress is stored against the badge ID,
its version and the requirement number; because switching versions starts
requirement progress fresh, numbers only need to be unique within one version.
A rank's parent that the official PDF leaves out, such as Scout `1` above
`1a`–`1f`, gets the number its lettered requirements imply
([#286](https://github.com/bryancassell/bluecard/issues/286)).

### Shipping and authoring

- **The catalog is a JSON file bundled in `assets/`**, loaded into memory on
  first use. About 140 badges is small enough that search is a simple
  in-memory filter. It's updated by releasing a new app version, and kept
  apart from the progress database, so catalog updates never need database
  migrations.
- **A shipped badge, rank or version is never removed**, because progress is
  stored against it. The catalog grows in stages, and the app treats whatever
  is in the file as the full list. `BundledCatalogTest` validates the file
  ([`docs/catalog.md`](docs/catalog.md#checking-your-changes)).

### Ranks

The catalog also lists the seven ranks, Scout through Eagle, in the order
they're earned ([`docs/catalog.md`](docs/catalog.md#rank),
[#193](https://github.com/bryancassell/bluecard/issues/193)). Most of what a
badge has carries over, so a `Rank` uses the same `RequirementsVersion`,
`Requirement` and `TrackerDefinition` types as a `MeritBadge`, and both are an
`Advancement`, which the code that serves both works on.

- **Rank progress shares the badge progress tables** (`badge_progress`,
  `requirement_progress`, `tracker_entry`), keyed by the rank's ID, so ranks
  needed no schema change or new export format. Badge and rank IDs must be
  unique across both, which the catalog test checks. Whatever reads every
  progress row, such as export and Clear all, reads ranks' too.
- **"Badge" in the progress layer means a badge or a rank** (`BadgeProgress`,
  `badgeId`, `badgeStart`), matching its tables. Code above it says
  `advancement` where it means either (`getAdvancements` in
  `data/catalog/CatalogRepository.kt`). A screen or count for one kind reads
  only that kind (`getBadges` or `getRanks`), so a rank never shows up as a
  badge.
- **A rank's status depends on the other ranks and on badges** (`standings` in
  `data/progress/RankStatus.kt`): ranks are earned in order, and a rank's
  requirement that asks for merit badges counts the badges the scout has
  completed ([Completion](#completion)). Nothing about it is stored, so
  unmarking a rank undoes what its mark counted as earned. Every screen asks
  `standings`, and a page that shows a rank, or can clear or delete what a
  rank counts, reads every badge's and rank's progress
  (`observeAllProgress`), not only its own ([Clearing data](#clearing-data)).
- **Only a rank's requirement records who signed off on it**
  (`RequirementProgress.signedOffBy`,
  [#248](https://github.com/bryancassell/bluecard/issues/248)): a column of
  the shared table that a badge's requirement leaves null, which import
  checks. It's saved with the notes in one write
  (`setRequirementSignOffAndComment`), so their one Save can't save one and
  fail the other.

## Data model

At a high level. The exact fields are in the code.

- **Profile** (DataStore): name, unit number.
- **Catalog** (JSON, read-only): `MeritBadge` or `Rank` (each an `Advancement`)
  → `RequirementsVersion` → `Requirement` (a tree) → optional
  `TrackerDefinition`.
- **Progress** (Room, database file `bluecard.db`,
  `data/progress/Progress.kt`): `BadgeProgress`, with its
  `RequirementProgress` and `TrackerEntry` rows, keyed by catalog IDs
  (strings), so progress survives catalog updates. Only a started badge has
  progress; its requirement progress and tracker entries are deleted with it.

### Completion

Completion is derived, not stored, from requirement progress, tracker entries
and the catalog (`completion` in `data/progress/Completion.kt`), so editing or
clearing progress can't leave a stale completion state behind. So is what
depends on it, each worked out in one place so every screen agrees: a badge's
status (`BadgeStatus.kt`), a rank's (`RankStatus.kt`, [Ranks](#ranks)), how
much of a badge is done, for its progress bar (`FractionDone.kt`), and which
requirements version a badge is worked on (`BadgeVersion.kt`), all in
`data/progress/`.

- **A requirement's own work** (`ownWork` in the catalog) is stored as that
  requirement's own `RequirementProgress`, so it needed no new table. A
  requirement with a fixed-row tracker and own work stores only the own work,
  whose date is the requirement's, since a row's date is only when it was
  typed in ([#229](https://github.com/bryancassell/bluecard/issues/229)). A
  date for the rows as well would have needed a new column, with a migration
  and a new export format, while the own work is usually the step after the
  rows.
- **A rank's requirement that asks for merit badges**
  (`Requirement.meritBadges`) is the only completion that depends on other
  items' progress
  ([#193](https://github.com/bryancassell/bluecard/issues/193)). Completion
  and the progress bar take the scout's completed badges as `EarnedBadges`, a
  parameter that defaults to none, so only the code that serves ranks passes
  it. Whether an Eagle "one of" group counts once comes from the catalog
  (`eagleGroupsCountOnce`), not the code, so each requirement follows its own
  official wording.

## Key flows

### Recording progress

- **Screens call `ProgressRepository` functions** and observe progress as a
  `Flow`, so they update as soon as data is saved.
- **Recording anything starts the badge or rank**, on the requirements version
  its pages show until then (the newest), dated today (`badgeStart` in
  `data/progress/BadgeVersion.kt`). There's no separate "start" step. The
  functions that record progress take a `BadgeStart`, and
  `ProgressRepository` starts the badge in the same transaction as the write,
  so a save that fails doesn't leave the badge started. A new function that a
  screen can call before the badge is started should take one too.
- **The repository cleans up what the scout types,** not the screens: it trims
  spaces around each value and drops blank ones (`normalizedText`,
  `normalizedTrackerValues`, `Counselor.normalized`).
- **A page that closes once its save succeeds closes itself from UI state**
  (`saved` or `done`), following the UI layer guide's
  [example](https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events),
  so a save that fails keeps the page open with the scout's edit.

### PDF report

`PdfReportRepository` builds a badge's or rank's report from the profile, the
catalog and the progress, lays it out with `StaticLayout` in the strings'
language and direction (see
[Language and layout direction](#language-and-layout-direction)), and draws it
onto framework `PdfDocument` pages. `androidx.pdf` isn't used: it's for viewing
PDFs, is still in beta, and needs API 28. Sharing goes through the
[Android Sharesheet](https://developer.android.com/training/sharing/send) with
a `FileProvider` URI, and saving through the
[system file picker](https://developer.android.com/training/data-storage/shared/documents-files),
so neither needs storage permissions (req. 5).

### Clearing data

- **Every removal of what the scout recorded asks first** with the shared
  `ConfirmDialog` (`ui/ConfirmDialog.kt`), opened by a button with
  `removalButtonColors`. Discarding unsaved changes asks with it too (see
  [Navigation](#navigation)).
- **A removal that could un-earn a rank names the ranks in its dialog**, from
  `noLongerEarned` (`data/progress/RankStatus.kt`), so every page that can
  clear or delete what a rank counts reads every badge's and rank's progress.
  Badge detail, Requirement detail and Rank detail share their Clear progress
  button and its dialog (`ui/badge/ClearProgress.kt`).
- **A function a page can call just after a clear does nothing for a badge
  that isn't started**, rather than throw, since a page can show a badge for a
  moment after it's cleared (`ProgressRepository`).

### Export and import

Export writes one JSON document (a format version, the profile and all
progress) to a file the scout chooses, and import reads one back
(`data/backup/BackupFormat.kt`). Import checks the whole file before it changes
anything, since it can replace all current data. Then the scout chooses to
merge it with their data or replace everything with it.

- **Any change to the format needs a new format version**
  (`BACKUP_FORMAT_VERSION`), even an added field. Every field is required and
  unknown fields are rejected, so an older app turns away a newer file rather
  than importing it without what it doesn't know. So does raising a field's
  length limit, which import holds a file to.
- **Import still reads older format versions,** so exports made before a
  format change keep importing, without what was added since. A version 1 file
  is read with `explicitNulls = false` (`backupJsonV1`), one `Json` setting
  rather than a reader of its own.
- **A merge needs no change dates**
  ([#28](https://github.com/bryancassell/bluecard/issues/28)): the scout
  chooses a side for each badge and rank whose progress differs
  (`data/backup/Merge.kt`, `ui/data/MergeChoices.kt`). Picking the newer side
  automatically would take a database migration, a new format version and a
  record of what was deleted, or progress cleared on one side would come back
  from the other.

### Backup

Android [Auto Backup](https://developer.android.com/identity/data/autobackup)
stays on, so the Room database and DataStore file are backed up to the scout's
Google Drive and restored on a new phone (req. 4). This is Android's system
backup, not app sync, and the scout can turn it off in system settings.

- **Backup rules include only the databases and DataStore directories**
  (`res/xml/data_extraction_rules.xml` and `res/xml/backup_rules.xml`). A
  library that stores data in either directory would be backed up too, so
  check new dependencies for that.

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
  (`failLoads`, `failReads`) or writes (`failSaves`) throw an `IOException`,
  for testing each screen's failure states. The profile and progress contract
  tests check that the real repositories throw one too.

### ViewModel tests

ViewModel tests are local JVM tests against fake repositories, using
`kotlinx-coroutines-test` and a `MainDispatcherRule`, and check each UI state
and each event. They run with Robolectric, because their failure tests reach
`android.util.Log`, whose methods throw in plain local tests. The alternative,
`returnDefaultValues`, makes every Android method return null or zero, which
the [local tests guide](https://developer.android.com/training/testing/local-tests)
says "might allow failing tests to pass".

### Hilt in tests

A test that launches a Hilt activity replaces production bindings with
`@TestInstallIn` modules, or removes a module with `@UninstallModules` and
supplies fakes with `@BindValue`, as `MainActivityTest` does
([`docs/toolchain.md`](docs/toolchain.md#tests-local-vs-instrumented)). Each
module's KDoc says which repositories it binds, and so which a test fakes
together.

### Room and migration tests

Room repository tests (`RoomProgressRepositoryTest`), migration tests
(`MigrationTest`) and damaged database tests
(`SetAsideDamagedDatabaseFactoryTest`) run locally with Robolectric, which
runs Android's SQLite code.

### Compose UI and screenshot tests

- **Compose UI tests** run locally with Robolectric, one test per UI state and
  interaction, fed by fake repositories or fixed UI state. Robolectric shows no
  keyboard, so a test of what stays above it moves one as a phone does
  (`OnScreenKeyboard` in `testing/`).
- **Each screen's and dialog's tests use Robolectric's native graphics**
  (`@GraphicsMode(NATIVE)`). Its default graphics measure every character as
  1px wide, and show a page out of touch mode, where buttons can take focus.
  Native graphics start in touch mode, as a phone is while the scout taps it;
  a test of a hardware keyboard asks for keyboard mode (`InputModeManager`).
- **They run Google's accessibility checks**
  ([ATF](https://github.com/google/Accessibility-Test-Framework-for-Android))
  with the `AccessibilityChecks` rule (`testing/`), so a control with no label
  for screen readers or a touch target under 48dp fails the test rather than
  waiting for someone to try the page with TalkBack. Navigation tests and
  other components' tests don't, since the screens' and dialogs' own tests check
  what they show; `MainActivityTest` and `PageTransitionsTest` say why they
  can't.
- **A test that reads pixels runs on SDK 36** (`@Config(sdk = [36])`), because
  on SDK 37 Robolectric 4.17 draws only a class's first screenshot.
- **Screenshot tests** ([Roborazzi](https://github.com/takahirom/roborazzi))
  check only looks that semantics can't tell apart, such as a requirement
  row's number box in each state, on a fixed screen (`w360dp-h640dp-xhdpi`).
  Every test run compares them against reference images committed in
  `app/src/test/screenshots/`
  ([`docs/toolchain.md`](docs/toolchain.md#tests-local-vs-instrumented)).

### Catalog, report and backup tests

`PdfDocument` doesn't run under Robolectric, so report tests are split: local
tests check the layout and drawing (`ReportLayoutTest`) and use a fake PDF
writer (`PdfReportRepositoryTest`), and an instrumented test
(`PdfDocumentWriterTest`) writes a real PDF and reads it back.
`PdfDocumentWriter` is left out of the per-class coverage check, as are the
classes Hilt and Room generate.

### Instrumented tests in CI

CI's **Instrumented tests** job runs `app/src/androidTest` on a Gradle Managed
Device: a Pixel 6 emulator on API 37, the target SDK, with the Google APIs
image and 16 KB pages, the page size Google Play requires
(`app/build.gradle.kts`).
[`docs/toolchain.md`](docs/toolchain.md#continuous-integration) says why the
other emulator options didn't work, and why the emulator isn't cached.

## Release build

- **R8 shrinks, optimizes and obfuscates the release build's code, and unused
  resources are removed** (`app/build.gradle.kts`), as Android's
  [app optimization guide](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
  recommends ([#197](https://github.com/bryancassell/bluecard/issues/197)).
- **Only a release build runs shrunk code.** The debug app and local tests
  don't, so they can't find what R8 breaks at runtime. CI builds the release
  app, so it catches R8's build errors; the
  [QA test plan](docs/qa-test-plan.md) checks a release build at runtime.
  Code reached only through reflection needs a keep rule in
  `app/proguard-rules.pro`.
- **Gradle leaves the release build unsigned.** Publishing signs it with
  `apksigner`, so the release key's password never reaches a Gradle build,
  which runs third-party plugins and whatever branch is checked out, and
  anyone can build the release app.
- **Test builds are GitHub pre-releases**
  ([#238](https://github.com/bryancassell/bluecard/issues/238)), so friends
  and family can test without a Google Play Console account. Moving to Play
  means choosing between Play's own signing key, which makes testers
  reinstall, and handing Play BlueCard's
  ([`docs/toolchain.md`](docs/toolchain.md#publishing-a-test-release)).

## Debug builds

Debug builds install as their own app, next to a release build, and run two
tools that point out mistakes while the app is in use, StrictMode and
LeakCanary. Release builds have neither.
[`docs/toolchain.md`](docs/toolchain.md#debug-tools) says how to use them.

- **Debug builds have their own application ID,
  `io.github.bryancassell.bluecard.debug`**
  ([#245](https://github.com/bryancassell/bluecard/issues/245)). A test
  release and a debug build are signed with different keys, so with one ID,
  installing either meant uninstalling the other, which deletes its data.
  Their launcher name is "BlueCard Debug", on an orange icon, since launchers
  cut the name short.
- **Names that must be unique on the phone, such as a content provider's
  authority, are built from the application ID** (`${applicationId}` in the
  manifest, `context.packageName` in code), so the two builds can install side
  by side.
- **[StrictMode](https://developer.android.com/reference/android/os/StrictMode)
  logs every violation and never crashes the app**; `BlueCardApplication` says
  why. Fix a violation, or permit it as narrowly as possible, such as
  `StrictMode.allowThreadDiskReads()` around one call, with a comment saying
  why.
- **[LeakCanary](https://square.github.io/leakcanary/)** reports activities
  and windows that are still in memory after they're destroyed.

## Decisions

Technical decisions, each linked to the section that explains it. Choices about
how the app looks and behaves are in [`PRD.md`](PRD.md#design-decisions).

| Decision | Choice | Why |
|---|---|---|
| [Architecture](#architecture-approach) | UI and data layers; no domain layer yet | Android's recommendations; the domain layer is optional |
| [Modules](#architecture-approach) | Single `:app` module | The modularization guide's reasons don't apply at this size |
| [Navigation](#navigation) | Navigation 3 | Named by the recommendations page; stable since 1.0.0 |
| [Focus between pages](#navigation) | A focus target around the pages takes the focus a page lets go of | Otherwise Compose gives it to a page's first item, which opened the keyboard on Badges |
| [Saved back stack](#navigation) | A `NavBackStack<BlueCardNavKey>` saved with the sealed interface's serializer | No reflection for R8 to break, and no experimental API |
| [Persistence](#repositories) | Room 2.8 for progress; Preferences DataStore for the profile | The DataStore guide's criteria; BlueCard doesn't need Room 3's Kotlin Multiplatform |
| [Dependency injection](#dependency-injection) | Hilt | Recommended once there are multiple screens with ViewModels |
| [Catalog](#merit-badge-catalog) | Our own summaries in a bundled JSON file, linking to official pages; no official images | Scouting America's terms of use and trademarks |
| [Requirement versions](#requirement-versions) | Every shipped version stays; a started badge stays on its version until the scout switches | The advancement rules allow finishing on the previous requirements |
| [Ranks](#ranks) | Ranks share badges' catalog types and progress tables | The badge machinery carries over with no schema change |
| [Requirement IDs](#requirement-ids) | The official number, unique within its requirements version | Easy to check against the official page |
| [Badge completion](#completion) | Derived from progress and the catalog, never stored | Nothing to keep in sync when progress changes |
| [Own work with rows](#completion) | A fixed-row requirement's own work is stored as the requirement's progress | No new column, migration or export format |
| [Rank status](#ranks) | Derived in one place from every rank's progress, never stored | A rank's status depends on the others |
| [Rank sign-off](#ranks) | A `requirement_progress` column, saved with the notes in one write | No new table, and one Save can't half-fail |
| [Text fields](#text-fields) | `TextFieldState` held in the ViewModel, its text kept in `SavedStateHandle` | The text field guide's recommendation |
| [Load failures](#load-and-save-failures) | An `IOException` reading data shows a message in the screen's place; anything else crashes | Errors belong in UI state; crashes reach Android vitals |
| [Crash reporting](#load-and-save-failures) | None in the app; Google Play's Android vitals | Automatic reports need the `INTERNET` permission (req. 1) |
| [Damaged database](#storage-errors) | Damaged files are set aside, never deleted, and the scout is told | Progress is never lost without the scout knowing |
| [Save failures](#load-and-save-failures) | A snackbar from UI state; the screen keeps showing what's stored | The UI layer guide's pattern for ViewModel messages |
| [Failure announcements](#load-and-save-failures) | A message that takes a screen's place is a live region | Screen readers hear it as it appears, but not again after rotation |
| [Screen reader labels](#screen-reader-labels) | A description that replaces a button's text goes on the `Text` inside it | TalkBack read one on the button and then the text too |
| [Live regions](#live-regions) | A live region is set only when its text should be read out | Compose reports a node's first layout as a change |
| [PDF](#pdf-report) | Framework `PdfDocument`, laid out with `StaticLayout` | `androidx.pdf` is a viewer, in beta, and needs API 28 |
| [Save, share](#pdf-report), [export, import](#export-and-import) | System file picker, Sharesheet, FileProvider; JSON with kotlinx.serialization | No storage permissions; Kotlin's official serialization library |
| [Older export formats](#export-and-import) | Still imported | Exports from before a format change keep working |
| [Backup](#backup) | Auto Backup of the databases and DataStore only | Records survive a phone change, with no app sync |
| [Screenshot tests](#compose-ui-and-screenshot-tests) | Roborazzi under Robolectric, only for looks semantics can't show | They run with the local tests, with no emulator |
| [PDF report tests](#catalog-report-and-backup-tests) | Layout tested locally; `PdfDocumentWriter` on an emulator | `PdfDocument` doesn't run under Robolectric |
| [Instrumented tests in CI](#instrumented-tests-in-ci) | A Gradle Managed Device on API 37 with 16 KB pages | The target SDK and the page size Play requires |
| [Release build](#release-build) | R8 shrinks, optimizes and obfuscates; checked on emulators | The app optimization guide recommends it |
| [Release signing](#release-build) | `apksigner` signs when publishing, and Gradle builds unsigned; test builds are GitHub pre-releases | The key's password never reaches a Gradle build |
| [Debug application ID](#debug-builds) | Debug builds' application ID ends in `.debug` | A debug build and a test release install side by side |
| [Debug tools](#debug-builds) | StrictMode and LeakCanary in debug builds only; StrictMode never crashes | They catch disk access and leaks while the app is in use |
