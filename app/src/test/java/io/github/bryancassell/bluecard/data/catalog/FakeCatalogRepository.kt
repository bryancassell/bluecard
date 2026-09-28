package io.github.bryancassell.bluecard.data.catalog

/** A [CatalogRepository] that returns the badges it was given, for other features' tests. */
class FakeCatalogRepository(var badges: List<MeritBadge> = emptyList()) : CatalogRepository {
    override suspend fun getBadges(): List<MeritBadge> = badges
}
