import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.ManagedVirtualDevice
import com.android.build.api.variant.ScopedArtifacts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.room3)
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
        // system image and starts and stops it: ./gradlew pixel6Api37DebugAndroidTest.
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

    // MigrationTestHelper reads each database version's schema from assets. The Room plugin
    // adds the schemas to instrumented tests' assets only, and migration tests run locally,
    // where Robolectric reads the debug build's assets. So debug builds carry the schemas too;
    // release builds don't. Nothing makes merging assets wait for Room to copy a new database
    // version's schema here, so the first test run after a version bump can miss it, and
    // MigrationTest fails until it runs again (#96). Room's schema copy declares no outputs, so
    // Gradle can't order other tasks after it, and working around that needed more build code
    // than a rerun is worth.
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
room3 {
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
    // Generated Android, Room and Kotlin classes, @Preview functions (kept in
    // *Preview.kt files), which only run in Android Studio, and code that only runs on a
    // device.
    val exclusions = listOf(
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

// Local tests get BundledSQLiteDriver's Android library, whose loader calls
// System.loadLibrary("sqliteJni") and finds nothing on a desktop JVM. Since androidx.sqlite 2.6.2
// it loads <androidx.sqlite.driver.bundled.path>/<...name> instead when both are set, so the
// tests get the host's library from sqlite-bundled-jvm, which registers the same JNI functions.
// testing/BundledSqlitePerSandbox sets the path.
val bundledSqliteJvm = configurations.create("bundledSqliteJvm") {
    isCanBeConsumed = false
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    }
}
// The folder and file name in sqlite-bundled-jvm's natives/ for macOS (developers) or Linux (CI).
val bundledSqliteHost = run {
    val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"
    if ("mac" in System.getProperty("os.name").lowercase()) {
        "osx_$arch" to "libsqliteJni.dylib"
    } else {
        "linux_$arch" to "libsqliteJni.so"
    }
}
val extractBundledSqlite = tasks.register<Sync>("extractBundledSqlite") {
    // Resolved when the task runs, not while Gradle plans the build.
    from(zipTree(bundledSqliteJvm.elements.map { it.single().asFile })) {
        include("natives/${bundledSqliteHost.first}/*")
        eachFile { path = name }
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("bundledSqlite"))
}

// The library is an input of the tests by its path in the build folder, not the absolute path a
// system property would give, so the build cache can reuse results from another checkout.
abstract class BundledSqliteArguments : CommandLineArgumentProvider {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val folder: DirectoryProperty

    @get:Input
    abstract val name: Property<String>

    override fun asArguments() = listOf(
        "-Dbluecard.bundledSqlite.dir=${folder.get().asFile.absolutePath}",
        "-Dandroidx.sqlite.driver.bundled.name=${name.get()}"
    )
}
tasks.withType<Test>().configureEach {
    jvmArgumentProviders += objects.newInstance<BundledSqliteArguments>().apply {
        folder.set(layout.dir(extractBundledSqlite.map { it.destinationDir }))
        name.set(bundledSqliteHost.second)
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
    implementation(libs.kotlinx.datetime)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.annotations)
    implementation(libs.androidx.room3.runtime)
    // Strictly, so a Room update that needs a newer androidx.sqlite fails to resolve until
    // `sqlite` is raised with it: local tests load sqlite-bundled-jvm's library at that version.
    implementation(libs.androidx.sqlite.bundled) {
        version { strictly(libs.versions.sqlite.get()) }
    }
    ksp(libs.androidx.room3.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.bignum)
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
    // MigrationTestHelper, which checks each database migration against the committed schemas.
    testImplementation(libs.androidx.room3.testing)
    bundledSqliteJvm(libs.androidx.sqlite.bundled.jvm)
    testImplementation(libs.androidx.junit)
    // Compose UI tests bring in an older Espresso that fails on SDK 37 under Robolectric.
    testImplementation(libs.androidx.espresso.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Google's Accessibility Test Framework checks (testing/AccessibilityChecks.kt).
    testImplementation(libs.accessibility.test.framework)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Reports leaked activities and windows in debug builds. It starts itself; its launcher
    // icon is turned off in src/debug/res/values/leak_canary.xml.
    debugImplementation(libs.leakcanary.android)
}
