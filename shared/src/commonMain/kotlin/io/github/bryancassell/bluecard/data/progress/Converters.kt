package io.github.bryancassell.bluecard.data.progress

import androidx.room3.ColumnTypeConverter
import androidx.room3.ProvidedColumnTypeConverter
import io.github.bryancassell.bluecard.text.replacingLoneSurrogates
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/**
 * How Room stores types SQLite doesn't have.
 *
 * A stored value these can't read back is damage, and goes to [onUnreadableValue] before the
 * error is thrown (#363). The app stores only what these write, which SQLite keeps as it is
 * ([mapToJson]), and SQLite's journal keeps a write from being left half done, so only storage
 * that changed a cell's bytes can cause it. The exception is tracker values with half an emoji
 * saved before [mapToJson] escaped it, which are set aside too, rather than guessed at: what
 * SQLite made of one is also a real emoji. A library update that couldn't read what an earlier
 * version wrote, or a migration that wrote a value these can't read, would look the same, so
 * ConvertersTest reads back unusual values, and each MigrationTest reads its rows back through
 * Room. `PRAGMA quick_check` checks the database's structure, not what a cell holds, so it doesn't find
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
     * Stored as JSON, with each lone UTF-16 surrogate escaped (`\ud83d`). Callers store tracker
     * values cleaned up ([normalizedTrackerValues]), without one, and
     * [ProgressRepository.replaceAll] takes them that way too, so this only guards the JSON.
     * SQLite would join a lone surrogate with the closing quote ([replacingLoneSurrogates]), so
     * the JSON couldn't be read back, and all progress would be set aside. Escaped, it's ASCII,
     * and [jsonToMap] gives back the same text. In JSON, a lone surrogate can only be inside a string, where `\uXXXX` stands for it.
     */
    @ColumnTypeConverter
    fun mapToJson(values: Map<String, String>): String =
        replacingLoneSurrogates(Json.encodeToString(values)) {
            "\\u" + it.code.toString(16).padStart(4, '0')
        }

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
