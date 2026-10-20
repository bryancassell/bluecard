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
        // Made when first needed, so a missing property's message shows at every test, rather
        // than once and then behind "Could not initialize class". When the JVM exits, the
        // library and then its folder are deleted: deleteOnExit runs in the reverse order.
        val sandboxCopy: File by lazy {
            val name = property("androidx.sqlite.driver.bundled.name")
            val original = File(property("bluecard.bundledSqlite.dir"), name)
            val folder = Files.createTempDirectory("bundled-sqlite").toFile()
            folder.deleteOnExit()
            original.copyTo(File(folder, name)).deleteOnExit()
            folder
        }

        fun property(key: String): String = checkNotNull(System.getProperty(key)) {
            "$key isn't set. app/build.gradle.kts sets it for local tests run through Gradle."
        }
    }
}
