package io.github.bryancassell.bluecard.data.profile

/** The scout, as entered on first launch or changed since. */
data class Profile(val name: String, val unitNumber: String)

// The longest text each field takes. A text field's text is saved with its screen's state,
// which has a size limit, so a huge paste mustn't reach it. An import holds a file to the same
// limits, so imported text is never cut short when the scout edits it; raising one therefore
// needs a new export format version (BACKUP_FORMAT_VERSION), so an older app reports a newer
// export as one.

/** Longer than any real name. */
const val PROFILE_NAME_MAX_LENGTH = 100

/** Longer than any real unit number, even written out as "Troop 1234 B". */
const val UNIT_NUMBER_MAX_LENGTH = 20
