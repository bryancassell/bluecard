package io.github.bryancassell.bluecard.data.backup

import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupFormatTest {
    private val version = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)

    private val camping = BadgeProgressDetails(
        BadgeProgress("camping", version, started, Counselor("Pat Lee", email = "pat@example.com")),
        listOf(
            RequirementProgress("camping", "4b", true, day, comment = "Pitched a tent."),
            RequirementProgress("camping", "5", completed = true),
            RequirementProgress("camping", "6", comment = "Next trip.")
        ),
        listOf(
            // Added last, though it's listed first here.
            TrackerEntry(
                7,
                "camping",
                "9a",
                null,
                mapOf("date" to "2026-05-02", "nights" to "2"),
                day
            ),
            // Saved before database version 3, so it has no date.
            TrackerEntry(3, "camping", "9a", null, mapOf("nights" to "1")),
            TrackerEntry(5, "camping", "9b", rowNumber = 2, mapOf("place" to "Lake"), day)
        )
    )

    private val swimming = BadgeProgressDetails(
        BadgeProgress("swimming", version, started, completedOnPriorDate = day),
        emptyList(),
        emptyList()
    )

    private val backup = Backup(Profile("Alex Scout", "Troop 12"), listOf(camping, swimming))

    /** [backup] in the export format. */
    private val exportJson = """
        {
          "formatVersion": 1,
          "profile": { "name": "Alex Scout", "unitNumber": "Troop 12" },
          "badges": [
            {
              "badgeId": "camping",
              "requirementsVersion": "2026-01-01",
              "startedDate": "2026-03-01",
              "counselor": { "name": "Pat Lee", "phone": null, "email": "pat@example.com" },
              "completedOnPriorDate": null,
              "requirements": [
                { "number": "4b", "completed": true, "completedDate": "2026-04-15", "notes": "Pitched a tent." },
                { "number": "5", "completed": true, "completedDate": null, "notes": null },
                { "number": "6", "completed": false, "completedDate": null, "notes": "Next trip." }
              ],
              "trackerEntries": [
                { "requirementNumber": "9a", "rowNumber": null, "addedDate": null, "values": { "nights": "1" } },
                { "requirementNumber": "9b", "rowNumber": 2, "addedDate": "2026-04-15", "values": { "place": "Lake" } },
                {
                  "requirementNumber": "9a",
                  "rowNumber": null,
                  "addedDate": "2026-04-15",
                  "values": { "date": "2026-05-02", "nights": "2" }
                }
              ]
            },
            {
              "badgeId": "swimming",
              "requirementsVersion": "2026-01-01",
              "startedDate": "2026-03-01",
              "counselor": null,
              "completedOnPriorDate": "2026-04-15",
              "requirements": [],
              "trackerEntries": []
            }
          ]
        }
    """.trimIndent()

    /**
     * This backup as an import reads it: tracker entries in the order they were added, without
     * IDs, which the database gives them.
     */
    private fun Backup.asImported() = copy(
        progress = progress.map { details ->
            details.copy(
                trackerEntries = details.trackerEntries.sortedBy { it.id }.map { it.copy(id = 0) }
            )
        }
    )

    private fun valid(backup: Backup) = BackupReadResult.Valid(backup)

    @Test
    fun encodeBackup_writesEverything_inTheExportFormat() {
        assertEquals(
            Json.parseToJsonElement(exportJson),
            Json.parseToJsonElement(encodeBackup(backup))
        )
    }

    @Test
    fun decodeBackup_ofAnExport_givesBackWhatWasExported() {
        assertEquals(valid(backup.asImported()), decodeBackup(encodeBackup(backup)))
    }

    @Test
    fun decodeBackup_readsEveryFieldOfTheFormat() {
        assertEquals(valid(backup.asImported()), decodeBackup(exportJson))
    }

    @Test
    fun decodeBackup_withNoBadges_hasNoProgress() {
        val json = """
            { "formatVersion": 1, "profile": { "name": "Alex", "unitNumber": "12" }, "badges": [] }
        """

        assertEquals(valid(Backup(Profile("Alex", "12"), emptyList())), decodeBackup(json))
    }

    // A newer format may lay the rest out differently, so it's told apart before it's read.
    @Test
    fun decodeBackup_fromANewerFormat_isNewerFormat() {
        assertEquals(
            BackupReadResult.NewerFormat,
            decodeBackup("""{ "formatVersion": 2, "scout": "Alex" }""")
        )
    }

    @Test
    fun decodeBackup_ofAFileThatIsntAnExport_isInvalid() {
        val files = listOf(
            "",
            "Notes from the campout",
            "null",
            "[]",
            "{}",
            """{ "formatVersion": "1" }""",
            """{ "formatVersion": 0 }""",
            """{ "formatVersion": 1.5 }""",
            """{ "formatVersion": 1 }""",
            // Cut off part way.
            exportJson.take(exportJson.length / 2)
        )
        for (file in files) {
            assertEquals("For: $file", BackupReadResult.Invalid, decodeBackup(file))
        }
    }

    @Test
    fun decodeBackup_withAFieldMissing_unknown_orOfTheWrongKind_isInvalid() {
        val changes = listOf(
            // Every field is required, even one that can be null.
            """"completedOnPriorDate": null,""" to "",
            """"formatVersion": 1,""" to """"formatVersion": 1, "exportedOn": "2026-10-01",""",
            """"completed": false""" to """"completed": null""",
            """"requirements": []""" to """"requirements": {}""",
            """"startedDate": "2026-03-01"""" to """"startedDate": "2026-02-30"""",
            """"addedDate": null""" to """"addedDate": "April 15"""",
            """"rowNumber": 2""" to """"rowNumber": 99999999999"""
        )
        for ((from, to) in changes) {
            val file = exportJson.replaceFirst(from, to)
            check(file != exportJson) { "No $from in the file" }

            assertEquals("With $to", BackupReadResult.Invalid, decodeBackup(file))
        }
    }

    @Test
    fun decodeBackup_ofProgressTheAppCantHaveRecorded_isInvalid() {
        val tooLong = "x".repeat(MAX_IMPORTED_TEXT_LENGTH + 1)
        fun withCamping(change: (BadgeProgressDetails) -> BadgeProgressDetails) =
            backup.copy(progress = listOf(change(camping), swimming))
        fun withRequirement(requirement: RequirementProgress) =
            withCamping { it.copy(requirements = it.requirements + requirement) }
        fun withEntry(entry: TrackerEntry) =
            withCamping { it.copy(trackerEntries = it.trackerEntries + entry) }
        val backups = mapOf(
            "two of a badge" to backup.copy(progress = listOf(camping, swimming, camping)),
            "two of a requirement" to withRequirement(RequirementProgress("camping", "5")),
            "two entries in a row" to withEntry(TrackerEntry(9, "camping", "9b", 2, mapOf())),
            "row 0" to withEntry(TrackerEntry(9, "camping", "9b", 0, mapOf())),
            "a date on a requirement not completed" to
                withRequirement(RequirementProgress("camping", "7", false, day)),
            "a blank name" to backup.copy(profile = Profile(" ", "Troop 12")),
            "a blank unit number" to backup.copy(profile = Profile("Alex", "")),
            "a blank badge ID" to
                withCamping { it.copy(badge = it.badge.copy(badgeId = " ")) },
            "a blank requirement number" to withRequirement(RequirementProgress("camping", "")),
            "an entry with a blank requirement number" to
                withEntry(TrackerEntry(9, "camping", " ", null, mapOf())),
            "an entry with a blank column" to
                withEntry(TrackerEntry(9, "camping", "9a", null, mapOf("" to "2"))),
            "a long name" to backup.copy(profile = Profile(tooLong, "Troop 12")),
            "a long badge ID" to
                withCamping { it.copy(badge = it.badge.copy(badgeId = tooLong)) },
            "long notes" to withRequirement(RequirementProgress("camping", "7", comment = tooLong)),
            "a long phone number" to withCamping {
                it.copy(badge = it.badge.copy(counselor = Counselor(phone = tooLong)))
            },
            "a long tracker value" to
                withEntry(TrackerEntry(9, "camping", "9a", null, mapOf("nights" to tooLong)))
        )
        for ((name, invalid) in backups) {
            val read = decodeBackup(encodeBackup(invalid))

            assertEquals("With $name", BackupReadResult.Invalid, read)
        }
    }

    @Test
    fun decodeBackup_withTextAsLongAsAllowed_isValid() {
        val longest = "x".repeat(MAX_IMPORTED_TEXT_LENGTH)
        val notes = RequirementProgress("camping", "7", comment = longest)
        val withNotes = backup.copy(
            progress = listOf(camping.copy(requirements = camping.requirements + notes), swimming)
        )

        assertEquals(valid(withNotes.asImported()), decodeBackup(encodeBackup(withNotes)))
    }

    // As repositories store what the scout types, so an edited file can't store what the app
    // never would, such as notes that are only spaces.
    @Test
    fun decodeBackup_storesTextAsTheAppStoresWhatTheScoutTypes() {
        val untidy = Backup(
            Profile(" Alex Scout ", "Troop 12\n"),
            listOf(
                BadgeProgressDetails(
                    BadgeProgress("camping", version, started, Counselor(" Pat ", " ", null)),
                    listOf(RequirementProgress("camping", "6", comment = "  ")),
                    listOf(TrackerEntry(1, "camping", "9a", null, mapOf("a" to " 1 ", "b" to " ")))
                ),
                BadgeProgressDetails(
                    BadgeProgress("swimming", version, started, Counselor(" ", "", " ")),
                    emptyList(),
                    emptyList()
                )
            )
        )
        val tidy = Backup(
            Profile("Alex Scout", "Troop 12"),
            listOf(
                BadgeProgressDetails(
                    BadgeProgress("camping", version, started, Counselor("Pat")),
                    listOf(RequirementProgress("camping", "6")),
                    listOf(TrackerEntry(0, "camping", "9a", null, mapOf("a" to "1")))
                ),
                BadgeProgressDetails(
                    BadgeProgress("swimming", version, started),
                    emptyList(),
                    emptyList()
                )
            )
        )

        assertEquals(valid(tidy), decodeBackup(encodeBackup(untidy)))
    }
}
