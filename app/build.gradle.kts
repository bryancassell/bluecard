import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.ManagedVirtualDevice
import com.android.build.api.variant.ScopedArtifacts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.koin.compiler)
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
            // Installs next to a release build, which is signed with another key, so neither has
            // to be uninstalled, with its data, to install the other (#245).
            applicationIdSuffix = ".debug"
            // Record JaCoCo coverage data when local tests run.
            enableUnitTestCoverage = true
        }
        release {
            // R8 shrinks, optimizes and obfuscates the code, and unused resources are removed, as
            // Android's app optimization guide recommends. It took the APK from 10.1 MB to 2.1 MB
            // (#197).
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
        // The emulator that runs instrumented tests in CI, on the target SDK. Gradle downloads its
        // system image and starts and stops it: ./gradlew pixel6Api37DebugAndroidTest. Device
        // tests of :shared's classes, such as PdfDocumentWriterTest, are in this module too, so
        // CI starts one emulator rather than one for each module.
        managedDevices {
            localDevices {
                create("pixel6Api37") {
                    device = "Pixel 6"
                    sdkVersion = 37
                    systemImageSource = "google"
                    // The app's AndroidX native libraries must load with 16 KB pages, which Play
                    // requires from Android 15 on.
                    pageAlignment = ManagedVirtualDevice.PageAlignment.FORCE_16KB_PAGES
                    // The computer's own ABI, so the emulator runs without translation: arm64 on
                    // Apple silicon, x86_64 on CI's runners. AGP 10 defaults to arm64-v8a. AGP 9.4.1's
                    // setup task still warns that testedAbi isn't set, since it doesn't read it.
                    testedAbi =
                        if (System.getProperty("os.arch") == "aarch64") "arm64-v8a" else "x86_64"
                }
            }
        }
    }

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

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// Coverage gate for the testing rules in CLAUDE.md, measured from local tests. It checks this
// module's classes and :shared's together, counting what both modules' local tests run, since
// MainActivityTest here is the only test that reaches some of :shared's, such as
// BlueCardNavDisplay. :shared hands over its classes and coverage data through the
// configurations below (shared/build.gradle.kts). Runs as part of `check`, so `./gradlew build`
// enforces it.
val coverageAttribute = Attribute.of("io.github.bryancassell.bluecard.coverage", String::class.java)
val sharedCoverage = configurations.dependencyScope("sharedCoverage")
fun resolvableCoverage(name: String, kind: String) = configurations.resolvable(name) {
    extendsFrom(sharedCoverage.get())
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.VERIFICATION))
        attribute(coverageAttribute, kind)
    }
}
val sharedCoverageClasses = resolvableCoverage("sharedCoverageClasses", "classes")
val sharedCoverageData = resolvableCoverage("sharedCoverageData", "data")

val coverageClassJars = objects.listProperty<RegularFile>()
val coverageClassDirs = objects.listProperty<Directory>()

// Generated Android, Room and Kotlin classes, @Preview functions (kept in *Preview.kt files),
// which only run in Android Studio, and code that only runs on a device.
val coverageExclusions = listOf(
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*PreviewKt*.class",
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

// The verification and its report read the same classes and coverage data.
fun JacocoReportBase.countLocalTests() {
    dependsOn("testDebugUnitTest")
    // Locals, so the lambdas below don't hold the build script, which the configuration cache
    // can't store.
    val fileTrees = objects
    val exclusions = coverageExclusions
    classDirectories.setFrom(
        coverageClassJars,
        coverageClassDirs.map { dirs ->
            dirs.map { dir -> fileTrees.fileTree().setDir(dir).exclude(exclusions) }
        },
        sharedCoverageClasses.flatMap { it.incoming.files.elements }.map { dirs ->
            dirs.map { dir -> fileTrees.fileTree().setDir(dir).exclude(exclusions) }
        }
    )
    executionData.setFrom(
        layout.buildDirectory.file(
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
        ),
        sharedCoverageData
    )
}

val jacocoDebugCoverageVerification = tasks.register<JacocoCoverageVerification>(
    "jacocoDebugCoverageVerification"
) {
    group = "verification"
    description = "Fails if any class has less than 80% line coverage from local tests."
    countLocalTests()
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

// What the gate checks, as a report that shows which lines no test runs.
val jacocoDebugCoverageReport = tasks.register<JacocoReport>("jacocoDebugCoverageReport") {
    group = "verification"
    description =
        "Writes an HTML report of the line coverage jacocoDebugCoverageVerification checks."
    countLocalTests()
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
    implementation(project(":shared"))
    sharedCoverage(project(":shared"))
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.koin.android)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)

    testImplementation(project(":testing"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.datetime)
    // BackupRulesTest finds the profile's DataStore file as the app names it.
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.androidx.junit)
    // Compose UI tests bring in an older Espresso that fails on SDK 37 under Robolectric.
    testImplementation(libs.androidx.espresso.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.okio)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Reports leaked activities and windows in debug builds. It starts itself; its launcher
    // icon is turned off in src/debug/res/values/leak_canary.xml.
    debugImplementation(libs.leakcanary.android)
}
