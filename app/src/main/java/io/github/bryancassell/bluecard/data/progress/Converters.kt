package io.github.bryancassell.bluecard.data.progress

import androidx.room.TypeConverter
import java.time.LocalDate
import kotlinx.serialization.json.Json

/** How Room stores types SQLite doesn't have. */
class Converters {
    @TypeConverter
    fun localDateToString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun stringToLocalDate(text: String?): LocalDate? = text?.let(LocalDate::parse)

    @TypeConverter
    fun mapToJson(values: Map<String, String>): String = Json.encodeToString(values)

    @TypeConverter
    fun jsonToMap(json: String): Map<String, String> = Json.decodeFromString(json)
}
