package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Reads tables' definitions with the SQLite the app bundles, which each platform's test runs. */
abstract class TableDefinitionTest {
    @Test
    fun affinityOf_followsSQLitesRules_inTheirOrder() {
        val affinities = listOf(
            "INTEGER", "BIGINT", "int", "VARCHAR(10)", "TEXT", "CLOB", "BLOB", "REAL", "FLOAT",
            "DOUBLE PRECISION", "DECIMAL(10,5)", "TEXU",
            // From SQLite's page: a name that matches more than one rule takes the first's.
            "CHARINT", "FLOATING POINT",
            // As Room's check gives it, where SQLite gives BLOB.
            ""
        ).associateWith(::affinityOf)

        assertEquals(
            mapOf(
                "INTEGER" to Affinity.INTEGER,
                "BIGINT" to Affinity.INTEGER,
                "int" to Affinity.INTEGER,
                "VARCHAR(10)" to Affinity.TEXT,
                "TEXT" to Affinity.TEXT,
                "CLOB" to Affinity.TEXT,
                "BLOB" to Affinity.BLOB,
                "REAL" to Affinity.REAL,
                "FLOAT" to Affinity.REAL,
                "DOUBLE PRECISION" to Affinity.REAL,
                "DECIMAL(10,5)" to Affinity.NUMERIC,
                "TEXU" to Affinity.NUMERIC,
                "CHARINT" to Affinity.INTEGER,
                "FLOATING POINT" to Affinity.INTEGER,
                "" to Affinity.NUMERIC
            ),
            affinities
        )
    }

    @Test
    fun readTableDefinition_givesEachColumn_foreignKey_andIndex() {
        val definition = BundledSQLiteDriver().open(":memory:").use {
            it.execSQL("CREATE TABLE parent (id TEXT NOT NULL, PRIMARY KEY(id))")
            it.execSQL("CREATE TABLE pair (x TEXT NOT NULL, y TEXT NOT NULL, PRIMARY KEY(x, y))")
            it.execSQL(
                """
                CREATE TABLE child (
                    a TEXT NOT NULL, b INTEGER, c,
                    PRIMARY KEY(a, b),
                    FOREIGN KEY(b, a) REFERENCES pair(y, x) ON DELETE CASCADE,
                    FOREIGN KEY(c) REFERENCES parent
                )
                """
            )
            it.execSQL("CREATE UNIQUE INDEX index_child_c_a ON child (c DESC, a)")
            it.execSQL("CREATE INDEX child_expression ON child (b, c + 1)")
            it.readTableDefinition("child")
        }

        assertEquals(
            TableDefinition(
                "child",
                setOf(
                    TableDefinition.Column("a", Affinity.TEXT, notNull = true, primaryKey = true),
                    TableDefinition.Column("b", Affinity.INTEGER, primaryKey = true),
                    TableDefinition.Column("c", Affinity.NUMERIC)
                ),
                setOf(
                    TableDefinition.ForeignKey(
                        "pair",
                        listOf("b", "a"),
                        listOf("y", "x"),
                        "NO ACTION",
                        "CASCADE"
                    ),
                    // One that names no column refers to the parent's primary key.
                    TableDefinition.ForeignKey(
                        "parent",
                        listOf("c"),
                        listOf(null),
                        "NO ACTION",
                        "NO ACTION"
                    )
                ),
                // Not the one SQLite makes for the primary key.
                setOf(
                    TableDefinition.Index(
                        "index_child_c_a",
                        unique = true,
                        listOf("c", "a"),
                        listOf(true, false)
                    ),
                    // As in Room, only the table's columns, not the expression.
                    TableDefinition.Index("child_expression", unique = false, listOf("b"))
                )
            ),
            definition
        )
    }

    @Test
    fun indexes_areComparedAsRoomComparesThem() {
        val index = TableDefinition.Index("index_t_a", unique = false, listOf("a"))

        // A name with Room's prefix is compared by the prefix alone.
        assertEquals(
            setOf(index),
            setOf(TableDefinition.Index("index_t_b", unique = false, listOf("a")))
        )
        assertNotEquals(index, TableDefinition.Index("Index_t_a", unique = false, listOf("a")))
        assertNotEquals(
            TableDefinition.Index("t_a", unique = false, listOf("a")),
            TableDefinition.Index("t_b", unique = false, listOf("a"))
        )
        assertNotEquals(index, TableDefinition.Index("index_t_a", unique = true, listOf("a")))
        assertNotEquals(index, TableDefinition.Index("index_t_a", unique = false, listOf("b")))
        assertNotEquals(
            index,
            TableDefinition.Index("index_t_a", unique = false, listOf("a"), listOf(true))
        )
    }

    @Test
    fun readTableDefinition_ofATableThatIsntThere_hasNoColumns() {
        val definition = BundledSQLiteDriver().open(":memory:").use {
            it.readTableDefinition("missing")
        }

        assertEquals(TableDefinition("missing", emptySet()), definition)
    }
}
