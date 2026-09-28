package io.github.bryancassell.bluecard.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bryancassell.bluecard.data.profile.DataStoreProfileRepository
import io.github.bryancassell.bluecard.data.profile.ProfileRepository

/**
 * Binds [ProfileRepository]. Kept apart from DataModule so UI tests can replace just this
 * binding with a fake (`@UninstallModules(ProfileModule::class)` and `@BindValue`).
 */
@Module
@InstallIn(SingletonComponent::class)
interface ProfileModule {
    @Binds
    fun bindProfileRepository(repository: DataStoreProfileRepository): ProfileRepository
}
