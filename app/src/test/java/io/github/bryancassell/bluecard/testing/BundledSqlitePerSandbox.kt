package io.github.bryancassell.bluecard.testing

import java.io.File
import java.nio.file.Files
import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin

/**
 * Gives each Robolectric sandbox its own copy of the bundled SQLite library that
 * app/build.gradle.kts extracts for local tests.
 *
 * Each SDK level, and each `@GraphicsMode`, gets a sandbox with its own class loader, which loads
 * BundledSQLiteDriver's classes and so the library again. A JVM loads a library file into only
 * one class loader, so a second sandbox would fail with "already loaded in another classloader".
 * A symbolic link wouldn't help, since the JVM compares canonical paths. The driver reads
 * androidx.sqlite.driver.bundled.path each time it loads the library (androidx.sqlite 2.6.2),
 * so this points it at the sandbox's copy before each test.
 *
 * Registered in META-INF/services. Robolectric loads this class once in each sandbox, so
 * [sandboxCopy] is made once for each.
 */
class BundledSqlitePerSandbox : TestEnvironmentLifecyclePlugin {
    override fun onSetupApplicationState() {
        System.setProperty("androidx.sqlite.driver.bundled.path", sandboxCopy.absolutePath)
    }

    private companion object {
        // Both set by app/build.gradle.kts.
        val name: String = System.getProperty("androidx.sqlite.driver.bundled.name")
        val original = File(System.getProperty("bluecard.bundledSqlite.dir"), name)

        val sandboxCopy: File by lazy {
            val folder = Files.createTempDirectory("bundled-sqlite").toFile()
            original.copyTo(File(folder, name)).deleteOnExit()
            folder.apply { deleteOnExit() }
        }
    }
}
