import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The code the Android app and, later, the iOS app share (ARCHITECTURE.md, Architecture
// approach).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    // Registers lint's tasks, which the Android-KMP plugin doesn't on its own (AGP 9.4.1), and
    // makes `check` run them. Android Studio's Kotlin Multiplatform module template applies it too.
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kotlin.compose)
    // For its resources only, for now: Compose and Material 3 are still Google's artifacts (#345).
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.room3)
    alias(libs.plugins.roborazzi)
    jacoco
}

kotlin {
    compilerOptions {
        // Expected and actual classes are in Beta, and the compiler warns of each: Room's database
        // constructor and DocumentUri are. The Kotlin docs give this option to turn the warning
        // off: https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // With Android as the only target, the compiler lets common code call Android and JVM APIs.
    // With iOS's targets too, compileCommonMainKotlinMetadata refuses them, and needs no Xcode,
    // so CI runs it with -Pbluecard.checkCommonCode. The targets are declared only then: built
    // always, they'd make `build` link and run tests on an iOS simulator, which needs Xcode. #346
    // declares them for good.
    if (providers.gradleProperty("bluecard.checkCommonCode").isPresent) {
        iosArm64()
        iosSimulatorArm64()
    }

    android {
        namespace = "io.github.bryancassell.bluecard.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        // report_paths.xml, the one Android resource left: the strings, icons and catalog are
        // Compose Multiplatform resources (commonMain/composeResources). Off by default in this
        // plugin.
        androidResources {
            enable = true
        }

        withHostTest {
            // Robolectric needs the resources to run activities and Compose UI locally.
            isIncludeAndroidResources = true
            // Records JaCoCo coverage data, which :app's coverage gate reads.
            enableCoverage = true
            // Robolectric runs tests on the target SDK unless a test asks for another.
            targetSdk { version = release(libs.versions.android.targetSdk.get().toInt()) }
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

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.serialization.json)
            // Its dates and time zones are in this module's API, such as ZonedClock's.
            api(libs.kotlinx.datetime)
            implementation(libs.koin.annotations)
            implementation(libs.androidx.room3.runtime)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.bignum)
            implementation(libs.okio)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.androidx.activity.compose)
            // Compose's types are in this module's API, such as its composables' parameters and
            // the color schemes the app's window theme matches.
            api(project.dependencies.platform(libs.androidx.compose.bom))
            api(libs.androidx.compose.ui)
            implementation(libs.androidx.compose.ui.graphics)
            implementation(libs.androidx.compose.ui.tooling.preview)
            api(libs.androidx.compose.material3)
        }

        getByName("androidHostTest").dependencies {
            implementation(project(":testing"))
            // androidContext(), for AppKoinRule.
            implementation(libs.koin.android)
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.kotlinx.coroutines.test)
            // ViewModelScenario, which saves and restores a ViewModel's state as the system does.
            implementation(libs.androidx.lifecycle.viewmodel.testing)
            // MigrationTestHelper, which checks each database migration against the committed
            // schemas.
            implementation(libs.androidx.room3.testing)
            implementation(libs.androidx.junit)
            // Compose UI tests bring in an older Espresso that fails on SDK 37 under Robolectric.
            implementation(libs.androidx.espresso.core)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test.junit4)
            // Declares the activity that Compose UI tests show their content in.
            implementation(libs.androidx.compose.ui.test.manifest)
            // Google's Accessibility Test Framework checks (AccessibilityChecks.kt).
            implementation(libs.accessibility.test.framework)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
        }
    }
}

dependencies {
    // Strictly, so a Room update that needs a newer androidx.sqlite fails to resolve until
    // `sqlite` is raised with it: local tests load sqlite-bundled-jvm's library at that version.
    // Declared here, as the source set's block can't change a catalog entry's version.
    "androidMainImplementation"(libs.androidx.sqlite.bundled) {
        version { strictly(libs.versions.sqlite.get()) }
    }
    add("kspAndroid", libs.androidx.room3.compiler)
    // Android Studio's Compose previews need it at runtime. It isn't passed on to :app, which
    // adds it to debug builds only.
    "androidRuntimeClasspath"(libs.androidx.compose.ui.tooling)
}

// The generated Res class, with the strings, icons and catalog in
// src/commonMain/composeResources. Without a package, it would be named after this module's
// folder. It's public so :testing's helpers can read the strings too.
compose.resources {
    packageOfResClass = "io.github.bryancassell.bluecard.resources"
    publicResClass = true
}

// Room writes each database version's schema here, for reviewing schema changes and
// testing future migrations. Commit the generated files.
room3 {
    schemaDirectory("$projectDir/schemas")
}

// Screenshot tests' reference images, committed. Every test run checks against them
// (roborazzi.test.verify in gradle.properties); `./gradlew recordRoborazziAndroidHostTest`
// records them again after an intended change.
roborazzi {
    outputDir.set(file("src/androidHostTest/screenshots"))
}

// `./gradlew test` runs every module's local tests, as it does in :app; this plugin names its
// own testAndroidHostTest.
tasks.register("test") {
    group = "verification"
    description = "Runs this module's local tests."
    dependsOn("testAndroidHostTest")
}

tasks.withType<Test>().configureEach {
    // Robolectric reaches into JDK internals that JDK 17+ hides by default.
    // Other flags from https://robolectric.org/getting-started/ may be needed later.
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
    // Count code that runs under Robolectric, which loads classes in its own class loader.
    // https://github.com/robolectric/robolectric/issues/2230
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// Local tests get BundledSQLiteDriver's Android library, whose loader calls
// System.loadLibrary("sqliteJni") and finds nothing on a desktop JVM. Since androidx.sqlite 2.6.2
// it loads <androidx.sqlite.driver.bundled.path>/<...name> instead when both are set, so the
// tests get the host's library from sqlite-bundled-jvm, which registers the same JNI functions.
// BundledSqlitePerSandbox, in this module's tests, sets the path.
val bundledSqliteJvm = configurations.create("bundledSqliteJvm") {
    isCanBeConsumed = false
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    }
}
dependencies {
    bundledSqliteJvm(libs.androidx.sqlite.bundled.jvm)
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

// :app's coverage gate counts this module's classes, with the coverage that both modules' local
// tests record (app/build.gradle.kts). They're handed over as Gradle's guide to sharing outputs
// between projects describes, rather than :app reading this module's tasks or build folder:
// https://docs.gradle.org/current/userguide/how_to_share_outputs_between_projects.html
val coverageAttribute = Attribute.of("io.github.bryancassell.bluecard.coverage", String::class.java)
val coverageClassJars = objects.listProperty<RegularFile>()
val coverageClassDirs = objects.listProperty<Directory>()

interface Archives {
    @get:Inject val operations: ArchiveOperations
}

// The classes in one folder, the jars' unpacked, so the gate's exclusions apply to them all.
val coverageClasses = tasks.register<Sync>("coverageClasses") {
    // Locals, so the lambda below doesn't hold the build script, which the configuration cache
    // can't store.
    val archives = objects.newInstance<Archives>().operations
    val jars = coverageClassJars
    from(coverageClassDirs)
    from(jars.map { files -> files.map { archives.zipTree(it) } })
    include("**/*.class")
    into(layout.buildDirectory.dir("coverage/classes"))
}
androidComponents {
    onVariants { variant ->
        // MigrationTestHelper reads each database version's schema from assets. The Room plugin
        // adds the schemas to device tests' assets only, and migration tests run locally, so
        // the host tests' assets carry them too; the library's own assets don't, so the app
        // doesn't ship them. Nothing makes merging assets wait for Room to copy a new database
        // version's schema here, so the first test run after a version bump can miss it, and
        // MigrationTest fails until it runs again (#96). Room's schema copy declares no
        // outputs, so Gradle can't order other tasks after it, and working around that needed
        // more build code than a rerun is worth.
        variant.hostTests.values.forEach { it.sources.assets?.addStaticSourceDirectory("schemas") }
        variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
            .use(coverageClasses)
            .toGet(ScopedArtifact.CLASSES, { coverageClassJars }, { coverageClassDirs })
    }
}
configurations.consumable("coverageClassesElements") {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.VERIFICATION))
        attribute(coverageAttribute, "classes")
    }
    outgoing.artifact(coverageClasses)
}
configurations.consumable("coverageSourcesElements") {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.VERIFICATION))
        attribute(coverageAttribute, "sources")
    }
    // The main source sets' folders that exist: common code's and androidMain's.
    kotlin.sourceSets.filter { it.name.endsWith("Main") }
        .flatMap { it.kotlin.srcDirs }
        .filter { it.isDirectory }
        .forEach { outgoing.artifact(it) }
}
configurations.consumable("coverageDataElements") {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.VERIFICATION))
        attribute(coverageAttribute, "data")
    }
    // Where AGP writes it. The task doesn't exist yet while this script runs, so its own setting
    // can't be read here; :app's gate fails, naming the file, if it isn't there.
    outgoing.artifact(
        layout.buildDirectory.file(
            "outputs/unit_test_code_coverage/androidHostTest/testAndroidHostTest.exec"
        )
    ) { builtBy("testAndroidHostTest") }
}
