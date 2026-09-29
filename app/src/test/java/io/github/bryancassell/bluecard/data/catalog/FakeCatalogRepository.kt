package io.github.bryancassell.bluecard.data.catalog

import java.io.IOException

/** A [CatalogRepository] that returns the badges it was given, for other features' tests. */
class FakeCatalogRepository(var badges: List<MeritBadge> = emptyList()) : CatalogRepository {
    /** When true, [getBadges] throws, as reading the catalog asset does when it fails. */
    var failLoads = false

    override suspend fun getBadges(): List<MeritBadge> {
        if (failLoads) throw IOException("Load failed")
        return badges
    }
}
