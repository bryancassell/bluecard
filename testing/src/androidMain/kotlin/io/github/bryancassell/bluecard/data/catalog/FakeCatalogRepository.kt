package io.github.bryancassell.bluecard.data.catalog

import java.io.IOException

/**
 * A [CatalogRepository] that returns the badges and ranks it was given, for other features'
 * tests.
 */
class FakeCatalogRepository(
    var badges: List<MeritBadge> = emptyList(),
    var ranks: List<Rank> = emptyList()
) : CatalogRepository {
    /** When true, every read throws, as reading the catalog asset does when it fails. */
    var failLoads = false

    override suspend fun getBadges(): List<MeritBadge> {
        if (failLoads) throw IOException("Load failed")
        return badges
    }

    override suspend fun getRanks(): List<Rank> {
        if (failLoads) throw IOException("Load failed")
        return ranks
    }
}
