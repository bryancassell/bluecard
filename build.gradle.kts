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
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}
