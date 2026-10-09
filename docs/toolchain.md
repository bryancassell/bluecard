# Developer toolchain

This project uses the standard Android setup: Android Studio, its bundled JDK,
the Android SDK, and the Gradle wrapper. Everything that matters for a build is
pinned in the repository, so the same commands work on any machine and in CI.

## One-time machine setup (macOS)

1. **Install Android Studio**

   ```sh
   brew install --cask android-studio
   ```

2. **Install the Android SDK.** Open Android Studio, choose the **Standard**
   setup, accept the SDK licenses, and keep the default SDK location
   (`~/Library/Android/sdk`).

3. **Point the command line at the JDK and SDK.** Add this to `~/.zshrc`, then
   open a new terminal:

   ```sh
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   export ANDROID_HOME="$HOME/Library/Android/sdk"
   export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
   ```

   Check it worked: `java -version` and `adb --version` should both print a version.

You do **not** need to install Gradle, Kotlin, or a separate JDK.

## Everyday commands

Run these from the repository root.

| Command | What it does |
|---|---|
| `./gradlew build` | Compiles, builds the R8-shrunk release app, runs local tests, checks coverage and the testing rules, runs lint and the formatting check. This is the command CI runs. |
| `./gradlew spotlessApply` | Reformats all Kotlin and Gradle files to the project style. |
| `./gradlew spotlessCheck` | Fails if any file is not formatted (also part of `build`). |
| `./gradlew test` | Runs local tests only (`app/src/test`). |
| `./gradlew createDebugUnitTestCoverageReport` | Writes an HTML coverage report for local tests to `app/build/reports/coverage/test/debug/index.html`. |
| `./gradlew lint` | Runs Android lint. Reports are in `app/build/reports/`. |
| `./gradlew assembleDebug` | Builds an installable debug APK (`app/build/outputs/apk/debug/`). |
| `./gradlew installDebug` | Installs the debug app on a running emulator or connected device. Its application ID is `io.github.bryancassell.bluecard.debug`, so it installs next to a release build (see [`ARCHITECTURE.md`](../ARCHITECTURE.md#debug-builds)). |
| `./gradlew connectedAndroidTest` | Runs instrumented tests (`app/src/androidTest`) on an emulator or device. |
| `./gradlew pixel6Api37DebugAndroidTest` | Runs instrumented tests on an API 37 emulator that Gradle downloads, starts and shuts down, as CI does. |
| `./gradlew clean` | Deletes build outputs. Rarely needed; the build knows what changed. |

## How the pieces fit together

**Android Studio's bundled JDK.** Android Studio ships its own JDK (the
JetBrains Runtime). Pointing `JAVA_HOME` at it means terminal builds and IDE
builds use the same Java. The app itself is compiled for Java 17 bytecode
(`compileOptions` in `app/build.gradle.kts`), whatever JDK runs Gradle.

**Gradle wrapper** (`gradlew`, `gradle/wrapper/`). The wrapper downloads the
exact Gradle version in `gradle/wrapper/gradle-wrapper.properties` and checks
it against `distributionSha256Sum`, so nobody installs Gradle by hand and a
tampered download fails. Upgrade with
`./gradlew wrapper --gradle-version <version> --gradle-distribution-sha256-sum <sum>`
(the checksum is published next to each release on services.gradle.org), and
commit all four wrapper files.

**Android Gradle Plugin (AGP).** The plugin that knows how to build Android
apps. AGP 9 has Kotlin support built in, so the build only adds Kotlin compiler
plugins on top: Compose, and kotlinx.serialization (for `@Serializable`
navigation keys).

**KSP and Hilt.** [KSP](https://kotlinlang.org/docs/ksp-overview.html) (Kotlin
Symbol Processing) runs code generators at compile time.
[Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
uses it to generate the dependency injection code; its Gradle plugin wires the
generated code into the app. Libraries whose generators run through KSP are
added with `ksp(...)` (for app code) or `kspTest(...)` (for local tests) in
`app/build.gradle.kts`. Room also generates its database code with KSP. KSP
versions are no longer tied to Kotlin versions, but Hilt must be 2.60.1 or newer
to work with Kotlin 2.4.

**Room schemas** (`app/schemas/`). The Room Gradle plugin writes each database
version's schema there as JSON. Commit these files: they show schema changes in
review, and future migration tests read them. Changing an entity means raising
the database version and adding a migration, or existing users' data can't be
opened.

**Version catalog** (`gradle/libs.versions.toml`). Every library and plugin
version lives in this one file. Build scripts refer to entries by alias, such
as `libs.androidx.core.ktx`. Compose libraries are versioned together by the
Compose BOM ("bill of materials").

**Kotlin build scripts** (`*.gradle.kts`). The build is configured in Kotlin,
which gives type checking and IDE autocompletion.

**Performance settings** (`gradle.properties`). The configuration cache skips
re-reading build scripts when they haven't changed, the build cache reuses
task outputs, and parallel execution runs independent tasks together. A no-op
rebuild takes a few seconds.

**Code style: Spotless + ktlint.** The [Spotless](https://github.com/diffplug/spotless)
Gradle plugin runs [ktlint](https://ktlint.github.io/ktlint/) on every `.kt`
and `.gradle.kts` file. ktlint uses its `android_studio` style, which follows
[Android's Kotlin style guide](https://developer.android.com/kotlin/style-guide):
4-space indentation, a 100-character line limit, no wildcard imports, and no
trailing commas. If `./gradlew build` fails on formatting, run
`./gradlew spotlessApply` and commit the result; a few rules (such as wildcard
imports) must be fixed by hand.

**`.editorconfig`.** The shared style settings live in `.editorconfig`, which
both ktlint and Android Studio read, so **Code → Reformat Code** in the IDE
produces the same result the build expects. It replaces IDE-specific
`.idea/codeStyles` files.

**Lint as a gate.** `warningsAsErrors = true` makes lint warnings fail the
build, so problems get fixed when they appear instead of piling up.
The one exception is lint's "a newer version is available" checks, which are
turned off: they would fail the build whenever a new release came out, and
Dependabot proposes those updates instead.

**When lint itself crashes.** Now and then a lint task, such as
`lintAnalyzeDebugUnitTest`, fails with "Unexpected failure during lint analysis
of … (this is a bug in lint or one of the libraries it depends on)". That's
lint crashing, not a problem lint found in the code. Running the same command
again has passed in every case reported so far. A bug report to Google needs
lint's whole message. Gradle keeps each build's output in
`~/.gradle/daemon/<Gradle version>/daemon-<pid>.out.log` for 14 days, so the
message is there after the console has lost it:
`grep -l "Unexpected failure during lint" ~/.gradle/daemon/*/*.out.log` finds
it.

## Project layout

```
.
├── .editorconfig               Formatting rules for ktlint and Android Studio
├── build.gradle.kts            Root build: plugins for all modules, Spotless
├── settings.gradle.kts         Module list and repositories
├── .github/
│   ├── workflows/ci.yml        Continuous integration
│   └── dependabot.yml          Weekly dependency update pull requests
├── scripts/
│   └── check-test-rules.sh     Testing-rule checks run by the build
├── gradle.properties           Gradle and Android build settings
├── gradle/
│   ├── libs.versions.toml      Version catalog
│   └── wrapper/                Pinned Gradle version
└── app/
    ├── build.gradle.kts        App module: SDK levels, dependencies, lint
    ├── schemas/                Room database schemas (generated; commit them)
    └── src/
        ├── main/               App code, manifest, resources
        ├── test/               Local tests (run on your computer)
        └── androidTest/        Instrumented tests (run on a device)
```

## Tests: local vs instrumented

- **Local tests** (`app/src/test`) run on your computer's JVM in seconds. Plain
  logic tests need nothing else. Tests of activities and Compose UI use
  [Robolectric](https://developer.android.com/training/testing/local-tests/robolectric),
  which simulates Android on the JVM: mark the class with
  `@RunWith(AndroidJUnit4::class)`. Put every test here unless it needs a real
  device.
- **Hilt in tests.** ViewModel and other unit tests construct classes directly,
  passing fakes. Tests that launch a Hilt activity are marked `@HiltAndroidTest`,
  run with `@Config(application = HiltTestApplication::class)` and use
  `HiltAndroidRule` (see `MainActivityTest`). In those tests, `@TestInstallIn`
  modules in `app/src/test` replace production modules, for example
  `TestDispatchersModule` binds every dispatcher to one shared `TestDispatcher`,
  which a test can `@Inject` to control virtual time.
- **`MainDispatcherRule`** (`app/src/test/.../testing/`) replaces the main
  dispatcher in ViewModel tests, as in Android's
  [coroutines testing guide](https://developer.android.com/kotlin/coroutines/test).
- **Screenshot tests** ([Roborazzi](https://github.com/takahirom/roborazzi))
  are local tests too. Every test run, `./gradlew build` and CI included,
  compares them against the reference images in `app/src/test/screenshots/`. A
  failed comparison writes the new image, and one comparing the two, to
  `app/build/outputs/roborazzi/`, which CI uploads with its reports. After an
  intended change, `./gradlew recordRoborazziDebug` records them again; review
  the new images in the diff.
- **Migration tests after a database version bump.** The first test run can
  merge assets before Room writes the new schema, so `MigrationTest` fails.
  Run the tests again
  ([#96](https://github.com/bryancassell/bluecard/issues/96)).
- **Instrumented tests** (`app/src/androidTest`) run on an emulator or device.
  Use them only for behavior that needs a real Android runtime. They are slower
  and need a device, so `./gradlew build` does not run them.

To run instrumented tests, create a virtual device once in Android Studio
(**Device Manager → Create Virtual Device**), start it, then run
`./gradlew connectedAndroidTest`. Or let Gradle manage the emulator, as CI
does, with `./gradlew pixel6Api37DebugAndroidTest`: it downloads the API 37
system image for your computer's architecture the first time, then starts its
own emulator for the run, with no window, and shuts it down afterwards. CI's
runners use the x86_64 image; an Apple silicon Mac uses the arm64 one.

## Testing rules the build enforces

`CLAUDE.md` lists the project's testing best practices. `./gradlew build` fails
when it can detect that one is broken:

| Rule | Check |
|---|---|
| New code has at least 80% line coverage | Approximated as: every class has at least 80% line coverage from local tests (task `jacocoDebugCoverageVerification`). Generated code (Android, Hilt and Room classes) and `@Preview` functions are excluded; keep previews in `*Preview.kt` files. |
| Never skip tests | Any `@Ignore` in test code fails `scripts/check-test-rules.sh`. |
| Logic classes have unit tests | Every `*ViewModel`, `*UseCase`, `*Repository` and `*Mapper` file needs a matching `*Test.kt` in `app/src/test`. Files that only declare an interface (such as `CatalogRepository`) are skipped. |
| Prefer fakes over mocks | Adding mockk or Mockito fails the build. |

The other rules need a person to judge, so they are checked in code review:
tests check meaningful behavior, UI tests cover every state and interaction,
screenshot tests only where needed, and local tests are preferred. The same goes
for "coverage must not decrease": the build only knows the current coverage, not
what it was before. If a change lowers coverage, say so and why in the pull
request; `./gradlew createDebugUnitTestCoverageReport` shows the numbers.

When coverage fails, the error names the class. Run
`./gradlew createDebugUnitTestCoverageReport` and open the report to see which
lines no test runs.

One JaCoCo limitation: in a `suspend` function whose call really suspends (for
example into Room), JaCoCo counts the closing `}` as never run, even when tests
call the function. Write such one-line functions as expression bodies
(`suspend fun clearAll() = dao.deleteAll()`), which have no separate closing
line.

## Checking backup and restore

The app's data is copied to a new phone by Android's
[Auto Backup](https://developer.android.com/identity/data/autobackup). Two files
in `app/src/main/res/xml/` list what is backed up, which is only the databases
directory and the DataStore directory: `data_extraction_rules.xml` for Android
12 and higher, and `backup_rules.xml` for Android 11 and lower. Keep the rules
in the two files the same. Lint checks the files' syntax. `BackupRulesTest`
checks that each set of rules covers the files where the app stores data, that
all sets match, and that none limits backup to phones that can encrypt it.

After changing the rules, or where the app stores data, check a real backup and
restore on an emulator, as described in
[Test backup and restore](https://developer.android.com/identity/data/testingbackup).
Use an emulator, not your own phone: these commands change the device's backup
settings. If a phone is also connected, point `adb` at the emulator first with
`export ANDROID_SERIAL=emulator-5554`.

1. Note the emulator's backup settings, to put them back at the end: whether
   backup is enabled, and which transport is selected (marked `*`).

   ```sh
   adb shell bmgr enabled
   adb shell bmgr list transports
   ```

2. Install the debug app (`./gradlew installDebug`), open it, enter some data,
   then press Home. Room creates the database file only when the app first
   opens the database, so also visit a screen that shows or records progress.
   Don't force-stop the app: backup skips a stopped app, and `bmgr` reports
   "Backup is not allowed".
3. Back up with the local test transport, then uninstall and reinstall the app,
   which restores the backup:

   ```sh
   adb shell bmgr enable true
   adb shell bmgr transport com.android.localtransport/.LocalTransport
   adb shell settings put secure backup_local_transport_parameters 'is_encrypted=true'
   adb shell bmgr backupnow io.github.bryancassell.bluecard.debug
   adb shell pm uninstall --user 0 io.github.bryancassell.bluecard.debug
   adb install -t app/build/outputs/apk/debug/app-debug.apk
   ```

4. Before opening the app, list the restored files. Expect only the files the
   rules include: `files/datastore/profile.preferences_pb`, and
   `databases/bluecard.db` (with its `-wal` and `-shm` files, if present) if the
   app had opened its database. Any other file, such as `files/profileInstalled`,
   means the rules include too much. Then open the app and check that the data
   is back.

   ```sh
   adb shell run-as io.github.bryancassell.bluecard.debug find . -type f
   ```

5. Put back the settings you noted in step 1: select the transport that was
   marked `*`, and remove the test transport's settings. If backup was off,
   turn it off again with `adb shell bmgr enable false`.

   ```sh
   adb shell bmgr transport <transport noted in step 1>
   adb shell settings delete secure backup_local_transport_parameters
   ```

Device-to-device transfer, used when setting up a new phone from an old one,
follows the `<device-transfer>` rules. To check it, use the
[`test_d2d.sh` script](https://developer.android.com/identity/data/testingbackup#TestingTransfer)
from the same page, on an emulator with a Google Play system image, with the
debug app's package: `test_d2d.sh io.github.bryancassell.bluecard.debug`. The
script switches back to the Google backup transport before reinstalling,
because that transport performs the restore; if it's skipped, logcat shows
"Can't restore from D2d Transport". Its cleanup selects that transport again
but leaves backup on, so turn backup off afterwards if it was off in step 1.

## Checking a release build

R8 shrinks and obfuscates the release build (see
[`ARCHITECTURE.md`](../ARCHITECTURE.md#release-build)), but not the debug app
or local tests. CI builds the release app, so R8's build errors fail it, but
code that R8 breaks at runtime only fails when a release build runs. Check one
on an emulator before each release, and after adding a library, a keep rule,
or other code that uses reflection. Also check after updating AGP (which brings
R8), or a library that uses reflection or ships keep rules the app relies on:
kotlinx.serialization, Hilt, Room or DataStore. As with backup, use an
emulator, and point `adb` at it if a phone is also connected.

1. Build the release APK and sign it with the debug key. Gradle leaves the
   release build unsigned, and only publishing signs it with the release key
   (see [Publishing a test release](#publishing-a-test-release)). Uninstall
   any release build already installed, to start from a fresh install. A debug
   build has its own application ID, so it can stay. `BUILD_TOOLS` is the
   newest stable build tools; preview versions have a "-" in their name.

   ```sh
   ./gradlew assembleRelease
   BUILD_TOOLS="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | grep -v -e - | sort -V | tail -1)"
   "$BUILD_TOOLS/apksigner" sign --ks ~/.android/debug.keystore --ks-pass pass:android \
       --out app/build/outputs/apk/release/app-release.apk \
       app/build/outputs/apk/release/app-release-unsigned.apk
   adb uninstall io.github.bryancassell.bluecard
   adb install app/build/outputs/apk/release/app-release.apk
   ```

2. Clear the crash log, so that crashes from earlier runs of the app aren't
   mistaken for this build's: `adb logcat -b crash -c`.
3. Go through the key flows. Each one must work, not just not crash: some
   failures show a message instead, such as an import that says the file isn't
   a backup. Afterwards, check `adb logcat -b crash -d` for crashes.
   - Onboarding, then browse and search the badges and open one.
   - Record a counselor, a requirement's completion, and a tracker entry.
   - Create a badge's and a rank's PDF reports, then save and share them.
   - Edit the profile from Home.
   - Export, clear all data, then import the export.
   - Restore after process death: open a requirement page, press Home, run
     `adb shell am kill io.github.bryancassell.bluecard`, then reopen the app
     from Recents. It should come back on the same page, and Back should go
     through the pages under it.

A crash's stack trace shows R8's short names. `retrace` turns them back into
the source names, with the mapping file that the build wrote:
`retrace app/build/outputs/mapping/release/mapping.txt <stack trace file>`.
Each release build writes a new mapping file, so retrace before building
again. Testers can't see a stack trace, and Android vitals only reports
crashes from Play installs, so testers report crashes by hand. To retrace one,
reproduce it with their release's APK and use the mapping file attached to
that release. Once BlueCard is on Google Play, Play deobfuscates Android
vitals' crash reports from the mapping file that AGP puts in the app bundle
([Play Console Help](https://support.google.com/googleplay/android-developer/answer/9848633)),
so it needs no separate upload. `retrace` comes with the Android SDK Command-line
Tools, which the Standard setup doesn't install: add them in Android Studio's
**SDK Manager → SDK Tools → Android SDK Command-line Tools (latest)**.

## Publishing a test release

Friends and family test BlueCard with APKs from the repository's
[Releases](https://github.com/bryancassell/bluecard/releases) page, published
as pre-releases. Gradle builds the APK unsigned, and `apksigner` signs it with
BlueCard's release key, asking for the key's password (see
[`ARCHITECTURE.md`](../ARCHITECTURE.md#release-build)). Only a machine with the
release key can publish one.

The password never reaches a Gradle build, which runs third-party plugins and
the code of whatever branch is checked out, Dependabot's included.
[Sign your app](https://developer.android.com/studio/publish/app-signing) keeps
the password in a properties file that Gradle reads, where every build on the
machine, and malware that collects such files, could read it too. This doesn't
stop code running as the developer, such as a build of another branch, from
tampering with the APK that gets signed or the tools that sign it; only a
separate account or machine would. If CI signs releases later, it can run
`apksigner` the same way.

Moving to Google Play means choosing the app signing key. Play App Signing can
generate a key of its own, which Play recommends, but Android won't update an
app from an APK signed with a different key: testers would export their data,
uninstall, install from Play and import. Play can take BlueCard's release key
instead, so testers update in place.

[Android developer verification](https://developer.android.com/developer-verification)
reaches the US in 2027. From then on, register the package name and the
release key's certificate, or testers need Android's advanced flow to install
the app. A free limited distribution account covers up to 20 devices.

### Setting up the release key

Android only installs an update signed with the same key as the app already
installed, so every test release needs this one. If it's lost, every tester
has to uninstall and install again, losing their data unless they export it
first.

1. Create the key, once for the project. `keytool` asks for the password.
   The `chmod` commands make the key readable only by you.

   ```sh
   mkdir -p ~/keys && chmod 700 ~/keys
   keytool -genkeypair -keystore ~/keys/bluecard-release.p12 -storetype PKCS12 \
       -alias bluecard -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=BlueCard"
   chmod 600 ~/keys/bluecard-release.p12
   ```

   On another machine, copy `bluecard-release.p12` from the backup into
   `~/keys` in place of the `keytool` command. Anyone can read the
   certificate's name (`CN=BlueCard`) from the APK, so it names the app rather
   than a person. The key is valid for 10,000 days, since Sign your app asks
   for at least 25 years. For a `minSdk` of 24 or higher, `apksigner` signs
   with APK Signature Scheme v2, which every Android version BlueCard supports
   checks, and v3, without a v1 signature.

2. Save the keystore file and its password together in a password manager.
   Keep the password only there, not in a file that a build could read.

### Publishing a release

Run steps 2 to 5 in one terminal, since they share shell variables. The code
blocks have no comments, because zsh doesn't treat `#` as a comment when you
paste commands into it.

1. Unless no release has used them yet, raise `versionCode` by 1 and set
   `versionName` in `app/build.gradle.kts`, in a pull request. Android won't
   update an app to a lower `versionCode`, and a new one for each release
   tells the builds apart.
2. Once it's merged, build the APK from `main`. The build only runs if `main`
   has no uncommitted changes and matches `origin/main`, so the commit tagged
   later is exactly what was built.

   ```sh
   git switch main && git pull --ff-only &&
       [ -z "$(git status --porcelain)" ] &&
       [ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] &&
       COMMIT=$(git rev-parse HEAD) &&
       ./gradlew assembleRelease
   ```

3. Sign it with the release key, with `BUILD_TOOLS` set as in step 1 of
   [Checking a release build](#checking-a-release-build). The first command
   prints the APK's `versionCode` and `versionName`: check they're the new
   ones. The R8 mapping file is saved beside the signed APK, so a later build
   can't replace it. `apksigner` asks for the key's password, and the commands
   end by printing "Signed with the release key." only if the APK's
   certificate has the release key's SHA-256 digest.

   ```sh
   OUT=app/build/outputs
   UNSIGNED="$OUT/apk/release/app-release-unsigned.apk"
   "$BUILD_TOOLS/aapt2" dump badging "$UNSIGNED" | head -1 | cut -d " " -f 3-4
   VERSION=$("$BUILD_TOOLS/aapt2" dump badging "$UNSIGNED" | sed -n "s/^package: .* versionName='\([^']*\)'.*/\1/p")
   APK="$OUT/bluecard-${VERSION:?}.apk"
   MAPPING="$OUT/bluecard-$VERSION-mapping.txt"
   rm -f "$APK" && cp "$OUT/mapping/release/mapping.txt" "$MAPPING" &&
   "$BUILD_TOOLS/apksigner" sign --ks ~/keys/bluecard-release.p12 --ks-key-alias bluecard \
       --out "$APK" "$UNSIGNED" &&
   "$BUILD_TOOLS/apksigner" verify --print-certs "$APK" |
       grep -q "certificate SHA-256 digest: d1fe1f136ba0b07f8bc43a62dd193dea3f3f8474b1a7586aed4537e55248abff" &&
       echo "Signed with the release key."
   ```

4. Install it fresh on an emulator and go through steps 2 and 3 of
   [Checking a release build](#checking-a-release-build).

   ```sh
   adb uninstall io.github.bryancassell.bluecard
   adb install "$APK"
   ```

   From the second release on, also check an update the way testers get one.
   Set `PREVIOUS` to the previous release's `versionName`, and install that
   release:

   ```sh
   PREVIOUS=0.1.0
   gh release download "v$PREVIOUS" --pattern '*.apk' --dir app/build/previous --clobber
   adb uninstall io.github.bryancassell.bluecard
   adb install "app/build/previous/bluecard-$PREVIOUS.apk"
   ```

   Record some progress in the app, then install this release over it with
   `adb install -r "$APK"`. The progress should still be there.

5. Tag the commit that was built, and publish the release with the APK and
   its mapping file. `gh` asks for a title and release notes: say what's new
   and what to try.

   ```sh
   git tag "v${VERSION:?}" "${COMMIT:?}" && git push origin "v$VERSION" &&
       gh release create "v$VERSION" --verify-tag --prerelease "$APK" "$MAPPING"
   ```

6. Send testers the release's link. They install the APK by opening it on
   their phone, and allow their browser to install apps when Android asks. To
   update, they install the next release's APK over the old one, which keeps
   their data.

## Updating the README screenshots

The README shows three screenshots from `docs/images/`: Home, Camping's badge
page, and Camping requirement 9a's campout log. When a change makes them look
out of date, retake all three the same way, so they still match each other.

1. Install the debug app fresh on an emulator (they were taken on a Pixel 10),
   set to English (US), light mode, and the default font and display size.
2. Enter the same made-up data: the name Sam Rivera and unit Troop 214;
   Tenderfoot marked earned on an earlier date; Second Class 1a and 1b
   complete; First Aid marked completed on an earlier date; Cooking 1a and 1b
   complete; and for Camping, the counselor Jordan Lee, (214) 555-0142,
   jordan.lee@example.com, requirements 1a, 1b, 1c, 2 and 3a complete, and
   three campouts on 9a.
3. Clean up the status bar with System UI demo mode. Some notification icons
   stay visible anyway, so also clear them from the notification shade.

   ```sh
   adb shell settings put global sysui_demo_allowed 1
   adb shell am broadcast -a com.android.systemui.demo -e command enter
   adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000
   adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
   adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true
   adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile hide
   ```

4. Take each screenshot with `adb exec-out screencap -p > screen.png`, then
   leave demo mode: `adb shell am broadcast -a com.android.systemui.demo -e command exit`.
5. Scale each one to 540 pixels wide, twice the width the README shows it at,
   so it stays sharp on high-density screens. Reduce it to 256 colors, which
   keeps each file under about 60 KB with no visible difference. With Pillow,
   `img.resize((540, height), Image.Resampling.LANCZOS).convert("RGB").quantize(256, method=Image.Quantize.MEDIANCUT)`,
   where `height` is `round(img.height * 540 / img.width)` (1212 for the Pixel
   10), saved with `save(path, optimize=True)`, reproduces an unchanged screenshot
   byte for byte, so only the images that changed show in the diff. Another
   resize filter changes every pixel, and a save without `optimize` changes the
   file's bytes.

## Debug tools

Debug builds run two tools that point out mistakes while you use the app;
[`ARCHITECTURE.md`](../ARCHITECTURE.md#debug-builds) says why. Release builds
have neither.

**StrictMode** reports disk access on the main thread, streams and cursors that
are never closed, and more. It logs each violation, with the stack trace of the
call that caused it, under the Logcat tag `StrictMode`. A main-thread violation
also flashes the screen. Filter Android Studio's Logcat with `tag:StrictMode`,
or run:

```sh
adb logcat -s StrictMode
```

**LeakCanary** reports activities and windows that stay in memory after they're
destroyed. To look for a leak, open a screen, rotate the phone, then leave the
screen. Objects still in memory show as a LeakCanary notification. LeakCanary
dumps and analyzes the heap when you tap the notification, or by itself once 5
objects are held or the app goes to the background. The app pauses while the
heap is dumped. Open the results from the notification, or long-press the app
icon and choose **Leaks**. On Android 13 and higher, LeakCanary asks for
permission to show notifications the first time it has something to report.
Android Studio's Profiler can also run it
([Capture a heap dump](https://developer.android.com/studio/profile/capture-heap-dump)).
A heap dump holds whatever is in memory, and LeakCanary may save it in the
phone's public Download folder, so look for leaks with made-up records, not a
real scout's.

What LeakCanary doesn't report:

- **ViewModels.** It
  [doesn't watch them](https://github.com/square/leakcanary/blob/v2.14/leakcanary-object-watcher-android-androidx/src/main/java/leakcanary/internal/AndroidXFragmentDestroyWatcher.kt#L62-L67)
  in an app without fragments.
- **Known leaks in Android itself.** It brings in
  [Plumber](https://square.github.io/leakcanary/changelog/#plumber-android-is-a-new-artifact-that-fixes-known-android-leaks),
  which works around them in debug builds only, so release builds still have
  them.
- **Leaks in instrumented tests.** They don't use `DetectLeaksAfterTestSuccess`,
  since each check dumps the heap. LeakCanary still runs in them, but doesn't
  dump the heap while JUnit is loaded.

LeakCanary keeps its results in `leaks.db` in the databases directory, so a
debug build's backup includes them along with the scout's data.

## Continuous integration

GitHub Actions (`.github/workflows/ci.yml`) runs on every pull request and on
pushes to `main`. Two jobs run side by side, and both must pass before a pull
request can merge. The pull request must also be up to date with `main`, so
the checks have run on what `main` will have after the merge; if `main` has
moved, merge it into the branch and wait for the checks again:

- **Build** runs `./gradlew build`, the same command as locally.
- **Instrumented tests** runs `app/src/androidTest` on an API 37 emulator, with
  `./gradlew pixel6Api37DebugAndroidTest` (see
  [Tests: local vs instrumented](#tests-local-vs-instrumented)). It takes 3 to
  5 minutes, less than Build's 6 to 9: Gradle builds the app and test APKs
  while it downloads the emulator and system image, then boots the emulator
  and runs the tests.

A second workflow (`.github/workflows/docs.yml`) has one quick job, which must
pass too:

- **Doc growth** fails when a pull request makes `ARCHITECTURE.md` or `PRD.md`
  longer, counted in words, unless it has the `grows-docs` label.
  [`CLAUDE.md`](../CLAUDE.md#what-goes-where) says what belongs in each; add
  the label only once the developer agrees. Adding or removing the label runs
  the check again.

The Instrumented tests job's emulator choices:

- **A Gradle Managed Device, not `reactivecircus/android-emulator-runner`.**
  That action couldn't boot any API 37 image: in
  [#14](https://github.com/bryancassell/bluecard/issues/14)'s trial its
  emulator was still booting after 20 minutes, and its maintainers report the
  same. A managed device booted API 37 and ran the tests in about 3 minutes.
- **The Google APIs image.** The lighter Automated Test Device images go up to
  API 36 only (checked October 2026). The tests need API 35 or higher anyway:
  `PdfRenderer` reads a page's text from Android 15 on. Google's later API 37
  images (37.1 and 37.2) come only with 16 KB pages.
- **No emulator cache**
  ([#5](https://github.com/bryancassell/bluecard/issues/5)). Every run, the
  setup task downloads the emulator and the 2.1 GB system image and
  cold-boots the emulator to save a snapshot, about 3 minutes that partly
  overlap building the APKs.
  [#276](https://github.com/bryancassell/bluecard/issues/276) measured caching
  it in October 2026, with the Gradle cache restored. With no cache, the job
  took about 4m10s. Caching the emulator, image, AVD and snapshot (a 4.4 GB
  entry) took about 3m50s, since restoring took over a minute. Caching only
  the AVD and snapshot (2.5 GB), keyed by the installed emulator and image
  versions, took about 3m35s, and the snapshot loaded on every runner CPU
  tried. Build takes 6 to 9 minutes and runs at the same time, so neither
  made CI finish sooner, and each would use a quarter to almost half of the
  repository's 10 GB of Actions cache, which the Gradle caches need. If Build
  ever finishes before this job, revisit the AVD-only cache, which is in
  #276's history.

When a job fails, its reports are attached to the run: lint and local test
reports as `reports`, instrumented test reports as `instrumented-test-reports`.
Open the failed run on GitHub and download them from the **Artifacts** section
of the summary page.

Pull requests are squash-merged: each one becomes a single commit on `main`,
titled with the PR title and described by the PR description, so write both as
a summary of the whole change. Other merge methods are turned off in the
repository settings and the "Protect main" ruleset.

Dependabot (`.github/dependabot.yml`) checks weekly for newer versions of the
libraries and plugins in the version catalog, the Gradle wrapper, and the
GitHub Actions used by CI. Minor and patch updates arrive together in one pull
request per ecosystem (Gradle, GitHub Actions); each major update gets its own
pull request, since it may need code changes. CI runs on those pull requests
like any other. A pull request that updates AGP or one of the libraries listed
in [Checking a release build](#checking-a-release-build) also needs that check
before it merges, since CI can't find what R8 breaks at runtime.

Version updates propose a release only after it has been out for 3 days, so a
broken or compromised release has time to be pulled first. Security updates
don't wait, and neither do two kinds of dependency:

- **AndroidX libraries**, which `dependabot.yml` excludes. With the wait on,
  Dependabot never proposes a version it can't find a release date for, and
  Google Maven only gives a date for an artifact's newest version. When that
  newest version is a pre-release, the artifact's stable releases would never
  be proposed. Libraries from other Google Maven groups (such as
  `com.google.android.material`) have the same problem and need adding to the
  exclusion. Removing it is tracked in
  [#154](https://github.com/bryancassell/bluecard/issues/154).
- **The Android Gradle plugin**, usually. Only its newest version gets a real
  release date, and that is almost always a preview. Dependabot gives the
  other versions one shared old date, so a new stable release is usually
  proposed the day it comes out.
