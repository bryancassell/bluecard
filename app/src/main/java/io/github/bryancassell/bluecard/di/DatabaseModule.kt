package io.github.bryancassell.bluecard.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bryancassell.bluecard.data.progress.BlueCardDatabase
import io.github.bryancassell.bluecard.data.progress.FileDamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.SetAsideDamagedDatabaseFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        damagedProgress: FileDamagedProgressRepository
    ): BlueCardDatabase =
        Room.databaseBuilder(context, BlueCardDatabase::class.java, BlueCardDatabase.NAME)
            .openHelperFactory(SetAsideDamagedDatabaseFactory(damagedProgress))
            .build()
}
