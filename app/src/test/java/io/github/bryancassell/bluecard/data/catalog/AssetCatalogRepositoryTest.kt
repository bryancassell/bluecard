package io.github.bryancassell.bluecard.data.catalog

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** Loads the real bundled catalog through Android's asset manager (Robolectric). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AssetCatalogRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository =
        AssetCatalogRepository(ApplicationProvider.getApplicationContext(), dispatcher)

    @Test
    fun getBadges_returnsTheBundledCatalog() = runTest(dispatcher) {
        val expected = parseCatalog(
            File("src/main/assets/${AssetCatalogRepository.CATALOG_ASSET}").readText()
        ).badges
        assertEquals(expected, repository.getBadges())
    }

    @Test
    fun getRanks_returnsTheBundledCatalog() = runTest(dispatcher) {
        val expected = parseCatalog(
            File("src/main/assets/${AssetCatalogRepository.CATALOG_ASSET}").readText()
        ).ranks
        assertEquals(expected, repository.getRanks())
    }

    @Test
    fun getBadges_loadsOnlyOnce() = runTest(dispatcher) {
        assertSame(repository.getBadges(), repository.getBadges())
    }
}
