import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

android {
    namespace = "io.github.bryancassell.bluecard"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.bryancassell.bluecard"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Record JaCoCo coverage data when local tests run.
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric needs the app's resources to run activities and Compose UI locally.
        unitTests.isIncludeAndroidResources = true
        // Robolectric reaches into JDK internals that JDK 17+ hides by default.
        // Other flags from https://robolectric.org/getting-started/ may be needed later.
        unitTests.all {
            it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    testCoverage {
        jacocoVersion = libs.versions.jacoco.get()
    }

    lint {
        // Treat lint warnings as a signal worth fixing: fail the build on them.
        warningsAsErrors = true
        abortOnError = true
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// Coverage gate for the testing rules in CLAUDE.md, measured from local tests.
// Runs as part of `check`, so `./gradlew build` enforces it.
val coverageClassJars = objects.listProperty<RegularFile>()
val coverageClassDirs = objects.listProperty<Directory>()
val jacocoDebugCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    group = "verification"
    description = "Fails if local test line coverage is below the minimums."
    dependsOn("testDebugUnitTest")
    // Generated Android classes, and @Preview functions (kept in *Preview.kt files),
    // which only run in Android Studio.
    val exclusions = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*PreviewKt*.class"
    )
    val fileTrees = objects
    classDirectories.setFrom(
        coverageClassJars,
        coverageClassDirs.map { dirs ->
            dirs.map { dir -> fileTrees.fileTree().setDir(dir).exclude(exclusions) }
        }
    )
    executionData.setFrom(
        layout.buildDirectory.file(
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
        )
    )
    violationRules {
        // Module-wide line coverage must not decrease. This is a ratchet: when coverage
        // goes up, raise the minimum to the new value (rounded down). Lowering it needs
        // an explanation in the pull request.
        rule {
            limit {
                counter = "LINE"
                minimum = "1.00".toBigDecimal()
            }
        }
        // Every class needs at least 80% line coverage.
        rule {
            element = "CLASS"
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// Testing rules from CLAUDE.md that a text search can catch.
val checkTestRules by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails on skipped tests, logic classes without tests, or mocking libraries."
    commandLine(rootProject.file("scripts/check-test-rules.sh"))
}

tasks.named("check") { dependsOn(jacocoDebugCoverageVerification, checkTestRules) }

// Count code that runs under Robolectric, which loads app classes in its own class loader.
// https://github.com/robolectric/robolectric/issues/2230
tasks.withType<Test>().configureEach {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

androidComponents {
    onVariants(selector().withName("debug")) { variant ->
        variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
            .use(jacocoDebugCoverageVerification)
            .toGet(ScopedArtifact.CLASSES, { coverageClassJars }, { coverageClassDirs })
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    // Compose UI tests bring in an older Espresso that fails on SDK 37 under Robolectric.
    testImplementation(libs.androidx.espresso.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
