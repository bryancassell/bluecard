package io.github.bryancassell.bluecard.data.catalog

import java.io.File
import kotlinx.serialization.SerializationException
import org.junit.Assert.fail
import org.junit.Test

/** Checks the catalog file that ships in the app. See docs/catalog.md for the rules. */
class BundledCatalogTest {
    @Test
    fun bundledCatalog_isValid() {
        // Local tests run with the module directory as the working directory.
        val file = File("src/main/assets/${AssetCatalogRepository.CATALOG_ASSET}")
        val catalog = try {
            parseCatalog(file.readText())
        } catch (e: SerializationException) {
            fail("${file.path} is not valid catalog JSON: ${e.message}")
            return
        }
        val errors = CatalogValidator.validate(catalog)
        if (errors.isNotEmpty()) {
            fail("${file.path} has ${errors.size} problem(s):\n" + errors.joinToString("\n"))
        }
    }
}
