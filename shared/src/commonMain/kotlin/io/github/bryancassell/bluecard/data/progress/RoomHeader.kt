package io.github.bryancassell.bluecard.data.progress

/**
 * Whether storage has changed a database's Room schema [version] (its `user_version`) or its
 * [identityHash], given each version's hash and this build's version. SQLite doesn't use either,
 * so `quick_check` doesn't notice, and Room then refuses the database at every launch (#389). A
 * changed or missing hash with this build's version fails Room's identity check (`checkIdentity`,
 * Room 3.0.3). A version changed to another one has no migration, or runs migrations on a schema
 * that already has their columns, which fails with SQLITE_ERROR "duplicate column name". A
 * version changed to 0 makes Room check the tables against this build's schema, which an older
 * one fails.
 *
 * So a negative version, which no build saves, is damage. A hash that some version has had is
 * damage unless the database's version is one of those, which can be more than one
 * ([BlueCardDatabase.IDENTITY_HASHES]). Version 0 with this build's hash is left to Room, which
 * keeps the tables and saves the version again.
 *
 * A hash no version has had, or none, is damage with this build's version, which Room checks it
 * against. With another version, it's left to Room: an older database's, which Room migrates and
 * gives this build's hash; version 0, whose tables Room checks; or a newer build's, which Room
 * refuses with a missing migration. That crash is on purpose: reinstalling the newer build opens
 * it again, and setting it aside would tell the scout it's damaged. A newer version that kept a
 * schema this build knows can't be told from a damaged version, so it's set aside. That takes a
 * downgrade from such a version, while a version changed by storage is what this check is for.
 *
 * Room also accepts a "legacy" hash for this build's version, from an older way of computing it.
 * No database here has one: every Room version the app has used computed the hashes in
 * shared/schemas/, which CI fails the build for changing.
 */
internal fun isRoomHeaderDamaged(
    version: Long,
    identityHash: String?,
    identityHashes: List<Pair<Int, String>> = BlueCardDatabase.IDENTITY_HASHES,
    currentVersion: Int = BlueCardDatabase.VERSION
): Boolean {
    if (version < 0) return true
    val versionsWithHash =
        identityHashes.filter { it.second == identityHash }.map { it.first.toLong() }
    return if (versionsWithHash.isEmpty()) {
        version == currentVersion.toLong()
    } else {
        version !in versionsWithHash &&
            !(version == 0L && currentVersion.toLong() in versionsWithHash)
    }
}
