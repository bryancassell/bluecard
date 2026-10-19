package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * SPIKE (#339): whether BundledSQLiteDriver works under Robolectric, and in more than one
 * Robolectric sandbox in the same JVM. Each SDK level gets its own sandbox class loader, and a JVM
 * loads a native library into only one class loader.
 */
abstract class BundledDriverSpike {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun room_roundTripsThroughTheBundledDriver() = runTest {
        val database = Room.databaseBuilder(
            context,
            BlueCardDatabase::class.java,
            File(folder.root, BlueCardDatabase.NAME).absolutePath
        )
            .setDriver(BundledSQLiteDriver())
            .build()
        try {
            val badge =
                BadgeProgress("camping", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 15))
            database.progressDao().insertBadge(badge)

            assertEquals(
                listOf(badge),
                database.progressDao().observeAll().first().map {
                    it.badge
                }
            )
            val version = database.useReaderConnection { connection ->
                connection.usePrepared("SELECT sqlite_version()") {
                    it.step()
                    it.getText(0)
                }
            }
            println("SPIKE sdk=${Build.VERSION.SDK_INT} sqlite_version=$version")
        } finally {
            database.close()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class BundledDriverSpikeTest : BundledDriverSpike()

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class BundledDriverSdk30SpikeTest : BundledDriverSpike()
