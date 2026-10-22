package io.github.bryancassell.bluecard.data.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Loads the real bundled catalog through Android's asset manager (Robolectric). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AssetCatalogRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = AssetCatalogRepository(dispatcher)
    private val bundled =
        File("src/commonMain/composeResources/${AssetCatalogRepository.CATALOG_FILE}")

    @Test
    fun getBadges_returnsTheBundledCatalog() = runTest(dispatcher) {
        assertEquals(parseCatalog(bundled.readText()).badges, repository.getBadges())
    }

    @Test
    fun getRanks_returnsTheBundledCatalog() = runTest(dispatcher) {
        assertEquals(parseCatalog(bundled.readText()).ranks, repository.getRanks())
    }

    @Test
    fun getBadges_loadsOnlyOnce() = runTest(dispatcher) {
        assertSame(repository.getBadges(), repository.getBadges())
    }

    // Compose Multiplatform throws its own MissingResourceException, which isn't an IOException.
    @Test
    fun readResourceFile_missingFile_throwsIOException() = runTest {
        val error = runCatching { readResourceFile("files/missing.json") }.exceptionOrNull()
        assertTrue("Expected an IOException, got $error", error is IOException)
    }
}
