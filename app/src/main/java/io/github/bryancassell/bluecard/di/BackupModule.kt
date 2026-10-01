package io.github.bryancassell.bluecard.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.bryancassell.bluecard.data.backup.BackupRepository
import io.github.bryancassell.bluecard.data.backup.JsonBackupRepository

/**
 * Binds [BackupRepository]. Kept apart from DataModule, so tests that fake the progress and
 * profile repositories still export and import through the real one.
 */
@Module
@InstallIn(SingletonComponent::class)
interface BackupModule {
    @Binds
    fun bindBackupRepository(repository: JsonBackupRepository): BackupRepository
}
