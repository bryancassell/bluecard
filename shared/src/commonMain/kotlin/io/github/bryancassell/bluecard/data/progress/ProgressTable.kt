package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.SQLiteConnection

/**
 * A progress table as Room creates it in each schema version ([BlueCardDatabase.TABLES]).
 * [columns] are the first version's, and [addedColumns] those a later version added, with that
 * version.
 */
internal class ProgressTable(
    val name: String,
    private val columns: List<String>,
    private val addedColumns: Map<String, Int> = emptyMap(),
    private val foreignKeys: Set<TableDefinition.ForeignKey> = emptySet()
) {
    /** The table's definition in a database of [version]. */
    fun definitionAt(version: Int) = TableDefinition(
        columns.toSet() + addedColumns.filterValues { it <= version }.keys,
        foreignKeys
    )
}

/**
 * The parts of a table's definition that statements name, so that a change to one fails them:
 * its columns' names and its foreign keys.
 */
internal data class TableDefinition(val columns: Set<String>, val foreignKeys: Set<ForeignKey>) {
    /**
     * A foreign key, as `PRAGMA foreign_key_list` gives it, one row per column. [to] is null for
     * one that names no column, which refers to the other table's primary key.
     */
    data class ForeignKey(
        val table: String,
        val from: String,
        val to: String?,
        val onUpdate: String,
        val onDelete: String
    )
}

/**
 * [table]'s definition in this database, from `PRAGMA table_info` and `PRAGMA foreign_key_list`
 * (https://www.sqlite.org/pragma.html#pragma_table_info). It has no columns if there's no such
 * table.
 */
internal fun SQLiteConnection.readTableDefinition(table: String): TableDefinition {
    val columns = prepare("SELECT name FROM pragma_table_info(?)").use {
        it.bindText(1, table)
        buildSet { while (it.step()) add(it.getText(0)) }
    }
    val foreignKeys = prepare(
        """SELECT "table", "from", "to", on_update, on_delete FROM pragma_foreign_key_list(?)"""
    ).use {
        it.bindText(1, table)
        buildSet {
            while (it.step()) {
                add(
                    TableDefinition.ForeignKey(
                        it.getText(0),
                        it.getText(1),
                        if (it.isNull(2)) null else it.getText(2),
                        it.getText(3),
                        it.getText(4)
                    )
                )
            }
        }
    }
    return TableDefinition(columns, foreignKeys)
}
