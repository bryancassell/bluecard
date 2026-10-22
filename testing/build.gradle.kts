import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Fakes and test helpers that both :shared's and :app's local tests use, as Android's
// modularization guide suggests for test code more than one module needs:
// https://developer.android.com/topic/modularization/patterns#test-modules
// Code only one module's tests use stays with them.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    // Registers lint's tasks, as in shared/build.gradle.kts.
    alias(libs.plugins.android.lint)
}

kotlin {
    android {
        namespace = "io.github.bryancassell.bluecard.testing"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        lint {
            // As in the other modules: fail the build on warnings, apart from newer versions.
            warningsAsErrors = true
            abortOnError = true
            disable += setOf("AndroidGradlePluginVersion", "GradleDependency")
            // Lint checks this module as production code, but it's all test code, which may use
            // what libraries mark @VisibleForTesting, such as Compose's ViewRootForTest.
            disable += "VisibleForTests"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.core.ktx)
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.core)
            implementation(libs.androidx.junit)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test.junit4)
        }
    }
}
