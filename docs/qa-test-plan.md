# QA test plan

A test plan for a BlueCard release that Claude runs on its own. It sets up
emulators, works through every major user journey on every major device
variation, and files an issue for each bug it finds.

Run it before publishing a test release, as step 4 of
[Publishing a release](toolchain.md#publishing-a-release) asks. Also run it
after a change that only a release build would show broken, as listed in
[Checking a release build](toolchain.md#checking-a-release-build), such as
updating AGP or a library that uses reflection.

## Contents

- [Running the plan](#running-the-plan): what to ask, and what a run does
- [One-time setup](#one-time-setup): what a person installs once
- [Devices](#devices): the AVDs, the settings varied on them, and how to start them
- [Assignments](#assignments): which suites run on which device
- [Coordinator instructions](#coordinator-instructions): the main session's steps
- [Tester instructions](#tester-instructions): what each tester subagent follows
- [Test suites](#test-suites): the test cases
- [Filing issues](#filing-issues)
- [Keeping the plan current](#keeping-the-plan-current)

## Running the plan

Ask Claude, in a new session in this repository:

> Run the QA test plan in docs/qa-test-plan.md.

By default it tests a release build of what's checked out. To test something
else, say so:

- **A published test release:** "…against v0.2.0". The coordinator downloads
  that release's APK with `gh release download`.
- **A given APK,** such as one just signed with the release key while
  publishing: "…against app/build/outputs/bluecard-0.2.0.apk".
- **Only some assignments,** such as to check a fix: "…, only A3 and A6".

The run doesn't need anyone to watch it. The trial run in October 2026 took
about 3 hours, with up to three testers at a time, and about 1.6 million
tokens across its nine testers (90,000 to 250,000 each). Close Android Studio
first if you can (see
[Starting and stopping an emulator](#starting-and-stopping-an-emulator)).

The session that's asked is the **coordinator**. It builds or fetches the APK,
starts a fresh emulator for each [assignment](#assignments), and hands each
one to a **tester** subagent. A tester works through its suites on its own
emulator and reports back in text, so the screenshots and UI dumps it reads
stay out of the coordinator's context. The coordinator then merges the
testers' findings, files an issue for each new bug, and ends with a run report.

It tests the release build because R8 only shrinks the release build, and
that's what scouts install. Release builds aren't debuggable, so the plan
doesn't use `run-as` or anything else that needs a debug build.

## One-time setup

A person does this once per computer. Everything else the coordinator does
itself.

1. The machine setup in
   [One-time machine setup](toolchain.md#one-time-machine-setup-macos),
   including `gh auth login`.
2. **Android SDK Command-line Tools**, for `avdmanager` and the Android CLI
   (`android`), which installs system images: in
   Android Studio, **Settings → Languages & Frameworks → Android SDK → SDK
   Tools → Android SDK Command-line Tools (latest)**.

## Devices

### AVDs

The plan uses AVDs of its own, named `QA_*`, so a run never takes over an
emulator that another Claude session is using. Each is started with
`-read-only`, which keeps every change in temporary files that are deleted
when the emulator stops. So every assignment starts on a freshly set up phone,
and two assignments can run on the same AVD at once.

| AVD | Device profile | System image | Why |
|---|---|---|---|
| `QA_Phone_API37` | Pixel 10 (`pixel_10`), 1080×2424, gesture navigation | API 37, Google Play, 16 KB pages | The target SDK, on a typical phone. Most suites run here. |
| `QA_Small_API26` | Small Phone (`small_phone`), 720×1280 xhdpi, 360×640dp, 3-button navigation | API 26, Google APIs | The oldest Android BlueCard supports (`minSdk`), on the narrowest common screen. It has no TalkBack and no Chrome: links open in its WebView Browser Tester, and Gmail is its only app that takes a shared PDF. No dark mode or device language commands. |
| `QA_Tablet_API37` | Pixel Tablet (`pixel_tablet`), 2560×1600 | API 37, Google Play, 16 KB pages | A large screen. The app doesn't lock its orientation, so it shows its phone layout stretched across a tablet. |
| `QA_Foldable_API37` | Pixel 10 Pro Fold (`pixel_10_pro_fold`), 2076×2152 unfolded, 1080×2364 folded | API 37, Google Play, 16 KB pages | Folding and unfolding change the screen size while a page is open. |

### Creating the AVDs

The coordinator creates any AVD that's missing (`emulator -list-avds`).
Device IDs come from `avdmanager list device -c`. If one in the table is
gone, pick the closest profile, use it, and update the table in a pull
request.

```sh
export ANDROID_HOME=~/Library/Android/sdk
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
TOOLS=$ANDROID_HOME/cmdline-tools/latest/bin
"$TOOLS/android" sdk install system-images/android-37.2/google_apis_playstore_ps16k/arm64-v8a --no-metrics
"$TOOLS/android" sdk install system-images/android-26/google_apis/arm64-v8a --no-metrics
IMAGE_37="system-images;android-37.2;google_apis_playstore_ps16k;arm64-v8a"
IMAGE_26="system-images;android-26;google_apis;arm64-v8a"
echo no | "$TOOLS/avdmanager" create avd -n QA_Phone_API37 -k "$IMAGE_37" -d pixel_10
echo no | "$TOOLS/avdmanager" create avd -n QA_Small_API26 -k "$IMAGE_26" -d small_phone
echo no | "$TOOLS/avdmanager" create avd -n QA_Tablet_API37 -k "$IMAGE_37" -d pixel_tablet
echo no | "$TOOLS/avdmanager" create avd -n QA_Foldable_API37 -k "$IMAGE_37" -d pixel_10_pro_fold
```

`android sdk install` replaces `sdkmanager`, which says it's deprecated; it
names packages with `/` where `avdmanager` uses `;`. The Android CLI's own
`android emulator create` and `start` can't choose a system image, a name, a
port or read-only mode, so the plan uses `avdmanager` and the `emulator`
command. When Google ships a newer API 37 image, update both names.

### Starting and stopping an emulator

Each assignment gets its own emulator, on a console port from 5580 up (5580,
5582, 5584), which other sessions' emulators don't use (they take 5554 up).
Before using a port, check that `adb devices` doesn't list it.

```sh
PORT=5580
"$ANDROID_HOME/emulator/emulator" -avd QA_Phone_API37 -port $PORT -read-only \
    -no-snapshot -no-window -no-audio -no-boot-anim > "$RUN_DIR/emulator-$PORT.log" 2>&1 &
SERIAL=emulator-$PORT
adb -s $SERIAL wait-for-device
until [ "$(adb -s $SERIAL shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
# Shared storage is mounted a little after boot completes.
until adb -s $SERIAL shell ls /sdcard/Download > /dev/null 2>&1; do sleep 2; done
```

Stop it with `adb -s $SERIAL emu kill` once its tester has reported back.
Always pass `-s $SERIAL`: another session may have its own emulator running.

Android Studio, if it's open, sometimes restarts the adb server (seen in the
trial run in October 2026). Every emulator then drops off for a moment, and
some stay "offline" until `adb reconnect offline` is run. If an emulator stays
offline for more than a minute, run it, and tell that emulator's tester to
redo what was cut off. Closing Android Studio during a run avoids this.

### Settings varied on the devices

These are set over `adb` on a running emulator, not with more AVDs. A
read-only emulator forgets them when it stops.

| Variation | Turn on | Notes |
|---|---|---|
| Dark mode | `adb shell cmd uimode night yes` | API 29 and higher. |
| Largest text | `adb shell settings put system font_scale 2.0` | 2.0 is the largest in Settings on API 34 and higher; use 1.3 on API 26. |
| Largest display size | `adb shell wm density <width_px ÷ 2>`, such as 540 on a 1080-wide screen | Android's largest display size keeps the screen at least 320dp wide. Undo with `wm density reset`. |
| Landscape | `adb shell settings put system accelerometer_rotation 0` then `adb shell settings put system user_rotation 1` | `user_rotation 0` is portrait again. A tablet's natural orientation is landscape, so on it 0 is landscape. `ui.py launch` keeps the setting; if the screen turns back anyway, such as after a trip to the launcher, set it again. |
| Right-to-left device language | `adb shell cmd locale set-device-locale fa-IR` | Persian: right-to-left, with its own digits. BlueCard has no Persian strings, so it must stay in English, left to right, with Latin digits, in its dialogs and PDFs too ([ARCHITECTURE.md: Language and layout direction](../ARCHITECTURE.md#language-and-layout-direction)). System screens, such as the file picker, change. So does the date picker, by design: it shows Material 3's labels and dates in the device's language and direction (`CompletionDatePickerDialog`), while its OK and Cancel stay English. Undo with `en-US`. API 34 and higher. |
| TalkBack | `scripts/qa/ui.py $SERIAL talkback on` | See [TalkBack](#talkback). |
| Folded | `adb shell cmd device_state state 0` | Foldable only: 0 is closed, on the outer screen. `cmd device_state state reset` unfolds it again. |

## Assignments

Each assignment is one tester, on one fresh emulator. Suites marked
"smoke" run only their cases tagged **[smoke]**.

| ID | AVD | Settings | Suites |
|---|---|---|---|
| A1 | `QA_Phone_API37` | Default | [ONB](#onb-onboarding-and-profile), [HOME](#home-home), [FIND](#find-browse-and-search), [BADGE](#badge-badge-page-and-counselor), [REQ](#req-recording-requirements), [TRK](#trk-trackers) |
| A2 | `QA_Phone_API37` | Default | [DONE](#done-completing-a-badge-and-reports), [RANK](#rank-ranks), [CLR](#clr-clearing), [DATA](#data-export-and-import), [NAV](#nav-navigation-and-restoring-state) |
| A3 | `QA_Small_API26` | Default, then largest text (1.3) | Every suite as smoke, then [WALK](#walk-every-screen) at default and at largest text |
| A4 | `QA_Tablet_API37` | Landscape, then portrait | [WALK](#walk-every-screen) in each orientation, NAV-4 (rotation), then ONB, FIND, DONE and DATA as smoke, in landscape |
| A5 | `QA_Foldable_API37` | Unfolded, then folded | [WALK](#walk-every-screen) unfolded, NAV-5 (folding), then FIND, REQ and TRK as smoke, folded |
| A6 | `QA_Phone_API37` | Dark mode, then right-to-left language | [WALK](#walk-every-screen) in each, then FIND-1, FIND-2 and DONE-3 in the right-to-left language |
| A7 | `QA_Phone_API37` | Largest text and display size, then landscape | [WALK](#walk-every-screen) in each, then TRK-1 and REQ-4 at the largest text |
| A8 | `QA_Phone_API37` | TalkBack | [A11Y](#a11y-talkback) |
| A9 | `QA_Phone_API37` | Default | [REL](#rel-release-and-data-safety) |

Run A1, A2 and A3 first: they cover the most and the oldest Android, and a
blocking bug found there is worth knowing before the rest.

## Coordinator instructions

The coordinator is the session asked to run the plan. It doesn't drive the
app itself: testers do, so the coordinator's context stays small enough for
triage.

### 1. Prepare

1. **Run directory.** `RUN_DIR=build/qa/$(date +%Y-%m-%d-%H%M)`, made with
   `mkdir -p`. `build/` is ignored by git. Testers save screenshots there.
2. **Tools.** Export `ANDROID_HOME` and `JAVA_HOME` as in
   [Creating the AVDs](#creating-the-avds), and put
   `$ANDROID_HOME/platform-tools` on `PATH`, in each command: the Bash tool
   doesn't read `~/.zshrc`, and Gradle and `apksigner` need Java. Check that
   `cmdline-tools/latest/bin/avdmanager` exists, and stop and ask for the
   [one-time setup](#one-time-setup) if not. Check `gh auth status`.
3. **The build under test.**
   - By default, build and sign the release APK of what's checked out, as
     step 1 of [Checking a release build](toolchain.md#checking-a-release-build)
     does, without its `adb` commands. Note the commit
     (`git rev-parse --short HEAD`), and whether the tree has uncommitted
     changes (`git status --porcelain`); say so in the report if it does.
   - For a tag: `gh release download <tag> --pattern '*.apk' --dir "$RUN_DIR"`,
     and the commit is the tag's.
   - Read the APK's version with
     `"$BUILD_TOOLS/aapt2" dump badging "$APK" | head -1`.
4. **Static checks** (REL-4): run them now, since they need only the APK.
5. **AVDs.** Create any that are missing.
6. **Previous release.** `gh release list --limit 5`. If there's a release
   older than the build under test, A9 tests updating from it; download its
   APK into `$RUN_DIR/previous/`.

### 2. Run the assignments

Keep up to three emulators running at once: each takes 1 to 2 GB of memory,
and other sessions may be running emulators too. Check with `memory_pressure`
before starting another, and run fewer if less than a third is free. For each assignment:

1. Start a fresh emulator of its AVD on a free port, as in
   [Starting and stopping an emulator](#starting-and-stopping-an-emulator).
2. Install the build under test, and push the seed file:

   ```sh
   adb -s $SERIAL install "$APK"
   adb -s $SERIAL push scripts/qa/seed.json /sdcard/Download/bluecard-qa-seed.json
   ```

3. Start a tester with the Agent tool (`general-purpose`, in the
   background), with the prompt below filled in.
4. When a tester reports back, save its report to `$RUN_DIR/<ID>/report.md`,
   stop its emulator, and start the next assignment in its place.

The tester prompt:

```text
You're a QA tester for BlueCard, running assignment <ID> of docs/qa-test-plan.md.

- Emulator: <SERIAL> (<AVD>, Android <version>). It has booted, and the build under test is
  installed: io.github.bryancassell.bluecard, version <versionName> (<commit>).
- Settings to test with: <settings from the Assignments table>.
- Suites: <suites from the Assignments table>.
- Write everything, including screenshots, notes and any helper scripts, in <RUN_DIR>/<ID>/ only.

Read these parts of docs/qa-test-plan.md, and only these: "Tester instructions", your AVD's
row in "AVDs", "Settings varied on the devices" (unless your settings are the default), and
the sections for your suites. Find their line numbers with: grep -n '^##' docs/qa-test-plan.md
Then run your suites and report back as "Tester report" describes.
```

If a tester stops early, such as from a crash it can't get past, start a new
tester for what's left, on a fresh emulator, and tell it what the first one
found.

### 3. Triage and file

When every tester has reported:

1. **Merge** findings that are the same bug seen on different devices into
   one, noting every device and setting it was seen with. A bug seen only on
   one device or setting is a finding about that device or setting.
2. **Confirm** each bug, unless its tester already reproduced it from a clean
   start, or it's a crash with a stack trace. Start a tester on a fresh
   emulator with just its steps, and ask whether it happens, and whether it
   also happens on `QA_Phone_API37` with default settings.
3. **Look for an existing issue** with a few searches of its key words:
   `gh issue list --state all --search "<words>" --limit 10`. Check the
   open issues listed in the run report's known issues too.
   - Already filed and open: don't file it again. Comment on the issue only
     if the run adds something, such as another device it happens on.
   - Filed and closed as fixed: it's a regression. File a new issue that
     links the old one.
4. **File** each new bug, as [Filing issues](#filing-issues) describes.

### 4. Report and clean up

1. Stop every emulator the run started. Leave the AVDs.
2. Write the run report to `$RUN_DIR/report.md`, and end with it as the final
   message:

   ```markdown
   ## QA run: BlueCard <versionName> (<commit>), <date>

   **Result:** <N> new bugs filed, <N> known bugs seen again, <N> of <N> cases passed.

   | Assignment | Device | Passed | Failed | Blocked | Not run |
   |---|---|---|---|---|---|

   ### New issues
   - #123 <title> (A1, A6)

   ### Known issues seen again
   - #285 <title>: <commented, or nothing new>

   ### Not filed
   Observations that aren't bugs against the PRD, such as a layout that works but could
   be better, and suspected bugs that didn't reproduce.

   ### Not tested
   What couldn't run and why, such as REL-1 with no previous release.

   ### Plan problems
   What testers found wrong or unclear in this plan, to fix in a pull request.
   ```

## Tester instructions

You're testing one assignment on one emulator. Work through your suites in
order, judge each case against its expected result, and report back.

### Rules

- **Pass `-s <SERIAL>` to every `adb` command**, and give the serial to
  `scripts/qa/ui.py`. Other emulators belong to other testers or sessions.
- **Never run `adb kill-server` or `adb start-server`.** Every tester and
  session shares the one adb server, and restarting it drops every
  emulator's connection. If your device shows as offline for more than a
  minute, run `adb reconnect offline`, which resets only devices that are
  offline, and redo whatever was cut off.
- **Don't file issues, comment on them, or change files in the repository.**
  The coordinator files issues from your report.
- **Write only in your run directory, `<RUN_DIR>/<ID>/`,** including any
  helper scripts, and put your serial in their names. Testers run at the same
  time, so a script with a common name in a shared scratchpad can be replaced
  by another tester's and send your commands to their emulator.
- **Read the screen as text first**, with `scripts/qa/ui.py <SERIAL> screen`.
  Take a screenshot only to check how something looks, which text can't tell
  you, and keep it small: `shot` scales to 540px wide by default, and
  `--crop` cuts it to the part being checked.
- **Use the app as a scout would:** tap what's on screen and type into
  fields. Don't start the app's activities with `am start` or change its data
  any other way.
- **Don't stop at the first failure.** Note it, get the app back to a state
  where the next case can run, and carry on. If the app crashes, reopen it
  and carry on.

### Driving the emulator

`scripts/qa/ui.py` (Python 3, no packages to install) does most of it. Run
`scripts/qa/ui.py <SERIAL> -h` for every command. Call it by its path: zsh
doesn't split a command kept in a variable.

The Bash tool's shell doesn't read `~/.zshrc`, so `adb` isn't on its PATH.
Start each command that runs `adb` with
`export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH";`. `ui.py`
finds adb by itself.

| Task | Command |
|---|---|
| Open the app | `ui.py S launch`: opens it as a launcher does, and waits for it. `am start -n` would stack another copy of the activity on top instead. |
| See what's on screen | `ui.py S screen`: the focused window, then each piece of text and each control, with its state (`[tap]`, `[checked]`, `[disabled]`, `[field]`…) and where it is. A control's label joins the text inside it with " \| ". |
| Tap something | `ui.py S tap "Mark completed"`: ignores case, and takes a control whose label is exactly the text, else one starting with it, else one containing it, before a field holding that text (`--exact`, `--nth 2`, `--long`) |
| Type | Tap the field, then `ui.py S type "Troop 42"`. ASCII only. |
| Empty a field | Tap the field, then `ui.py S clear` |
| Wait for something | `ui.py S wait "Data imported."` (`--gone`, `--timeout 20`) |
| Scroll to something | `ui.py S scroll-to "Clear progress"` (`--up`) |
| Keys | `ui.py S key BACK`, `key ENTER`, `key MOVE_END`, `key DEL --repeat 30` |
| Screenshot | `ui.py S shot <RUN_DIR>/<ID>/home.png` (`--crop l,t,r,b`, `--width`) |
| What did a tap open? | `ui.py S starts`: recent activity starts, with their intents. The log shortens a link to its host, so `starts` also lists the full web, phone and email links that apps are holding, such as the one Chrome keeps while it shows its first-run screens. |

If `screen` can't read a screen, such as a web page, use a screenshot, or the
Android CLI's `android layout --device=S` or
`android screen capture --annotate`, in `$ANDROID_HOME/cmdline-tools/latest/bin`.

Other things you'll need:

- **Start from nothing:** `adb -s S shell pm clear io.github.bryancassell.bluecard`,
  then `ui.py S launch`, which shows Onboarding.
- **Start from the seed:** start from nothing, finish Onboarding with any name,
  then Manage data → Import → `bluecard-qa-seed.json` → Replace all, and wait
  for "Data imported.". The [seed](#the-seed) describes what you get.
- **Wait, don't sleep:** after a tap that opens something, `ui.py S wait` for
  text on the next screen. A page can take a moment, and the first launch
  after a settings change takes longer.
- **Check which app is in front:** `screen`'s first line names the focused
  window. After a stray Back, it may be the launcher; open the app again.
- **Below the fold:** on a seeded Home, Merit badges, Ranks and Manage data
  are further down, so use `scroll-to` before tapping them. Home also keeps
  its scroll position after an import.
- **The keyboard:** the first time it opens on a fresh emulator, Gboard can
  show a tip over the next field, such as "Tap to Google Search and more" on
  API 26, or "Emoji and keyboard font size updated" after a text size change;
  dismiss it first. Close the keyboard before swiping, or the swipe types into the field.
- **Back with the keyboard up** only closes the keyboard. Where a case says
  "press Back" while typing, press it twice.
- **What `screen` can't show:** a requirement row's state (its number's box,
  "Not needed") and progress bars. Use a cropped screenshot for those.
- **System tips:** a fresh tablet or foldable may show a tooltip, such as "Tap
  handle to access display options". Dismiss it, and don't report it.
- **Crashes:** `adb -s S logcat -b crash -d | grep -B1 -A40 'Process: io.github.bryancassell.bluecard'`.
  The crash log also has the emulator's own native crashes, such as
  `uwb-service`; ignore those. Clear the log with `-c` before each suite, and
  check it after. A crash in BlueCard is always a bug. The stack trace has
  R8's short names; include it as it is.
- **The app's warnings:**
  `adb -s S logcat -d --pid=$(adb -s S shell pidof io.github.bryancassell.bluecard) '*:W' | tail -50`
  shows the app's errors, and the load and save failures it logs.
- **Dates:** the emulator's date is today's. Dates after today can't be
  picked, by design.
- **Animations** stay on: page slides are part of what's tested. `screen`
  retries while the screen is animating.
- **The system file picker:** on a fresh emulator it opens at Recent files,
  which is empty: tap "Show roots", then Downloads, and it remembers Downloads
  after that. Pick a file by tapping its name. If a tap doesn't pick it (seen
  on API 30), use `key TAB`, `key DPAD_DOWN` and `key ENTER`. A saved file is
  in `/sdcard/Download/`.
- **Other apps on a fresh emulator** stop at their first-run screens: Chrome
  at its welcome, Gmail at its tour, with no account. Check that the right app
  opened with the right link, using `starts`, and go Back.
- **Checking an animation or a flash** (ONB-4, NAV-6):
  `adb -s S shell screenrecord --time-limit 5 /sdcard/qa.mp4` while doing it,
  pull the file, and split it into frames with ffmpeg. If ffmpeg isn't
  installed, `python3 -m venv --system-site-packages <RUN_DIR>/<ID>/venv` and
  `<RUN_DIR>/<ID>/venv/bin/pip install imageio-ffmpeg` provide one
  (`imageio_ffmpeg.get_ffmpeg_exe()`).
- **A saved PDF:** `adb -s S pull "/sdcard/Download/<name>.pdf" <RUN_DIR>/<ID>/`,
  then read it with the Read tool, which shows PDF pages.

### The seed

`scripts/qa/seed.json` is an export (format version 2) that gives every
screen something to show. After importing it:

- **Profile:** Alexandra Montgomery-Fitzgerald, Troop 1234, a name long enough
  to wrap.
- **Home:** Your rank Tenderfoot, 2 of 7 ranks earned, next Second Class.
  Merit badges 2 completed and 4 in progress. Eagle-required 2 of 13
  completed and 2 in progress (Hiking counts with Swimming, which is
  complete). In progress: Camping, Hiking, Personal Fitness, Space
  Exploration.
- **Camping:** counselor Pat Rivera with phone and email; 1a to 1c, 2 and 4a
  complete, 4a with two lines of notes; notes on 9; 9a's log has 3 campouts,
  12 of 20 nights.
- **Hiking:** counselor with email only; 5's fixed rows have 3 of 5 hikes.
- **First Aid:** completed on Nov 20, 2025. **Swimming:** completed on
  Jul 30, 2026.
- **Personal Fitness:** 7a's log has 2 entries.
- **Space Exploration:** 3's own work and 3a, 3b checked; 5a checked.
- **Ranks:** Tenderfoot marked earned on Feb 10, 2026, so Scout is "Counted
  as earned with Tenderfoot"; Second Class started, with sign-offs on 2a, 2b
  and 3, a 1a log entry and 1 of 4 rows in 7a.

If Home doesn't show these after importing it, that's a bug: import must
keep reading files in older format versions.

### Judging a case

Each case gives steps and what should happen. The details of how the app
should look and behave are in [`PRD.md`](../PRD.md)'s Design decisions, and
cases name the row to read, such as "PRD: Tracker totals". Read a row with
`grep -n '^| Tracker totals' PRD.md`.

A case **fails** when the app does something else, crashes, shows text cut
off or hidden, or loses data. Anything else you notice that the PRD doesn't
cover, such as an awkward layout, is an **observation**, not a failure. A
case is **blocked** when an earlier failure keeps it from running.

When a case fails:

1. Do it again from a clean start (from nothing, or from the seed) to see
   whether it happens every time. Note how often it happened.
2. Save a cropped screenshot that shows it, and the crash log if there is one.
3. Write down the shortest steps that show it, starting from nothing or from
   the seed.

Check the [known issues](#known-issues): a failure that matches one is
reported as that issue, not as a new bug.

### Tester report

Report back with this, and nothing else. Keep it short: the coordinator reads
nine of them.

```markdown
## <ID> on <AVD> (<settings>)

| Case | Result | Note |
|---|---|---|
| ONB-1 | pass | |
| ONB-2 | fail | Bug 1 |
| HOME-3 | blocked | By bug 1 |
| ... | | |

### Bug 1: <what's wrong, in one line>
- **Steps** (from nothing / from the seed): 1. … 2. … 3. …
- **Expected:** … (PRD: <row>)
- **Actual:** …
- **How often:** every time (3 of 3) / once in 3 tries
- **Seen with:** <device, Android version, settings>
- **Evidence:** <RUN_DIR>/<ID>/<file>.png; crash log excerpt if any
- **Known issue?** No / maybe #123

### Observations
- …

### Plan problems
- Anything in this plan that was wrong, unclear or didn't work as written,
  such as a step that couldn't be done or an expected result the PRD
  contradicts.
```

### TalkBack

TalkBack ignores `input tap` and `input swipe`, and `uiautomator dump`
interferes with it, so with TalkBack on, don't use `ui.py` commands that read
the screen (`screen`, `tap`, `wait`, `scroll-to`). Use:

- `ui.py S talkback on` and `talkback off`.
- `ui.py S tb right` and `tb left` to move to the next or previous item,
  `tb double-tap` to activate the item in focus, and `tb up` or `tb down` to
  change what swiping moves by. They're sent as touches through the emulator
  console, which TalkBack treats as a finger.
- `ui.py S tb-speech --clear` before a gesture, then `ui.py S tb-speech` to
  print what TalkBack said, with times.
- Screenshots, to see where TalkBack's focus outline is.

`tb-speech` reads TalkBack's log, which needs **Log output level: Verbose**.
A fresh emulator logs only errors. Set it while TalkBack is still off, so
`ui.py` taps work:

1. `adb -s S shell am start -n com.google.android.marvin.talkback/com.android.talkback.TalkBackPreferencesActivity`
2. `scroll-to` and tap "Advanced settings", then "Developer settings", then
   "Log output level".
3. Tap "VERBOSE", then "Yes, enable verbose logging". The setting then reads
   "Log output level | VERBOSE".

Turning TalkBack on may then ask to allow notifications, with TalkBack's
focus on "Don't allow": `tb double-tap` dismisses it.

### Known issues

Open issues a tester is likely to run into. Report a match as the issue, not
as a new bug. The coordinator checks the full list.

- [#297](https://github.com/bryancassell/bluecard/issues/297): out of touch
  mode, Save can move focus to a page's first item.
- [#299](https://github.com/bryancassell/bluecard/issues/299): on API 26, the
  Merge dialog's navigation bar buttons are white on its light page.
- [#304](https://github.com/bryancassell/bluecard/issues/304): with TalkBack,
  Badges announces the list's old size after a search changes it.
- [#305](https://github.com/bryancassell/bluecard/issues/305): with TalkBack,
  the rank card partly scrolled off screen is read without the rank.
- [#306](https://github.com/bryancassell/bluecard/issues/306): at the largest
  text and display size, the date picker runs off the right edge.
- [#307](https://github.com/bryancassell/bluecard/issues/307): at the largest
  text and display size, labels break mid-word, such as "Unmar" / "k".
- [#308](https://github.com/bryancassell/bluecard/issues/308): in landscape,
  Badges' search field is squeezed behind the keyboard.
- [#281](https://github.com/bryancassell/bluecard/issues/281): whether some
  messages are read out when a page is shown already showing them.
- [#148](https://github.com/bryancassell/bluecard/issues/148): a
  requirement's own-work checkbox moves when it completes the requirement.

## Test suites

Cases tagged **[smoke]** are the ones run when a suite runs as smoke. Each
case starts from where the one before it left off, unless it says where to
start.

### ONB: Onboarding and profile

- **ONB-1 [smoke] First launch.** From nothing, open the app. → Onboarding:
  "Welcome to BlueCard", Name and Unit number fields, both required, "Get
  started" disabled, and the line saying BlueCard isn't affiliated with
  Scouting America.
- **ONB-2 Required fields.** Type a name only, then clear it and type a unit
  only. → Get started stays disabled until both have text. Spaces alone don't
  count.
- **ONB-3 [smoke] Saving the profile.** Name "Alex O'Brien", unit "Troop 42",
  Get started. → Home, with "Alex O'Brien" and "Unit: Troop 42", the rank
  card ("None yet"), "You haven't started any merit badges yet.", and Merit
  badges, Ranks and Manage data.
- **ONB-4 [smoke] The profile is kept.** Close the app (`am force-stop`),
  and open it again while recording the screen (see "Checking an animation or
  a flash"). → Home straight after the splash screen, with no frame of
  Onboarding.
- **ONB-5 Editing the name and unit.** Manage data → Edit. → The "Name and
  unit" page, with both fields filled in. Change the unit to "Troop 7" and
  Save. → The page closes; Home shows "Unit: Troop 7". (PRD: Changing the name
  and unit number)
- **ONB-6 Discarding an edit.** Edit again, change the name, press Back. →
  "Discard changes?" with Cancel and a red Discard. Cancel keeps the page and
  the edit; Back then Discard closes it, and the name is unchanged. Clearing
  the name keeps Save off, and Back still asks. (PRD: Unsaved changes on Back)
- **ONB-7 Spaces and long text.** Edit the name to "  Sam  " and Save. → Home
  shows "Sam", trimmed. Paste or type a very long name. → The field stops at
  its limit, and Home wraps it without cutting it off.

### HOME: Home

Start from the seed.

- **HOME-1 [smoke] Summary.** → Everything in [The seed](#the-seed)'s Home
  line: name, unit, rank card, counts, Eagle-required counts, and the four
  badges in progress with a progress bar each. Personal Fitness's bar is
  empty: its only progress is log entries, which don't count toward the bar.
  (PRD: Rank on Home, Eagle-required progress, Badge progress bar)
- **HOME-2 Rank card.** Tap the rank card. → Second Class's Rank detail. Back
  → Home.
- **HOME-3 [smoke] Badge in progress.** Tap Camping. → Camping's Badge detail.
  Back → Home, scrolled where it was.
- **HOME-4 Counts follow changes.** Open Personal Fitness → Mark completed →
  today → OK, then Back. → Home: 3 completed, 3 in progress, Eagle-required
  3 of 13 completed and 1 in progress, and Personal Fitness is no longer
  listed in progress.
- **HOME-5 Links.** Merit badges opens Badges, Ranks opens Ranks, and Manage
  data opens Data management; Back from each returns to Home.

### FIND: Browse and search

- **FIND-1 [smoke] The list.** From the seed, open Merit badges. → "Merit
  badges" heading, the search field, "142 merit badges", badges in name order
  from American Business. Camping, Hiking, Personal Fitness and Space
  Exploration show "In progress" with a bar; First Aid and Swimming show
  "Completed". Eagle-required badges say so. Scroll to the end: the last badge
  is visible above the navigation bar.
- **FIND-2 [smoke] Search by name.** Type "camp". → Camping is listed, with
  the count line saying how many match. (PRD: Badge search)
- **FIND-3 Search rules.** Each of these, after Clear search:
  - "fit" → Personal Fitness (word start), not badges with "fit" inside a
    word.
  - "art" → doesn't list badges just because their summary says "part" or
    "start".
  - "SCIENCE ANIMAL" → Animal Science (any order, any case).
  - "xyzzy" → no badges, and a line saying none match.
- **FIND-4 Search by summary.** Search a word that's only in a summary, such
  as "rocket". → Space Exploration.
- **FIND-5 Clear search.** Clear search. → The full list and "142 merit
  badges" again; the field is empty.
- **FIND-6 Search and place are kept.** Clear search, scroll the list to
  Cooking, open it, Back. → The list is at the same place. Search "first",
  open First Aid, Back. → Still filtered by "first".

### BADGE: Badge page and counselor

Start from the seed.

- **BADGE-1 [smoke] The page.** Open Camping. → Name, a progress bar,
  "Eagle-required", the summary, "Official requirements", the status card
  ("In progress", "Already completed this badge?", Mark completed), the
  counselor (Pat Rivera, phone, email, Edit), and the requirements 1 to 10,
  each with its number in a box, its summary, and "Do 1 of 3" on 3. 1 and 2
  are complete; 4 and 9 are partly done. At the bottom, Clear progress. (PRD:
  Status card, Requirement list, Showing whether a requirement is complete)
- **BADGE-2 [smoke] Official link.** Tap Official requirements. → The browser
  opens `https://www.scouting.org/merit-badges/camping/` (check with
  `ui.py S starts`); on API 26, in its WebView Browser Tester. Back returns to
  Camping.
- **BADGE-3 Phone and email.** Tap the phone number. → The phone app opens
  with 555-0142 to dial, without calling. Back. Tap the email. → An email app
  opens with a `mailto:pat.rivera@example.com` link (`starts`); on a fresh
  emulator Gmail shows its tour, since it has no account. If no app can send
  email, a message says so. (PRD: Counselor details)
- **BADGE-4 Editing the counselor.** Edit counselor. → Name, phone and email,
  filled in. Clear the phone and Save. → The page closes, and Camping shows
  the counselor without a phone. Back with an unsaved change asks first.
- **BADGE-5 Adding a counselor.** Open a badge not started, such as Chess.
  → "Not started", "Add counselor". Add a name only and Save. → Chess is now
  in progress, with the counselor shown.
- **BADGE-6 Not started yet.** Open Archery. → "Not started", no progress bar
  and no Clear progress. Opening a requirement and going back changes
  nothing: the badge is still not started.

### REQ: Recording requirements

Start from the seed, on Camping.

- **REQ-1 [smoke] Marking complete.** Open requirement 6, then 6a. →
  Requirement 6a, its summary, a "Completed" checkbox, and Notes. Check it. →
  "Completed on" today, with Change date and Remove date. Back to 6. → 6a's
  box is filled with a check, and 6's row says "(1 of 5 complete)". (PRD:
  Marking a requirement complete, Counting complete sub-requirements)
- **REQ-2 [smoke] Changing the date.** On 6a, Change date. → The date picker,
  at the current date, with dates after today disabled. Pick a date earlier
  this month, OK. → That date shows.
- **REQ-3 Unchecking.** On 6a, type a note and Save notes. Uncheck 6a. → The
  date goes away; the note stays. Check it again. → The date picked in REQ-2
  comes back. (PRD: Unchecking a requirement)
- **REQ-4 [smoke] Notes.** On 6a, type two lines of notes (use `key ENTER`
  between them). → "Save notes" turns on, and the field and Save stay above
  the keyboard. Save notes. → Save notes turns off; leave and reopen 6a, and
  the notes are there. Back with unsaved notes asks "Discard changes?". (PRD: Requirement
  notes, Save while typing in a requirement's notes)
- **REQ-5 Do N of M.** Open 3 ("Do 1 of 3"). → Its own work's checkbox above
  "Do 1 of 3" and the sub-requirements. Check 3a. → 3b and 3c show "Not
  needed", but 3 isn't complete until its own work is checked too; on Camping,
  3's row says what's still to do. Check the own work. → 3 is complete.
  (PRD: A requirement's own work, Showing whether a requirement is complete)
- **REQ-6 Own work first.** Open Space Exploration → 3. → Its own work
  checked, 3a and 3b checked; the requirement isn't complete until enough
  sub-requirements are.
- **REQ-7 Clearing a requirement.** On Camping 4, Clear progress. → A dialog
  with Cancel and a red Clear. Cancel changes nothing. Clear removes 4a's
  completion and notes; the badge stays in progress. (PRD: Clearing a
  requirement)
- **REQ-8 Starting a badge.** Open Bugling, a requirement, and check it. →
  Bugling is in progress, on Badges and on Home. Uncheck it. → Still in
  progress. (PRD: Starting a badge)

### TRK: Trackers

Start from the seed.

- **TRK-1 [smoke] Adding to a log.** Camping → 9 → 9a. → "3 campouts",
  "12 of 20 nights", three rows numbered 1 to 3, Add campout. Add campout. →
  "Campout 4", a Start date with Add date, and fields for Event or place,
  Nights and Slept in. Pick a date, fill the fields with Nights "2.5", and
  Save. → Back on 9a: "4 campouts", "14.5 of 20 nights", and Campout 4's
  summary. (PRD: Trackers, Tracker totals)
- **TRK-2 Keyboard.** On a new row, tap the first text field. → Next moves to
  the next field; the last one-line field has Done. While typing in the last
  field, it and Save stay above the keyboard. (PRD: Moving between a tracker
  row's fields, Save while typing in a tracker row)
- **TRK-3 Number field.** In Nights, type letters and "1.2.3". → Only digits
  and one decimal separator are taken.
- **TRK-4 Editing and deleting.** Open Campout 2, change the place, Save. →
  The summary changes. Open it again, Delete. → A dialog with a red Delete;
  after it, the remaining campouts are numbered 1 to 3, and the total drops.
- **TRK-5 [smoke] Fixed rows.** Hiking → 5. → Five rows, Hike 1 to Hike 5,
  three filled in, its own-work checkbox above them. Fill in Hikes 4 and 5. →
  5 isn't complete until its own work is checked too; check it. → Complete,
  dated as PRD: Completing a fixed-row tracker's requirement says. Delete
  Hike 2. → The others keep their numbers, and 5 is no longer complete.
- **TRK-6 Multi-line column.** Hiking 5, a row's Notes: type two lines. → The
  field grows; on the requirement page the row's summary shows the line break
  as a space.
- **TRK-7 Unsaved row.** Start a new campout, type a place, press Back. →
  "Discard changes?"; Discard leaves no new row.

### DONE: Completing a badge and reports

Start from the seed.

- **DONE-1 [smoke] Mark completed.** Open Archery (not started) → Mark
  completed. → The date picker, at today; later dates disabled. Pick a date
  last month, OK. → The status card says "Completed on <date>", with Change
  date, Unmark, Share report and Save report. Archery is Completed on Badges
  and counted on Home. (PRD: Marking a badge completed on a prior date)
- **DONE-2 Unmark.** Unmark. → No dialog; the badge is in progress again
  (started), and Mark completed opens at the date just unmarked.
- **DONE-3 [smoke] Save report.** Open First Aid → Save report. → The system
  file picker, suggesting a name such as "First Aid merit badge report.pdf".
  Save to Downloads. Pull the PDF and read it. → US Letter pages with the
  scout's name and unit, First Aid, "Completed on Nov 20, 2025", the
  requirements version, every requirement with "Not recorded" for ones with
  nothing done, page numbers and today's date. (PRD: PDF report)
- **DONE-4 Report with details.** Mark Camping completed (today), Save report,
  and read it. → The counselor, requirements with their dates and notes
  (including 4a's two lines), 9a's campouts with "12 of 20 nights", "Not
  recorded" on requirements with nothing done, and "Not completed" on ones
  partly done, such as 4 and 9. (PRD: Marking a badge completed on a prior
  date)
- **DONE-5 [smoke] Share report.** On First Aid, Share report. → The share
  sheet opens, saying it's sharing one file, "First Aid merit badge
  report.pdf". Back returns to First Aid. On API 26, Gmail is the only app
  that takes a PDF, so Android opens Gmail straight away, and `starts` shows
  the chooser started for `application/pdf`.
- **DONE-6 Cancelled save.** Save report, then Back out of the file picker. →
  No message, nothing saved, and the page is as it was.

### RANK: Ranks

Start from the seed.

- **RANK-1 [smoke] Ranks list.** Home → Ranks. → Scout to Eagle Scout in
  order; Scout and Tenderfoot "Earned"; Second Class "In progress" with a bar;
  the rest without a label. (PRD: Ranks)
- **RANK-2 [smoke] Rank detail.** Open Second Class. → Summary, official link
  (a PDF on scouting.org), progress bar, status card ("In progress",
  "Already earned Second Class?", Mark earned), requirements, Clear progress.
  No counselor and no Eagle-required label.
- **RANK-3 Signed off by.** Open 2a. → "Signed off by" shows "Scoutmaster
  Lee", above the notes, and one Save. Change it and Save. → Saved; it
  doesn't change whether 2a is complete. (PRD: Signed off by)
- **RANK-4 Counted as earned.** Open Star → Mark earned → a date → OK. →
  Star "Earned on <date>"; Second Class and First Class say "Counted as
  earned with Star", with Add date. Home's card says Star. Unmark Star. →
  Second Class, First Class and Home are as before. Star stays started, so it
  now shows a progress bar on Ranks. (PRD: Marking a rank earned on a prior
  date, Starting a badge)
- **RANK-5 Merit badges toward Star.** Open Star → 3. → "2 of 6 merit badges"
  and "2 of 4 Eagle-required" (First Aid and Swimming), what's still needed,
  and the completed badges listed with their dates. (PRD: Merit badge
  requirements)
- **RANK-6 Time in rank.** Open Star → 1. → "Eligible 4 months after earning
  First Class, which isn't earned yet". (PRD: Time in rank)
- **RANK-7 Rank report.** Open Tenderfoot → Save report, and read the PDF. →
  Tenderfoot, "Earned on Feb 10, 2026", every requirement, "Not recorded"
  where nothing is.

### CLR: Clearing

Start from the seed.

- **CLR-1 [smoke] Clearing a badge.** Camping → Clear progress. → A dialog
  saying what will be removed, including the counselor, with Cancel and a red
  Clear. Clear. → The page stays, Not started, no counselor, nothing
  recorded. Camping is no longer in progress on Home. (PRD: Clearing a badge)
- **CLR-2 Clearing a rank's requirement.** Second Class → 2a → Clear
  progress → Clear. → 2a is no longer complete and its sign-off is gone; 2b
  keeps its own; Second Class stays started. (PRD: Clearing a requirement)
- **CLR-3 Clearing a rank.** Tenderfoot → Clear progress. → The dialog says
  "Scout will no longer count as earned.", since Scout was counted with
  Tenderfoot's mark; it names only the other ranks. Clear. → Neither is
  earned; Home's card says "None yet". (PRD: Clearing a rank)
- **CLR-4 Clearing with unsaved notes.** On a requirement with notes, change
  the notes without saving, then Clear progress. → The dialog warns that the
  unsaved notes go too; on a rank's requirement, the "sign-off and notes",
  which save together. (PRD: Unsaved notes when clearing)
- **CLR-5 [smoke] Clear all.** Manage data → Clear all. → A dialog; Clear.
  → "Progress cleared."; Home shows no badges started and "None yet", with
  the name and unit kept. Clear all is now disabled. (PRD: Clearing all
  progress)

### DATA: Export and import

Start from the seed.

- **DATA-1 [smoke] Export.** Manage data → Export. → The file picker
  suggests "BlueCard export <today>.json". Save to Downloads. → The file is in
  `/sdcard/Download/`; it's JSON with formatVersion 2, the profile, and every
  started badge and rank. (PRD: Export)
- **DATA-2 [smoke] Replace all.** Clear all, then Import the export → Replace
  all. → "Data imported."; Home matches the seed again.
- **DATA-3 Merge without conflicts.** Clear Hiking. Import the export → Merge.
  → "Data merged." with no questions; Hiking is back, everything else as it
  was. (PRD: Merging an import)
- **DATA-4 Merge with conflicts.** Change the unit number, and check Camping
  6a. Import the export → Merge. → A full-screen dialog asking about the
  profile and Camping, each starting on "this phone", showing how far each
  side got. Pick the file's Camping and keep the phone's profile; Merge. →
  Camping matches the file, and the unit number is the phone's. Back in the
  dialog after choosing asks "Discard changes?".
- **DATA-5 Not an export.** Import a file that isn't an export, such as a
  saved report PDF (or push a text file to `/sdcard/Download/`). → A message
  saying the file isn't a BlueCard export; nothing changes.
- **DATA-6 Newer version.** Copy the export, change `formatVersion` to 99, push
  it to `/sdcard/Download/`, and import it. → A message saying it's from a
  newer version of BlueCard; nothing changes.
- **DATA-7 Cancelled import.** Import, then Back out of the file picker. → No
  message, nothing changes.

### NAV: Navigation and restoring state

- **NAV-1 [smoke] Restoring after the system stops the app.** From the seed,
  open Camping → 9 → 9a. Press Home, open Settings
  (`adb -s S shell am start -a android.settings.SETTINGS`), wait 10 seconds,
  run `adb -s S shell am kill --user 0 io.github.bryancassell.bluecard`, and
  check `adb -s S shell pidof io.github.bryancassell.bluecard` prints nothing.
  Android kills only an app in the background, and API 26 needs `--user 0`.
  Open the app with `ui.py S launch`. → 9a again; Back goes to 9, Camping,
  then Home.
- **NAV-2 Unsaved edit survives.** Start a new campout, type a place, then
  stop the app as in NAV-1 and reopen it. → The new campout's page with the
  typed place.
- **NAV-3 Double taps.** On Badges, tap a badge twice quickly
  (`adb -s S shell "input tap X Y; input tap X Y"`). → One Badge detail
  opens; one Back returns to Badges. Do the same on Official requirements. →
  `starts` shows one VIEW start from BlueCard. (ARCHITECTURE.md: Double taps)
- **NAV-4 Rotation.** On a tracker row with typed, unsaved text, turn to
  landscape and back with `user_rotation` (see "Settings varied on the
  devices"). → The text and the page stay. Rotate on a scrolled page too,
  such as Badges partway down. → It keeps its place. Also rotate with a
  dialog open (Clear progress). → The dialog stays.
- **NAV-5 Folding.** (Foldable only.) Unfolded, start a new campout and type a
  place. Fold, then unfold. → The page and the text stay at each step, laid
  out for each screen.
- **NAV-6 Back swipe.** With gesture navigation, swipe from the left edge on
  a page with unsaved changes
  (`adb -s S shell input swipe 2 1200 500 1200 200`). → The page doesn't
  move, and "Discard changes?" shows. On a page without changes, it goes back
  with the page sliding. (PRD: Page transitions)

### WALK: Every screen

Open every screen with the assignment's setting on, take one screenshot of
each (more where a page scrolls), and look at it. Start from the seed.

On each screen, look for:

- Text cut off, overlapping other text, or running off the screen. A name or
  summary may wrap; it must not be cut short without an ellipsis.
- Anything hidden behind the status bar, navigation bar, camera cutout or
  keyboard, or that can't be scrolled to.
- Buttons squeezed, wrapped mid-word, or pushed off a dialog.
- Text, or the status and navigation bars' icons, hard to read against the
  background, especially in dark mode and in dialogs. (PRD: Colors)
- A layout that's broken for the screen size: content in a narrow strip, or
  stretched so lines are too long to read comfortably, is an observation;
  anything unreadable or unusable is a failure.

The screens, and how to reach them:

1. Onboarding (from nothing), with the keyboard open on the Name field.
2. Home, scrolled through.
3. Badges, then with "camp" searched and the keyboard open.
4. Badge detail: Camping (scroll through), Chess (not started), First Aid
   (completed, with report buttons).
5. Requirement detail: Camping 9 (sub-requirements and notes), Camping 9a (a
   log), Hiking 5 (fixed rows and own work), Camping 3 (Do 1 of 3).
6. Tracker entry: a new campout, with the keyboard open on the last field.
7. The date picker, from Mark completed on Chess.
8. Edit counselor, on Camping.
9. Ranks; Rank detail for Scout (counted as earned), Second Class and Star;
   Second Class 2a (Signed off by); Star 3 (merit badges).
10. Data management; Edit name and unit.
11. Dialogs: Clear progress on Camping, Discard changes on Edit counselor,
    Import's Merge or Replace all.
12. The merge dialog: change the unit number, check Camping 6a, then Manage
    data → Import → `bluecard-qa-seed.json` → Merge.
13. The share sheet, from First Aid's Share report (not on API 26, which
    opens Gmail instead).

### A11Y: TalkBack

Start from the seed, with TalkBack's log set to Verbose and TalkBack on (see
[TalkBack](#talkback)). For each case, clear the speech log, make the
gestures, and read what was spoken.

What every screen must do: each control is read with a name, its role
(button, check box, edit box, heading) and its state, never just "Button" or
"Unlabeled"; headings are read as headings; swiping right visits everything
in reading order; nothing is read twice in a row.

- **A11Y-1 Home.** Swipe through Home. → The name and unit; the rank card as
  one heading with "Tenderfoot" and "2 of 7 ranks earned"; badges read with
  how much is done, such as "40% done. In progress". (PRD: Rank on Home,
  Badge progress bar)
- **A11Y-2 Badges.** Double-tap Merit badges. → Focus goes to the "Merit
  badges" heading, and the count isn't read as the page opens. Focus the
  search field, double-tap, type "camp" (`ui.py S type` works with TalkBack
  on). → The new count is read once, about a second after typing stops.
  Go Back to Home and open Badges again. → Focus is on the heading again, not
  in the search field, and the keyboard stays closed. (PRD: Search result
  announcements, Focus when the page changes)
- **A11Y-3 Requirement.** Open Camping → 6 → 6a. Move to "Completed" and
  double-tap. → It's read as a check box, and its new state is read after
  the double-tap. The date buttons read "Change date" and "Remove date".
- **A11Y-4 Tracker entry.** Add a campout. → The date button reads "Add date:
  Start date"; each field is read with its label. (PRD: Trackers)
- **A11Y-5 Dialog.** Camping → Clear progress. → Focus moves into the dialog;
  its text and both buttons are read; Cancel closes it and focus returns to
  the page.
- **A11Y-6 Data management.** → Edit is read as "Edit name and unit"; Export,
  Import and Clear all are each read as a button with its name. Import the seed with Replace
  all. → "Data imported." is read. (PRD: Changing the name and unit number)

### REL: Release and data safety

- **REL-1 Updating from the previous release.** Only when there's a previous
  release (the coordinator says). Uninstall the build under test, and install
  the previous release's APK. If the build under test is signed with the
  debug key, re-sign the previous APK with it first, or Android won't update
  one with the other:
  `apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --out previous-debug.apk previous.apk`.
  Import the seed, then install the build under test over it with
  `adb -s S install -r`. → The app opens on Home with the seed's data, and
  NAV-1 still passes.
- **REL-2 Auto Backup and restore.** As in
  [Checking backup and restore](toolchain.md#checking-backup-and-restore),
  steps 1 to 3 and 5, with the release package
  `io.github.bryancassell.bluecard` and the build under test's APK. Step 4's
  `run-as` doesn't work on a release build, so skip the file list: open the
  app instead. → Home with the seed's data, without Onboarding.
- **REL-3 [smoke] Crash log.** At the end, check the crash log for BlueCard,
  as in [Driving the emulator](#driving-the-emulator). → No BlueCard crashes.
  (Every tester checks this; REL-3 is the record of it.)
- **REL-4 Static checks** (the coordinator runs these on the APK):
  `"$BUILD_TOOLS/aapt2" dump permissions "$APK"`. → No
  `android.permission.INTERNET` and no runtime permissions, such as storage
  or contacts. (ARCHITECTURE.md: Success criteria)

## Filing issues

The coordinator files one issue per bug, with `gh issue create`, in the
style of the repository's other bug reports, such as
[#285](https://github.com/bryancassell/bluecard/issues/285).

- **Title:** what's wrong, as a sentence a scout would recognize, such as
  "The date picker's OK button is off screen on a small phone with large
  text".
- **Labels:** `bug`, and `accessibility` too when it affects screen reader,
  large text or contrast users.
- **Body:**

  ```markdown
  <What happens, in a sentence or two.>

  Expected: <what should happen>, as PRD.md's <row> says.

  ## Steps
  1. <From nothing / From the QA seed (scripts/qa/seed.json)> …
  2. …

  ## Seen on
  - BlueCard <versionName>, release build, <commit or tag>
  - <AVD>: <device>, Android <version> (API <n>), <settings>
  - Happened <every time (3 of 3) / N of M tries>. <Devices and settings where it didn't happen.>

  ## Logs
  <Crash stack trace, if any, as reported. Retrace it with the mapping file if one was kept:
  `retrace app/build/outputs/mapping/release/mapping.txt <file>`.>

  Found by a run of the [QA test plan](https://github.com/bryancassell/bluecard/blob/main/docs/qa-test-plan.md) on <date>.
  ```

Screenshots stay in the run directory: `gh` can't attach images to an
issue. Describe what the screenshot shows instead.

File a crash, data loss, or a journey that can't be finished as soon as
it's confirmed. Don't file observations: list them in the run report, where
the developer can decide.

## Keeping the plan current

- **A new screen or journey** adds a case to its suite, and a line to
  [WALK](#walk-every-screen).
- **A change to the backup format** leaves `scripts/qa/seed.json` alone: import
  must keep reading older versions, and the seed checks that it does.
  Replace it only when a new version adds something the seed should show,
  and keep the old one for a case of its own.
- **A change to `minSdk` or `targetSdk`** changes the AVDs' system images.
- **A fixed known issue** comes off [Known issues](#known-issues), and a new
  one likely to trip testers up goes on.
- **A run that went wrong,** such as a tester confused by a step, is fixed
  here in the same pull request as the run's findings, or its own.
