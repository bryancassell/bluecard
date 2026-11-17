package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun readTableDefinition_givesEachColumn_andEachForeignKey() {
        val definition = BundledSQLiteDriver().open(":memory:").use {
            it.execSQL("CREATE TABLE parent (id TEXT NOT NULL, PRIMARY KEY(id))")
            it.execSQL(
                """
                CREATE TABLE child (
                    a TEXT NOT NULL, b INTEGER, c,
                    PRIMARY KEY(a, b),
                    FOREIGN KEY(a) REFERENCES parent(id) ON DELETE CASCADE,
                    FOREIGN KEY(b) REFERENCES parent
                )
                """
            )
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
                    TableDefinition.ForeignKey("parent", "a", "id", "NO ACTION", "CASCADE"),
                    // One that names no column refers to the parent's primary key.
                    TableDefinition.ForeignKey("parent", "b", null, "NO ACTION", "NO ACTION")
                )
            ),
            definition
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
