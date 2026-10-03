package io.github.bryancassell.bluecard.data.progress

import io.github.bryancassell.bluecard.data.catalog.ColumnTotal
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import java.math.BigDecimal

/**
 * The decimal separators a number in a tracker row can have, whichever the scout's keyboard
 * offers: a point, a comma, and the Arabic decimal separator that Persian keyboards offer.
 */
val DECIMAL_SEPARATORS = setOf('.', ',', '\u066B')

/**
 * A number column's stored value as a number, or null if it isn't one. The scout types digits of
 * any script, with at most one decimal separator ([DECIMAL_SEPARATORS]), and it's stored as
 * typed, but a value imported from a backup can be any text.
 */
fun storedNumber(text: String): BigDecimal? {
    val digits = text.map { char ->
        when (char) {
            in DECIMAL_SEPARATORS -> '.'
            else -> char.digitToIntOrNull()?.digitToChar() ?: return null
        }
    }
    // Null for a lone separator, which has no digit, or for more than one.
    return String(digits.toCharArray()).toBigDecimalOrNull()
}

/**
 * A number column's values added up ([sum]), against the amount the requirement asks for
 * ([total]), as in "4 of 6 hours".
 */
data class TrackerTotal(val sum: BigDecimal, val total: ColumnTotal)

/**
 * This tracker's columns that have a [total][TrackerColumn.total], in column order, each with its
 * values added up over the [entries] recorded for its requirement. Only a log has totals
 * (docs/catalog.md), so every entry is a row. A value that isn't a number ([storedNumber]) isn't
 * counted.
 */
fun TrackerDefinition.totals(entries: List<TrackerEntry>): List<TrackerTotal> =
    columns.mapNotNull { column ->
        column.total?.let { total ->
            val sum = entries.sumOf { it.values[column.id]?.let(::storedNumber) ?: BigDecimal.ZERO }
            TrackerTotal(sum, total)
        }
    }
