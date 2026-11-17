package io.github.bryancassell.bluecard.data.progress

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import kotlinx.coroutines.sync.Mutex

// Each version's schema is in shared/schemas/, and MigrationTest checks each migration. TABLES
// has each version's tables.
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
         * Each schema version's tables, as Room creates them for the entities, as in the
         * version's file in shared/schemas/. [SetAsideDamagedDatabaseDriver] checks a database's
         * tables against its version's before Room opens it. A version that changes a table
         * gives it a definition of its own. SetAsideDamagedDatabaseDriverTest fails until every
         * version's tables are here.
         */
        internal val TABLES = mapOf(
            1 to listOf(BADGE_PROGRESS, REQUIREMENT_PROGRESS_1, TRACKER_ENTRY_1),
            2 to listOf(BADGE_PROGRESS, REQUIREMENT_PROGRESS_1, TRACKER_ENTRY_2),
            3 to listOf(BADGE_PROGRESS, REQUIREMENT_PROGRESS_1, TRACKER_ENTRY_3),
            4 to listOf(BADGE_PROGRESS, REQUIREMENT_PROGRESS_4, TRACKER_ENTRY_3)
        )
    }
}

// Each version's tables, for BlueCardDatabase.TABLES, in the order they're made.
private val BADGE_PROGRESS = TableDefinition(
    "badge_progress",
    setOf(
        TableDefinition.Column("badgeId", Affinity.TEXT, notNull = true, primaryKey = true),
        TableDefinition.Column("requirementsVersion", Affinity.TEXT, notNull = true),
        TableDefinition.Column("startedDate", Affinity.TEXT, notNull = true),
        TableDefinition.Column("completedOnPriorDate", Affinity.TEXT),
        TableDefinition.Column("counselor_name", Affinity.TEXT),
        TableDefinition.Column("counselor_phone", Affinity.TEXT),
        TableDefinition.Column("counselor_email", Affinity.TEXT)
    )
)

// The foreign key of each table whose rows belong to a started badge.
private val BADGE_FOREIGN_KEY =
    TableDefinition.ForeignKey("badge_progress", "badgeId", "badgeId", "NO ACTION", "CASCADE")

private val REQUIREMENT_PROGRESS_1 = TableDefinition(
    "requirement_progress",
    setOf(
        TableDefinition.Column("badgeId", Affinity.TEXT, notNull = true, primaryKey = true),
        TableDefinition.Column(
            "requirementNumber",
            Affinity.TEXT,
            notNull = true,
            primaryKey = true
        ),
        TableDefinition.Column("completed", Affinity.INTEGER, notNull = true),
        TableDefinition.Column("completedDate", Affinity.TEXT),
        TableDefinition.Column("comment", Affinity.TEXT)
    ),
    setOf(BADGE_FOREIGN_KEY)
)

private val REQUIREMENT_PROGRESS_4 =
    REQUIREMENT_PROGRESS_1 + TableDefinition.Column("signedOffBy", Affinity.TEXT)

private val TRACKER_ENTRY_1 = TableDefinition(
    "tracker_entry",
    setOf(
        TableDefinition.Column("id", Affinity.INTEGER, notNull = true, primaryKey = true),
        TableDefinition.Column("badgeId", Affinity.TEXT, notNull = true),
        TableDefinition.Column("requirementNumber", Affinity.TEXT, notNull = true),
        TableDefinition.Column("values", Affinity.TEXT, notNull = true)
    ),
    setOf(BADGE_FOREIGN_KEY)
)

private val TRACKER_ENTRY_2 =
    TRACKER_ENTRY_1 + TableDefinition.Column("rowNumber", Affinity.INTEGER)

private val TRACKER_ENTRY_3 = TRACKER_ENTRY_2 + TableDefinition.Column("addedDate", Affinity.TEXT)

/**
 * Builds the database, without the reflection that iOS doesn't have. Room's compiler generates
 * each platform's actual, so the IDE's warning that there is none is suppressed, as the Room for
 * Kotlin Multiplatform guide does: https://developer.android.com/kotlin/multiplatform/room
 */
@Suppress("KotlinNoActualForExpect")
expect object BlueCardDatabaseConstructor : RoomDatabaseConstructor<BlueCardDatabase> {
    override fun initialize(): BlueCardDatabase
}
