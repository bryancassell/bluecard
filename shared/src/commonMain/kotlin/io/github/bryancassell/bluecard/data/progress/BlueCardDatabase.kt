package io.github.bryancassell.bluecard.data.progress

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import kotlinx.coroutines.sync.Mutex

// Each version's schema is in shared/schemas/, and MigrationTest checks each migration. TABLES
// says which version added each column.
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
         * Each schema version, with the identity hash Room saves in a database of that version,
         * as in the version's file in shared/schemas/. [SetAsideDamagedDatabaseDriver] checks a
         * database's pair against them before Room opens it. Room keeps its own copy of only
         * the latest, in code it keeps internal (`RoomOpenDelegate`, Room 3.0.3).
         *
         * Room computes the hash from the tables alone, so a new version that keeps its schema,
         * such as one whose migration only changes data, has a line of its own with the same
         * hash. If a Room update ever computes another hash for a version, as CI would show, the
         * old line stays beside the new one, since installed databases keep the old hash.
         * SetAsideDamagedDatabaseDriverTest fails until every version's hash is here.
         */
        val IDENTITY_HASHES = listOf(
            1 to "380113d95e4e8e587e57cce0d98668d1",
            2 to "67dcaffcb2188a16a46be6fa4f75ba25",
            3 to "3e67ea8b76032a8049f6f3cad8313660",
            4 to "bdfb11170140a5d1e2c7675c1126dcb3"
        )

        /**
         * Each table Room creates for the entities, with the columns and foreign keys it has in
         * each schema version, as in shared/schemas/. [SetAsideDamagedDatabaseDriver] checks a
         * database's tables against its version's before Room opens it.
         * SetAsideDamagedDatabaseDriverTest fails until every version's are here.
         */
        internal val TABLES = listOf(
            ProgressTable(
                "badge_progress",
                listOf(
                    "badgeId",
                    "requirementsVersion",
                    "startedDate",
                    "completedOnPriorDate",
                    "counselor_name",
                    "counselor_phone",
                    "counselor_email"
                )
            ),
            ProgressTable(
                "requirement_progress",
                listOf("badgeId", "requirementNumber", "completed", "completedDate", "comment"),
                addedColumns = mapOf("signedOffBy" to 4),
                foreignKeys = setOf(BADGE_FOREIGN_KEY)
            ),
            ProgressTable(
                "tracker_entry",
                listOf("id", "badgeId", "requirementNumber", "values"),
                addedColumns = mapOf("rowNumber" to 2, "addedDate" to 3),
                foreignKeys = setOf(BADGE_FOREIGN_KEY)
            )
        )
    }
}

// The foreign key of each table whose rows belong to a started badge.
private val BADGE_FOREIGN_KEY =
    TableDefinition.ForeignKey("badge_progress", "badgeId", "badgeId", "NO ACTION", "CASCADE")

/**
 * Builds the database, without the reflection that iOS doesn't have. Room's compiler generates
 * each platform's actual, so the IDE's warning that there is none is suppressed, as the Room for
 * Kotlin Multiplatform guide does: https://developer.android.com/kotlin/multiplatform/room
 */
@Suppress("KotlinNoActualForExpect")
expect object BlueCardDatabaseConstructor : RoomDatabaseConstructor<BlueCardDatabase> {
    override fun initialize(): BlueCardDatabase
}
