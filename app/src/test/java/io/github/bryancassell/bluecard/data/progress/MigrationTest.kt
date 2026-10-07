package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BlueCardDatabase::class.java
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun migrate1To2_keepsTrackerEntries_asLogEntries() = runTest {
        helper.createDatabase(DATABASE, 1).use {
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

        helper.runMigrationsAndValidate(DATABASE, 2, true).close()

        val database = Room.databaseBuilder(context, BlueCardDatabase::class.java, DATABASE).build()
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
        helper.createDatabase(DATABASE, 2).use {
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

        helper.runMigrationsAndValidate(DATABASE, 3, true).close()

        val database = Room.databaseBuilder(context, BlueCardDatabase::class.java, DATABASE).build()
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
        helper.createDatabase(DATABASE, 3).use {
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

        helper.runMigrationsAndValidate(DATABASE, 4, true).close()

        val database = Room.databaseBuilder(context, BlueCardDatabase::class.java, DATABASE).build()
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
