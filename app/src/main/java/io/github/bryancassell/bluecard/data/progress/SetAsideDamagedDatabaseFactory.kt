package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File

/**
 * Opens Room's database as Room does by default ([FrameworkSQLiteOpenHelperFactory]), except
 * that a database SQLite finds damaged is set aside ([FileDamagedProgressRepository.setAside])
 * rather than deleted (#70). Room 2.8 keeps Android's default corruption handler, which deletes
 * the database files (checked in Room 2.8.5 and androidx.sqlite 2.6.2).
 */
class SetAsideDamagedDatabaseFactory(private val damagedProgress: FileDamagedProgressRepository) :
    SupportSQLiteOpenHelper.Factory {
    private val framework = FrameworkSQLiteOpenHelperFactory()

    override fun create(
        configuration: SupportSQLiteOpenHelper.Configuration
    ): SupportSQLiteOpenHelper = framework.create(
        SupportSQLiteOpenHelper.Configuration(
            context = configuration.context,
            name = configuration.name,
            callback = SetAsideWhenDamaged(configuration.callback),
            useNoBackupDirectory = configuration.useNoBackupDirectory,
            allowDataLossOnRecovery = configuration.allowDataLossOnRecovery
        )
    )

    /** Room's [room] callback, except when SQLite finds the database damaged. */
    private inner class SetAsideWhenDamaged(private val room: SupportSQLiteOpenHelper.Callback) :
        SupportSQLiteOpenHelper.Callback(room.version) {
        override fun onConfigure(db: SupportSQLiteDatabase) = room.onConfigure(db)

        override fun onCreate(db: SupportSQLiteDatabase) = room.onCreate(db)

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
            room.onUpgrade(db, oldVersion, newVersion)

        override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
            room.onDowngrade(db, oldVersion, newVersion)

        override fun onOpen(db: SupportSQLiteDatabase) = room.onOpen(db)

        /**
         * Called by Android's SQLite when it finds the database damaged. If that's while
         * opening it, the database isn't open yet, and SQLite then creates a new one in its
         * place. If it's while reading or writing, the database is open, and it's closed first,
         * as the default handler does. Room keeps using the closed connection (checked in Room
         * 2.8.5), so the next read or write throws an exception the repository treats as a bug,
         * which crashes the app, and the new database is created when the app starts again.
         * Damage is rare, so this was chosen over reporting those exceptions as storage
         * failures until the app restarts, or moving every read to a new database. SQLite's
         * https://www.sqlite.org/howtocorrupt.html lists causes such as failing storage;
         * neither it nor Android publishes how often it happens.
         */
        override fun onCorruption(db: SupportSQLiteDatabase) {
            // Set aside even if closing fails, as the default handler deletes in that case.
            try {
                if (db.isOpen) db.close()
            } finally {
                damagedProgress.setAside(File(checkNotNull(db.path)))
            }
        }
    }
}
