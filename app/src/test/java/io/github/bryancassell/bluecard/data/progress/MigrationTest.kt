package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks each database migration: that it makes the schema committed in app/schemas/, and that
 * progress stored before it reads back the same after it.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        context.getDatabasePath(DATABASE),
        BundledSQLiteDriver(),
        BlueCardDatabase::class
    )

    /**
     * Migrates the database to [version] and checks it against that version's committed schema.
     * The helper checks the schema's tables, but not for others, such as one a migration should
     * have dropped: it passes validateUnknownTables = false to Room (checked in room3-testing
     * 3.0.3). So this checks that too.
     */
    private suspend fun migrateAndValidate(version: Int) =
        helper.runMigrationsAndValidate(version).use { migrated ->
            val tables = migrated.prepare(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
            ).use { buildSet { while (it.step()) add(it.getText(0)) } }
            assertEquals(schemaTables(version) + "room_master_table", tables)
        }

    /** The tables in [version]'s committed schema, which the Room Gradle plugin adds to the assets. */
    private fun schemaTables(version: Int): Set<String> {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("${BlueCardDatabase::class.java.name}/$version.json")
            .use { Json.parseToJsonElement(it.reader().readText()) }
        return schema.jsonObject.getValue("database").jsonObject.getValue("entities").jsonArray
            .map { it.jsonObject.getValue("tableName").jsonPrimitive.content }
            .toSet()
    }

    /** Opens the migrated database as the app does. */
    private fun open() = Room.databaseBuilder(context, BlueCardDatabase::class.java, DATABASE)
        .setDriver(BundledSQLiteDriver())
        .build()

    @Test
    fun migrate1To2_keepsTrackerEntries_asLogEntries() = runTest {
        helper.createDatabase(1).use {
            it.execSQL(
                """
                INSERT INTO badge_progress (badgeId, requirementsVersion, startedDate)
                VALUES ('personal-fitness', '2026-01-01', '2026-03-01')
                """
            )
            it.execSQL(
                """
                INSERT INTO tracker_entry (id, badgeId, requirementNumber, `values`)
                VALUES (1, 'personal-fitness', '7a', '{"minutes":"30"}'),
                    (2, 'personal-fitness', '7a', '{"minutes":"45"}')
                """
            )
        }

        migrateAndValidate(2)

        val database = open()
        try {
            val progress = database.progressDao().observe("personal-fitness").first()!!
            assertEquals(
                BadgeProgress(
                    "personal-fitness",
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 3, 1)
                ),
                progress.badge
            )
            // Two entries of the same log, which has no row numbers.
            assertEquals(
                listOf(
                    TrackerEntry(1, "personal-fitness", "7a", null, mapOf("minutes" to "30")),
                    TrackerEntry(2, "personal-fitness", "7a", null, mapOf("minutes" to "45"))
                ),
                progress.trackerEntries.sortedBy { it.id }
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate2To3_keepsTrackerEntries_withoutADate() = runTest {
        helper.createDatabase(2).use {
            it.execSQL(
                """
                INSERT INTO badge_progress (badgeId, requirementsVersion, startedDate)
                VALUES ('personal-management', '2026-01-01', '2026-03-01')
                """
            )
            it.execSQL(
                """
                INSERT INTO tracker_entry (id, badgeId, requirementNumber, rowNumber, `values`)
                VALUES (1, 'personal-management', '2a', 1, '{"income":"10"}'),
                    (2, 'personal-management', '2a', 2, '{"income":"20"}')
                """
            )
        }

        migrateAndValidate(3)

        val database = open()
        try {
            val progress = database.progressDao().observe("personal-management").first()!!
            assertEquals(
                listOf(
                    TrackerEntry(1, "personal-management", "2a", 1, mapOf("income" to "10")),
                    TrackerEntry(2, "personal-management", "2a", 2, mapOf("income" to "20"))
                ),
                progress.trackerEntries.sortedBy { it.id }
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate3To4_keepsRequirementProgress_withoutASignOff() = runTest {
        helper.createDatabase(3).use {
            it.execSQL(
                """
                INSERT INTO badge_progress (badgeId, requirementsVersion, startedDate)
                VALUES ('scout', '2026-01-01', '2026-03-01')
                """
            )
            it.execSQL(
                """
                INSERT INTO requirement_progress
                    (badgeId, requirementNumber, completed, completedDate, comment)
                VALUES ('scout', '1a', 1, '2026-03-02', 'At the first meeting.')
                """
            )
        }

        migrateAndValidate(4)

        val database = open()
        try {
            val progress = database.progressDao().observe("scout").first()!!
            assertEquals(
                listOf(
                    RequirementProgress(
                        "scout",
                        "1a",
                        completed = true,
                        completedDate = LocalDate.of(2026, 3, 2),
                        comment = "At the first meeting."
                    )
                ),
                progress.requirements
            )
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DATABASE = "migration-test.db"
    }
}
