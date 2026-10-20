package io.github.bryancassell.bluecard.data.progress

import androidx.room3.ColumnTypeConverter
import java.time.LocalDate
import kotlinx.serialization.json.Json

/** How Room stores types SQLite doesn't have. */
class Converters {
    @ColumnTypeConverter
    fun localDateToString(date: LocalDate?): String? = date?.toString()

    @ColumnTypeConverter
    fun stringToLocalDate(text: String?): LocalDate? = text?.let(LocalDate::parse)

    @ColumnTypeConverter
    fun mapToJson(values: Map<String, String>): String = Json.encodeToString(values)

    @ColumnTypeConverter
    fun jsonToMap(json: String): Map<String, String> = Json.decodeFromString(json)
}
