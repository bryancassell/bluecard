package io.github.bryancassell.bluecard.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bryancassell.bluecard.data.catalog.AssetCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.profile.DataStoreProfileRepository
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RoomProgressRepository

/** Binds each repository interface to its implementation. */
@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindCatalogRepository(repository: AssetCatalogRepository): CatalogRepository

    @Binds
    fun bindProfileRepository(repository: DataStoreProfileRepository): ProfileRepository

    @Binds
    fun bindProgressRepository(repository: RoomProgressRepository): ProgressRepository
}
