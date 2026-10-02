package io.github.bryancassell.bluecard.data.catalog

/** The catalog of merit badges and ranks: read-only, bundled with the app. */
interface CatalogRepository {
    /**
     * Every badge in the catalog. Loaded on first use, then kept in memory. Throws an
     * `IOException` if the catalog can't be read.
     */
    suspend fun getBadges(): List<MeritBadge>

    /** Every rank in the catalog, in the order they're earned. Loaded as [getBadges] is. */
    suspend fun getRanks(): List<Rank>
}

/**
 * Every badge and rank in the catalog, for the pages that serve both, such as a requirement's,
 * to find one by its ID. Throws as [CatalogRepository.getBadges] does.
 */
suspend fun CatalogRepository.getAdvancements(): List<Advancement> = getBadges() + getRanks()
