package io.github.bryancassell.bluecard.data.progress

import androidx.room3.RoomDatabase
import androidx.room3.testing.MigrationTestHelper
import okio.Path

/** A builder for a database in [file], as the platform's app makes it. */
expect fun databaseBuilder(file: Path): RoomDatabase.Builder<BlueCardDatabase>

/**
 * Room's migration helper for a database in [file], on the SQLite the app bundles. Each platform's
 * reads the committed schemas from a different place.
 */
expect fun migrationTestHelper(file: Path): MigrationTestHelper

/** The JSON of [version]'s schema, committed in shared/schemas/, from where the helper reads it. */
expect fun committedSchema(version: Int): String
