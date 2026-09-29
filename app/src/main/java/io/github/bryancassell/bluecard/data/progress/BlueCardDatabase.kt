package io.github.bryancassell.bluecard.data.progress

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [BadgeProgress::class, RequirementProgress::class, TrackerEntry::class],
    version = 1
)
@TypeConverters(Converters::class)
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
