package io.github.bryancassell.bluecard.data.progress

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import okio.Path

// Android's helper reads the schemas through Robolectric's instrumentation, from the local tests'
// assets, which shared/build.gradle.kts adds them to.
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
