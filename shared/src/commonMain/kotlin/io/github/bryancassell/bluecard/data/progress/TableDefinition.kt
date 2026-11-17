package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.SQLiteConnection

/**
 * A table's definition, with what Room's check of a database's tables compares (`TableInfo`,
 * Room 3.0.3): each column's name, affinity, NOT NULL and whether it's in the primary key, the
 * foreign keys, and the indexes made by CREATE INDEX. Room runs that check after each migration,
 * on a version-0 database that isn't empty, and on one without `room_master_table`.
 *
 * Room compares a column's default only where its entity declares one, and none here does. The
 * columns a migration added have `DEFAULT NULL`, which a new table's lack.
 */
internal data class TableDefinition(
    val name: String,
    val columns: Set<Column>,
    val foreignKeys: Set<ForeignKey> = emptySet(),
    val indexes: Set<Index> = emptySet()
) {
    /** This table with [column] added. */
    operator fun plus(column: Column) = copy(columns = columns + column)

    data class Column(
        val name: String,
        val affinity: Affinity,
        val notNull: Boolean = false,
        val primaryKey: Boolean = false
    )

    /**
     * A foreign key, from the rows of `PRAGMA foreign_key_list` with its id, as Room groups them:
     * its [columns] and the [referencedColumns] they refer to, in order, with the names as the
     * definition writes them. A referenced column is null where the key names none, so it refers
     * to the other table's primary key.
     */
    data class ForeignKey(
        val table: String,
        val columns: List<String>,
        val referencedColumns: List<String?>,
        val onUpdate: String,
        val onDelete: String
    )

    /**
     * An index made by CREATE INDEX, from `PRAGMA index_list` and `PRAGMA index_xinfo`: the
     * table's columns it holds, in order, and whether each is in descending order. As in Room,
     * what isn't one of the table's columns, such as an expression, is left out, so an index whose
     * column storage changed into an expression differs.
     *
     * Room names its indexes `index_<table>_<columns>`, and compares such a name by that prefix
     * alone, so this does too.
     */
    class Index(
        val name: String,
        val unique: Boolean,
        val columns: List<String>,
        val descending: List<Boolean> = columns.map { false }
    ) {
        private val comparedName = if (name.startsWith(ROOM_PREFIX)) ROOM_PREFIX else name

        override fun equals(other: Any?) = other is Index &&
            comparedName == other.comparedName &&
            unique == other.unique &&
            columns == other.columns &&
            descending == other.descending

        override fun hashCode() = listOf(comparedName, unique, columns, descending).hashCode()

        override fun toString() =
            "Index(name=$name, unique=$unique, columns=$columns, descending=$descending)"

        private companion object {
            const val ROOM_PREFIX = "index_"
        }
    }
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
 * [table]'s definition in this database, from `PRAGMA table_info`, `foreign_key_list`,
 * `index_list` and `index_xinfo` (https://www.sqlite.org/pragma.html#pragma_table_info), read as
 * Room reads them (`readTableInfo`, Room 3.0.3). It has no columns if there's no such table.
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
    // One row for each of a key's columns, with the key's id.
    val foreignKeys = prepare(
        """
        SELECT id, "table", "from", "to", on_update, on_delete FROM pragma_foreign_key_list(?)
        ORDER BY id, seq
        """
    ).use {
        it.bindText(1, table)
        val keys = mutableMapOf<Long, TableDefinition.ForeignKey>()
        while (it.step()) {
            val column = it.getText(2)
            val referenced = if (it.isNull(3)) null else it.getText(3)
            keys[it.getLong(0)] = keys[it.getLong(0)]?.let { key ->
                key.copy(
                    columns = key.columns + column,
                    referencedColumns = key.referencedColumns + referenced
                )
            } ?: TableDefinition.ForeignKey(
                it.getText(1),
                listOf(column),
                listOf(referenced),
                it.getText(4),
                it.getText(5)
            )
        }
        keys.values.toSet()
    }
    // Origin c is CREATE INDEX, not one SQLite made for a primary key or UNIQUE.
    val indexes = prepare(
        """SELECT name, "unique" FROM pragma_index_list(?) WHERE origin = 'c'"""
    ).use {
        it.bindText(1, table)
        buildList { while (it.step()) add(it.getText(0) to (it.getLong(1) == 1L)) }
    }.mapTo(mutableSetOf()) { (name, unique) -> readIndex(name, unique) }
    return TableDefinition(table, columns, foreignKeys, indexes)
}

// A cid below 0 is the rowid or an expression.
private fun SQLiteConnection.readIndex(name: String, unique: Boolean) = prepare(
    """SELECT name, "desc" FROM pragma_index_xinfo(?) WHERE cid >= 0 ORDER BY seqno"""
).use {
    it.bindText(1, name)
    val columns = mutableListOf<String>()
    val descending = mutableListOf<Boolean>()
    while (it.step()) {
        columns += it.getText(0)
        descending += it.getLong(1) > 0
    }
    TableDefinition.Index(name, unique, columns, descending)
}
