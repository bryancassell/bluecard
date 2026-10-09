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

/**
 * Binds the catalog, progress and damaged-progress repositories to their implementations. A UI
 * test that fakes one of them (`@UninstallModules(DataModule::class)` and `@BindValue`) supplies
 * all three.
 */
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
