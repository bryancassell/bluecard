package io.github.bryancassell.bluecard.data.backup

import io.github.bryancassell.bluecard.data.catalog.AssetCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.parseCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that the QA test plan's seed (docs/qa-test-plan.md, The seed) still imports into the
 * bundled catalog. It names requirement numbers and tracker columns, which can change until a
 * version is released, and every assignment of a QA run starts by importing it.
 */
class QaSeedTest {
    // Local tests run with the module directory as the working directory.
    private val seed = File("../scripts/qa/seed.json")
    private val catalog = File("src/main/assets/${AssetCatalogRepository.CATALOG_ASSET}")

    @Test
    fun qaSeed_importsIntoTheBundledCatalog() {
        val parsed = parseCatalog(catalog.readText())

        val result = decodeBackup(seed.readText(), parsed.badges + parsed.ranks)

        assertTrue("${seed.path} doesn't import: $result", result is BackupReadResult.Valid)
        assertEquals(8, (result as BackupReadResult.Valid).backup.progress.size)
    }
}
