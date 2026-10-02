package io.github.bryancassell.bluecard.data.backup

import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.profile.PROFILE_NAME_MAX_LENGTH
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.UNIT_NUMBER_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.COUNSELOR_EMAIL_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.COUNSELOR_NAME_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.COUNSELOR_PHONE_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.NOTES_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TRACKER_NUMBER_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TRACKER_TEXT_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupFormatTest {
    private val version = LocalDate.of(2026, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)

    private fun badge(id: String, vararg requirements: Requirement) = MeritBadge(
        id = id,
        name = id,
        summary = "Our summary.",
        officialUrl = "https://www.scouting.org/merit-badges/$id/",
        requirementVersions = listOf(RequirementsVersion(version, requirements.toList()))
    )

    private fun column(id: String, type: TrackerColumnType) = TrackerColumn(id, id, type)

    /** The badges this app's catalog has, with the requirements the tests record. */
    private val catalog = listOf(
        badge(
            "camping",
            Requirement("4", "Do these.", children = listOf(Requirement("4b", "Pitch a tent."))),
            Requirement("5", "Cook."),
            Requirement("6", "Plan a trip."),
            Requirement("7", "Hike."),
            Requirement(
                "9",
                "Camp.",
                children = listOf(
                    Requirement(
                        "9a",
                        "Log your nights.",
                        tracker = TrackerDefinition(
                            listOf(
                                column("date", TrackerColumnType.DATE),
                                column("nights", TrackerColumnType.NUMBER),
                                column("note", TrackerColumnType.TEXT),
                                column("details", TrackerColumnType.MULTILINE_TEXT)
                            ),
                            "campout",
                            "campouts"
                        )
                    ),
                    Requirement(
                        "9b",
                        "Camp three times.",
                        tracker = TrackerDefinition(
                            listOf(column("place", TrackerColumnType.TEXT)),
                            "campout",
                            "campouts",
                            rowCount = 3
                        )
                    )
                )
            )
        ),
        badge("swimming", Requirement("1", "Swim."))
    )

    private fun decode(json: String) = decodeBackup(json, catalog)

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
        assertEquals(valid(backup.asImported()), decode(encodeBackup(backup)))
    }

    @Test
    fun decodeBackup_readsEveryFieldOfTheFormat() {
        assertEquals(valid(backup.asImported()), decode(exportJson))
    }

    @Test
    fun decodeBackup_withNoBadges_hasNoProgress() {
        val json = """
            { "formatVersion": 1, "profile": { "name": "Alex", "unitNumber": "12" }, "badges": [] }
        """

        assertEquals(valid(Backup(Profile("Alex", "12"), emptyList())), decode(json))
    }

    // A newer format may lay the rest out differently, so it's told apart before it's read.
    @Test
    fun decodeBackup_fromANewerFormat_isNewerFormat() {
        assertEquals(
            BackupReadResult.NewerFormat,
            decode("""{ "formatVersion": 2, "scout": "Alex" }""")
        )
    }

    // An export from a newer version of the app can have badges or requirements versions this
    // one doesn't, in the same format.
    @Test
    fun decodeBackup_withABadgeOrVersionTheCatalogDoesntHave_isNewerFormat() {
        val archery = BadgeProgressDetails(
            BadgeProgress("archery", version, started),
            emptyList(),
            emptyList()
        )
        val newerVersion = camping.copy(
            badge = camping.badge.copy(requirementsVersion = LocalDate.of(2027, 1, 1))
        )
        for (progress in listOf(archery, newerVersion)) {
            val newer = backup.copy(progress = listOf(swimming, progress))

            assertEquals(BackupReadResult.NewerFormat, decode(encodeBackup(newer)))
        }
    }

    // Read without a tree of the whole file, which a deep one would overflow the stack with.
    @Test
    fun decodeBackup_ofADeeplyNestedFile_isntReadIntoATree() {
        val deep = "[".repeat(100_000) + "]".repeat(100_000)

        assertEquals(
            BackupReadResult.Invalid,
            decode("""{ "formatVersion": 1, "profile": $deep, "badges": [] }""")
        )
        assertEquals(
            BackupReadResult.Invalid,
            decode("""{ "other": $deep, "formatVersion": 1 }""")
        )
        assertEquals(
            BackupReadResult.NewerFormat,
            decode("""{ "other": $deep, "formatVersion": 2 }""")
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
            assertEquals("For: $file", BackupReadResult.Invalid, decode(file))
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

            assertEquals("With $to", BackupReadResult.Invalid, decode(file))
        }
    }

    @Test
    fun decodeBackup_ofProgressTheAppCantHaveRecorded_isInvalid() {
        fun withCamping(change: (BadgeProgressDetails) -> BadgeProgressDetails) =
            backup.copy(progress = listOf(change(camping), swimming))
        fun withRequirement(requirement: RequirementProgress) =
            withCamping { it.copy(requirements = it.requirements + requirement) }
        fun withEntry(number: String, rowNumber: Int?, values: Map<String, String> = mapOf()) =
            withCamping {
                val entry = TrackerEntry(9, "camping", number, rowNumber, values)
                it.copy(trackerEntries = it.trackerEntries + entry)
            }
        fun withCounselor(counselor: Counselor) =
            withCamping { it.copy(badge = it.badge.copy(counselor = counselor)) }
        val backups = mapOf(
            "two of a badge" to backup.copy(progress = listOf(camping, swimming, camping)),
            "two of a requirement" to withRequirement(RequirementProgress("camping", "5")),
            "a requirement not in the version" to
                withRequirement(RequirementProgress("camping", "8")),
            "a blank requirement number" to withRequirement(RequirementProgress("camping", "")),
            "a date on a requirement not completed" to
                withRequirement(RequirementProgress("camping", "7", false, day)),
            "two entries in a row" to withEntry("9b", 2),
            "row 0" to withEntry("9b", 0),
            "a row past the tracker's rows" to withEntry("9b", 4),
            "a fixed-row tracker's entry without a row" to withEntry("9b", null),
            "a log's entry with a row" to withEntry("9a", 1),
            "an entry for a requirement without a tracker" to withEntry("5", null),
            "an entry for a requirement not in the version" to withEntry("8", null),
            "an entry in a column the tracker doesn't have" to
                withEntry("9a", null, mapOf("weather" to "Rain")),
            "a date column without a date" to withEntry("9a", null, mapOf("date" to "May 2")),
            "a blank name" to backup.copy(profile = Profile(" ", "Troop 12")),
            "a blank unit number" to backup.copy(profile = Profile("Alex", "")),
            "a long name" to
                backup.copy(profile = Profile(tooLong(PROFILE_NAME_MAX_LENGTH), "Troop 12")),
            "a long unit number" to
                backup.copy(profile = Profile("Alex", tooLong(UNIT_NUMBER_MAX_LENGTH))),
            "a long counselor name" to
                withCounselor(Counselor(name = tooLong(COUNSELOR_NAME_MAX_LENGTH))),
            "a long phone number" to
                withCounselor(Counselor(phone = tooLong(COUNSELOR_PHONE_MAX_LENGTH))),
            "a long email address" to
                withCounselor(Counselor(email = tooLong(COUNSELOR_EMAIL_MAX_LENGTH))),
            "long notes" to withRequirement(
                RequirementProgress("camping", "7", comment = tooLong(NOTES_MAX_LENGTH))
            ),
            "a long tracker note" to
                withEntry("9a", null, mapOf("note" to tooLong(TRACKER_TEXT_MAX_LENGTH))),
            "a long multi-line tracker note" to
                withEntry("9a", null, mapOf("details" to tooLong(TRACKER_TEXT_MAX_LENGTH))),
            "a long tracker number" to
                withEntry("9a", null, mapOf("nights" to tooLong(TRACKER_NUMBER_MAX_LENGTH)))
        )
        for ((name, invalid) in backups) {
            val read = decode(encodeBackup(invalid))

            assertEquals("With $name", BackupReadResult.Invalid, read)
        }
    }

    /** Text one character longer than [maxLength]. */
    private fun tooLong(maxLength: Int) = "x".repeat(maxLength + 1)

    // As long as each field lets the scout type, so the app takes back all it exported.
    @Test
    fun decodeBackup_withTextAsLongAsEachFieldTakes_isValid() {
        fun longest(maxLength: Int) = "x".repeat(maxLength)
        val counselor = Counselor(
            longest(COUNSELOR_NAME_MAX_LENGTH),
            longest(COUNSELOR_PHONE_MAX_LENGTH),
            longest(COUNSELOR_EMAIL_MAX_LENGTH)
        )
        val values = mapOf(
            "note" to longest(TRACKER_TEXT_MAX_LENGTH),
            "details" to longest(TRACKER_TEXT_MAX_LENGTH),
            "nights" to longest(TRACKER_NUMBER_MAX_LENGTH)
        )
        val longestBackup = Backup(
            Profile(longest(PROFILE_NAME_MAX_LENGTH), longest(UNIT_NUMBER_MAX_LENGTH)),
            listOf(
                BadgeProgressDetails(
                    BadgeProgress("camping", version, started, counselor),
                    listOf(
                        RequirementProgress("camping", "7", comment = longest(NOTES_MAX_LENGTH))
                    ),
                    listOf(TrackerEntry(0, "camping", "9a", null, values))
                )
            )
        )

        assertEquals(valid(longestBackup), decode(encodeBackup(longestBackup)))
    }

    @Test
    fun decodeBackup_keepsLineBreaksInMultilineText() {
        val values = mapOf("details" to "Pitched a tent.\nCooked dinner.")
        val withLineBreaks = backup.copy(
            progress = listOf(
                camping.copy(
                    trackerEntries = listOf(TrackerEntry(0, "camping", "9a", null, values))
                )
            )
        )

        assertEquals(valid(withLineBreaks), decode(encodeBackup(withLineBreaks)))
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
                    listOf(
                        TrackerEntry(
                            1,
                            "camping",
                            "9a",
                            null,
                            mapOf(
                                "nights" to " 1 ",
                                "note" to " "
                            )
                        )
                    )
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
                    listOf(TrackerEntry(0, "camping", "9a", null, mapOf("nights" to "1")))
                ),
                BadgeProgressDetails(
                    BadgeProgress("swimming", version, started),
                    emptyList(),
                    emptyList()
                )
            )
        )

        assertEquals(valid(tidy), decode(encodeBackup(untidy)))
    }
}
