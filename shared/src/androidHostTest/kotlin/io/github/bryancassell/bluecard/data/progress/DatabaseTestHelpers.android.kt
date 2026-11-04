package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import okio.Path

// Each of these needs Robolectric.

actual fun databaseBuilder(file: Path) = Room.databaseBuilder(
    ApplicationProvider.getApplicationContext<Context>(),
    file.toString(),
    BlueCardDatabaseConstructor::initialize
)

// Android's helper reads the schemas through the instrumentation, from the local tests' assets,
// which shared/build.gradle.kts adds them to. It's also a JUnit rule, which closes the
// connections it made when a test ends, but the tests close each one themselves.
actual fun migrationTestHelper(file: Path) = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    File(file.toString()),
    BundledSQLiteDriver(),
    BlueCardDatabase::class,
    BlueCardDatabaseConstructor::initialize
)

actual fun committedSchema(version: Int) = InstrumentationRegistry.getInstrumentation().context
    .assets.open("${BlueCardDatabase::class.java.name}/$version.json")
    .use { it.reader().readText() }
