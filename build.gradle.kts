// Top-level build file. Plugins are declared here with `apply false` so every
// module resolves the same plugin versions from the version catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.koin.compiler) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
}

// Code formatting. `./gradlew spotlessCheck` fails on unformatted code (and runs
// as part of `./gradlew check` and `./gradlew build`); `./gradlew spotlessApply`
// fixes it. ktlint reads its style settings from .editorconfig.
//
// The targets name only modules' src folders and the build scripts, so Spotless
// never scans a build folder or .claude/, where Claude Code keeps other sessions'
// git worktrees. A "**/" target skips only this project's own build, .gradle and
// .git folders, and a targetExclude is resolved by scanning every file it matches
// (FormatExtension.parseTargetIsExclude, Spotless 8.10.4). Either way, Spotless
// would read folders other builds are writing, failing when a file disappears
// mid-scan (#16), and would check or reformat other sessions' work. A module
// nested more than one folder deep needs its own target.
spotless {
    kotlin {
        target("*/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
