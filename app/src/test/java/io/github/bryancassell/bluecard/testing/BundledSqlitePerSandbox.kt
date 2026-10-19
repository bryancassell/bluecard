package io.github.bryancassell.bluecard.testing

import java.io.File
import java.nio.file.Files
import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin

/**
 * SPIKE (#339): gives each Robolectric sandbox its own copy of the bundled SQLite library.
 *
 * Each sandbox loads its own copy of BundledSQLiteDriver's classes, and so loads the library again.
 * A JVM loads a library file into only one class loader, so a second sandbox would fail with
 * "already loaded in another classloader". The driver reads androidx.sqlite.driver.bundled.path
 * each time it loads the library, so this points it at a copy for the sandbox before each test.
 * Registered in META-INF/services; Robolectric calls it before each test.
 */
class BundledSqlitePerSandbox : TestEnvironmentLifecyclePlugin {
    override fun onSetupApplicationState() {
        System.setProperty(PATH, sandboxCopy.absolutePath)
    }

    private companion object {
        const val PATH = "androidx.sqlite.driver.bundled.path"

        // Set by app/build.gradle.kts.
        val name: String = System.getProperty("androidx.sqlite.driver.bundled.name")
        val original = File(System.getProperty("bluecard.bundledSqlite.dir"), name)

        // This class, and so this copy, is loaded once in each sandbox.
        val sandboxCopy: File by lazy {
            val dir = Files.createTempDirectory("bundled-sqlite").toFile()
            original.copyTo(File(dir, name)).deleteOnExit()
            dir.apply { deleteOnExit() }
        }
    }
}
