package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.SQLiteConnection

/**
 * A table's definition, with what Room's check of a database's tables compares (`TableInfo`,
 * Room 3.0.3): each column's name, affinity, NOT NULL and whether it's in the primary key, and the
 * foreign keys. Room runs that check after each migration, and on a version-0 database that isn't
 * empty.
 *
 * Room compares a column's default only where its entity declares one, and none here does. The
 * columns a migration added have `DEFAULT NULL`, which a new table's lack. Indexes aren't
 * compared: SQLite reports one that names a changed column as SQLITE_CORRUPT, and gives Room the
 * table's own name for a column whose letter case changed (checked with SQLite 3.50.6).
 */
internal data class TableDefinition(
    val name: String,
    val columns: Set<Column>,
    val foreignKeys: Set<ForeignKey> = emptySet()
) {
    /** This table with [column] added, as a migration that adds a column leaves it. */
    operator fun plus(column: Column) = copy(columns = columns + column)

    data class Column(
        val name: String,
        val affinity: Affinity,
        val notNull: Boolean = false,
        val primaryKey: Boolean = false
    )

    /**
     * A foreign key, as `PRAGMA foreign_key_list` gives it, one row per column, with the names as
     * the definition writes them. [to] is null for one that names no column, which refers to the
     * other table's primary key.
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
 * A column's type affinity, which decides how SQLite stores the values written to it:
 * https://www.sqlite.org/datatype3.html#type_affinity
 */
internal enum class Affinity { TEXT, NUMERIC, INTEGER, REAL, BLOB }

/**
 * The affinity of a column declared with [type], by SQLite's rules in their order
 * (https://www.sqlite.org/datatype3.html#determination_of_column_affinity), as Room's check
 * applies them (`findAffinity`, Room 3.0.3). So a column with no type is NUMERIC, where SQLite
 * gives it BLOB.
 */
internal fun affinityOf(type: String): Affinity {
    val name = type.uppercase()
    return when {
        "INT" in name -> Affinity.INTEGER
        "CHAR" in name || "CLOB" in name || "TEXT" in name -> Affinity.TEXT
        "BLOB" in name -> Affinity.BLOB
        "REAL" in name || "FLOA" in name || "DOUB" in name -> Affinity.REAL
        else -> Affinity.NUMERIC
    }
}

/**
 * [table]'s definition in this database, from `PRAGMA table_info` and `PRAGMA foreign_key_list`
 * (https://www.sqlite.org/pragma.html#pragma_table_info). It has no columns if there's no such
 * table.
 */
internal fun SQLiteConnection.readTableDefinition(table: String): TableDefinition {
    // table_info's pk is the column's place in the primary key, or 0 if it isn't in it.
    val columns = prepare("""SELECT name, type, "notnull", pk FROM pragma_table_info(?)""").use {
        it.bindText(1, table)
        buildSet {
            while (it.step()) {
                add(
                    TableDefinition.Column(
                        it.getText(0),
                        affinityOf(it.getText(1)),
                        notNull = it.getLong(2) != 0L,
                        primaryKey = it.getLong(3) > 0
                    )
                )
            }
        }
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
    return TableDefinition(table, columns, foreignKeys)
}
