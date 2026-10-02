import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
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
            // R8 shrinks, optimizes and obfuscates the code, and unused resources are removed.
            // See ARCHITECTURE.md (Release build).
            isMinifyEnabled = true
            isShrinkResources = true
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

    // MigrationTestHelper reads each database version's schema from assets. The Room plugin
    // adds the schemas to instrumented tests' assets only, and migration tests run locally,
    // where Robolectric reads the debug build's assets. So debug builds carry the schemas too;
    // release builds don't.
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")

    testCoverage {
        jacocoVersion = libs.versions.jacoco.get()
    }

    lint {
        // Treat lint warnings as a signal worth fixing: fail the build on them.
        warningsAsErrors = true
        abortOnError = true
        // "A newer version is available" checks would fail the build whenever a new
        // release comes out. Dependabot proposes those updates instead.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency")
    }
}

// Room writes each database version's schema here, for reviewing schema changes and
// testing future migrations. Commit the generated files.
room {
    schemaDirectory("$projectDir/schemas")
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// Screenshot tests' reference images, committed. Every test run checks against them
// (roborazzi.test.verify in gradle.properties); `./gradlew recordRoborazziDebug` records
// them again after an intended change.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

// Coverage gate for the testing rules in CLAUDE.md, measured from local tests.
// Runs as part of `check`, so `./gradlew build` enforces it.
val coverageClassJars = objects.listProperty<RegularFile>()
val coverageClassDirs = objects.listProperty<Directory>()
val jacocoDebugCoverageVerification = tasks.register<JacocoCoverageVerification>(
    "jacocoDebugCoverageVerification"
) {
    group = "verification"
    description = "Fails if any class has less than 80% line coverage from local tests."
    dependsOn("testDebugUnitTest")
    // Generated Android, Hilt, Room and Kotlin classes, @Preview functions (kept in
    // *Preview.kt files), which only run in Android Studio, and code that only runs on a
    // device.
    val exclusions = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*PreviewKt*.class",
        // Hilt: components, injectors, factories and aggregation metadata.
        "**/Hilt_*.class",
        "**/*_HiltComponents*.class",
        "**/*_ComponentTreeDeps*.class",
        "**/*_GeneratedInjector*.class",
        "**/*_HiltModules*.class",
        "**/*_Factory*.class",
        "**/*_Provide*Factory*.class",
        "**/*_MembersInjector*.class",
        "**/hilt_aggregated_deps/**",
        "**/dagger/hilt/internal/**",
        // Room: generated database and DAO implementations.
        "**/*_Impl.class",
        "**/*_Impl\$*.class",
        // Kotlin: copies of an interface's default arguments for Java code compiled against
        // older Kotlin. Kotlin callers use the interface's own static methods instead.
        "**/*\$DefaultImpls.class",
        // Writes PDFs with Android's PdfDocument, which only runs on a device. Its instrumented
        // test (androidTest/.../PdfDocumentWriterTest) checks it instead.
        "**/data/report/PdfDocumentWriter.class"
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
val checkTestRules = tasks.register<Exec>("checkTestRules") {
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
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    // ViewModelScenario, which saves and restores a ViewModel's state as the system does.
    testImplementation(libs.androidx.lifecycle.viewmodel.testing)
    testImplementation(libs.hilt.android.testing)
    // MigrationTestHelper, which checks each database migration against the committed schemas.
    testImplementation(libs.androidx.room.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(libs.androidx.junit)
    // Compose UI tests bring in an older Espresso that fails on SDK 37 under Robolectric.
    testImplementation(libs.androidx.espresso.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
