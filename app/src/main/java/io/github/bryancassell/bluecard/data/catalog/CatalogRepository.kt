package io.github.bryancassell.bluecard.data.catalog

/** The merit badge catalog: read-only, bundled with the app. */
interface CatalogRepository {
    /**
     * Every badge in the catalog. Loaded on first use, then kept in memory. Throws an
     * `IOException` if the catalog can't be read.
     */
    suspend fun getBadges(): List<MeritBadge>
}
