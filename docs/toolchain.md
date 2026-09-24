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
| `./gradlew build` | Compiles, runs unit tests and lint. This is the command CI runs. |
| `./gradlew test` | Runs local unit tests only (`app/src/test`). |
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
apps. AGP 9 has Kotlin support built in, so the build only adds the Compose
compiler plugin on top.

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

**Lint as a gate.** `warningsAsErrors = true` makes lint warnings fail the
build, so problems get fixed when they appear instead of piling up.

## Project layout

```
.
├── build.gradle.kts            Root build: declares plugins for all modules
├── settings.gradle.kts         Module list and repositories
├── gradle.properties           Gradle and Android build settings
├── gradle/
│   ├── libs.versions.toml      Version catalog
│   └── wrapper/                Pinned Gradle version
└── app/
    ├── build.gradle.kts        App module: SDK levels, dependencies, lint
    └── src/
        ├── main/               App code, manifest, resources
        ├── test/               Local unit tests (run on your computer)
        └── androidTest/        Instrumented tests (run on a device)
```

## Tests: local vs instrumented

- **Local unit tests** (`app/src/test`) run on your computer's JVM in seconds.
  Put as much logic as possible where these can test it.
- **Instrumented tests** (`app/src/androidTest`) run on an emulator or device
  and can exercise real Android and Compose UI. They are slower and need a
  device, so `./gradlew build` does not run them.

To run instrumented tests, create a virtual device once in Android Studio
(**Device Manager → Create Virtual Device**), start it, then run
`./gradlew connectedAndroidTest`.
