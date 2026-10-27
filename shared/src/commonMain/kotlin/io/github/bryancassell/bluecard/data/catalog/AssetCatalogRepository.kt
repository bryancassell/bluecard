package io.github.bryancassell.bluecard.data.catalog

import io.github.bryancassell.bluecard.di.IoDispatcher
import io.github.bryancassell.bluecard.resources.Res
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.IOException
import org.jetbrains.compose.resources.MissingResourceException

/**
 * Loads the catalog from Compose Multiplatform's resources ([CATALOG_FILE]) once, on first use.
 * On Android they're the app's assets.
 */
class AssetCatalogRepository(@param:IoDispatcher private val ioDispatcher: CoroutineDispatcher) :
    CatalogRepository {
    private val mutex = Mutex()
    private var catalog: Catalog? = null

    override suspend fun getBadges(): List<MeritBadge> = cachedCatalog().badges

    override suspend fun getRanks(): List<Rank> = cachedCatalog().ranks

    private suspend fun cachedCatalog(): Catalog = mutex.withLock {
        catalog ?: load().also { catalog = it }
    }

    private suspend fun load(): Catalog = withContext(ioDispatcher) {
        parseCatalog(readResourceFile(CATALOG_FILE).decodeToString())
    }

    companion object {
        /** The catalog's path in Compose Multiplatform's resources, `composeResources/`. */
        const val CATALOG_FILE = "files/catalog.json"
    }
}

/**
 * The file at [path] in Compose Multiplatform's resources. Throws an `IOException` if it can't
 * be read: the data layer reports data it can't read that way (ARCHITECTURE.md, Storage errors),
 * but Compose Multiplatform throws a `MissingResourceException` when the file can't be opened.
 */
internal suspend fun readResourceFile(path: String): ByteArray = try {
    Res.readBytes(path)
} catch (e: MissingResourceException) {
    throw IOException("Couldn't open $path", e)
}
