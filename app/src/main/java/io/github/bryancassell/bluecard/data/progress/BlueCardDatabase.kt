package io.github.bryancassell.bluecard.data.progress

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase

// Each version's schema is in app/schemas/, and MigrationTest checks each migration.
// Version 2 added TrackerEntry.rowNumber, version 3 TrackerEntry.addedDate, and version 4
// RequirementProgress.signedOffBy.
//
// On Android, Room 3 finds the generated BlueCardDatabase_Impl by reflection, and its keep rule
// keeps it in release builds. iOS has no reflection to find it, so in common code (#345) this
// needs @ConstructedBy, with an expect object whose actuals Room generates.
@Database(
    entities = [BadgeProgress::class, RequirementProgress::class, TrackerEntry::class],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ]
)
@ColumnTypeConverters(Converters::class)
abstract class BlueCardDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    companion object {
        /**
         * The database file name. The backup rules include the whole databases directory, so
         * they don't name it; BackupRulesTest checks that they cover it.
         */
        const val NAME = "bluecard.db"
    }
}
