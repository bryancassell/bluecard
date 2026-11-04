package io.github.bryancassell.bluecard.data.progress

import androidx.room3.testing.MigrationTestHelper
import okio.Path

/**
 * Room's migration helper for a database in [file], on the SQLite the app bundles. Each platform's
 * reads the committed schemas from a different place, and closes the connections it opened
 * differently: Android's is a JUnit rule, and iOS's test calls [MigrationTestHelper.finished].
 */
expect fun migrationTestHelper(file: Path): MigrationTestHelper

/** The JSON of [version]'s schema, committed in shared/schemas/, from where the helper reads it. */
expect fun committedSchema(version: Int): String
