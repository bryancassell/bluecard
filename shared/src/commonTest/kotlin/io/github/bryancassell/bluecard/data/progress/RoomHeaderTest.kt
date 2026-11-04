package io.github.bryancassell.bluecard.data.progress

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomHeaderTest {
    private val current = BlueCardDatabase.VERSION.toLong()
    private val currentHash = hashOf(BlueCardDatabase.VERSION)
    private val olderHash = hashOf(BlueCardDatabase.VERSION - 1)

    private fun hashOf(version: Int) =
        BlueCardDatabase.IDENTITY_HASHES.first { it.first == version }.second

    @Test
    fun eachVersionWithItsHash_isntDamaged() {
        BlueCardDatabase.IDENTITY_HASHES.forEach { (version, hash) ->
            assertFalse(isRoomHeaderDamaged(version.toLong(), hash), "Version $version")
        }
    }

    @Test
    fun thisBuildsVersion_withAnotherHashOrNone_isDamaged() {
        assertTrue(isRoomHeaderDamaged(current, UNKNOWN_HASH))
        assertTrue(isRoomHeaderDamaged(current, null))
        assertTrue(isRoomHeaderDamaged(current, olderHash))
    }

    @Test
    fun knownHash_withAVersionThatDidntHaveIt_isDamaged() {
        assertTrue(isRoomHeaderDamaged(current or 64L, currentHash))
        assertTrue(isRoomHeaderDamaged(current - 1, currentHash))
        assertTrue(isRoomHeaderDamaged(-current, currentHash))
        // An older database whose version changed, before Room migrates it.
        assertTrue(isRoomHeaderDamaged(current - 2, olderHash))
    }

    @Test
    fun versionZero_withThisBuildsHashOrAnUnknownOne_isLeftToRoom() {
        // Room keeps tables of this build's schema and saves the version and hash again.
        assertFalse(isRoomHeaderDamaged(0, currentHash))
        assertFalse(isRoomHeaderDamaged(0, null))
        assertFalse(isRoomHeaderDamaged(0, UNKNOWN_HASH))
    }

    @Test
    fun versionZero_withAnOlderHash_isDamaged() {
        // Room would check an older schema's tables against this build's, and refuse them.
        assertTrue(isRoomHeaderDamaged(0, olderHash))
    }

    @Test
    fun olderVersion_withAnUnknownHashOrNone_isLeftToRoom() {
        // Room migrates it without reading the hash, then saves this build's.
        assertFalse(isRoomHeaderDamaged(current - 1, UNKNOWN_HASH))
        assertFalse(isRoomHeaderDamaged(current - 1, null))
    }

    @Test
    fun newerVersion_withAnUnknownHash_isLeftToRoom() {
        // A newer build's database, which Room refuses on purpose.
        assertFalse(isRoomHeaderDamaged(current + 1, UNKNOWN_HASH))
    }

    @Test
    fun versionThatKeptTheSchema_sharesItsHash_andIsntDamaged() {
        // As when version 5's migration only changes data.
        val hashes = listOf(4 to HASH, 5 to HASH)

        assertFalse(isRoomHeaderDamaged(4, HASH, hashes, currentVersion = 5))
        assertFalse(isRoomHeaderDamaged(5, HASH, hashes, currentVersion = 5))
        assertFalse(isRoomHeaderDamaged(0, HASH, hashes, currentVersion = 5))
        assertTrue(isRoomHeaderDamaged(3, HASH, hashes, currentVersion = 5))
        assertTrue(isRoomHeaderDamaged(6, HASH, hashes, currentVersion = 5))
    }

    @Test
    fun versionWithTwoHashes_acceptsBoth() {
        // As when a Room update computes another hash for a version installed databases keep.
        val hashes = listOf(4 to HASH, 4 to UNKNOWN_HASH)

        assertFalse(isRoomHeaderDamaged(4, HASH, hashes, currentVersion = 4))
        assertFalse(isRoomHeaderDamaged(4, UNKNOWN_HASH, hashes, currentVersion = 4))
    }

    private companion object {
        const val HASH = "bdfb11170140a5d1e2c7675c1126dcb3"

        // A hash no version of this app has had.
        const val UNKNOWN_HASH = "0123456789abcdef0123456789abcdef"
    }
}
