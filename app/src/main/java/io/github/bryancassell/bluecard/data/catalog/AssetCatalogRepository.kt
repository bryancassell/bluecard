package io.github.bryancassell.bluecard.data.catalog

import android.content.Context
import io.github.bryancassell.bluecard.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Loads the catalog from assets/catalog.json once, on first use. */
class AssetCatalogRepository(
    private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CatalogRepository {
    private val mutex = Mutex()
    private var catalog: Catalog? = null

    override suspend fun getBadges(): List<MeritBadge> = cachedCatalog().badges

    override suspend fun getRanks(): List<Rank> = cachedCatalog().ranks

    private suspend fun cachedCatalog(): Catalog = mutex.withLock {
        catalog ?: load().also { catalog = it }
    }

    private suspend fun load(): Catalog = withContext(ioDispatcher) {
        val json = context.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }
        parseCatalog(json)
    }

    companion object {
        const val CATALOG_ASSET = "catalog.json"
    }
}
