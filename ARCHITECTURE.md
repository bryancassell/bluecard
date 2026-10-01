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
| A new tracker needs only catalog data (req. 7) | Tests render and store trackers defined only in test catalog data (`TrackerEntryViewModelTest`, `MainActivityTest`) **(CI)**. |
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
  reports crashes from users who allow it, but not caught exceptions. Revisit
  this if crash reporting is added
  ([#63](https://github.com/bryancassell/bluecard/issues/63)). Until then,
  `catchLoadFailure` logs each failure it catches with `Log.w`, so logcat and bug
  reports show which data failed and why. A failed save is logged the same way.
  Screen readers don't announce the
  message yet when it replaces the loading indicator
  ([#69](https://github.com/bryancassell/bluecard/issues/69)).
- **Save failures are a UI state too.** When something can't be saved, a
  repository throws an `IOException`. ViewModels that save progress launch each
  write with a `SaveRunner` (`ui/SaveFailure.kt`), which logs the failure and
  puts a `SaveFailure` in the screen's state. The screen shows "Couldn't save. Try
  again." in a snackbar (`SaveFailedSnackbarHost`) and tells the ViewModel once
  it's gone, which clears it. Each failure is a new object, and the snackbar is
  keyed by it, so a failure that arrives just as the last one is cleared is
  still shown. That's the pattern in the UI layer guide's
  [Handle ViewModel events](https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events),
  which says ViewModel events "should always result in a UI state update". The
  screen keeps showing what's stored, so a change that failed visibly didn't
  happen. As with loads, any other exception crashes. Onboarding predates this
  and shows its own message under its button.
- **Reloading after a load failure has limits.** To keep it simple, the message
  has no "Try again" button; it asks the scout to close and reopen the app. A
  screen loads again only when its ViewModel is created, or when the screen is
  shown after being hidden for more than 5 seconds (`WhileSubscribed(5_000)`).
  Badges and the badge pages get a new ViewModel each time they open, but Home
  and the navigation root keep theirs for as long as the activity lives. On
  Android 12 and higher, Back on Home moves the app to the background instead of
  finishing the activity
  ([behavior change](https://developer.android.com/about/versions/12/behavior-changes-all)),
  so reopening the app within 5 seconds shows the message again. A failure
  after a screen has loaded also stays until the screen loads again. A "Try
  again" button would fix both.
- **Screen readers hear how many badges match a search.** Under the Badges
  search field, a line says how many badges are listed ("12 merit badges"), or
  "No merit badges match your search." Screen readers hear it from a polite
  live region. Android 16 deprecated `announceForAccessibility`, and its
  [behavior changes](https://developer.android.com/about/versions/16/behavior-changes-all)
  point to live regions instead, "used sparingly".
  - **The live region stays composed while the search field is shown, and
    only its text changes.** Compose announces a live region only when a node
    that already exists changes (`sendSemanticsPropertyChangeEvents` in
    `AndroidComposeViewAccessibilityDelegateCompat`, Compose UI 1.12.1). A new
    node isn't announced, so the count isn't read out when the screen first
    shows it. The load-failed message and Onboarding's save-failed message
    have the same gap
    ([#69](https://github.com/bryancassell/bluecard/issues/69)), which this
    approach could also close.
  - **Screen readers hear a new count once the scout stops typing for a
    second**, while the line and the list change on every keystroke. With
    TalkBack 17 on Android 17, the count's announcement reached TalkBack just
    before the echo of the key typed, and TalkBack doesn't let new speech cut
    off a polite live region. A count announced on every keystroke was spoken
    first and held back the echo by 1 to 4 seconds. Waiting for the pause
    keeps the echo first. A key typed while the count is being spoken still
    waits for it.
  - **The live region is kept apart from the count the line shows.** TalkBack
    announces a live region whenever it changes at all, even just its size, so
    a live region around the shown count read out the old count as soon as the
    shown one changed. The line has two texts: the shown count, hidden from
    accessibility services, and the live region, laid out from the announced
    count alone and not drawn.
  - **Only a change is announced.** A keystroke that leaves the count the same
    says nothing more. After "Clear search", the full count is announced
    straight away, before TalkBack reads the search field, where its focus
    moves whether or not the field had input focus. After a pause, the count
    would come just as the scout starts a new search. Deleting the search a key
    at a time still waits for the pause.
- **Text fields are state-based.** A text field edits a `TextFieldState` that
  its screen's ViewModel holds. The
  [text field guide](https://developer.android.com/develop/ui/compose/text/user-input)
  recommends state-based fields over `value` and `onValueChange`, which invite
  async updates, and encourages keeping `TextFieldState` in ViewModels. The
  ViewModel creates the state with `SavedStateHandle.textFieldState`
  (`ui/TextFieldSavedState.kt`), which restores the text and keeps it with a
  [saved state provider](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate#non-parcelable).
  Onboarding's name and unit number fields and Badges search work this way.
  Fields that start as stored text, the requirement comment and the counselor's
  fields, use `StoredTextFields` instead (below), and a tracker row's fields
  work like it. The provider reads the text each time the system
  saves state, so the text survives the system stopping the app even if it
  changed while nothing collected the screen's UI state. Navigation 3 saves a
  screen's state once when it leaves the display, and not again while it's in
  the back stack, so a change made after that isn't kept. (Copying the text
  into `SavedStateHandle` had the same limit.) `restoredText` ignores any value
  under its key that isn't the kind of `Bundle` it keeps, as a backstop for the
  extras of the intent that opened the app (see Navigation). `StoredTextFields`
  keeps a page's fields only once the stored text has loaded into them
  (`loadOnce`), so if the system stops the app before then, the page loads the
  stored text again instead of restoring empty fields. It keeps and restores
  the fields together. The Tracker entry page does the same for a row's fields
  itself, since which fields it has depends on the catalog, which loads with
  the row. Its date columns are text fields too, holding the date as
  `YYYY-MM-DD` as it's stored, so they're kept the same way; the page shows them
  as dates with a date picker. `StoredTextFields` writes the stored text in a
  snapshot of its own (`Snapshot.withMutableSnapshot`): otherwise `snapshotFlow`
  only sees the change when Compose next applies changes made outside a
  snapshot, which it does once a frame. Saved state has a size limit, so every
  text field has a length limit (`TextLengthLimit`): 100 characters for
  Onboarding's name, Badges search and the counselor's name, 20 for the unit
  number and a tracker row's number columns, 50 for the counselor's phone, 254
  for their email, 500 for a tracker row's text columns and 2,000 for the
  requirement comment. It keeps as much of an edit, such as a long paste, as
  fits, and never cuts the text already in the field or splits an emoji. A
  single-line field keeps a pasted line break in its text but doesn't show one,
  so the single-line text fields (Onboarding's, Badges search, the counselor's
  and a tracker row's text columns, whose values its requirement's page shows on
  one line) also replace each line break with a space (`LineBreaksAsSpaces`); a
  number field rejects one. That runs after the length limit, so the limit cuts
  a huge paste before it's scanned.
- **Navigation uses [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)**,
  which the recommendations page names for single-activity apps with more than
  one screen. Each destination is a `@Serializable` key, and ViewModels are
  scoped to back stack entries with `lifecycle-viewmodel-navigation3`.
  - **Screens' `SavedStateHandle`s don't start with the launching intent's
    extras.** `ComponentActivity` gives them to ViewModels as default
    arguments, and Navigation 3 passes the activity's defaults on to every
    screen. `MainActivity` is exported, so any app could fill a screen's saved
    state, such as the requirement comment, with an extra of the same name.
    BlueCard uses neither intent extras nor default arguments, so `MainActivity`
    overrides `defaultViewModelCreationExtras` to leave the default arguments
    empty ([#83](https://github.com/bryancassell/bluecard/issues/83)). That
    covers every ViewModel created with the activity's creation extras, as
    Hilt and Navigation 3 create them, including those scoped to the activity.
    The activity's default factory still passes the extras to a ViewModel
    created without creation extras. BlueCard creates none that way, and
    overriding the factory would replace Hilt's.
- **Pages slide the full width of their area, side by side**
  (`ui/navigation/PageTransitions.kt`), as Navigation 3's
  [animation guide](https://developer.android.com/guide/navigation/navigation-3/animate-destinations)
  shows. Navigation 3's defaults are a 700 ms crossfade, and a back gesture
  that shrinks the page to 70%
  ([#104](https://github.com/bryancassell/bluecard/issues/104)).
  - **Opening a page:** it slides in from the end, and the page it leaves
    slides out toward the start.
  - **Back, and a released back swipe from either edge:** the closing page
    slides out toward the end, and the page returned to follows it in from the
    start. The pages never overlap, so neither fades. Back while a page is
    still sliding in plays its opening slide backwards from where it is, as
    `NavDisplay` does for a cancelled back swipe, so the pages stay side by
    side.
  - **Slides take 375 ms with `FastOutSlowInEasing`,** the easing `tween`
    uses by default, and what Material's first
    [duration guidance](https://m1.material.io/motion/duration-easing.html)
    calls the standard curve. That guidance says "Large, complex, full-screen
    transitions may have longer durations, occurring over 375ms", and
    "Transitions that exceed 400ms may feel too slow." The platform's activity
    slides take 450 ms with Material's emphasized easing
    (`fast_out_extra_slow_in`), which moves a full-width slide 90% of the way
    in its first 170 ms, at up to 9dp per millisecond (77dp a frame at
    120 Hz). On a Pixel 9, opening a page felt too fast, and a dropped frame
    showed as a jump. BlueCard's slide gets 90% of the way in 237 ms, and
    peaks at about 3dp per millisecond.
  - **A back swipe doesn't move the pages; releasing it plays Back's slide.**
    The navigation root calls the `NavDisplay` overload that takes a
    `SceneState` and a `NavigationEventState`, which registers no back handler,
    and nothing reports a back swipe to that state. The root's own
    `BackHandler` handles Back instead:
    - **It's the only back handler at the root,** added before the screens.
      The navigationevent library gives Back to the enabled handler added
      last, so a handler a screen adds goes first, even when the screen is
      composed along with the root, as after rotation.
    - **It's off on Home and Onboarding,** so the system's back-to-home
      animation plays there.
    - **It checks the back stack as it is,** because its enabled state only
      updates in the next frame. Two Backs before then would otherwise empty the
      back stack and crash `NavDisplay`.

    This gives up the peek at the page underneath that predictive back offers.
    When the pages followed the finger, `NavDisplay` finished a released swipe
    with a tween from rest, ignoring the finger's speed, and on a Pixel 9 that
    felt slow beside opening a page.
  - **The slides mirror in a right-to-left layout** (`SlideDirection.Start`
    and `End`).
  - **Pages are clipped to their area** (`clipToBounds` on `NavDisplay`).
    `NavDisplay` doesn't clip its `AnimatedContent`, so a sliding page would
    draw under a navigation bar or cutout at the side, as in landscape.
  - **This isn't what Material 3 advises.** Its
    [transition patterns](https://m3.material.io/styles/motion/transitions/transition-patterns)
    say "Both Android and iOS should use platform defaults for forward and
    backward navigation" between "screens at consecutive levels of
    hierarchy", and "Don't use a Lateral transition for navigating
    hierarchical screens. Sliding content the full width of the screen is
    excessive for a high frequency transition." BlueCard first played the
    platform's activity slides, which move 96dp and fade. On a Pixel 9,
    opening a page felt too short, the back swipe's fade looked bad, and a
    swipe from the right edge that didn't slide the page looked broken.
- **A double tap opens a screen once, and doesn't press anything on it.**
  - **Screens navigate with `rememberNavigateFrom`** (`ui/navigation/`), which
    ignores a tap unless the tapping screen is on top of the screens
    `NavDisplay` is showing. So a second tap that reaches the screen it came
    from, in the same frame as the first or as a screen reader's click, can't
    open a screen twice.
  - **Screens ignore touches while they animate**
    (`rememberIgnoreTouchesNavEntryDecorator`). `NavDisplay` draws both screens
    during a transition: side by side during their 375 ms slide, or one over
    the other during a crossfade. A cover over each animating screen takes
    touches:
    - **on a screen animating out, until it's gone.** Otherwise a tap on the
      part still on screen could press its controls. After Back, the screen
      returned to takes taps where the closing screen has slid away once its
      double-tap timeout ends, before the 375 ms slide does.
    - **on a screen animating in, for the double-tap timeout**
      (`ViewConfiguration.doubleTapTimeoutMillis`, 300 ms). Otherwise the
      second tap of a double tap would press whatever is under the finger on
      the new screen, such as "Official requirements", which opens the browser
      ([#61](https://github.com/bryancassell/bluecard/issues/61)). The first
      screen appears without a transition and takes touches straight away.
    - A touch that starts on a cover stays with it until the finger lifts, so a
      swipe that starts then doesn't scroll.
  - **Taps on a new screen work after the timeout, even while it's still
    sliding in.** [#56](https://github.com/bryancassell/bluecard/pull/56)
    dropped `dropUnlessResumed` because it ignored taps for the whole
    animation. Taps further apart than the timeout aren't a double tap to the
    platform either.
  - **A screen still animating out can't be opened again** (`DrawnScreens`).
    `NavDisplay` keeps a screen's state, ViewModel included, until it's out of
    both the back stack and composition, so the same key pushed again during
    its slide out would bring it back as it was: a Tracker entry page that closed
    after a save would reopen closed, with Save disabled. After Back, taps reach
    the screen returned to where the closing one has slid away, and a screen
    reader's clicks reach it straight away, before the closing screen leaves
    composition. So a decorator records the
    content keys of the screens `NavDisplay` draws, and `rememberNavigateFrom`
    ignores opening one of them: tapping the row that opened the closing
    screen does nothing until its slide ends.
- **A double tap starts another app once.** Badge detail's "Official
  requirements" link and the counselor's phone and email start other apps with
  one function from `rememberStartOtherApp` (`ui/`), which the screen shares
  among them. The other app takes a moment to cover BlueCard, so both taps of a
  double tap can reach the control, and a browser could open two tabs, or an
  email app two drafts
  ([#97](https://github.com/bryancassell/bluecard/issues/97)).
  - **After a tap, all three ignore taps for the double-tap timeout** (300 ms),
    as a screen animating in does. A tap that finds no app counts too, so a
    double tap shows its message once, and the next tap tries again.
  - **It doesn't wait for the scout to come back from the other app.** Some
    starts never take BlueCard's place, such as one that screen pinning blocks,
    which doesn't throw, so waiting for BlueCard's window to get focus back, or
    for `ON_RESUME`, could leave the controls locked. It would also misfire in
    desktop windows, where the tap that focuses BlueCard's window can arrive
    before the focus does. A tap more than 300 ms after the first can still
    reach BlueCard if the other app hasn't covered it yet. On an Android 37
    emulator, Android dropped most second taps that came 140–200 ms after the
    first, as the other app took over, but not all of them.
  - **The link starts the browser with `ACTION_VIEW`**, as Compose's
    `UriHandler` does, so all three handle "no app" the same way.
- **Launch:** Home is the fixed start destination. Until a profile is saved, the
  navigation root shows Onboarding in place of the back stack, because the
  [navigation principles](https://developer.android.com/guide/navigation/principles#fixed_start_destination)
  say one-time setup screens "should not be considered start destinations".
  When a profile is saved, or goes missing, Onboarding and the back stack
  crossfade, as `NavDisplay` does by default (700 ms), rather than slide:
  neither is a page of the other. The navigation root tells this apart from
  opening a page by the bottom of the back stack changing.
  The splash screen stays up until the saved profile loads, using
  [core-splashscreen](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate)'s
  `setKeepOnScreenCondition`, so the wrong screen never flashes first. The
  [splash screen guide](https://developer.android.com/develop/ui/views/launch/splash-screen)
  suggests holding the first frame for loading "a small amount of data, such as
  loading in-app settings from a local disk".
- **Text inside a string follows the strings' language, not the device's.**
  `strings_language` in `strings.xml` names the language of the strings the app
  shows, which differs from the device's when the app has no strings for it.
  Lists inside a string (`rememberBadgeNameListFormatter`), numbers and dates
  (`rememberCompletionDateFormatter`) are
  formatted in that language, and plurals follow its rules, so a sentence never
  mixes two languages: on a Persian phone, English strings read "Do 2 of 3", not
  "Do ۲ of ۳", like catalog numbers such as "4c(1)" in the same row.
  `StringsLanguageTagTest` checks that each `strings.xml` names its own language
  with a well-formed tag: a wrong tag, such as "en" left in a translation, would
  replace the whole translation with English.
- **Screens are laid out in the strings' language's direction, not the
  device's** ([#66](https://github.com/bryancassell/bluecard/issues/66)). On a
  Persian or Arabic phone, the English strings are laid out left-to-right as on
  an English phone: a requirement's number comes before its text, and a
  sentence's final period stays at its end. Mirrored, they would read as broken
  English.
  - **`MainActivity` sets it** in `attachBaseContext`, with an override
    configuration in the direction of the strings' first locale
    (`stringsLocale`). Setting it on the activity, not only in Compose, keeps
    its views, Compose (which follows its view), keyboard and D-pad focus,
    `LocalConfiguration` and the activity's resources in step.
  - **The manifest keeps `android:supportsRtl="true"`,** so a right-to-left
    translation would be laid out right-to-left with no other change.
  - **Text the scout types keeps its own direction** (`ui/TypedText.kt`), so a
    name typed in Persian reads right-to-left with its final period at its end.
    Compose would otherwise give it the layout's direction and put the period at
    its start. Fields the scout types in use `typedTextFieldStyle()`, whose text
    direction follows the content. Typed text shown on screen, alone or inside
    one of the app's strings (the name and "Unit: %1$s" on Home), goes through
    `typedText()`, which wraps it with
    [`BidiFormatter.unicodeWrap`](https://developer.android.com/training/basics/supporting-devices/languages#FormatText).
    Code outside Compose, such as the PDF report, must wrap it the same way.
- **`BlueCardApp` provides `LocalResources` in the strings' language**
  (`ProvideStringsLanguageResources`), so every `stringResource` and
  `pluralStringResource` follows it, with nothing to remember at each call.
  - **Locales** (`stringsLocales`): first, the device's first locale in the
    strings' language and script, which keeps the device's region and digit
    choice, or else the strings' language itself. Then the device's other
    locales, so Android can fall back to them when the app has no strings in the
    first, such as for a pseudo-locale. When these are the device's own locales,
    as on an English phone, the device's resources are used unchanged.
  - **Only the locales change.** The resources keep the configuration's layout
    direction, which `MainActivity` sets, so direction-specific resources, such
    as `drawable-ldrtl`, match the layout.
  - **Labels that follow the device:** those the app doesn't read through
    `LocalResources`, such as the text selection toolbar (Cut, Copy, Paste),
    Material 3's labels and the role and state names TalkBack reads. Material 3's
    date picker formats its dates in the device's language too: in the version
    the app uses (1.4.0), `rememberDatePickerState` takes no locale. Compose
    Foundation's right-click menu does read `LocalResources`, so it follows the
    strings' language. These labels are laid out in the activity's direction,
    so on a Persian phone the toolbar lists its Persian labels left-to-right,
    except the date picker: `CompletionDatePickerDialog` lays it out in the
    device's language's direction, because a Persian date laid out
    left-to-right reads out of order.
  - **Code outside Compose** that formats a string with a number, such as the
    PDF report, must use the same locales (`stringsLocales`), and lay out in the
    first one's direction.
- **Material 3** components, themed by `BlueCardTheme` with the blue card's
  colors. Every phone shows the same colors:
  - **No dynamic color.** The app doesn't take its colors from the wallpaper.
  - **Light only, for now.** The one scheme, `BlueCardColorScheme` in
    `ui/theme/Color.kt`, is used in dark mode too, until the app has a dark
    scheme (#108). The window and splash screen backgrounds match it, and the
    system bar icons stay dark. In dark mode the window theme isn't declared
    light (`isLightTheme`), which opts out of Android's force dark and force
    invert, so the system doesn't darken the colors either.
    - **Known gap.** On Android 8.0 the splash screen's navigation bar icons
      stay white on its light background until the app draws, because dark
      navigation bar icons in a theme need Android 8.1.
    - **Don't follow dark mode elsewhere.** `isSystemInDarkTheme()` still
      reports the system's dark mode, and `-night` resources still apply in
      it. Until #108, nothing but the window theme above should use either:
      it would put dark-mode colors, images or bar icons on the light app.
  - **Source.** Material Color Utilities' fidelity scheme from Scouting
    America Blue (`#003F87`), which stays `primary`. The background and
    surfaces are light tints of the card stock's Pale Blue (`#9AB3D5`). Errors
    use Material's default red, because Scouting America Red (`#CE1126`) is
    below 4.5:1 on the darker surfaces.
  - **Readability first.** Readability comes before matching the card's colors
    exactly. Every text color meets WCAG AA (4.5:1) on every surface, and
    `BlueCardColorSchemeTest` checks each pair.

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
- **Writes outlive the screen.** `RoomProgressRepository` runs each write in an
  app-lifetime scope and waits for it, so leaving a screen cancels only the
  wait, not the write. That's the pattern in the data layer guide's
  [Make an operation live longer than the screen](https://developer.android.com/topic/architecture/data-layer#make_an_operation_live_longer_than_the_screen).
  Writes take a first-come, first-served lock, so they happen in the order
  they're made. A storage failure after the scout has left the screen goes
  unreported, but a bug still crashes the app.
- **Stored data that can't be read or saved is reported as an `IOException`.**
  The
  [data layer guide](https://developer.android.com/topic/architecture/data-layer)
  ("Expose errors") says the data layer can expose errors "using custom
  exceptions". DataStore and the asset manager already throw `IOException`, and
  `RoomProgressRepository` wraps Room's `SQLiteException` in one, for its reads
  and its writes. ViewModels then
  catch it without depending on a storage API (req. 9). Errors that can only
  come from a mistake in the app's code aren't wrapped, so they still crash as
  bugs. SQLite's [result codes](https://www.sqlite.org/rescode.html) say that of
  misuse of its interface ("the application is incorrectly coded"). This app
  also counts a constraint violation, an out-of-range parameter or column
  number and a datatype mismatch as bugs, because each write checks what it
  needs first, such as that the badge is started. A
  corrupted database is the exception: the SQLite library deletes it. If a read
  finds the corruption, the scout sees the load-failed message, and the app
  reopens with no progress. If opening the database finds it, the progress is
  gone without any message
  ([#70](https://github.com/bryancassell/bluecard/issues/70)).

### Dependency injection

[Hilt](https://developer.android.com/training/dependency-injection/hilt-android),
using constructor injection. The
[recommendations page](https://developer.android.com/topic/architecture/recommendations)
says to use Hilt once an app has "multiple screens with ViewModels" or
ViewModels scoped to the navigation back stack; BlueCard will have both. Hilt
modules bind each repository interface to its implementation and provide the
Room database, DataStore, a coroutine dispatcher, an app-lifetime
`CoroutineScope` (`@ApplicationScope`) and a `Clock` for today's date (the
dispatcher and clock injected so tests can replace them). The clock reads the
device's time zone each time it's read, not once when it's made, so after a
time zone change, a page that's already open records dates in the new zone
([#79](https://github.com/bryancassell/bluecard/issues/79)). A "today" held in
a page's UI state, such as the date picker's latest date, catches up the next
time that state updates.

### Package layout

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
│   └── backup/         BackupRepository (export/import format)
└── di/                 Hilt modules
```

## Screens

| Screen | PRD journey |
|---|---|
| **Onboarding** | First launch: ask for name and unit number. Shown until the profile is saved. |
| **Home** | Name, unit, and a progress summary: how many badges are completed and in progress, and Eagle-required progress. Below the summary, each badge in progress, in the same row as on Badges, opening its Badge detail. Links to Badges and Data management. |
| **Badges** | Browse all current badges and search by name or description. One screen: the list filters as the scout types. |
| **Badge detail** | Summary, Eagle-required flag, link to the official page, counselor details (tapping the phone or email opens the phone or email app), requirement list with completion state, each opening the requirement's page, "mark completed on a prior date", and "generate report" once complete. |
| **Requirement detail** | Every requirement's own page: whether it's complete, with a checkbox and completion date for one the scout marks complete by hand, its sub-requirements with their completion state, its tracker's rows, and the scout's comment. |
| **Tracker entry** | One row of a requirement's tracker, to fill in, change or delete: a field for each of the tracker's columns. |
| **Edit counselor** | The badge's merit badge counselor: name, phone and email, each optional. Opened from Badge detail; closes once saved. |
| **Data management** | Clear all progress, export, import. Clearing a single badge or a single requirement's progress lives on the badge and requirement screens. |

The PRD asks that requirements be understandable "without extensive
scrolling", so the badge detail page lists only the top-level requirements. Each
is one row: its official number, our one-line summary, "Do N of M" when only
some of its sub-requirements are needed, and a check once it's complete. A
requirement with a tracker says how much of it is filled in, such as "8 of 12
weeks". Every row opens the requirement's own page, with its completion date and
comment. The scout marks a requirement complete there by hand, unless it has
sub-requirements or a tracker with a fixed number of rows. One with
sub-requirements is complete once enough of them are, and lists them on its page
the same way; each opens its own page in turn. A requirement with a tracker
lists its rows there, and each opens the Tracker entry page (see
[Key flows](#key-flows)). One with a fixed number of rows is complete once every
row is filled in. A page whose requirement completes this way has no checkbox or
date field, and says "Completed" once it's done. Each page shows one level of the
requirement tree.
Both pages show the requirements version the badge was started on, or the newest
version for a badge the scout hasn't started. The counselor takes a few lines
between the official link and the requirements, and is entered on a page of its
own.

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
  - `TrackerEntry`: an ID, badge ID, requirement number, the row it fills in a
    tracker with a fixed number of rows (null in a log), and the row's values
    keyed by the catalog's column IDs, all stored as text (a date as
    `YYYY-MM-DD`). A row of a fixed-row tracker has at most one entry, which a
    unique index enforces. IDs only grow (`AUTOINCREMENT`), so a new entry's is
    higher than any before it, even a deleted one's. Each entry records the date
    it was first saved (`addedDate`), which changing it keeps. An entry saved
    before database version 3 has none until it's next changed, which records
    that day.

**Completion is derived, not stored**
(`data/progress/Completion.kt`), from requirement progress, tracker entries and
the catalog:

- A requirement with children is complete when all of them are, or its "N of
  these" count is, even if it also has a tracker. One without children but with
  a tracker with a fixed number of rows is complete when every row has an entry (`filledRows`, which the tracker's "8 of 12
  weeks" count uses too). Any other requirement, including one with a log, is
  complete when the scout marked it complete (`isMarkedByHand`). A `completed`
  mark or date stored for a requirement that isn't marked by hand doesn't
  count.
- A badge is complete when all its top-level requirements are, or when it was
  marked completed on a prior date.
- The completion date is when the last requirement it needed was completed. It
  is the prior date for a badge marked that way. It is unknown if a needed
  requirement has no date.
- A fixed-row tracker's requirement is completed on the latest date one of its
  rows was first saved (`TrackerEntry.addedDate`), and has no date if a row
  has none. The tracker's own date column isn't used, because not every
  tracker has one.

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
  the requirement. It also lists the badges in progress, so the scout can get
  back to one without searching Badges: alphabetically, in the row Badges uses
  (`ui/badges/BadgeRow.kt`), and all of them, since a scout rarely has more than
  a few going at once. Screen readers hear the rows as a list, as on Badges.
  Progress on a badge that isn't in the catalog isn't counted or listed.
- **Browse and search.** The Badges ViewModel combines the catalog, the
  scout's progress and the search text, to list the matching badges with each
  one's status from `data/progress/BadgeStatus.kt`. A badge matches when every
  word of the search starts a word in its name or summary, in any order and
  ignoring case (`ui/badges/BadgeSearch.kt`). Words are runs of letters and
  digits, in the search and in badge text alike, so spaces and punctuation only
  separate them; a search with no words lists every badge. The list stays
  alphabetical, and a new set of matches is shown from the top. The search is
  capped at 100 characters, because it's saved with the screen's state. A line
  above the list says how many badges match, for screen readers to announce
  (see [UI layer](#ui-layer)).
- **Recording progress.** Badge and requirement screens call `ProgressRepository`
  functions (set completed date, set comment, add tracker row, set counselor,
  mark badge completed on a date); the screens observe progress as a `Flow`, so
  they update as soon as data is saved.
  - **Recording anything starts the badge**, on the requirements version its
    pages show until then (the newest), dated today (`ui/badge/ProgressRecorder.kt`).
    There's no separate "start" step. `markRequirementCompleted`,
    `setRequirementComment`, `addTrackerEntry` and `setCounselor` take a
    `BadgeStart`, and `ProgressRepository` starts the badge in the same transaction as the write, so a save that fails doesn't
    leave the badge started. Other functions that record progress should take one
    when a screen first calls them. Undoing everything recorded leaves the badge
    started, so it stays In progress until the scout clears it
    ([#45](https://github.com/bryancassell/bluecard/issues/45)).
  - **Completing a requirement.** Only a requirement without sub-requirements or
    a fixed-row tracker is marked complete; one with sub-requirements is complete
    when enough of them are, and one with a fixed-row tracker when every row is
    filled in (`Completion.kt`). Checking it on its page records it completed today, and
    the scout can pick another date there or remove it. Dates after today can't
    be picked. Unchecking removes the date and keeps the comment, but the page
    remembers the date until it closes: checking the requirement again on that
    page brings the date back, so a mistaken tap loses nothing. A date change
    applies only to a completed requirement, so one that lands just after an
    uncheck can't complete it again.
  - **Comments.** Every requirement can have one, including one with
    sub-requirements, for notes about it as a whole. The page's comment field is
    saved when the scout taps Save, which is enabled once the field differs from
    the saved comment. The repository trims spaces around it
    (`normalizedText`), and an empty comment removes it. The field takes up
    to 2,000 characters; the repository doesn't limit the length. An unsaved edit survives the system
    stopping the app, but leaving the page discards it.
  - **Trackers.** The catalog defines each tracker's columns (date, number or
    text), what a row is called ("week", "weeks"), and optionally a fixed
    number of rows. A log, a tracker without a fixed number, lists its entries
    in the order they were added, numbered from 1 ("Session 3"), with a button
    to add one. A fixed-row tracker lists every row ("Week 1" to "Week 12"),
    filled in or not. Either kind opens a row on the Tracker entry page, a form
    with a field for each column: a date with a date picker (no dates after
    today, as for completion dates), a number field that takes only digits and
    one decimal separator (a separator without a digit isn't saved), or a
    one-line text field of up to 500 characters. A row is opened with its
    entry's ID, if it has one, and its number, and the page goes by whichever
    its tracker uses: the entry in a log, the number in a fixed-row tracker.
    Save is enabled once the fields differ from what's saved and aren't all
    empty; a row with nothing in it is deleted instead. The page's ViewModel
    checks that too, as the button can lag the fields by a frame. Saving keeps
    values for columns the tracker doesn't have, though a shipped tracker never
    loses one (`docs/catalog.md`). It's a single write (`addTrackerEntry` with
    the row's entry ID), which adds the entry again if it was deleted since the
    page opened. The page reads what's
    saved once, when it opens, so being shown again after a while doesn't
    reload it. It closes once a save or delete is done (`done` in its UI
    state), so a save that fails keeps the page open with the scout's edit. The
    system can stop the app after a save but before the page closes, as when
    the scout leaves the app while saving; a new log entry's page restored then
    closes if the log has an entry newer than the page, rather than offering to
    add it again. Delete asks first, and can't be used while a save is under
    way. Leaving the page discards an unsaved edit, as with a comment.
    `addTrackerEntry` gives a fixed-row tracker's row that already has an entry
    the new values instead of adding a second one. The repository trims spaces
    around each value and drops blank ones (`normalizedTrackerValues`). A
    fixed-row tracker completes its requirement once every row is filled in, and
    deleting a row makes it incomplete again, as unchecking a sub-requirement
    does to its parent. A log doesn't, because the catalog doesn't say how many
    entries it needs: the scout marks its requirement complete, as one without a
    tracker. An added row records the date it was first saved, which the Tracker
    entry page passes to `addTrackerEntry` from its clock; changing the row
    keeps that date. A row saved before database version 3 has no date, so the
    first change records that day as its date.
  - **Counselor.** Badge detail shows the counselor's name, phone and email,
    with a button to add or edit them. Tapping the phone opens the phone app
    with the number filled in (`ACTION_DIAL`, which needs no permission), and
    tapping the email opens an email app (`ACTION_SENDTO` with a `mailto:`
    address). If no app can, as on a tablet without a phone app, a message
    says so. The fields are edited on Edit counselor and saved when the scout
    taps Save, which is enabled once they differ from the saved counselor. The
    repository trims spaces around each field (`Counselor.normalized`) and
    drops empty ones; with none left, the counselor is removed. There's no
    format check: a phone number or email address that's wrong opens its app
    with what the scout typed. Each field is one line, and a line break pasted
    into one becomes a space (`LineBreaksAsSpaces`, see UI layer).
    Once the save succeeds, the ViewModel sets
    `saved` in the UI state and the screen closes itself
    (`closeIfOnTop`, which does nothing if the scout has already gone back),
    following the UI layer guide's
    [example](https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events)
    of navigating from UI state. The screen then tells the ViewModel it has
    closed (`onClosed`), which clears `saved`, so the page works again if it's
    opened before its ViewModel is cleared. The page stays open if a field
    changed while it saved, so the change isn't lost. A save that fails keeps the page open with its
    fields and the snackbar, so the scout can try again. Leaving the page
    without saving discards the edits, as with a comment.
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
  They check each UI state and each event. They run with Robolectric, because
  their load- and save-failure tests reach `android.util.Log`, whose methods
  throw in plain local tests. The alternative, `returnDefaultValues`, makes every Android method
  return null or zero instead; the
  [local tests guide](https://developer.android.com/training/testing/local-tests)
  says it "might allow failing tests to pass" and adds: "Only use it as a last
  resort."
- **Saved state in tests.** Tests that a ViewModel keeps state when the system
  stops the app use `ViewModelScenario` from `lifecycle-viewmodel-testing`. Its
  `recreate()` saves state, passes it through a `Parcel` and restores it into a
  new ViewModel. Handing a second ViewModel the same `SavedStateHandle` doesn't
  run saved state providers, so it can't test them. `scenario.viewModel`
  creates the ViewModel when first read, so a test reads it before
  `recreate()`. `recreate()` doesn't clear the ViewModel it replaces, so that
  ViewModel's coroutines keep running and `onCleared` isn't called.
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
- **Migration tests** (`MigrationTest`) run locally too, with Room's
  `MigrationTestHelper`. It reads each version's schema from assets, and the
  Room Gradle plugin adds `app/schemas/` to instrumented tests' assets only.
  Robolectric reads the debug build's assets, so debug builds carry the
  schemas as assets; release builds don't.
- **Contract tests keep fakes honest.** A repository's behavior is written once
  as an abstract test class (for example `ProgressRepositoryContract`). The real
  implementation's test and the fake's test both extend it, so the fake used by
  other features' tests behaves like the real repository.
- **Load failures in tests.** Each fake has a `failLoads` switch that makes its
  reads throw an `IOException`, for testing each screen's `LoadFailed` state. The
  profile and progress contract tests check that the real repositories throw one
  too, using a folder where the DataStore file or the Room database should be.
  The progress fake also has a `failSaves` switch for save failures, and the
  contract tests check that Room's writes throw an `IOException` the same way.
- **Compose UI tests** run locally with Robolectric, one test per UI state and
  interaction, fed by fake repositories or fixed UI state. A test that depends
  on how text is measured, such as whether a long label wraps, uses
  Robolectric's native graphics (`@GraphicsMode(NATIVE)`), since its default
  graphics measure every character as 1px wide.
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
| Page transitions | Full-width slides, side by side, as Navigation 3's animation guide shows, with `FastOutSlowInEasing` over 375 ms, as Material's first duration guidance gives for full-screen transitions; mirrored right-to-left; clipped to the pages' area; a back swipe from either edge doesn't move the pages, and plays Back's slide once released; Onboarding and the back stack crossfade when they replace each other | Navigation 3's defaults (a crossfade, and a 70% shrink on the gesture) looked strange. Material 3 advises the platform's 96dp slides, but on a Pixel 9 they felt too short, and a back swipe that faded or didn't slide looked bad. Emphasized easing made a full-width slide too fast, and a finished swipe too slow beside it |
| Persistence | Room 2.8 for progress; Preferences DataStore for the profile | DataStore guide's own criteria; Room 2.8 over Room 3 because BlueCard doesn't need Kotlin Multiplatform |
| Dependency injection | Hilt | Recommended once there are multiple screens with ViewModels |
| Catalog | Our own summaries in a bundled JSON file, linking to official pages; no official text or images | Scouting America's terms of use and trademarks |
| Requirement versions | Newest by default; the scout can pick the previous version when the catalog has one; a badge stays on its version until the scout switches it | Scouting America's advancement rules allow finishing on the previous requirements; keeps recorded progress matched to its requirements |
| Versions in the first release | Current requirements only; versions are kept from the first release on | Project decision for the initial app; keeping every version from then on protects existing users' recorded progress |
| PDF | Framework `PdfDocument` | `androidx.pdf` is a viewer, in beta, and needs API 28 |
| Save, share, export, import | System file picker, Sharesheet, FileProvider; JSON via kotlinx.serialization | No storage permissions needed; kotlinx.serialization JSON is stable and Kotlin's official library |
| Backup | Android Auto Backup on, with rules that include only the databases and DataStore directories; not limited to phones that can encrypt the backup | Scouts keep their records across phone changes; this is system backup, not app sync |
| Text fields | State-based (`TextFieldState`), held in the ViewModel; its text kept in `SavedStateHandle` by a saved state provider | The text field guide recommends state-based fields and holding their state in ViewModels. The provider reads the text only when the system saves state, so it keeps every change without anything collecting the screen's state. `SavedStateHandle.saveable` would too, but it's experimental |
| Badge search | Every word of the search must start a word in the badge's name or summary, in any order, ignoring case | Finds a badge from the start of any word ("fit" finds Personal Fitness) without matching inside words, so a short search like "art" isn't flooded with summaries that say "part" or "start" |
| Search result announcements | A visible count of the matches. Screen readers hear it from a polite live region that stays composed and is laid out apart from the shown count. It changes once typing pauses for a second, or straight away after Clear search | Android 16 deprecates announcements in favor of live regions. Compose announces only a node that already exists. TalkBack speaks a changed count ahead of the key the scout just typed, doesn't let it be cut off, and announces a live region on any change, even of its size |
| Badge completion | Derived from requirement progress and the catalog, never stored | Nothing to keep in sync when progress is edited or cleared |
| Marking requirements complete | A "Completed" checkbox on the requirement's page, dated today, with the date and comment beside it. Rows only open the requirement's page, with a check once it's complete | Chosen after using the app, in place of a checkbox on each row ([#103](https://github.com/bryancassell/bluecard/issues/103)). Each part takes a trip to its page, so marking many (Personal Fitness 3 has seven) is slower; the PRD's date and comment are optional |
| Completing a fixed-row tracker's requirement | Always inferred once every row is filled in, with no checkbox. Its date is when the last row was first saved. A log keeps the checkbox | A fixed-row tracker is a list of parts, like sub-requirements ([#105](https://github.com/bryancassell/bluecard/issues/105)). The tracker's date column isn't used, because Personal Management 2a and 2c have none. A log has no target in the catalog, such as Camping 9a's 20 nights |
| Unchecking a requirement | Removes its date, but the page remembers the date until it closes, and checking the requirement again there brings it back | A mistaken tap loses nothing, while stored progress stays simple: a requirement that isn't complete has no date |
| Starting a badge | Recording anything starts it; it stays started after everything is undone | No extra step before recording; clearing a badge is its own action ([#45](https://github.com/bryancassell/bluecard/issues/45)) |
| Trackers | Listed on the requirement's page; each row filled in on its own page with a field for each column, saved with a Save button. A fixed-row tracker shows every row, and a row keeps its number when another is deleted | Four or more fields don't fit in a dialog or a table row on a phone once the keyboard is up. Numbered rows match trackers such as a 13-week budget, where each week is its own row |
| Tracker row names | The catalog gives the singular and plural in lowercase ("week", "weeks"), and the app capitalizes the singular for titles | Counts read naturally ("8 of 12 weeks", "1 session") without the app pluralizing catalog text |
| Counselor details | Shown on Badge detail; edited on a page of their own with a Save button, which closes it once the save succeeds; the phone and email open the phone and email apps | Entered once and read often, so Badge detail stays short; closing only after a successful save shows a failure while the scout can still try again; `ACTION_DIAL` and `ACTION_SENDTO` need no permissions |
| Requirement comments | On every requirement, saved with a Save button | The scout decides when a comment is saved, and a save that fails is reported right then, not while they're still typing |
| Save failures | A snackbar from UI state; what's on screen keeps showing what's stored | The UI layer guide's pattern for messages from the ViewModel |
| Load failures | A screen that can't read stored data (`IOException`) shows a message in place of its content; any other exception crashes | The UI layer guide keeps errors in UI state. Crashes reach Android vitals, while caught exceptions would go unreported because the app has no crash reporting of its own; revisit with [#63](https://github.com/bryancassell/bluecard/issues/63) |
| Requirement IDs | A requirement's official number, unique within its requirements version | Less to author and easy to check against the official page; switching versions starts progress fresh, so IDs don't need to match across versions |
| Catalog authoring | The project writes every summary, in no particular order | All badges get covered eventually; order doesn't affect the design |
| Import | Replaces all current data, after a warning | Simplest correct behavior; merging is tracked in [#28](https://github.com/bryancassell/bluecard/issues/28) |
| Switching requirement versions | Resets the badge's requirement progress, after a confirmation; counselor details stay | Avoids attaching entries to the wrong requirement; carrying progress over is tracked in [#29](https://github.com/bryancassell/bluecard/issues/29) |
| Requirement changes | Shipped with regular app updates, with no urgency or monitoring process | The app doesn't need to reflect Scouting America's changes immediately. Scouts who already started may keep the previous requirements anyway. Until an update ships, a scout starting a changed badge sees the previous requirements in the app; the linked official page has the current ones |
| Links to scouting.org | Link each badge to its official page | The PRD asks for links. Scouting America's [terms of use](https://www.scouting.org/legal/terms-and-conditions/), read literally, restrict linking without permission; resolve before release ([#26](https://github.com/bryancassell/bluecard/issues/26)) |
| Official wording | Use Scouting America's terms, such as "Merit Badge" and "Eagle Scout", and official badge names | Clearest for scouts. These are Scouting America [trademarks](https://licensingbsa.org/trademarks/); resolve before release ([#27](https://github.com/bryancassell/bluecard/issues/27)) |
