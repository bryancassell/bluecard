package io.github.bryancassell.bluecard.data.catalog

import java.io.File
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Checks the catalog file that ships in the app. See docs/catalog.md for the rules. */
class BundledCatalogTest {
    // Local tests run with the module directory as the working directory.
    private val file = File("src/main/assets/${AssetCatalogRepository.CATALOG_ASSET}")

    @Test
    fun bundledCatalog_isValid() {
        val errors = CatalogValidator.validate(bundledCatalog())
        if (errors.isNotEmpty()) {
            fail("${file.path} has ${errors.size} problem(s):\n" + errors.joinToString("\n"))
        }
    }

    @Test
    fun bundledCatalog_listsTheRanksInTheOrderTheyreEarned() {
        // Ranks are earned in order, and the list order is that order.
        assertEquals(
            listOf("scout", "tenderfoot", "second-class", "first-class", "star", "life", "eagle"),
            bundledCatalog().ranks.map { it.id }
        )
    }

    private fun bundledCatalog(): Catalog = try {
        parseCatalog(file.readText())
    } catch (e: SerializationException) {
        throw AssertionError("${file.path} is not valid catalog JSON: ${e.message}", e)
    }
}
