package io.github.bryancassell.bluecard.data.progress

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule on a table of its own, with version 4 current, so later versions of the app's table
 * don't change what these check. SetAsideDamagedDatabaseDriverTest checks the app's table.
 */
class RoomHeaderTest {
    private fun isDamaged(
        version: Long,
        hash: String?,
        hashes: List<Pair<Int, String>> = HASHES,
        currentVersion: Int = 4
    ) = isRoomHeaderDamaged(version, hash, hashes, currentVersion)

    @Test
    fun eachVersionWithItsHash_isntDamaged() {
        HASHES.forEach { (version, hash) ->
            assertFalse(isDamaged(version.toLong(), hash), "Version $version")
        }
    }

    @Test
    fun thisBuildsVersion_withAnotherHashOrNone_isDamaged() {
        assertTrue(isDamaged(4, UNKNOWN_HASH))
        assertTrue(isDamaged(4, null))
        assertTrue(isDamaged(4, HASH_3))
    }

    @Test
    fun knownHash_withAVersionThatDidntHaveIt_isDamaged() {
        assertTrue(isDamaged(4L or 64, HASH_4))
        assertTrue(isDamaged(3, HASH_4))
        // An older database whose version changed, before Room migrates it.
        assertTrue(isDamaged(2, HASH_3))
    }

    @Test
    fun negativeVersion_isDamaged_whateverTheHash() {
        assertTrue(isDamaged(-4, HASH_4))
        assertTrue(isDamaged(-4, UNKNOWN_HASH))
        assertTrue(isDamaged(-4, null))
    }

    @Test
    fun versionZero_withThisBuildsHashOrAnUnknownOne_isLeftToRoom() {
        // Room keeps tables of this build's schema and saves the version and hash again.
        assertFalse(isDamaged(0, HASH_4))
        assertFalse(isDamaged(0, null))
        assertFalse(isDamaged(0, UNKNOWN_HASH))
    }

    @Test
    fun versionZero_withAnOlderHash_isDamaged() {
        // Room would check an older schema's tables against this build's, and refuse them.
        assertTrue(isDamaged(0, HASH_3))
    }

    @Test
    fun olderVersion_withAnUnknownHashOrNone_isLeftToRoom() {
        // Room migrates it without reading the hash, then saves this build's.
        assertFalse(isDamaged(3, UNKNOWN_HASH))
        assertFalse(isDamaged(3, null))
    }

    @Test
    fun newerVersion_withAnUnknownHash_isLeftToRoom() {
        // A newer build's database, which Room refuses on purpose.
        assertFalse(isDamaged(5, UNKNOWN_HASH))
    }

    @Test
    fun versionThatKeptTheSchema_sharesItsHash_andIsntDamaged() {
        // As when version 5's migration only changes data.
        val hashes = HASHES + (5 to HASH_4)

        assertFalse(isDamaged(4, HASH_4, hashes, currentVersion = 5))
        assertFalse(isDamaged(5, HASH_4, hashes, currentVersion = 5))
        assertFalse(isDamaged(0, HASH_4, hashes, currentVersion = 5))
        assertTrue(isDamaged(3, HASH_4, hashes, currentVersion = 5))
        assertTrue(isDamaged(6, HASH_4, hashes, currentVersion = 5))
    }

    @Test
    fun versionWithTwoHashes_acceptsBoth() {
        // As when a Room update computes another hash for a version installed databases keep.
        val hashes = HASHES + (4 to UNKNOWN_HASH)

        assertFalse(isDamaged(4, HASH_4, hashes))
        assertFalse(isDamaged(4, UNKNOWN_HASH, hashes))
    }

    private companion object {
        const val HASH_3 = "3e67ea8b76032a8049f6f3cad8313660"
        const val HASH_4 = "bdfb11170140a5d1e2c7675c1126dcb3"
        val HASHES = listOf(
            1 to "380113d95e4e8e587e57cce0d98668d1",
            2 to "67dcaffcb2188a16a46be6fa4f75ba25",
            3 to HASH_3,
            4 to HASH_4
        )

        // A hash no version in HASHES has.
        const val UNKNOWN_HASH = "0123456789abcdef0123456789abcdef"
    }
}
