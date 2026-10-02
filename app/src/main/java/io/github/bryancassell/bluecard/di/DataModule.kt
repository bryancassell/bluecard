package io.github.bryancassell.bluecard.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bryancassell.bluecard.data.catalog.AssetCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.DamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.FileDamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RoomProgressRepository

/** Binds each repository interface to its implementation. */
@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindCatalogRepository(repository: AssetCatalogRepository): CatalogRepository

    @Binds
    fun bindProgressRepository(repository: RoomProgressRepository): ProgressRepository

    @Binds
    fun bindDamagedProgressRepository(
        repository: FileDamagedProgressRepository
    ): DamagedProgressRepository
}
