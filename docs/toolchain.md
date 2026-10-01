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
| `./gradlew build` | Compiles, runs local tests, checks coverage and the testing rules, runs lint and the formatting check. This is the command CI runs. |
| `./gradlew spotlessApply` | Reformats all Kotlin and Gradle files to the project style. |
| `./gradlew spotlessCheck` | Fails if any file is not formatted (also part of `build`). |
| `./gradlew test` | Runs local tests only (`app/src/test`). |
| `./gradlew createDebugUnitTestCoverageReport` | Writes an HTML coverage report for local tests to `app/build/reports/coverage/test/debug/index.html`. |
| `./gradlew lint` | Runs Android lint. Reports are in `app/build/reports/`. |
| `./gradlew assembleDebug` | Builds an installable debug APK (`app/build/outputs/apk/debug/`). |
| `./gradlew installDebug` | Installs the debug app on a running emulator or connected device. |
| `./gradlew connectedAndroidTest` | Runs instrumented tests (`app/src/androidTest`) on an emulator or device. |
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
- **Instrumented tests** (`app/src/androidTest`) run on an emulator or device.
  Use them only for behavior that needs a real Android runtime. They are slower
  and need a device, so `./gradlew build` does not run them. There are none yet.

To run instrumented tests, create a virtual device once in Android Studio
(**Device Manager → Create Virtual Device**), start it, then run
`./gradlew connectedAndroidTest`.

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
   adb shell bmgr backupnow io.github.bryancassell.bluecard
   adb shell pm uninstall --user 0 io.github.bryancassell.bluecard
   adb install -t app/build/outputs/apk/debug/app-debug.apk
   ```

4. Before opening the app, list the restored files. Expect only the files the
   rules include: `files/datastore/profile.preferences_pb`, and
   `databases/bluecard.db` (with its `-wal` and `-shm` files, if present) if the
   app had opened its database. Any other file, such as `files/profileInstalled`,
   means the rules include too much. Then open the app and check that the data
   is back.

   ```sh
   adb shell run-as io.github.bryancassell.bluecard find . -type f
   ```

5. Put back the settings you noted in step 1: select the transport that was
   marked `*`, remove the test transport's settings, and turn backup off again
   if it was off.

   ```sh
   adb shell bmgr transport <transport noted in step 1>
   adb shell settings delete secure backup_local_transport_parameters
   adb shell bmgr enable false   # only if backup was off in step 1
   ```

Device-to-device transfer, used when setting up a new phone from an old one,
follows the `<device-transfer>` rules. To check it, use the
[`test_d2d.sh` script](https://developer.android.com/identity/data/testingbackup#TestingTransfer)
from the same page, on an emulator with a Google Play system image. The script
switches back to the Google backup transport before reinstalling, because that
transport performs the restore; if it's skipped, logcat shows "Can't restore
from D2d Transport". Its cleanup selects that transport again but leaves backup
on, so turn backup off afterwards if it was off in step 1.

## Continuous integration

GitHub Actions (`.github/workflows/ci.yml`) runs on every pull request and on
pushes to `main`. Its **Build** job runs `./gradlew build`, the same command as
locally, and must pass before a pull request can merge.

When the job fails, the lint and test reports are attached to the run as a
`reports` artifact: open the failed run on GitHub and download it from the
**Artifacts** section of the summary page.

CI does not run instrumented tests yet, because there are none; an emulator job
will be added with the first test that needs a real device.

Pull requests are squash-merged: each one becomes a single commit on `main`,
titled with the PR title and described by the PR description, so write both as
a summary of the whole change. Other merge methods are turned off in the
repository settings and the "Protect main" ruleset.

Dependabot (`.github/dependabot.yml`) checks weekly for newer versions of the
libraries and plugins in the version catalog, the Gradle wrapper, and the
GitHub Actions used by CI. Minor and patch updates arrive together in one pull
request per ecosystem (Gradle, GitHub Actions); each major update gets its own
pull request, since it may need code changes. CI runs on those pull requests
like any other.

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
