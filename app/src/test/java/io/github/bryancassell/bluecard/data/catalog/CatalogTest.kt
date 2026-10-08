package io.github.bryancassell.bluecard.data.catalog

import java.time.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Parsing catalog JSON into models. */
class CatalogTest {
    @Test
    fun parseCatalog_readsEveryField() {
        val json = """
            {
              "formatVersion": 1,
              "badges": [{
                "id": "hiking",
                "name": "Hiking",
                "summary": "Plan and complete a series of hikes.",
                "officialUrl": "https://www.scouting.org/merit-badges/hiking/",
                "eagleRequired": true,
                "eagleGroup": "cycling-hiking-swimming",
                "requirementVersions": [{
                  "effectiveDate": "2026-01-01",
                  "requirements": [{
                    "number": "1",
                    "summary": "Do two of these.",
                    "requiredCount": 2,
                    "ownWork": "Plan your hikes.",
                    "children": [
                      { "number": "1a", "summary": "First." },
                      { "number": "1b", "summary": "Second." },
                      {
                        "number": "1c",
                        "summary": "Log your hikes.",
                        "tracker": {
                          "columns": [
                            { "id": "date", "label": "Date", "type": "date" },
                            {
                              "id": "miles",
                              "label": "Miles",
                              "type": "number",
                              "total": { "needed": 20, "label": "mile", "labelPlural": "miles" }
                            },
                            { "id": "notes", "label": "Notes", "type": "text" },
                            { "id": "sights", "label": "What you saw", "type": "multiline-text" }
                          ],
                          "rowLabel": "hike",
                          "rowLabelPlural": "hikes",
                          "rowCount": 5
                        }
                      }
                    ]
                  }]
                }]
              }],
              "ranks": [{
                "id": "tenderfoot",
                "name": "Tenderfoot",
                "summary": "Learn the basics of camping.",
                "officialUrl": "https://www.scouting.org/tenderfoot/",
                "requirementVersions": [{
                  "effectiveDate": "2026-01-01",
                  "requirements": [
                    { "number": "1", "summary": "Pack for a campout." },
                    { "number": "2", "summary": "Be active in your troop.", "monthsInRank": 4 },
                    {
                      "number": "3",
                      "summary": "Earn six merit badges.",
                      "meritBadges": { "total": 6, "eagleRequired": 4, "eagleGroupsCountOnce": true }
                    },
                    {
                      "number": "4",
                      "summary": "Log at least 30 days of exercise.",
                      "tracker": {
                        "columns": [{ "id": "date", "label": "Date", "type": "date" }],
                        "rowLabel": "day",
                        "rowLabelPlural": "days",
                        "rowsNeeded": 30
                      }
                    }
                  ]
                }]
              }]
            }
        """.trimIndent()

        val expected = Catalog(
            formatVersion = 1,
            badges = listOf(
                MeritBadge(
                    id = "hiking",
                    name = "Hiking",
                    summary = "Plan and complete a series of hikes.",
                    officialUrl = "https://www.scouting.org/merit-badges/hiking/",
                    eagleRequired = true,
                    eagleGroup = "cycling-hiking-swimming",
                    requirementVersions = listOf(
                        RequirementsVersion(
                            effectiveDate = LocalDate.of(2026, 1, 1),
                            requirements = listOf(
                                Requirement(
                                    number = "1",
                                    summary = "Do two of these.",
                                    requiredCount = 2,
                                    ownWork = "Plan your hikes.",
                                    children = listOf(
                                        Requirement(number = "1a", summary = "First."),
                                        Requirement(number = "1b", summary = "Second."),
                                        Requirement(
                                            number = "1c",
                                            summary = "Log your hikes.",
                                            tracker = TrackerDefinition(
                                                columns = listOf(
                                                    TrackerColumn(
                                                        "date",
                                                        "Date",
                                                        TrackerColumnType.DATE
                                                    ),
                                                    TrackerColumn(
                                                        "miles",
                                                        "Miles",
                                                        TrackerColumnType.NUMBER,
                                                        ColumnTotal(20, "mile", "miles")
                                                    ),
                                                    TrackerColumn(
                                                        "notes",
                                                        "Notes",
                                                        TrackerColumnType.TEXT
                                                    ),
                                                    TrackerColumn(
                                                        "sights",
                                                        "What you saw",
                                                        TrackerColumnType.MULTILINE_TEXT
                                                    )
                                                ),
                                                rowLabel = "hike",
                                                rowLabelPlural = "hikes",
                                                rowCount = 5
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            ),
            ranks = listOf(
                Rank(
                    id = "tenderfoot",
                    name = "Tenderfoot",
                    summary = "Learn the basics of camping.",
                    officialUrl = "https://www.scouting.org/tenderfoot/",
                    requirementVersions = listOf(
                        RequirementsVersion(
                            effectiveDate = LocalDate.of(2026, 1, 1),
                            requirements = listOf(
                                Requirement(number = "1", summary = "Pack for a campout."),
                                Requirement(
                                    number = "2",
                                    summary = "Be active in your troop.",
                                    monthsInRank = 4
                                ),
                                Requirement(
                                    number = "3",
                                    summary = "Earn six merit badges.",
                                    meritBadges = MeritBadgesNeeded(
                                        total = 6,
                                        eagleRequired = 4,
                                        eagleGroupsCountOnce = true
                                    )
                                ),
                                Requirement(
                                    number = "4",
                                    summary = "Log at least 30 days of exercise.",
                                    tracker = TrackerDefinition(
                                        columns = listOf(
                                            TrackerColumn("date", "Date", TrackerColumnType.DATE)
                                        ),
                                        rowLabel = "day",
                                        rowLabelPlural = "days",
                                        rowsNeeded = 30
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        assertEquals(expected, parseCatalog(json))
    }

    @Test
    fun parseCatalog_usesDefaultsForOptionalFields() {
        val badge = parseCatalog(
            """
            {"formatVersion": 1, "badges": [{"id": "a", "name": "A", "summary": "S",
              "officialUrl": "https://www.scouting.org/a/", "requirementVersions": [
              {"effectiveDate": "2025-01-01", "requirements": [{"number": "1", "summary": "R"}]}]}],
              "ranks": []}
            """.trimIndent()
        ).badges.single()

        assertEquals(false, badge.eagleRequired)
        assertEquals(null, badge.eagleGroup)
        val requirement = badge.requirementVersions.single().requirements.single()
        assertEquals(Requirement(number = "1", summary = "R"), requirement)
    }

    @Test
    fun parseCatalog_rejectsUnknownField() {
        assertThrows(SerializationException::class.java) {
            parseCatalog("""{"formatVersion": 1, "badges": [], "ranks": [], "badgez": []}""")
        }
    }

    @Test
    fun parseCatalog_rejectsMissingRequiredField() {
        assertThrows(SerializationException::class.java) {
            parseCatalog("""{"formatVersion": 1, "ranks": []}""")
        }
        assertThrows(SerializationException::class.java) {
            parseCatalog("""{"formatVersion": 1, "badges": []}""")
        }
    }

    @Test
    fun localDateSerializer_writesIsoDate() {
        assertEquals(
            "\"2026-01-01\"",
            Json.encodeToString(LocalDateSerializer, LocalDate.of(2026, 1, 1))
        )
    }

    @Test
    fun localDateSerializer_rejectsNonIsoDate() {
        val error = assertThrows(SerializationException::class.java) {
            Json.decodeFromString(LocalDateSerializer, "\"January 1, 2026\"")
        }
        assertEquals("\"January 1, 2026\" is not a date in YYYY-MM-DD form", error.message)
    }

    private val children =
        listOf(Requirement("1a", "A."), Requirement("1b", "B."), Requirement("1c", "C."))

    @Test
    fun choiceCount_isTheChildrenNeeded_whenFewerThanAll() {
        val choice = Requirement("1", "Do two.", requiredCount = 2, children = children)

        assertEquals(2, choice.choiceCount)
    }

    // A count of all the children is no choice.
    @Test
    fun choiceCount_ofAllTheChildrenOrNone_isNull() {
        assertNull(Requirement("1", "Do all.", requiredCount = 3, children = children).choiceCount)
        assertNull(Requirement("1", "Do all.", children = children).choiceCount)
        assertNull(Requirement("1", "Do it.").choiceCount)
    }

    private val columns = listOf(TrackerColumn("notes", "Notes", TrackerColumnType.TEXT))

    @Test
    fun rowTitle_isTheRowLabelCapitalized() {
        assertEquals("Week", TrackerDefinition(columns, "week", "weeks").rowTitle)
    }

    @Test
    fun rowsLabel_inLog_agreesWithTheCountRecorded() {
        val log = TrackerDefinition(columns, "session", "sessions")

        assertEquals("sessions", log.rowsLabel(0))
        assertEquals("session", log.rowsLabel(1))
        assertEquals("sessions", log.rowsLabel(5))
    }

    // "1 of 12 weeks": the label agrees with the number of rows.
    @Test
    fun rowsLabel_withFixedRows_agreesWithTheRowCount() {
        val twelve = TrackerDefinition(columns, "week", "weeks", rowCount = 12)
        val one = TrackerDefinition(columns, "week", "weeks", rowCount = 1)

        assertEquals("weeks", twelve.rowsLabel(1))
        assertEquals("week", one.rowsLabel(0))
    }

    // "1 of 10 animals": the label agrees with the number of rows the log needs.
    @Test
    fun rowsLabel_inLogThatNeedsRows_agreesWithTheRowsNeeded() {
        val ten = TrackerDefinition(columns, "animal", "animals", rowsNeeded = 10)
        val one = TrackerDefinition(columns, "trek", "treks", rowsNeeded = 1)

        assertEquals("animals", ten.rowsLabel(1))
        assertEquals("trek", one.rowsLabel(3))
    }

    @Test
    fun rowsOutOf_isTheFixedRowsOrTheRowsNeeded() {
        assertEquals(12, TrackerDefinition(columns, "week", "weeks", rowCount = 12).rowsOutOf)
        assertEquals(10, TrackerDefinition(columns, "animal", "animals", rowsNeeded = 10).rowsOutOf)
        assertNull(TrackerDefinition(columns, "session", "sessions").rowsOutOf)
    }
}
