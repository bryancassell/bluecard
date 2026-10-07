package io.github.bryancassell.bluecard.ui.data

import io.github.bryancassell.bluecard.data.backup.Backup
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeChoicesTest {
    private val version = LocalDate.of(2026, 1, 1)
    private val oldVersion = LocalDate.of(2025, 1, 1)
    private val started = LocalDate.of(2026, 3, 1)
    private val day = LocalDate.of(2026, 4, 15)
    private val profile = Profile("Alex Scout", "Troop 12")

    private fun requirements(effective: LocalDate) = RequirementsVersion(
        effective,
        listOf(Requirement("1", "First."), Requirement("2", "Second."))
    )

    private fun badge(id: String, name: String) = MeritBadge(
        id = id,
        name = name,
        summary = "Our summary of $name.",
        officialUrl = "https://www.scouting.org/merit-badges/$id/",
        requirementVersions = listOf(requirements(oldVersion), requirements(version))
    )

    private fun rank(id: String, name: String) = Rank(
        id = id,
        name = name,
        summary = "Our summary of $name.",
        officialUrl = "https://www.scouting.org/$id.pdf",
        requirementVersions = listOf(requirements(version))
    )

    // Not in alphabetical order, so the choices' order is the catalog's.
    private val badges = listOf(badge("cooking", "Cooking"), badge("camping", "Camping"))
    private val ranks = listOf(rank("scout", "Scout"), rank("tenderfoot", "Tenderfoot"))

    /** Progress on [id], on [effective] requirements, with requirements [done] completed. */
    private fun progress(
        id: String,
        vararg done: String,
        effective: LocalDate = version,
        completedOnPriorDate: LocalDate? = null
    ) = BadgeProgressDetails(
        BadgeProgress(id, effective, started, completedOnPriorDate = completedOnPriorDate),
        done.map { RequirementProgress(id, it, completed = true, completedDate = day) },
        emptyList()
    )

    private fun choices(
        phone: List<BadgeProgressDetails>,
        file: List<BadgeProgressDetails>,
        fileProfile: Profile = profile
    ) = mergeChoices(badges, ranks, Backup(profile, phone), Backup(fileProfile, file))

    @Test
    fun mergeChoices_hasEachBadgeThenRankStartedOnBoth_withDifferentProgress() {
        val file = listOf(
            progress("camping", "1", "2"),
            progress("cooking", "1"),
            progress("scout", completedOnPriorDate = day),
            progress("tenderfoot", "1")
        )
        val phone = listOf(
            progress("camping", "1"),
            // The same as the file's, so there's nothing to choose.
            progress("cooking", "1"),
            progress("scout")
        )

        val choices = choices(phone, file)

        assertEquals(
            listOf(
                AdvancementChoice(
                    "camping",
                    "Camping",
                    isRank = false,
                    phone = ProgressSummary(done = false, fractionDone = 0.5f),
                    file = ProgressSummary(done = true)
                ),
                AdvancementChoice(
                    "scout",
                    "Scout",
                    isRank = true,
                    phone = ProgressSummary(done = false, fractionDone = 0f),
                    file = ProgressSummary(done = true)
                )
            ),
            choices.advancements
        )
        assertNull(choices.profile)
        assertFalse(choices.isEmpty)
    }

    // Each side's rank is summed up from that side's ranks: Tenderfoot is in progress in the
    // file, where Scout is earned, but not earned on the phone, where Scout isn't.
    @Test
    fun mergeChoices_sumsUpEachSidesRank_fromThatSidesProgress() {
        val phone = listOf(progress("tenderfoot", "1"))
        val file = listOf(progress("scout", completedOnPriorDate = day), progress("tenderfoot"))

        val tenderfoot = choices(phone, file).advancements.single()

        assertEquals(ProgressSummary(done = false, fractionDone = 0.5f), tenderfoot.phone)
        assertEquals(ProgressSummary(done = false, fractionDone = 0f), tenderfoot.file)
    }

    @Test
    fun mergeChoices_onDifferentRequirementsVersions_hasEachSidesVersion() {
        val phone = listOf(progress("camping", "1", effective = oldVersion))
        val file = listOf(progress("camping", "1"))

        val camping = choices(phone, file).advancements.single()

        assertEquals(oldVersion, camping.phone.requirementsVersion)
        assertEquals(version, camping.file.requirementsVersion)
    }

    @Test
    fun mergeChoices_withADifferentProfile_hasBoth() {
        val fileProfile = Profile("Alex Lee", "Troop 12")

        val choices = choices(emptyList(), emptyList(), fileProfile)

        assertEquals(ProfileChoice(profile, fileProfile), choices.profile)
        assertFalse(choices.isEmpty)
    }

    @Test
    fun mergeChoices_withNothingDifferentOnBoth_isEmpty() {
        val choices = choices(
            phone = listOf(progress("camping", "1")),
            file = listOf(progress("camping", "1"), progress("cooking"))
        )

        assertTrue(choices.isEmpty)
    }

    @Test
    fun fromFile_hasTheBadgesAndRanksChosenFromTheFile() {
        val choices = choices(
            phone = listOf(progress("camping"), progress("cooking"), progress("scout")),
            file = listOf("camping", "cooking", "scout").map { progress(it, "1") }
        )
        val chosen = choices.copy(
            advancements = choices.advancements.map { it.copy(fromFile = it.id != "cooking") }
        )

        assertEquals(setOf("camping", "scout"), chosen.fromFile)
    }
}
