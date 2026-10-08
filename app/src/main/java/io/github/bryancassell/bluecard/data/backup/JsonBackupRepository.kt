package io.github.bryancassell.bluecard.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.getAdvancements
import io.github.bryancassell.bluecard.data.openDocument
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.runOutlivingCaller
import io.github.bryancassell.bluecard.data.saveDocument
import io.github.bryancassell.bluecard.di.ApplicationScope
import io.github.bryancassell.bluecard.di.IoDispatcher
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.CharacterCodingException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * [BackupRepository] that writes and reads exports as JSON ([encodeBackup], [decodeBackup]),
 * through the content resolver, so it reaches any document the scout picks. A file is checked
 * against the catalog of badges and ranks in this version of the app.
 *
 * Exporting and importing run in [externalScope], so each finishes even if the scout leaves
 * the screen ([runOutlivingCaller]).
 */
class JsonBackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val externalScope: CoroutineScope
) : BackupRepository {
    override suspend fun exportBackup(destination: Uri) = externalScope.runOutlivingCaller {
        withContext(ioDispatcher) {
            context.contentResolver.saveDocument(destination) {
                encodeBackup(currentBackup()).encodeToByteArray()
            }
        }
    }

    private suspend fun currentBackup(): Backup {
        val profile = checkNotNull(profileRepository.observeProfile().first()) {
            "The scout hasn't saved their profile"
        }
        return Backup(profile, progressRepository.observeAllProgress().first())
    }

    override suspend fun readBackup(source: Uri): BackupReadResult = withContext(ioDispatcher) {
        val bytes = context.contentResolver.openDocument(source)
            .use { it.readAtMost(MAX_FILE_SIZE) }
            ?: return@withContext BackupReadResult.Invalid
        val json = try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            // Not text, such as a photo. Caught here, since it's an IOException too.
            return@withContext BackupReadResult.Invalid
        }
        // An editor can add a byte order mark when it saves the file, which JSON doesn't allow.
        decodeBackup(json.removePrefix("\uFEFF"), catalogRepository.getAdvancements())
    }

    // Progress first: it's replaced in one transaction, so if that fails, nothing has changed.
    override suspend fun importBackup(backup: Backup) = externalScope.runOutlivingCaller {
        progressRepository.replaceAll(backup.progress)
        profileRepository.saveProfile(backup.profile)
    }

    // Progress first, as for an import.
    override suspend fun mergeBackup(
        backup: Backup,
        fromFile: Set<String>,
        profileFromFile: Boolean
    ) = externalScope.runOutlivingCaller {
        progressRepository.merge(backup.progress, replacing = fromFile)
        if (profileFromFile) profileRepository.saveProfile(backup.profile)
    }

    /** Everything left in this stream, or null if it holds more than [limit] bytes. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (bytes.size() <= limit) {
            val count = read(buffer)
            if (count == -1) return bytes.toByteArray()
            bytes.write(buffer, 0, count)
        }
        return null
    }

    companion object {
        /**
         * The largest file read, so a large file the scout picks by mistake, such as a video,
         * can't use up the app's memory. An export with notes filled to their 2,000-character
         * limit on 5,000 requirements is about 10 MB, far more than a scout records.
         */
        internal const val MAX_FILE_SIZE = 20 * 1024 * 1024
    }
}
