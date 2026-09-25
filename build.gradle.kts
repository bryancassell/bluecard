// Top-level build file. Plugins are declared here with `apply false` so every
// module resolves the same plugin versions from the version catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

// Code formatting. `./gradlew spotlessCheck` fails on unformatted code (and runs
// as part of `./gradlew check` and `./gradlew build`); `./gradlew spotlessApply`
// fixes it. ktlint reads its style settings from .editorconfig.
//
// Targets starting with "**/" already skip build, .gradle and .git folders while
// scanning. Don't add targetExclude("**/build/**"): Spotless scans the whole
// project, build folders included, to evaluate it, which races with other tasks
// writing to app/build (#16).
spotless {
    kotlin {
        target("**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
