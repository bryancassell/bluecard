package io.github.bryancassell.bluecard.data.progress

import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.bryancassell.bluecard.data.systemFileSystem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.Path
import okio.Path.Companion.toPath
import platform.posix.getenv

actual fun databaseBuilder(file: Path) =
    Room.databaseBuilder(file.toString(), BlueCardDatabaseConstructor::initialize)

// iOS's helper reads the schemas from a folder, rather than from assets as Android's does. Its
// finished() closes the connections it made, but the tests close each one themselves.
actual fun migrationTestHelper(file: Path) = MigrationTestHelper(
    schemaDirectory(),
    file.toString(),
    BundledSQLiteDriver(),
    BlueCardDatabase::class,
    BlueCardDatabaseConstructor::initialize
)

actual fun committedSchema(version: Int) = systemFileSystem.read(
    schemaDirectory().toPath() / BlueCardDatabase::class.qualifiedName!! / "$version.json"
) { readUtf8() }

/**
 * shared/schemas, as an absolute path on the Mac, which the simulator's tests can read. A test
 * starts in the simulator's own data folder, so shared/build.gradle.kts passes the path.
 */
@OptIn(ExperimentalForeignApi::class)
private fun schemaDirectory(): String = checkNotNull(getenv(SCHEMAS_VARIABLE)?.toKString()) {
    "$SCHEMAS_VARIABLE isn't set. shared/build.gradle.kts sets it for iOS tests run through Gradle."
}

private const val SCHEMAS_VARIABLE = "BLUECARD_SCHEMAS"
