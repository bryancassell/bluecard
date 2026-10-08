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
  - [Screen reader labels](#screen-reader-labels)
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
  UI state, which updates only when the page's data changes. The date picker's
  latest date works this way: screens pass their ViewModel's `today()` down, and
  the picker calls it as it opens, so a page left open past midnight offers the
  new day. It's a read, not an event, and an exception to state flowing down.
- **What a page remembers survives the system stopping the app.** It's kept in
  the ViewModel's `SavedStateHandle`, as a text field's text is (see
  [Text fields](#text-fields)): for example the date Requirement detail brings
  back to an unchecked requirement, and Badge detail's and Rank detail's
  unmarked dates. A date is kept as its epoch day and read with
  `dateFromEpochDay` (`ui/SavedStateDate.kt`), which, like `restoredText`,
  ignores a value of another kind, as a backstop to
  [#83](https://github.com/bryancassell/bluecard/issues/83). A key observed
  with `getStateFlow` is set to null to forget it, not removed: `remove` drops
  the flow, so the screen would stop following the key.

### Load and save failures

- **Load failures are a UI state.** When stored data can't be read, a repository
  throws an `IOException` (see [Storage errors](#storage-errors)). Each
  ViewModel that loads data turns it into a `LoadFailed` state with
  `catchLoadFailure` (`ui/LoadFailure.kt`), and the screen shows a message in
  place of its content. When the profile can't be read, the navigation root
  shows the message in place of the whole app. This follows "Show errors on the
  screen" in the UI layer guide, which keeps errors in UI state. The message has
  no "Try again" button, which limits when a screen loads again
  (`catchLoadFailure`). Data management shows no such message: it loads only
  whether a badge or rank is started, for its Clear all button, which its
  export and import don't need.
- **Save failures are a UI state too.** When something can't be saved, a
  repository throws an `IOException`. ViewModels that save progress launch each
  write with a `TaskRunner` (`ui/TaskFailure.kt`), which puts a `TaskFailure` in
  the screen's state, and the screen shows "Couldn't save. Try again." in a
  snackbar. That's the pattern in the UI layer guide's
  [Handle ViewModel events](https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events),
  which says ViewModel events "should always result in a UI state update". The
  screen keeps showing what's stored, so a change that failed visibly didn't
  happen. Data management runs its import and clear as it runs its export,
  with its own messages in one snackbar, rather than with a `TaskRunner`.
- **Screen readers hear each snackbar from its live region.** Material 3
  (1.4.0) gives each snackbar a polite live region and the pane title "Alert",
  and composes each one as a new node. TalkBack read every failed save on
  Onboarding, retries included
  ([#234](https://github.com/bryancassell/bluecard/issues/234)): Compose sent a
  subtree change from the new snackbar, and TalkBack reads a live region's text
  on any change it's the source of. It skipped "Alert" when a snackbar replaced
  one still showing, since the pane title hadn't changed, so don't rely on a
  snackbar's pane title. That doesn't mean every new live-region node is
  announced: a screen message composed in its own branch wasn't (below).
- **A message that takes a screen's place is a live region composed while the
  screen loads** ([#69](https://github.com/bryancassell/bluecard/issues/69)),
  so screen readers announce it as it appears. `ScreenMessage`
  (`ui/ScreenMessage.kt`) is a polite live region with no text while loading,
  and the message once loading fails or the content is unavailable. While it
  has no text, it's hidden from screen readers: Compose lets them focus any
  node with text, even empty text, and a hidden node is still tracked. A screen
  shows its loading, load-failed and unavailable states from one `when` branch
  that calls `LoadingOrMessage`, so the node stays the same as its text
  changes. The navigation root, which shows nothing while loading, calls
  `ScreenMessage` itself the same way. Compose sends a live region's change only for a node it has already
  seen (`sendSemanticsPropertyChangeEvents` in
  `AndroidComposeViewAccessibilityDelegateCompat`, Compose UI 1.12.1). A
  message composed as a new node, as in its own branch, isn't announced.
  - **Live regions are what Android points to.** When Android 16 deprecated
    `announceForAccessibility`, its
    [behavior changes](https://developer.android.com/about/versions/16/behavior-changes-all)
    pointed to live regions "to inform the user of changes to critical UI",
    and to pane titles "for significant UI changes like window changes".
  - **Two cases aren't announced.** A failure after a screen has loaded
    replaces the screen's content, not its loading state, so the message is a
    new node. That's rare, and TalkBack should still read the message as its
    focus moves off the content that went away, but that wasn't checked. A
    screen composed already showing the message, such as after rotation,
    doesn't announce it again; it was announced when it first appeared.
  - **A pane title was tried first.** Compose announces a pane even as a new
    node, but TalkBack treated the message like a window: it said "BlueCard",
    the window's title, whenever the message went away. Compose also throws
    when it has to merge a pane title into a parent, which happens only with a
    screen reader on.
- **Any other exception is a bug and still crashes the app.** The app has no
  crash reporting of its own. Once BlueCard is on Google Play, a crash is the
  only way a bug reaches the developer without a scout reporting it:
  [Android vitals](https://developer.android.com/topic/performance/vitals)
  reports crashes from users who allow it, but not caught exceptions. Testers
  of the GitHub test builds report crashes by hand (see
  [Release build](#release-build)). Each
  caught load or save failure is logged with `Log.w`, so logcat and bug reports
  show which data failed and why.
- **Android vitals is the only automatic crash reporting**
  ([#63](https://github.com/bryancassell/bluecard/issues/63)). It needs no code
  in the app. A tool that sends reports automatically, such as Firebase
  Crashlytics or ACRA over HTTP, needs the `INTERNET` permission, which
  requirement 1 rules out. Scouts can be younger than 13, and Google Play's
  [Families policy](https://support.google.com/googleplay/android-developer/answer/9893335)
  says an app whose audience includes children "must not implement APIs or SDKs
  that are not approved for use in child-directed services". ACRA can also email
  a report that the scout reviews and sends from their own email app, which
  needs no `INTERNET` permission. It was left out: it adds a library that runs
  in its own process and a dialog after every crash, and it reports only what
  scouts choose to send.

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
  text field `ImeAction.Done`; a multi-line field keeps Enter. Next uses
  Compose's default focus order, which skips buttons in touch mode, so a
  tracker entry's Next goes past a date's buttons to the next text field
  ([#182](https://github.com/bryancassell/bluecard/issues/182)).
- **A tracker entry's last field, and a requirement's notes, scroll into view
  together with the Save button under them** (`KeepInViewWhileFocused` in
  `ui/KeepInViewWhileFocused.kt`). As the keyboard opens, Compose keeps
  only a focused field's cursor in view, which can leave the Save button under
  the field behind the keyboard
  ([#172](https://github.com/bryancassell/bluecard/issues/172),
  [#178](https://github.com/bryancassell/bluecard/issues/178)). They're
  brought into view together only when they fit, so a field too tall for both
  still keeps its cursor in view. They're also brought into view only once the
  keyboard has stopped moving: while a request runs, Compose stops following
  the cursor, and a phone showed a tall field's cursor left behind the
  keyboard. When the keyboard stops with the viewport grown, they're asked for
  only if a request is still owed, so a number pad shorter than the letters
  doesn't leave Save behind it
  ([#244](https://github.com/bryancassell/bluecard/issues/244),
  [#253](https://github.com/bryancassell/bluecard/issues/253)), and the page
  isn't pulled back after the scout scrolled away. Telling that the keyboard
  is moving takes
  `WindowInsets.imeAnimationTarget`, which is `@ExperimentalLayoutApi`, so this
  function opts in. A change to that API would fail the build when Compose is
  updated.

### Navigation

- **Navigation uses [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)**,
  which the recommendations page names for single-activity apps with more than
  one screen. Each destination is a `@Serializable` key, and ViewModels are
  scoped to back stack entries with `lifecycle-viewmodel-navigation3`.
- **The back stack is saved without reflection.** Every key implements the
  sealed `BlueCardNavKey`, and the back stack is a `NavBackStack<BlueCardNavKey>`
  saved with that interface's compiler-written serializer (`rememberBackStack`
  in `NavKeys.kt`). Navigation 3's `rememberNavBackStack` was not used: without
  a `SavedStateConfiguration` it finds keys by class name with reflection, which
  R8's renaming could break in the release build alone, where local tests can't
  see it ([#203](https://github.com/bryancassell/bluecard/issues/203)). With
  one, it registers keys in a `SerializersModule` (`subclassesOfSealed` is
  experimental) and accepts any `NavKey`. Navigation adds keys only as
  `BlueCardNavKey`s (`rememberNavigateFrom`), so the compiler rejects a key
  that the back stack couldn't save. A key missing `@Serializable` still
  compiles, but the sealed serializer leaves it out, so `NavKeysTest` checks
  every subclass is saved and restored.
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
- **The navigation root decides whether Back asks before discarding unsaved
  changes** (`UnsavedChangesByPage`), from the back stack as it is when Back
  arrives. Pages report their changes with `ConfirmDiscardOnBack` and add no
  back handler of their own: one is added and turned on only as its page is
  drawn, a frame or more behind the back stack, so a quick Back would ask on a
  page sliding away, or close one with unsaved changes before it's drawn again.

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
  `rememberOtherAppStarter` (`ui/`), shared among the screen's controls that
  open another app. After a tap, it ignores taps for the double-tap timeout,
  so a browser doesn't open two tabs, or an email app two drafts
  ([#97](https://github.com/bryancassell/bluecard/issues/97)). Data
  management's Edit and Clear all go through it too (`OtherAppStarter.tap`), so
  a tap just after Export or Import doesn't open a page or dialog under the file
  picker.

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
  `stringsLanguageResources` and `completionDateFormatter` (`text/`). The
  screens' resources come from the same function, so the PDF and the screens
  can't format a string differently.
  `StringsLanguageTagTest` checks that each `strings.xml` names its own
  language: a wrong tag, such as "en" left in a translation, would replace the
  whole translation with English.
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
  button's content is a `ButtonText` (`ui/ButtonText.kt`), which sets it as the
  text's `contentDescription`, never on the button's modifier
  ([#166](https://github.com/bryancassell/bluecard/issues/166),
  [#256](https://github.com/bryancassell/bluecard/issues/256)). Compose gives
  TalkBack the button's parts in turn, so a description on the button became a
  part of its own: TalkBack read "Edit name and unit. Edit. Button". On the
  text, it replaces the text's label. Tests of such a label check the `Text`
  node in the unmerged tree, and that the button has only one description: the
  merged node has the description wherever it's set.

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
  ([#70](https://github.com/bryancassell/bluecard/issues/70)). When SQLite
  finds the database file damaged (corrupt), Android's default handler, which
  Room 2.8 keeps, deletes it. `SetAsideDamagedDatabaseFactory` gives Room a
  handler that moves it instead, with the files SQLite keeps beside it, such as
  the write-ahead log that holds the latest saves, to a folder of its own in
  `damaged-progress/`, in the no-backup directory
  (`FileDamagedProgressRepository`). Each folder is named by when its copy was
  set aside, and none is replaced, since an earlier copy may hold more progress
  than a later one. If the files can't be moved, they're deleted as before.
  Either way the scout sees a notice until they dismiss it (see
  [`PRD.md`](PRD.md#design-decisions)), which a file in `damaged-progress/`
  keeps across launches.
  - **Damage found while opening:** SQLite then creates a new, empty database,
    and the notice shows straight away.
  - **Damage found while reading or writing:** that read or write fails with an
    `IOException`, as other storage failures do, so the screen shows its
    load-failed message, and the notice shows straight away. The handler
    closes the database first, as Android's does, but Room keeps using its one
    closed connection (checked with Room 2.8.5). So the next read or write,
    such as when the scout reopens the app while its process is still running,
    throws an exception the repository treats as a bug, and the app crashes.
    It opens a new database when it starts again. Damage is rare, so this was
    chosen over reporting those exceptions as storage failures until the app
    restarts, or moving every read to a new database.
    SQLite's [How To Corrupt](https://www.sqlite.org/howtocorrupt.html) page
    lists causes such as failing storage; neither it nor Android publishes how
    often it happens.
  - **The damaged copy isn't backed up,** so a phone restored from a backup
    gets neither it nor a notice about it. Nothing reads it yet; it's kept so
    its progress could be recovered later.

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
| **Home** | Name, unit, and a card with the scout's rank, a trail of every rank, and the rank in progress, opening its Rank detail. A progress summary: how many badges are completed and in progress, and Eagle-required progress. Below the summary, each badge in progress, in the same row as on Badges, with its progress bar, opening its Badge detail. Links to Badges, Ranks and Data management. |
| **Badges** | Browse all current badges and search by name or description, with a progress bar on each badge in progress. One screen: the list filters as the scout types. |
| **Badge detail** | A progress bar while the badge is in progress, summary, Eagle-required flag, link to the official page, a status card (where the badge stands, "mark completed on a prior date" while it isn't complete, and "Share report" and "Save report" once it is), counselor details (tapping the phone or email opens the phone or email app), and requirement list with completion state, each opening the requirement's page. At the bottom, once the badge is started, a button clears its progress. |
| **Ranks** | The seven ranks, Scout through Eagle Scout, in the order they're earned, in the same rows as Badges, each with its status and progress bar ([Ranks](#ranks)). |
| **Rank detail** | Like Badge detail without the counselor or Eagle-required label: summary, official link, progress bar, a status card with how it's earned ("mark earned on a prior date", the rank above that counts it as earned, the date it was earned on, or the rank below it waits on) and "Share report" and "Save report" once earned, the requirement list, and Clear progress. |
| **Requirement detail** | Every requirement's own page: whether it's complete, with a checkbox and completion date for one the scout marks complete by hand, a completion date for one completed by its fixed-row tracker, its sub-requirements with their completion state, its tracker's rows, and the scout's notes. At the bottom, once anything is recorded, a button clears its progress and that of the requirements under it. |
| **Tracker entry** | One row of a requirement's tracker, to fill in, change or delete: a field for each of the tracker's columns. |
| **Edit counselor** | The badge's merit badge counselor: name, phone and email, each optional. Opened from Badge detail; closes once saved. |
| **Data management** | A button that opens Edit name and unit, export, import, and last, a button that clears all progress. Clearing a single badge or a single requirement's progress lives on the badge and requirement screens. |
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

- **Our own words only.** For each badge, the catalog has our own short
  summary, and for each requirement our own one-line summary. Official wording
  is kept only where it's the plain, obvious way to say something, such as
  names and lists ([what counts as copying](docs/catalog.md#what-counts-as-copying)).
  No badge images or logos are included. Both are because Scouting America's
  [trademarks](https://licensingbsa.org/trademarks/) and terms of use require
  written permission.
- **The official page URL is stored per badge** rather than built from the
  name, because the URLs don't always match (Fish and Wildlife Management lives
  at `/merit-badges/fish-wildlife-management/`). Official pages have no
  per-requirement anchors, so requirement links go to the badge page.
  scouting.org has no page for each rank, so a rank's URL is its requirements
  PDF, the official wording a scout would otherwise look up.

### Trackers

Requirements such as Personal Fitness's 12-week exercise log or Personal
Management's 13-week budget need repeated entries. The catalog describes a
tracker generically (its columns and their types: date, number, text or
multi-line text, and optionally a number of rows or weeks), and the app renders
and stores any tracker the same way. New trackers then need only catalog data,
not new code (req. 7). Multi-line text is a type of its own, not a flag on
text, so each `when` over the types (the field, the row's summary, the report
and import) has to decide how to handle it.

A number column of a log can have a total the requirement asks for, such as
Life 4's 6 hours of service (`TrackerColumn.total`), which
`data/progress/TrackerTotals.kt` adds the column up against (PRD.md's Tracker
totals).

- **Completion doesn't read totals.** It reads only what the scout marked and
  which rows are filled in, so a total is only a guide
  ([#193](https://github.com/bryancassell/bluecard/issues/193)). A fixed-row
  tracker can't have a total, because its rows complete it and its row shows
  how many are filled in.
- **Numbers are stored as the scout typed them**: digits of any script, with a
  point, a comma or the Arabic decimal separator, whichever their keyboard
  offers (`DECIMAL_SEPARATORS`). So they're read with `storedNumber` when
  they're added up, and import rejects a file with a value `storedNumber` can't
  read ([#233](https://github.com/bryancassell/bluecard/issues/233)). One
  stored while a catalog edited during development had the column as text may
  not be a number, and isn't counted. They're added as `BigDecimal`, so 0.1 and
  0.2 hours make 0.3.

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
  validates the file (unique IDs, valid structure, a URL for every badge and
  rank).
- **Scouts BSA Test Lab pilot badges are left out** until they become
  official: the catalog has no way to mark a badge as a pilot, and ranks'
  merit badge counts (Star 3, Life 3) would credit one. The unit test enforces
  it by requiring each badge's URL to be a `/merit-badges/` page, which pilots
  don't have.
- **Discontinued badges aren't handled yet:** the Badges list shows every badge
  in the catalog. Once shipped, a badge can't be removed, because progress is
  stored against it, so hiding discontinued badges from scouts who haven't
  started them is tracked in
  [#55](https://github.com/bryancassell/bluecard/issues/55).

### Ranks

The catalog also has a list of the seven ranks, Scout through Eagle, in the
order they're earned, written by the same rules as badges, plus a few of their
own for linking, numbering and out-of-date official text
([`docs/catalog.md`](docs/catalog.md#rank),
[#193](https://github.com/bryancassell/bluecard/issues/193)). Most of what a
badge has carries over: numbered requirements with sub-requirements, trackers,
requirement versions and completion. So a `Rank` uses the same
`RequirementsVersion`, `Requirement` and `TrackerDefinition` types as a
`MeritBadge`, and both are an `Advancement`, which the code that serves both
works on.

- **Rank progress shares the badge progress tables** (`badge_progress`,
  `requirement_progress`, `tracker_entry`), keyed by the rank's ID, so ranks
  needed no schema change. Badge and rank IDs must therefore be unique across
  both, which the catalog test checks. Revisit this if it gets in the way.
- **"Badge" in the progress layer means a badge or a rank** (`BadgeProgress`,
  `badgeId`, `badgeStart`), matching its tables. Code above it says
  `advancement` where it means either, such as the `advancementId` of
  Requirement detail and Tracker entry, which find it among both
  (`getAdvancements` in `data/catalog/CatalogRepository.kt`). A screen or count
  for one kind reads only that kind (`getBadges` or `getRanks`), so a rank never
  shows up as a badge.
- **What reads every progress row reads ranks' too.** Export lists a started
  rank with the badges, under the file's `badges` key, and import checks each
  one against the badges and ranks in the catalog, so ranks needed no new
  export format version ([Export and import](#export-and-import)). Clear all
  clears rank progress too, and a started rank turns it on.
- **A rank's status depends on the other ranks** (`data/progress/RankStatus.kt`),
  because ranks are earned in order. A rank is earned once it's complete
  ([Completion](#completion)) and the rank below it is earned, or once it or a
  rank above it is marked earned on a prior date, so the earned ranks are
  always the lowest ones. The lowest rank not earned is in progress, even
  before it's started. Like completion, nothing about it is stored, so
  unmarking a rank undoes what its mark counted as earned. Every screen asks
  `standings` for a rank's status and bar, as they ask `BadgeStatus.kt` for a
  badge's, so they agree. A page that shows a rank reads every rank's progress
  (`observeAllProgress`), not only its own, as does one that can clear what a
  rank counts ([Clearing data](#clearing-data)).
- **Merit badge requirements** (`Requirement.meritBadges`) complete from the
  badges the scout has completed, not from anything recorded on the rank
  ([Completion](#completion)), so `standings` takes the scout's
  `EarnedBadges`.
- **Only a rank's requirement records who signed off on it**
  (`RequirementProgress.signedOffBy`,
  [#248](https://github.com/bryancassell/bluecard/issues/248)). It's a column
  of the shared table that a badge's requirement leaves null, which import
  checks (see [`PRD.md`](PRD.md#design-decisions) for why only ranks have it).
  It's saved with the notes in one write (`setRequirementSignOffAndComment`),
  so their one Save can't save one and fail the other.
- **Time in rank** (`Requirement.monthsInRank`, `data/progress/TimeInRank.kt`)
  is counted from the date `standings` gives the rank below, so it agrees with
  Rank detail about when that rank was earned. Like a tracker's total, it's
  only a guide: completion doesn't read it, so the scout checks the requirement
  off ([#193](https://github.com/bryancassell/bluecard/issues/193)).

## Data model

At a high level. The exact fields are in the code.

- **Profile** (DataStore): name, unit number.
- **Catalog** (JSON, read-only): `MeritBadge` or `Rank` (each an `Advancement`)
  → `RequirementsVersion` → `Requirement` (a tree) → optional
  `TrackerDefinition`.
- **Progress** (Room, database file `bluecard.db`), keyed by catalog IDs
  (strings), so progress survives catalog updates. Only a started badge has
  progress; its requirement progress and tracker entries are deleted with it.
  - `BadgeProgress`: badge ID, requirements version (its effective date, recorded
    when the badge is started), started date, counselor (name, phone, email, all
    optional), and the date it was marked completed on a prior date, if any.
  - `RequirementProgress`: badge ID, requirement number, whether it is complete
    (or, for a requirement completed by its fixed-row tracker, whether the scout
    gave its completion date), completion date (optional), notes (`comment`,
    optional), and, for a rank's requirement, who signed off on it
    (`signedOffBy`, optional).
  - `TrackerEntry`: an ID that only grows, badge ID, requirement number, the row
    it fills in a tracker with a fixed number of rows (null in a log), the date
    it was first saved, and the row's values keyed by the catalog's column IDs,
    all stored as text (a date as `YYYY-MM-DD`).

### Completion

Completion is derived, not stored (`data/progress/Completion.kt`), from
requirement progress, tracker entries and the catalog:

- A requirement with children is complete when enough of them are, even if it
  also has a tracker. One that also asks for work of its own (`ownWork` in the
  catalog) needs the scout to mark that complete too. One without children but
  with a fixed-row tracker is complete when every row has an entry. A rank's
  requirement that asks for merit badges is complete once the scout has
  completed enough of them (below). Any other requirement, including one with
  a log, is complete when the scout marked it complete.
- A badge is complete when all its top-level requirements are, or when it was
  marked completed on a prior date.
- The completion date is when the last requirement or own work it needed was
  completed, or the prior date for a badge marked that way. A requirement with a
  fixed-row tracker is completed on the date the scout gave it, if they gave
  one, or else on the date its last row was first saved.
- A requirement has part done (`hasPartDone`) once anything in it that the scout
  records is: its own work, a requirement under it at any depth, a row of a
  tracker on it or under it, or a badge that counts toward the merit badges it
  asks for. Its row shows this until it's complete.

A requirement's own work is stored as that requirement's own
`RequirementProgress`, as for one marked complete by hand, so it needs no new
table. The catalog marks the requirements that have own work, rather than every
requirement with children needing a check, because most only group their
children. The work can't be a child of its own, because the catalog's numbers
and nesting must match the official page
([#143](https://github.com/bryancassell/bluecard/issues/143)).

The date the scout gives a requirement completed by its fixed-row tracker is
stored the same way: `completed` with the date, or with none once they remove
it. For that requirement, `completed` only means they gave a date, and doesn't
complete it, so the date isn't tied to the rows
(`ProgressRepository.setCompletedFromRowsDate`). This needed no migration or new
export format. A mark left from before
[#105](https://github.com/bryancassell/bluecard/issues/105), when these
requirements had a checkbox, becomes the date the scout gave
([#116](https://github.com/bryancassell/bluecard/issues/116)).

A rank's requirement that asks for merit badges (`Requirement.meritBadges`,
such as Star 3's six, at least four of them Eagle-required) is the only
completion that depends on other items' progress
([#193](https://github.com/bryancassell/bluecard/issues/193)). Completion and
the progress bar take the scout's completed badges as `EarnedBadges`
(`data/progress/EarnedBadges.kt`), worked out once per change in progress
rather than in each requirement. Whether an Eagle "one of" group counts once
comes from the catalog (`eagleGroupsCountOnce`), not the code, so each
requirement can follow its own official wording, and a group counts once
through the same `eagleSlots` as Home. `EarnedBadges` is a parameter that
defaults to none, because a badge's requirements never ask for badges, which
the catalog test checks. So only the code that serves ranks passes it, and
`standings` requires it. Nothing about the requirement is stored, so a page
that shows a rank reads every badge's progress as well as every rank's.

Because nothing about completion is saved, editing or clearing progress can't
leave a stale completion state behind.

A badge's status (not started, in progress or completed) is derived the same
way, in `data/progress/BadgeStatus.kt`, so every screen that shows it agrees. A
rank's status is too, from the ranks below and above it as well
(`data/progress/RankStatus.kt`, [Ranks](#ranks)).
So is how much of a badge is done, for its progress bar
(`data/progress/FractionDone.kt`), with partial credit for each part of a
requirement that's done (see [`PRD.md`](PRD.md#design-decisions)'s Badge
progress bar). Whether a badge shows the bar comes from its status, not from
how much is done.
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
It works out every rank's standing with `standings`, as Ranks does
([Ranks](#ranks)), for the rank card (`ui/home/RankCard.kt`): the highest rank
earned, a trail of every rank, and the rank in progress. It counts
badges completed and in progress, and Eagle-required progress against the
Eagle-required badges in the catalog, counting each Eagle "one of" group
once (`eagleSlots` in `data/catalog/Catalog.kt`, which Eagle 3 counts by too;
see [`PRD.md`](PRD.md#design-decisions)). It also lists every badge in
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
- **Recording anything starts the badge or rank**, on the requirements version
  its pages show until then (the newest), dated today (`badgeStart` in
  `data/progress/BadgeVersion.kt`). There's no separate "start" step.
  `markRequirementCompleted`, `setRequirementSignOffAndComment`,
  `addTrackerEntry`, `setCounselor` and `setCompletedOnPriorDate` take a
  `BadgeStart`, and `ProgressRepository` starts the badge in the same
  transaction as the write, so a save that fails doesn't leave the badge
  started. Other functions that
  record progress should take one when a screen first calls them.
- **The repository cleans up what the scout types:** it trims spaces around
  each value and drops blank ones (`normalizedText`, `normalizedTrackerValues`,
  `Counselor.normalized`).
- **A page that closes once its save succeeds closes itself from UI state**
  (`saved` or `done` in its UI state), following the UI layer guide's
  [example](https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events)
  of navigating from UI state, so a save that fails keeps the page open with the
  scout's edit. Edit counselor, Tracker entry and Edit name and unit work this
  way; Requirement detail stays open after its notes are saved.

### PDF report

Once a badge is complete, Badge detail offers "Share report" and "Save report",
and Rank detail does once a rank is earned. `PdfReportRepository` reads the
profile, the catalog and the progress (`data/report/AdvancementReport.kt`) and
lays out the pages with `StaticLayout` (`ReportLayout.kt`), in the strings'
language and direction (see
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
  leaves the page.

Neither needs storage permissions. A report that can't be created or saved
shows a snackbar, as a failed save does (`TaskRunner`).

A rank's report reads every rank's and badge's progress, as Rank detail does,
and asks `standings` for the rank's standing ([Ranks](#ranks)). So it says how
the rank was earned as the page does, and a rank has a report exactly when the
page offers one: once it's earned, even when it's counted as earned with a rank
above it and isn't started.

### Clearing data

`ProgressRepository` deletes all progress, one badge's progress, or the progress
of a requirement and every one under it (`clearRequirements`), each in one
transaction, after a confirmation dialog. Every removal of what the scout
recorded asks first with the shared `ConfirmDialog` (`ui/ConfirmDialog.kt`),
opened by a button with `removalButtonColors` (`removalOutlinedButtonColors`
for Data management's outlined Clear all). Discarding unsaved changes asks with
it too, opened by Back (see [Navigation](#navigation)). Badge detail and
Requirement detail share their Clear progress button and its dialog
(`ui/badge/ClearProgress.kt`) with Rank detail, which clears a rank as Badge
detail clears a badge. The dialog names the ranks the clear would stop counting
as earned, which all three pages ask `noLongerEarned`
(`data/progress/RankStatus.kt`), so they agree. Clearing a badge or one of its
requirements can leave a rank's merit badges short, so Badge detail and
Requirement detail read every badge's and rank's progress
(`observeAllProgress`), as Rank detail does. Clearing progress does not clear
the profile.
Clearing a requirement leaves its badge started, and clearing a badge deletes
its `BadgeProgress`, so it's no longer started. A page can show a badge for a
moment after it's cleared, so a function a page calls then does nothing for a
badge that isn't started, rather than throw: `markRequirementNotCompleted`,
`removeCompletedOnPriorDate` and the report functions. Clearing doesn't delete a report shared before from the
cache: an app it was shared with, such as an email app that reads it only when
it sends, may still need it.

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
- **Import still reads older format versions,** so exports made before a
  format change keep importing, without what was added since. Version 2 added
  a requirement's sign-off
  ([#248](https://github.com/bryancassell/bluecard/issues/248)), so a version
  1 file is read with `explicitNulls = false` (`backupJsonV1`): a missing field
  that can be null reads as null, and a sign-off in one is rejected. That also
  takes a version 1 file missing another such field, which only a hand-edited
  file could be, as a smaller cost than a reader of its own: a class for the
  old layout leaves the serialization plugin's constructor and encoder for it
  untested, and a tree of each requirement can overflow the stack.
- **Import checks the whole file before it changes anything:** that it's JSON
  in this format, and that it holds only what this version of the app could
  have recorded. Each badge or rank, and its requirements version, must be in
  the catalog: a newer app's catalog can add some without a new format version,
  so a file with one the catalog doesn't have is reported as from a newer
  version too. Each requirement, tracker row and column must be in that
  version, each tracker entry must have a value, a date or number column must
  hold a date or number, only a rank's requirement can have a sign-off, and no
  text can be longer than its field takes, so none is cut short when the scout
  edits it.
- **Import cleans up text as the app does when the scout saves it,** rather
  than rejecting a file for it ([`PRD.md`](PRD.md#design-decisions)): it's
  trimmed, and each line break in single-line text (the name, unit number,
  counselor's fields, a requirement's sign-off and a tracker's text columns)
  is replaced with a space, as its field replaces them (see
  [Text fields](#text-fields)). Length limits apply to the text as it's
  stored. A number column's value must then be a number, so
  even a lone "." is rejected, though saving leaves one out: the field never
  saves one, and dropping it without dropping other non-numbers would take a
  rule of its own ([#233](https://github.com/bryancassell/bluecard/issues/233)).
  A tracker entry with no value left once cleaned up is rejected too, rather
  than dropped ([#239](https://github.com/bryancassell/bluecard/issues/239)).
  Only a development build can export one: a row saved before its field
  replaced pasted line breaks
  ([#155](https://github.com/bryancassell/bluecard/pull/155)) whose only value
  is a next line (U+0085), which `trim()` keeps but import makes a space.
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
profile repository. `DataModule` binds the catalog, progress and
damaged-progress repositories together, so a test that fakes one of them
supplies all three. `BackupModule` binds only the backup repository, so such a
test still exports and imports through the real one.

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
  - After a database version bump, the first test run can merge assets
    before Room writes the new schema, and `MigrationTest` fails. Run it
    again. Room's schema copy declares no outputs, so Gradle can't order
    other tasks after it or see the new file in the same build. Working
    around that needed more build code than a rerun is worth
    ([#96](https://github.com/bryancassell/bluecard/issues/96)).
- **Damaged database tests** (`SetAsideDamagedDatabaseFactoryTest`) run
  locally too. Robolectric runs Android's SQLite code, which calls the
  corruption handler for a file that isn't a database, or one whose pages are
  overwritten, as on a phone.

### Compose UI and screenshot tests

- **Compose UI tests** run locally with Robolectric, one test per UI state and
  interaction, fed by fake repositories or fixed UI state. A test that depends
  on how text is measured, such as whether a long label wraps, uses
  Robolectric's native graphics (`@GraphicsMode(NATIVE)`), since its default
  graphics measure every character as 1px wide. Robolectric shows no keyboard,
  so a test of what stays above it moves one as a phone does
  (`OnScreenKeyboard` in `testing/`). It sends the page's view the keyboard's
  final insets, then its insets frame by frame through the platform's
  `WindowInsetsAnimation` events. A keyboard that appears in one step doesn't show the behavior that
  depends on frames, such as the page following the cursor. With its default
  graphics, Robolectric shows a page out of touch mode, where buttons can take
  focus. Native graphics start in touch mode, as a phone is while the scout
  taps it, so a test of where focus goes on a phone uses them, as every
  screen's tests do (below). A test of a hardware keyboard asks for keyboard
  mode (`InputModeManager`).
- **Each screen's and dialog's tests run Google's accessibility checks**
  ([ATF](https://github.com/google/Accessibility-Test-Framework-for-Android))
  on every window, dialogs included, before each click, scroll, touch, key or
  text input and again on the state the test ends in. Compose doesn't run them
  before a semantics action, a focus request, or replacing or clearing a
  field's text. A control with no label for screen readers or a touch target
  under 48dp fails the test, so it doesn't wait for someone to try the page
  with TalkBack. The class applies the `AccessibilityChecks` rule
  (`testing/`) inside its compose rule. Compose's own
  `enableAccessibilityChecks()` checks nothing under Robolectric, where ATF
  skips composables, so the rule works around that through a hook restricted
  to Compose's own libraries
  ([#196](https://github.com/bryancassell/bluecard/issues/196) compares the
  options). The checks also need Robolectric's native graphics,
  so these classes use them, and a test in them that reads pixels runs on SDK
  36, as screenshot tests do. The workaround relies on details that aren't
  public API, so `AccessibilityChecksTest` checks that each kind of problem
  still fails a test. Native graphics and the checks added no measurable time
  to the suite (checked with Compose UI 1.12.1, ATF 4.1.1 and Robolectric
  4.17).
  - **Only errors fail a test, and contrast isn't checked.** ATF checks
    contrast only from screenshots, so the rule takes none. Taken in these
    tests, they gave hundreds of warnings that weren't about the app's colors:
    most screen tests use Material's default theme, and disabled buttons and
    text partway through fading in were counted too. `BlueCardColorSchemeTest`
    checks the app's colors instead ([Theme](#theme)).
  - **Every test that opens the date picker uses a taller screen**
    (`DATE_PICKER_SCREEN`). On Robolectric's default 320x470dp screen, a month
    that spans six weeks gives each day a 47dp touch target. A short window,
    such as a phone's in landscape, has the same problem in the app
    ([#282](https://github.com/bryancassell/bluecard/issues/282)).
  - **Navigation tests and other components' tests don't run them**, such as
    `MainActivityTest`, `PageTransitionsTest` and the text field tests. What
    they show is checked by the screens' and dialogs' own tests. Under native
    graphics, launching `MainActivity` never finishes, because Robolectric
    keeps drawing frames, and one of `PageTransitionsTest`'s frame-by-frame
    checks fails.
  - **Any other result that can't be fixed yet is suppressed** in the rule's
    validator (`setSuppressingResultMatcher`), matching only that result, with
    a comment linking its issue. The date picker's tests use a taller screen
    instead (above), and #282 tracks the problem.
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

## Release build

- **R8 shrinks, optimizes and obfuscates the release build's code, and unused
  resources are removed** (`isMinifyEnabled` and `isShrinkResources` in
  `app/build.gradle.kts`, with `proguard-android-optimize.txt`), as Android's
  [app optimization guide](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
  recommends. It took the APK from 10.1 MB to 2.1 MB
  ([#197](https://github.com/bryancassell/bluecard/issues/197)).
- **Only a release build runs shrunk code.** The debug app and local tests
  don't, so they can't find what R8 breaks at runtime. CI builds the release
  app, so it catches R8's build errors. Runtime problems need a release build
  on an emulator, checked as described in
  [`docs/toolchain.md`](docs/toolchain.md#checking-a-release-build).
- **Code reached only through reflection needs a keep rule** in
  `app/proguard-rules.pro`, as narrow as possible, with a comment saying what
  needs it. None is needed yet: Hilt, Room, DataStore and
  kotlinx.serialization ship rules for what they reach by reflection or by
  name.
- **Navigation keys' class names aren't kept.** The back stack is saved without
  reflection (see [Navigation](#navigation)), so R8's renaming doesn't affect
  it. Renaming a key in the source changes what is saved, but Android drops an
  app's saved state when the app is updated (checked on Android 37), so state
  saved by one version is never read by another.
- **Crash reports in Android vitals are deobfuscated by Play,** from the R8
  mapping file that AGP puts in the app bundle
  ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/9848633)),
  so it needs no separate upload.
- **Gradle leaves the release build unsigned. Publishing signs it with
  `apksigner`, which asks for the release key's password.** The password never
  reaches a Gradle build, which runs third-party plugins and the code of
  whatever branch is checked out, Dependabot's included.
  [Sign your app](https://developer.android.com/studio/publish/app-signing)
  keeps the password in a properties file that Gradle reads, where every build
  on the machine, and malware that collects such files, could read it too.
  This doesn't stop code running as the developer, such as a build of another
  branch, from tampering with the APK that gets signed or the tools that sign
  it; only a separate account or machine would. Contributors and CI build the
  same unsigned APK, so `./gradlew build` works for anyone. If CI signs later,
  it can run `apksigner` the same way.
- **The release key is RSA 4096 in a PKCS12 keystore, valid for 10,000
  days.** Sign your app asks for at least 25 years. For a `minSdk` of 24 or
  higher, `apksigner` signs with APK Signature Scheme v2, which every Android
  version BlueCard supports checks, and v3, without a v1 signature.
- **Test builds are GitHub pre-releases**
  ([#238](https://github.com/bryancassell/bluecard/issues/238)), for friends
  and family to test without a Google Play Console account.
  [`docs/toolchain.md`](docs/toolchain.md#publishing-a-test-release) says how
  to publish one. Android vitals only reports crashes from Play installs, so
  testers report crashes by hand. Each release carries its R8 mapping file,
  to retrace a crash reproduced with that release.
- **Moving to Google Play means choosing the app signing key.** Play App
  Signing can generate a key of its own, which Play recommends, but Android
  won't update an app from an APK signed with a different key: testers would
  export their data, uninstall, install from Play and import. Play can take
  BlueCard's release key instead, so testers update in place.

## Debug builds

Debug builds install as their own app, next to a release build. They also run
two tools that point out mistakes while the app is in use, StrictMode and
LeakCanary, which release builds don't have.
[`docs/toolchain.md`](docs/toolchain.md#debug-tools) says where to see what
they report.

- **Debug builds have their own application ID,
  `io.github.bryancassell.bluecard.debug`**
  ([#245](https://github.com/bryancassell/bluecard/issues/245)), from
  `applicationIdSuffix` in `app/build.gradle.kts`, as in
  [Configure build variants](https://developer.android.com/build/build-variants#build-types).
  A test release is signed with the release key and a debug build with the
  machine's debug key, and Android won't update an app from an APK signed with
  a different key. With one ID, installing either build on a phone with the
  other meant uninstalling it, which deletes its data. Now the two install side
  by side, each with its own data and backup.
  - **Names that must be unique on the phone, such as a content provider's
    authority, are built from the application ID**: `${applicationId}` in the
    manifest and `context.packageName` in code, as the report FileProvider's
    authority is in both. Two installed apps can't declare the same authority,
    so a fixed one would stop the two builds installing side by side.
  - **The launcher name is "BlueCard Debug", on an orange icon** in place of
    the blue one, from `app/src/debug/res/values`. Launchers cut the name to
    about "BlueCard De…", so the color is what tells the icons apart at a
    glance. Themed icons are monochrome, so with those on, only the name
    differs.
- **[StrictMode](https://developer.android.com/reference/android/os/StrictMode)**
  is turned on in `BlueCardApplication` when the app is debuggable, as in
  [Now in Android](https://github.com/android/nowinandroid/blob/main/app/src/main/kotlin/com/google/samples/apps/nowinandroid/NiaApplication.kt).
  It checks `ApplicationInfo.FLAG_DEBUGGABLE`, since AGP no longer generates
  `BuildConfig` by default.
  - The thread policy reports disk and network access on the main thread. It
    logs each violation and flashes the screen, as in the
    [core app quality](https://developer.android.com/develop/adaptive-apps/quality-guidelines/core-app-quality#strictmode)
    StrictMode test.
  - The VM policy reports streams and cursors that are never closed, leaked
    activities, and a `content://` URI sent to another app without a
    permission grant. It logs each one.
  - Both use `detectAll()`, which turns on new checks as `targetSdk` rises.
    So neither crashes the app (`penaltyDeath()`). Now in Android
    [removed it](https://github.com/android/nowinandroid/pull/1857) after
    crashes from code it doesn't own, and under Robolectric a VM policy's
    `penaltyDeath()` ends the whole test run.
  - Fix a violation, or permit it as narrowly as possible, such as
    `StrictMode.allowThreadDiskReads()` around one call, with a comment saying
    why. The reference says not to "feel compelled to fix everything that
    StrictMode finds."
  - Local tests can't rely on StrictMode being on or off. Under Robolectric,
    its policies outlive the test that set them. Main-thread violations stop
    being logged once a test ends before StrictMode has logged one. So
    `BlueCardApplicationTest` turns StrictMode off and starts the app again
    before each check.
- **[LeakCanary](https://square.github.io/leakcanary/)** is a
  `debugImplementation` dependency and starts itself. It reports activities and
  windows that are still in memory after they're destroyed. It
  [doesn't watch ViewModels](https://github.com/square/leakcanary/blob/v2.14/leakcanary-object-watcher-android-androidx/src/main/java/leakcanary/internal/AndroidXFragmentDestroyWatcher.kt#L62-L67)
  in an app without fragments.
  - Its launcher icon is off (`src/debug/res/values/leak_canary.xml`), so
    `adb shell monkey` and `getLaunchIntentForPackage()` open `MainActivity`.
  - Its heap dumps hold whatever is in memory, and it may save them in the
    phone's public Download folder, so test with made-up records.
  - It brings in [Plumber](https://square.github.io/leakcanary/changelog/#plumber-android-is-a-new-artifact-that-fixes-known-android-leaks),
    which works around known leaks in Android itself, in debug builds only.
    So LeakCanary doesn't report those leaks, though release builds still have
    them.
  - It keeps its results in `leaks.db` in the databases directory, so a debug
    build's backup includes them along with the scout's data.
  - It stays on 2.x until 3.0 is stable.
  - Instrumented tests don't fail on leaks (`DetectLeaksAfterTestSuccess`).
    They run only locally, and each check dumps the heap. LeakCanary still
    runs in them, but doesn't dump the heap while JUnit is loaded.

## Decisions

Technical decisions, each linked to the section that explains it. Choices about
how the app looks and behaves are in [`PRD.md`](PRD.md#design-decisions).

| Decision | Choice | Why |
|---|---|---|
| [Architecture](#architecture-approach) | UI and data layers; no domain layer yet | Android's recommendations; the domain layer is optional |
| [Modules](#architecture-approach) | Single `:app` module | The modularization guide's reasons don't apply at this size |
| [Navigation](#navigation) | Navigation 3 | Named by the recommendations page and used by Now in Android; stable since 1.0.0 |
| [Saved back stack](#navigation) | A `NavBackStack<BlueCardNavKey>`, saved with the sealed interface's serializer, not `rememberNavBackStack` | No reflection, so R8 can't break saving the back stack and local tests cover it. The compiler rejects a key outside `BlueCardNavKey`, and `NavKeysTest` one missing `@Serializable`. Needs no experimental API, unlike registering keys in a `SavedStateConfiguration` with `subclassesOfSealed` |
| [Persistence](#repositories) | Room 2.8 for progress; Preferences DataStore for the profile | DataStore guide's own criteria; Room 2.8 over Room 3 because BlueCard doesn't need Kotlin Multiplatform |
| [Dependency injection](#dependency-injection) | Hilt | Recommended once there are multiple screens with ViewModels |
| [Catalog](#merit-badge-catalog) | Our own summaries in a bundled JSON file, linking to official pages; official wording only where it's the plain way to say something; no official images | Scouting America's terms of use and trademarks |
| [Requirement versions](#requirement-versions) | Every shipped version stays in the catalog; each started badge records its version and stays on it until the scout switches | Scouting America's advancement rules allow finishing on the previous requirements; keeps recorded progress matched to its requirements |
| [Ranks](#ranks) | Ranks share badges' catalog types, as an `Advancement`, and their progress tables, keyed by ID | Most of the badge machinery carries over to ranks with no schema change |
| [Requirement IDs](#requirement-ids) | A requirement's official number, unique within its requirements version | Less to author and easy to check against the official page; switching versions starts progress fresh, so IDs don't need to match across versions |
| [Badge completion](#completion) | Derived from requirement progress and the catalog, never stored | Nothing to keep in sync when progress is edited or cleared |
| [Rank status](#ranks) | Derived in one place from every rank's progress, never stored, including the ranks a rank marked earned counts as earned | Ranks are earned in order, so a rank's status depends on the others; unmarking a rank can't leave one below it earned by mistake |
| [Rank sign-off](#ranks) | A nullable `requirement_progress` column that only a rank's requirement fills, saved with the notes in one write | No new table; the notes' one Save can't save one field and fail the other |
| [Text fields](#text-fields) | State-based (`TextFieldState`), held in the ViewModel; its text kept in `SavedStateHandle` by a saved state provider | The text field guide recommends state-based fields and holding their state in ViewModels. The provider reads the text only when the system saves state, so it keeps every change without anything collecting the screen's state. `SavedStateHandle.saveable` would too, but it's experimental |
| [Load failures](#load-and-save-failures) | A screen that can't read stored data (`IOException`) shows a message in place of its content; any other exception crashes | The UI layer guide keeps errors in UI state. On Google Play, crashes reach Android vitals, while caught exceptions would go unreported because the app has no crash reporting of its own |
| [Crash reporting](#load-and-save-failures) | None in the app; Google Play's Android vitals reports crashes, and testers of GitHub builds report them by hand | Needs no code. Automatic reports need the `INTERNET` permission (req. 1), and Google Play's Families policy limits the SDKs an app for children can use. ACRA's email reports would add a library and a dialog after every crash |
| [Damaged database](#storage-errors) | Room's corruption handler is replaced by one that moves the files to the no-backup directory, keeping every copy, rather than deleting them. Damage found while the database is open leaves Room's connection closed, so the next read or write crashes | Progress is never lost without the scout knowing. Damage is rare, so the closed connection isn't replaced while the app runs |
| [Save failures](#load-and-save-failures) | A snackbar from UI state; what's on screen keeps showing what's stored | The UI layer guide's pattern for messages from the ViewModel |
| [Failure announcements](#load-and-save-failures) | A message that takes a screen's place is a live region, composed with no text while the screen loads | Compose announces a live region only when a node it has seen changes. A pane title, tried first, made TalkBack say "BlueCard" whenever the message went away |
| [Screen reader labels](#screen-reader-labels) | A description that replaces a button's text is set on the `Text` inside it | TalkBack read one set on the button and then the text too |
| [PDF](#pdf-report) | Framework `PdfDocument`, laid out with `StaticLayout` | `androidx.pdf` is a viewer, in beta, and needs API 28 |
| [Save, share](#pdf-report), [export, import](#export-and-import) | System file picker, Sharesheet, FileProvider; JSON via kotlinx.serialization | No storage permissions needed; kotlinx.serialization JSON is stable and Kotlin's official library |
| [Older export formats](#export-and-import) | Still read; version 1 with `explicitNulls = false` | Exports from before a format change keep importing, with one `Json` setting rather than a reader of their own |
| [Backup](#backup) | Android Auto Backup on, with rules that include only the databases and DataStore directories; not limited to phones that can encrypt the backup | Scouts keep their records across phone changes; this is system backup, not app sync |
| [Screenshot tests](#compose-ui-and-screenshot-tests) | Roborazzi under Robolectric, compared against committed images on every test run, only for looks that semantics can't show | `CLAUDE.md` asks for screenshot tests where semantics can't tell states apart. They run with the other local tests, with no device or emulator |
| [PDF report tests](#catalog-report-and-backup-tests) | Layout and drawing tested locally with Robolectric's native graphics. `PdfDocumentWriter` tested on a device, outside CI and the coverage check | `PdfDocument` doesn't run under Robolectric, and CI has no emulator |
| [Release build](#release-build) | R8 shrinks, optimizes and obfuscates the code and removes unused resources; checked at runtime by hand on an emulator | Android's app optimization guide recommends it for every release build. CI has no emulator and there are no device tests of the app's screens, so automated tests of the shrunk app would be new work of their own |
| [Release signing](#release-build) | BlueCard's own key, applied by `apksigner` when publishing; Gradle always builds the release unsigned. Test builds are GitHub pre-releases | The key's password never reaches a Gradle build, and anyone can build the release app. Friends and family can test without a Play Console account. Moving to Play means choosing between Play's own key, which makes testers reinstall, and handing Play this one |
| [Debug application ID](#debug-builds) | Debug builds' application ID ends in `.debug`, and their launcher name is "BlueCard Debug", on an orange icon. Names that must be unique on the phone, such as provider authorities, are built from the application ID | A debug build and a test release are signed with different keys. With one ID, neither could replace the other without uninstalling it and its data. The color tells the icons apart, since launchers cut the name short |
| [Debug tools](#debug-builds) | StrictMode and LeakCanary in debug builds only. StrictMode logs every violation and flashes the screen for main-thread ones; it never crashes the app | They catch main-thread disk access, unclosed streams and leaks while the app is in use. Crashing on violations broke Now in Android when new checks or code it didn't own set them off |
