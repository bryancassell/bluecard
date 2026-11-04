package io.github.bryancassell.bluecard.data.progress

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import kotlinx.coroutines.sync.Mutex

// Each version's schema is in shared/schemas/, and MigrationTest checks each migration.
// Version 2 added TrackerEntry.rowNumber, version 3 TrackerEntry.addedDate, and version 4
// RequirementProgress.signedOffBy.
@ConstructedBy(BlueCardDatabaseConstructor::class)
@Database(
    entities = [BadgeProgress::class, RequirementProgress::class, TrackerEntry::class],
    version = BlueCardDatabase.VERSION,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ]
)
@ColumnTypeConverters(Converters::class)
abstract class BlueCardDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    /**
     * Lets writes in one at a time, in the order they were made (it's first come, first served).
     * It's the database's, so writes queue in order however many repositories use it: a save of
     * notes can't run ahead of a clear made on another screen.
     */
    internal val writeOrder = Mutex()

    companion object {
        /**
         * The database file name. The backup rules include the whole databases directory, so
         * they don't name it; BackupRulesTest checks that they cover it.
         */
        const val NAME = "bluecard.db"

        /** The schema version, which Room saves as the database file's `user_version`. */
        const val VERSION = 4

        /**
         * The identity hash Room saves in the database for [VERSION]'s schema, as in its file in
         * shared/schemas/. [SetAsideDamagedDatabaseDriver] compares a database's with it before
         * Room opens it. Room's own copy is in the code it generates, which it keeps internal
         * (`RoomOpenDelegate`, Room 3.0.3). SetAsideDamagedDatabaseDriverTest fails until this is
         * updated for a new version.
         */
        const val IDENTITY_HASH = "bdfb11170140a5d1e2c7675c1126dcb3"
    }
}

/**
 * Builds the database, without the reflection that iOS doesn't have. Room's compiler generates
 * each platform's actual, so the IDE's warning that there is none is suppressed, as the Room for
 * Kotlin Multiplatform guide does: https://developer.android.com/kotlin/multiplatform/room
 */
@Suppress("KotlinNoActualForExpect")
expect object BlueCardDatabaseConstructor : RoomDatabaseConstructor<BlueCardDatabase> {
    override fun initialize(): BlueCardDatabase
}
