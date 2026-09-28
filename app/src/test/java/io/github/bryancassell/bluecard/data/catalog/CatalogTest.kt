package io.github.bryancassell.bluecard.data.catalog

import java.time.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
                    "children": [
                      { "number": "1a", "summary": "First." },
                      { "number": "1b", "summary": "Second." },
                      {
                        "number": "1c",
                        "summary": "Log your hikes.",
                        "tracker": {
                          "columns": [
                            { "id": "date", "label": "Date", "type": "date" },
                            { "id": "miles", "label": "Miles", "type": "number" },
                            { "id": "notes", "label": "Notes", "type": "text" }
                          ],
                          "rowCount": 5,
                          "rowLabel": "Hike"
                        }
                      }
                    ]
                  }]
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
                                                        TrackerColumnType.NUMBER
                                                    ),
                                                    TrackerColumn(
                                                        "notes",
                                                        "Notes",
                                                        TrackerColumnType.TEXT
                                                    )
                                                ),
                                                rowCount = 5,
                                                rowLabel = "Hike"
                                            )
                                        )
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
              {"effectiveDate": "2025-01-01", "requirements": [{"number": "1", "summary": "R"}]}]}]}
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
            parseCatalog("""{"formatVersion": 1, "badges": [], "badgez": []}""")
        }
    }

    @Test
    fun parseCatalog_rejectsMissingRequiredField() {
        assertThrows(SerializationException::class.java) {
            parseCatalog("""{"formatVersion": 1}""")
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
        assertThrows(RuntimeException::class.java) {
            Json.decodeFromString(LocalDateSerializer, "\"January 1, 2026\"")
        }
    }
}
