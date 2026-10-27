package io.github.bryancassell.bluecard.data.backup

import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.Documents
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.getAdvancements
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.runOutlivingCaller
import io.github.bryancassell.bluecard.di.ApplicationScope
import io.github.bryancassell.bluecard.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.buffer
import okio.use

/**
 * [BackupRepository] that writes and reads exports as JSON ([encodeBackup], [decodeBackup]),
 * in the documents the scout picks ([documents]). A file is checked against the catalog of
 * badges and ranks in this version of the app.
 *
 * Exporting and importing run in [externalScope], so each finishes even if the scout leaves
 * the screen ([runOutlivingCaller]).
 */
class JsonBackupRepository(
    private val documents: Documents,
    private val catalogRepository: CatalogRepository,
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val externalScope: CoroutineScope
) : BackupRepository {
    override suspend fun exportBackup(destination: DocumentUri) = externalScope.runOutlivingCaller {
        documents.save(destination) { encodeBackup(currentBackup()).encodeToByteArray() }
    }

    private suspend fun currentBackup(): Backup {
        val profile = checkNotNull(profileRepository.observeProfile().first()) {
            "The scout hasn't saved their profile"
        }
        return Backup(profile, progressRepository.observeAllProgress().first())
    }

    override suspend fun readBackup(source: DocumentUri): BackupReadResult =
        withContext(ioDispatcher) {
            val bytes = documents.open(source).buffer().use { it.readAtMost(MAX_FILE_SIZE) }
                ?: return@withContext BackupReadResult.Invalid
            val json = try {
                bytes.decodeToString(throwOnInvalidSequence = true)
            } catch (e: CharacterCodingException) {
                // Not text, such as a photo. Caught here, since on Android it's an IOException too.
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

    /** Everything left in this source, or null if it holds more than [limit] bytes. */
    private fun BufferedSource.readAtMost(limit: Int): ByteArray? =
        if (request(limit + 1L)) null else readByteArray()

    companion object {
        /**
         * The largest file read, so a large file the scout picks by mistake, such as a video,
         * can't use up the app's memory. An export with notes filled to their 2,000-character
         * limit on 5,000 requirements is about 10 MB, far more than a scout records.
         */
        internal const val MAX_FILE_SIZE = 20 * 1024 * 1024
    }
}
