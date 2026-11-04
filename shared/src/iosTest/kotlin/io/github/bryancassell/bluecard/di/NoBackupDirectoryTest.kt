package io.github.bryancassell.bluecard.di

import io.github.bryancassell.bluecard.data.systemFileSystem
import io.github.bryancassell.bluecard.testing.FakeWarningLog
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.posix.chmod

class NoBackupDirectoryTest {
    private val fileSystem = systemFileSystem
    private val parent =
        NSTemporaryDirectory().toPath() / "NoBackupDirectoryTest-${Random.nextLong()}"
    private val warningLog = FakeWarningLog()
    private val warnings get() = warningLog.warnings.map { it.message }

    @AfterTest
    fun deleteFiles() {
        chmod((parent / NO_BACKUP_FOLDER).toString(), 0b111_000_000u)
        fileSystem.deleteRecursively(parent)
    }

    @Test
    fun isMade_andLeftOutOfBackups() {
        fileSystem.createDirectories(parent)

        val directory = noBackupDirectory(parent, warningLog)

        assertEquals(parent / NO_BACKUP_FOLDER, directory)
        assertTrue(fileSystem.metadata(directory).isDirectory)
        assertTrue(isExcludedFromBackup(directory))
        assertEquals(emptyList(), warnings)
    }

    // Apple says some file operations clear the mark, so it's set on a folder already there.
    @Test
    fun folderAlreadyThere_isMarked() {
        fileSystem.createDirectories(parent / NO_BACKUP_FOLDER)

        val directory = noBackupDirectory(parent, warningLog)

        assertTrue(isExcludedFromBackup(directory))
    }

    // The mark is an extended attribute, which a folder that can't be written can't be given.
    @Test
    fun markThatCantBeSet_isWarnedAbout_andTheFolderIsStillGiven() {
        val folder = parent / NO_BACKUP_FOLDER
        fileSystem.createDirectories(folder)
        chmod(folder.toString(), 0b101_000_000u)

        val directory = noBackupDirectory(parent, warningLog)

        assertEquals(folder, directory)
        assertFalse(isExcludedFromBackup(directory))
        assertTrue(warnings.single().startsWith("Couldn't leave $folder out of backups"))
    }

    // As on a full disk. Throwing would stop the progress database from opening.
    @Test
    fun folderThatCantBeMade_isWarnedAbout_andStillGiven() {
        // A file where the parent folder should be, so nothing can be made in it.
        fileSystem.createDirectories(parent)
        val notAFolder = parent / "file"
        fileSystem.write(notAFolder) {}

        val directory = noBackupDirectory(notAFolder, warningLog)

        assertEquals(notAFolder / NO_BACKUP_FOLDER, directory)
        assertEquals(listOf("Couldn't make $directory"), warnings)
    }
}

/** Whether [path] is marked to be left out of backups, read afresh rather than from a cache. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun isExcludedFromBackup(path: Path): Boolean = memScoped {
    val value = alloc<ObjCObjectVar<Any?>>()
    val url = NSURL.fileURLWithPath(path.toString())
    check(url.getResourceValue(value.ptr, NSURLIsExcludedFromBackupKey, null)) {
        "Couldn't read whether $path is left out of backups"
    }
    (value.value as NSNumber).boolValue
}
