// Top-level build file. Plugins are declared here with `apply false` so every
// module resolves the same plugin versions from the version catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
