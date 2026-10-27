package io.github.bryancassell.bluecard.data.progress

import androidx.room3.ColumnTypeConverter
import androidx.room3.ProvidedColumnTypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/**
 * How Room stores types SQLite doesn't have.
 *
 * A stored value these can't read back is damage, and goes to [onUnreadableValue] before the
 * error is thrown (#363). The app stores only what these write, and SQLite's journal keeps a
 * write from being left half done, so only storage that changed a cell's bytes can cause it.
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

    @ColumnTypeConverter
    fun mapToJson(values: Map<String, String>): String = Json.encodeToString(values)

    @ColumnTypeConverter
    fun jsonToMap(json: String): Map<String, String> = reading { Json.decodeFromString(json) }

    // LocalDate.parse throws DateTimeFormatException and decodeFromString SerializationException,
    // both IllegalArgumentExceptions.
    private inline fun <T> reading(read: () -> T): T = try {
        read()
    } catch (e: IllegalArgumentException) {
        onUnreadableValue(e)
        throw e
    }
}
