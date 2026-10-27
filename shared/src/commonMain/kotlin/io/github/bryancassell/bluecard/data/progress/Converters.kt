package io.github.bryancassell.bluecard.data.progress

import androidx.room3.ColumnTypeConverter
import androidx.room3.ProvidedColumnTypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/**
 * How Room stores types SQLite doesn't have.
 *
 * A stored value these can't read back is damage, and goes to [onUnreadableValue] before the
 * error is thrown (#363). The app stores only what these write, which SQLite keeps as it is
 * ([mapToJson]), and SQLite's journal keeps a write from being left half done, so only storage
 * that changed a cell's bytes can cause it. A library update that couldn't read what an earlier
 * version wrote would look the same, so ConvertersTest reads back unusual values too.
 * `PRAGMA quick_check` checks the database's structure, not what a cell holds, so it doesn't find
 * it. On Android, the driver records it, as it does damage SQLite finds while reading, so the
 * next launch sets the database aside. Otherwise the error would crash the app at every launch,
 * since Home reads all the progress. Since it takes a parameter, Room can't make it, so each
 * database builder adds one ([ProvidedColumnTypeConverter]).
 */
@ProvidedColumnTypeConverter
class Converters(private val onUnreadableValue: (IllegalArgumentException) -> Unit) {
    @ColumnTypeConverter
    fun localDateToString(date: LocalDate?): String? = date?.toString()

    @ColumnTypeConverter
    fun stringToLocalDate(text: String?): LocalDate? = text?.let { reading { LocalDate.parse(it) } }

    /**
     * Stored as JSON, with each lone UTF-16 surrogate escaped (`\ud83d`), such as half an emoji
     * that was pasted or imported. As androidx.sqlite 2.7.1 bundles it, SQLite joins a lone
     * surrogate with the character after it when it stores the text. Here that's the closing
     * quote, so the JSON couldn't be read back (RoomProgressRepositoryTest). Escaped, it's ASCII,
     * and [jsonToMap] gives back the same text.
     */
    @ColumnTypeConverter
    fun mapToJson(values: Map<String, String>): String =
        Json.encodeToString(values).escapingLoneSurrogates()

    @ColumnTypeConverter
    fun jsonToMap(json: String): Map<String, String> = reading { Json.decodeFromString(json) }

    // LocalDate.parse throws DateTimeFormatException and decodeFromString SerializationException,
    // both IllegalArgumentExceptions (kotlinx-datetime 0.8.0, kotlinx-serialization 1.12.0).
    private inline fun <T> reading(read: () -> T): T = try {
        read()
    } catch (e: IllegalArgumentException) {
        onUnreadableValue(e)
        throw e
    }
}

// In JSON, a lone surrogate can only be inside a string, where \uXXXX stands for it.
private fun String.escapingLoneSurrogates() = buildString {
    val text = this@escapingLoneSurrogates
    for ((i, char) in text.withIndex()) {
        val paired = (char.isHighSurrogate() && text.getOrNull(i + 1)?.isLowSurrogate() == true) ||
            (char.isLowSurrogate() && text.getOrNull(i - 1)?.isHighSurrogate() == true)
        if (char.isSurrogate() && !paired) {
            append("\\u").append(char.code.toString(16).padStart(4, '0'))
        } else {
            append(char)
        }
    }
}
