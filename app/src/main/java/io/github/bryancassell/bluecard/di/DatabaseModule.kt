package io.github.bryancassell.bluecard.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.bryancassell.bluecard.data.progress.BlueCardDatabase
import io.github.bryancassell.bluecard.data.progress.FileDamagedProgressRepository
import io.github.bryancassell.bluecard.data.progress.SetAsideDamagedDatabaseDriver
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
            .setDriver(SetAsideDamagedDatabaseDriver(BundledSQLiteDriver(), damagedProgress))
            .build()
}
