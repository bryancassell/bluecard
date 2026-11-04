package io.github.bryancassell.bluecard.data.progress

import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.bryancassell.bluecard.data.NoBackgroundTime
import io.github.bryancassell.bluecard.data.OutlivingWork
import io.github.bryancassell.bluecard.data.systemFileSystem
import io.github.bryancassell.bluecard.di.DeviceClock
import io.github.bryancassell.bluecard.testing.FakeWarningLog
import io.github.bryancassell.bluecard.testing.TemporaryDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/**
 * Opens the database as the app does, on files damaged as SQLite finds them or with values
 * [Converters] can't read, with the SQLite the app bundles. Each platform's test runs it, since
 * SQLite's errors are the platform's own.
 */
abstract class SetAsideDamagedDatabaseDriverTest {
    /** The error [error] was raised for, where the platform's [sqliteException] keeps it. */
    protected abstract fun causeOf(error: SQLiteException): Throwable?

    private val fileSystem = systemFileSystem
    private val temporary = TemporaryDirectory()

    // As Android's folders for the app's databases and for what backups leave out.
    private val noBackup = temporary.path / "no_backup"
    private val file = temporary.path / "databases" / BlueCardDatabase.NAME

    init {
        fileSystem.createDirectory(noBackup)
        fileSystem.createDirectory(file.parent!!)
    }

    // Lazy, so a failure to make it, such as iOS's when the schemas' folder isn't given, fails the
    // test and still deletes the folder.
    private val helper by lazy { migrationTestHelper(file) }

    private val warningLog = FakeWarningLog()

    // As the app's scope, which outlives the screens.
    private val outlivingWork = OutlivingWork(CoroutineScope(SupervisorJob()), NoBackgroundTime)

    private val damagedProgress = FileDamagedProgressRepository(
        { noBackup },
        DeviceClock,
        warningLog,
        outlivingWork,
        Dispatchers.Unconfined
    )
    private val databases = mutableListOf<BlueCardDatabase>()

    @AfterTest
    fun closeDatabasesAndDeleteFiles() {
        try {
            databases.forEach { it.close() }
        } finally {
            temporary.delete()
        }
    }

    /** The driver as a new app run gets it. */
    private fun newDriver() = SetAsideDamagedDatabaseDriver(BundledSQLiteDriver(), damagedProgress)

    private fun open(driver: SetAsideDamagedDatabaseDriver = newDriver()) = databaseBuilder(file)
        .setDriver(driver)
        .addColumnTypeConverter(Converters(driver::recordUnreadableValue))
        .build()
        .also { databases += it }

    private suspend fun BlueCardDatabase.readProgress() = progressDao().observeAll().first()

    private val folder = noBackup / FileDamagedProgressRepository.FOLDER_NAME
    private val damageFile = folder / "damage-found"

    /** The folders progress was set aside in. */
    private fun copies() = fileSystem.listOrNull(folder).orEmpty()
        .filter { fileSystem.metadata(it).isDirectory }

    /** The database file set aside, the only one in its folder's only copy. */
    private fun setAside() = copies().single() / BlueCardDatabase.NAME

    private fun readBytes(file: Path) = fileSystem.read(file) { readByteArray() }

    private fun writeBytes(file: Path, bytes: ByteArray) = fileSystem.write(file) { write(bytes) }

    private fun lastModified(file: Path) = fileSystem.metadata(file).lastModifiedAtMillis!!

    /** Deletes the database and the files SQLite keeps beside it, as Context.deleteDatabase does. */
    private fun deleteDatabase() {
        for (suffix in listOf("", "-journal", "-wal", "-shm")) {
            fileSystem.delete("$file$suffix".toPath())
        }
    }

    @Test
    fun databaseDamagedBeforeItOpens_isSetAside_andAnEmptyOneOpens() = runTest {
        val damaged = ByteArray(8192) { 7 }
        writeBytes(file, damaged)

        val progress = open().readProgress()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertContentEquals(damaged, readBytes(setAside()))
        assertTrue(damagedProgress.observeNoticePending().first())
    }

    @Test
    fun damagedDatabaseThatCantBeSetAside_isntUsed_andIsSetAsideAtTheNextLaunch() = runTest {
        val damaged = ByteArray(8192) { 7 }
        writeBytes(file, damaged)
        // Stops the database being moved or deleted.
        val error = whileReadOnly(file.parent!!) {
            runCatching { newDriver().open(file.toString()) }.exceptionOrNull()
        }

        assertTrue(error is SQLiteException && error.isDamage(), "Expected damage, got $error")
        assertContentEquals(damaged, readBytes(file))
        assertTrue(damagedProgress.isDamageRecorded(file))
        assertFalse(damagedProgress.observeNoticePending().first())

        // The next launch.
        val progress = open().readProgress()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        // The only copy: the first try left no empty folder.
        assertContentEquals(damaged, readBytes(setAside()))
        assertTrue(damagedProgress.observeNoticePending().first())
    }

    @Test
    fun databaseWithDamagedPages_isSetAsideWhenItOpens_andAnEmptyOneOpens() = runTest {
        saveProgressAndDamageIt()
        val damaged = readBytes(file)

        val progress = open().readProgress()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertContentEquals(damaged, readBytes(setAside()))
        assertTrue(damagedProgress.observeNoticePending().first())
    }

    @Test
    fun damageFoundWhileReading_isSetAsideAtTheNextLaunch_evenIfTheCheckPasses() = runTest {
        // One driver for two databases, so the second skips the check, as a database damaged
        // after it's checked would.
        val driver = newDriver()
        val undamaged = saveProgressAndDamageIt(open(driver))
        val damagedAt = lastModified(file)
        val database = open(driver)
        val repository =
            RoomProgressRepository(
                database,
                outlivingWork
            )

        val error = runCatching { repository.observeAllProgress().first() }.exceptionOrNull()

        // A bug, which crashes the app, rather than a load failure (IOException).
        assertTrue(
            error is SQLiteException && error.isDamage(),
            "Expected SQLite to find the damage, got $error"
        )
        // Even a statement that reads nothing fails after that, so nothing more is saved.
        val next = runCatching {
            database.useReaderConnection { it.usePrepared("SELECT 1") { select -> select.step() } }
        }.exceptionOrNull()
        assertTrue(
            next is SQLiteException && next.message == error.message,
            "Expected the same error, got $next"
        )
        assertTrue(damagedProgress.isDamageRecorded(file))
        assertEquals(emptyList<Path>(), copies())
        assertFalse(damagedProgress.observeNoticePending().first())

        // The next launch, with damage the check doesn't find. Nothing saves into the database
        // once the damage is found, so putting the file back keeps its time from before that.
        databases.last().close()
        writeBytes(file, undamaged)
        setLastModified(file, damagedAt)
        val progress = open().readProgress()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertContentEquals(undamaged, readBytes(setAside()))
        assertTrue(damagedProgress.observeNoticePending().first())
        assertFalse(damagedProgress.isDamageRecorded(file))
    }

    @Test
    fun valueThatCantBeRead_isSetAsideAtTheNextLaunch_thoughTheCheckPasses() = runTest {
        saveProgress(open())
        // A byte changed by storage, in a page SQLite still finds valid.
        val check = BundledSQLiteDriver().open(file.toString()).use { sqlite ->
            sqlite.execSQL("UPDATE badge_progress SET startedDate = '2026-0' || char(1) || '3-01'")
            sqlite.prepare("PRAGMA quick_check").use {
                it.step()
                it.getText(0)
            }
        }
        assertEquals("ok", check)
        val damaged = readBytes(file)
        val database = open()
        val repository =
            RoomProgressRepository(
                database,
                outlivingWork
            )

        val error = runCatching { repository.observeAllProgress().first() }.exceptionOrNull()

        // A bug, which crashes the app, rather than a load failure (IOException).
        assertTrue(error is IllegalArgumentException, "Expected the date's error, got $error")
        // Even a statement that reads nothing fails after that, so nothing more is saved. It fails
        // as SQLite's damage does, which Room's InvalidationTracker catches.
        val next = runCatching {
            database.useReaderConnection { it.usePrepared("SELECT 1") { select -> select.step() } }
        }.exceptionOrNull()
        assertTrue(
            next is SQLiteException && next.isDamage() && causeOf(next)?.message == error.message,
            "Expected damage caused by the date's error, got $next"
        )
        assertTrue(damagedProgress.isDamageRecorded(file))
        assertEquals(emptyList<Path>(), copies())
        assertFalse(damagedProgress.observeNoticePending().first())

        database.close()
        val progress = open().readProgress()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertContentEquals(damaged, readBytes(setAside()))
        assertTrue(damagedProgress.observeNoticePending().first())
        assertFalse(damagedProgress.isDamageRecorded(file))
    }

    @Test
    fun recordedDamageThatCantBeCleared_deletesOnlyTheDatabaseItWasFoundIn() = runTest {
        open().readProgress()
        databases.single().close()
        damagedProgress.recordDamage()
        // As the database's last save and the damage found in it could be, so the new database
        // Room makes is newer than the record.
        dateInOrder(older = file, newer = damageFile)
        // Stops the set-aside and the record's deletion.
        val progress = whileReadOnly(folder) {
            // The launch after the damage was found, which can't set it aside, so deletes it.
            val first = open()
            assertEquals(emptyList<BadgeProgressDetails>(), first.readProgress())
            assertTrue(damagedProgress.observeNoticePending().first())
            assertEquals(emptyList<Path>(), copies())
            assertTrue(fileSystem.exists(damageFile))
            damagedProgress.dismissNotice()
            first.progressDao().insertBadge(
                BadgeProgress("camping", VERSION, LocalDate(2026, 3, 1))
            )
            first.close()

            // The launch after that, with the record still there.
            open().readProgress()
        }

        assertEquals(listOf("camping"), progress.map { it.badge.badgeId })
        assertFalse(damagedProgress.observeNoticePending().first())
        // The second launch only tried to delete the record again.
        assertEquals(
            listOf(
                "Couldn't set aside a damaged database, so deleted it: $file",
                "Couldn't delete $damageFile",
                "Couldn't delete $damageFile"
            ),
            warningLog.warnings.map { it.message }
        )
    }

    @Test
    fun recordOlderThanTheDatabase_isCleared_andTheDatabaseStaysInUse() = runTest {
        saveProgress(open())
        damagedProgress.recordDamage()
        // As when a set-aside couldn't delete the record, and Room made this database after it.
        dateInOrder(older = damageFile, newer = file)

        val progress = open().readProgress()

        assertEquals(300, progress.size)
        assertFalse(fileSystem.exists(damageFile))
        assertEquals(emptyList<Path>(), copies())
        assertFalse(damagedProgress.observeNoticePending().first())
    }

    /**
     * Saves enough badges to fill several pages, then damages every page after the tables'
     * first ones. SQLite reads only those first pages to open the database, so it opens, and
     * finds the damage when progress is read. Gives the file as it was before the damage.
     */
    private suspend fun saveProgressAndDamageIt(database: BlueCardDatabase = open()): ByteArray {
        saveProgress(database)
        val (pageSize, lastTablePage) = BundledSQLiteDriver().open(file.toString()).use { sqlite ->
            sqlite.longFor("PRAGMA page_size") to
                sqlite.longFor("SELECT max(rootpage) FROM sqlite_master")
        }
        val undamaged = readBytes(file)
        fileSystem.openReadWrite(file).use {
            val firstDamaged = lastTablePage * pageSize
            assertTrue(it.size() > firstDamaged, "Expected pages after the tables' first ones")
            val damaged = (it.size() - firstDamaged).toInt()
            it.write(firstDamaged, ByteArray(damaged) { 0x55 }, 0, damaged)
        }
        return undamaged
    }

    /** Saves enough badges to fill several pages, then closes [database]. */
    private suspend fun saveProgress(database: BlueCardDatabase) {
        repeat(300) {
            database.progressDao().insertBadge(
                BadgeProgress("badge-$it-${"x".repeat(200)}", VERSION, LocalDate(2026, 3, 1))
            )
        }
        database.close()
    }

    private fun SQLiteConnection.longFor(sql: String) = prepare(sql).use {
        it.step()
        it.getLong(0)
    }

    @Test
    fun databaseWithAPageNothingUses_staysInUse() = runTest {
        saveProgress(open())
        addPageNothingUses()
        val check = BundledSQLiteDriver().open(file.toString()).use { sqlite ->
            sqlite.prepare("PRAGMA quick_check").use {
                it.step()
                it.getText(0)
            }
        }
        assertTrue(check.endsWith(": never used"), check)

        val progress = open().readProgress()

        assertEquals(300, progress.size)
        assertEquals(emptyList<Path>(), copies())
        assertFalse(damagedProgress.observeNoticePending().first())
    }

    /**
     * Adds a page to the end of the database and counts it in the database's size, with nothing
     * using it, as when a page leaks. SQLite trusts the size in the header, whose numbers are
     * big-endian (https://www.sqlite.org/fileformat2.html#in_header_database_size).
     */
    private fun addPageNothingUses() = fileSystem.openReadWrite(file).use {
        val pageSize = it.source(16).buffer().use { header ->
            header.readShort().toInt() and 0xFFFF
        }
            .let { size -> if (size == 1) 65536 else size }
        val pages = it.source(28).buffer().use { header -> header.readInt() }
        it.resize((pages + 1L) * pageSize)
        it.sink(28).buffer().use { header -> header.writeInt(pages + 1) }
    }

    @Test
    fun connectionWhoseLogLimitsFail_isClosed_andTheErrorGoesOn() {
        val full = SQLiteException("Error code: 13, message: database or disk is full")
        var closed = false
        val failing = object : SQLiteDriver {
            override fun open(fileName: String) = object : SQLiteConnection {
                override fun prepare(sql: String): SQLiteStatement = throw full

                override fun close() {
                    closed = true
                }
            }
        }

        val error = runCatching {
            SetAsideDamagedDatabaseDriver(failing, damagedProgress).open(file.toString())
        }.exceptionOrNull()

        assertEquals(full, error)
        assertTrue(closed)
    }

    @Test
    fun connections_limitTheWriteAheadLog_asAndroidsSQLiteDoes() {
        newDriver().open(file.toString()).use {
            assertEquals(524288L, it.longFor("PRAGMA journal_size_limit"))
            assertEquals(100L, it.longFor("PRAGMA wal_autocheckpoint"))
        }
    }

    @Test
    fun checkThatFailsForAnotherReason_movesNothing_andRunsAgainAtTheNextOpen() = runTest {
        // A folder where the database file should be, so SQLite can't open it.
        fileSystem.createDirectory(file)
        val driver = newDriver()
        val repository = RoomProgressRepository(
            open(driver),
            outlivingWork
        )

        val error = runCatching { repository.observeAllProgress().first() }.exceptionOrNull()

        // A load failure, as for a full disk.
        assertTrue(error is IOException, "Expected an IOException, got $error")
        assertTrue(fileSystem.metadata(file).isDirectory)
        assertEquals(emptyList<Path>(), copies())
        assertFalse(damagedProgress.observeNoticePending().first())

        // Room tries again at the next read.
        fileSystem.delete(file)
        val damaged = ByteArray(8192) { 7 }
        writeBytes(file, damaged)
        val progress = repository.observeAllProgress().first()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertContentEquals(damaged, readBytes(setAside()))
    }

    @Test
    fun olderDatabase_isMigratedByRoom() = runTest {
        helper.createDatabase(1).use {
            it.execSQL(
                """
                INSERT INTO badge_progress (badgeId, requirementsVersion, startedDate)
                VALUES ('camping', '2026-01-01', '2026-03-01')
                """
            )
        }

        val progress = open().readProgress()

        assertEquals(listOf("camping"), progress.map { it.badge.badgeId })
    }

    @Test
    fun newerDatabase_isRefusedByRoom() = runTest {
        // As a newer build leaves it: a higher version, with its own schema's identity hash.
        changeSavedProgress(
            "PRAGMA user_version = ${BlueCardDatabase.VERSION + 1}",
            "UPDATE room_master_table SET identity_hash = '$UNKNOWN_HASH'"
        )

        assertRefusedByRoom()
    }

    @Test
    fun newerDatabaseWithAnIdentityTableThisBuildCantRead_isRefusedByRoom() = runTest {
        // As a newer build's Room could leave it, if it ever defined the table otherwise.
        changeSavedProgress(
            "PRAGMA user_version = ${BlueCardDatabase.VERSION + 1}",
            *RENAME_IDENTITY_COLUMN
        )

        assertRefusedByRoom()
    }

    @Test
    fun changedIdentityHash_isSetAsideWhenItOpens_andAnEmptyOneOpens() = runTest {
        // Its first character changed by storage.
        val damaged = changeSavedProgress(
            """
            UPDATE room_master_table SET identity_hash =
                CASE substr(identity_hash, 1, 1) WHEN '0' THEN '1' ELSE '0' END ||
                substr(identity_hash, 2)
            """
        )

        assertSetAsideWhenItOpens(damaged)
    }

    @Test
    fun missingIdentityHash_isSetAsideWhenItOpens_andAnEmptyOneOpens() = runTest {
        val damaged = changeSavedProgress("DELETE FROM room_master_table")

        assertSetAsideWhenItOpens(damaged)
    }

    @Test
    fun nullIdentityHash_isSetAsideWhenItOpens_andAnEmptyOneOpens() = runTest {
        val damaged = changeSavedProgress("UPDATE room_master_table SET identity_hash = NULL")

        assertSetAsideWhenItOpens(damaged)
    }

    @Test
    fun higherVersionWithThisSchema_isSetAsideWhenItOpens_andAnEmptyOneOpens() = runTest {
        // A version no build has had, as when storage changes a bit of version 4 to make 68.
        val damaged = changeSavedProgress("PRAGMA user_version = ${BlueCardDatabase.VERSION + 64}")

        assertSetAsideWhenItOpens(damaged)
    }

    @Test
    fun lowerVersionWithThisSchema_isSetAsideWhenItOpens_andAnEmptyOneOpens() = runTest {
        // Version 1, whose schema every later version has changed.
        val damaged = changeSavedProgress("PRAGMA user_version = 1")

        assertSetAsideWhenItOpens(damaged)
    }

    @Test
    fun olderDatabaseWithAnotherVersion_isSetAsideWhenItOpens_andAnEmptyOneOpens() = runTest {
        helper.createDatabase(3).use {
            it.execSQL(
                """
                INSERT INTO badge_progress (badgeId, requirementsVersion, startedDate)
                VALUES ('camping', '2026-01-01', '2026-03-01')
                """
            )
        }
        val damaged = changeFile("PRAGMA user_version = 2")

        assertSetAsideWhenItOpens(damaged)
    }

    @Test
    fun identityTableWithoutItsColumn_isSetAsideWhenItOpens_andAnEmptyOneOpens() = runTest {
        val damaged = changeSavedProgress(*RENAME_IDENTITY_COLUMN)

        assertSetAsideWhenItOpens(damaged)
    }

    @Test
    fun tableWithARenamedColumn_isSetAsideWhenItOpens_andAnEmptyOneOpens() = runTest {
        val damaged = changeSavedProgress(*RENAME_COUNSELOR_COLUMN)

        assertSetAsideWhenItOpens(damaged)
    }

    @Test
    fun olderTableWithARenamedColumn_isSetAsideWhenItOpens_andAnEmptyOneOpens() = runTest {
        helper.createDatabase(3).use {
            it.execSQL(
                """
                INSERT INTO badge_progress (badgeId, requirementsVersion, startedDate)
                VALUES ('camping', '2026-01-01', '2026-03-01')
                """
            )
        }
        val damaged = changeFile(*RENAME_COUNSELOR_COLUMN)

        assertSetAsideWhenItOpens(damaged)
    }

    @Test
    fun foreignKeyToARenamedColumn_isSetAsideWhenItOpens_andAnEmptyOneOpens() = runTest {
        val damaged = changeSavedProgress(
            *changeDefinition(
                "requirement_progress",
                "REFERENCES `badge_progress`(`badgeId`)",
                "REFERENCES `badge_progress`(`badgeIe`)"
            )
        )

        assertSetAsideWhenItOpens(damaged)
    }

    @Test
    fun missingIdentityTableWithAVersionNoBuildHas_isSetAsideWhenItOpens() = runTest {
        val damaged = changeSavedProgress(
            "DROP TABLE room_master_table",
            "PRAGMA user_version = ${BlueCardDatabase.VERSION + 64}"
        )

        assertSetAsideWhenItOpens(damaged)
    }

    @Test
    fun identityHashQueryThatFailsForAnotherReason_movesNothing() = runTest {
        saveProgress(open())
        val busy = SQLiteException("Error code: 5, message: database is locked")
        // Fails Room's identity query, which the check runs too, as another app run might.
        val failing = object : SQLiteDriver {
            override fun open(fileName: String): SQLiteConnection {
                val connection = BundledSQLiteDriver().open(fileName)
                return object : SQLiteConnection by connection {
                    override fun prepare(sql: String): SQLiteStatement =
                        if ("FROM room_master_table WHERE" in sql) {
                            throw busy
                        } else {
                            connection.prepare(sql)
                        }
                }
            }
        }
        val repository = RoomProgressRepository(
            open(SetAsideDamagedDatabaseDriver(failing, damagedProgress)),
            outlivingWork
        )

        val error = runCatching { repository.observeAllProgress().first() }.exceptionOrNull()

        // A load failure, as for a full disk, rather than damage.
        assertTrue(error is IOException, "Expected an IOException, got $error")
        assertEquals(emptyList<Path>(), copies())
        assertFalse(damagedProgress.observeNoticePending().first())
    }

    @Test
    fun versionZero_isLeftToRoom_whichKeepsTheProgress_andSavesTheVersionAgain() = runTest {
        saveProgress(open())
        val saved = savedHeader()
        changeFile("PRAGMA user_version = 0")

        val progress = open().readProgress()

        assertEquals(300, progress.size)
        assertEquals(emptyList<Path>(), copies())
        databases.last().close()
        assertEquals(saved, savedHeader())
    }

    @Test
    fun missingIdentityTable_isLeftToRoom_whichKeepsTheProgress_andSavesTheHashAgain() = runTest {
        saveProgress(open())
        val saved = savedHeader()
        changeFile("DROP TABLE room_master_table")

        val progress = open().readProgress()

        assertEquals(300, progress.size)
        assertEquals(emptyList<Path>(), copies())
        databases.last().close()
        assertEquals(saved, savedHeader())
    }

    @Test
    fun identityHashes_areTheOnesRoomSaves() = runTest {
        // Each version's from its schema in shared/schemas/.
        val saved = (1..BlueCardDatabase.VERSION).map { version ->
            deleteDatabase()
            helper.createDatabase(version).close()
            version to savedHeader().second
        }
        // This build's as the app saves it.
        deleteDatabase()
        open().readProgress()
        databases.single().close()
        val (savedVersion, savedHash) = savedHeader()

        assertEquals(BlueCardDatabase.VERSION.toLong(), savedVersion)
        assertEquals(saved.last().second, savedHash)
        saved.forEach {
            assertTrue(
                it in BlueCardDatabase.IDENTITY_HASHES,
                "BlueCardDatabase.IDENTITY_HASHES needs $it, the hash Room saves for its version."
            )
        }
        // Any other line keeps an old hash for its version, which no version has now.
        (BlueCardDatabase.IDENTITY_HASHES - saved.toSet()).forEach { (version, hash) ->
            assertTrue(version in 1..BlueCardDatabase.VERSION, "$version isn't a version")
            assertTrue(
                saved.none { it.second == hash },
                "($version, $hash) has another version's hash"
            )
        }
    }

    @Test
    fun tables_areTheOnesRoomCreates() = runTest {
        // Each version's from its schema in shared/schemas/.
        (1..BlueCardDatabase.VERSION).forEach { version ->
            deleteDatabase()
            helper.createDatabase(version).use { assertTables(version, it) }
        }
        // This build's as the app creates them.
        deleteDatabase()
        open().readProgress()
        databases.single().close()
        BundledSQLiteDriver().open(file.toString()).use {
            assertTables(BlueCardDatabase.VERSION, it)
        }
    }

    /** Checks that [BlueCardDatabase.TABLES] has [connection]'s tables, as of [version]. */
    private fun assertTables(version: Int, connection: SQLiteConnection) {
        // Room's and SQLite's own tables aside.
        val names = connection.prepare(
            """
            SELECT name FROM sqlite_master WHERE type = 'table'
            AND name NOT IN ('room_master_table', 'sqlite_sequence')
            """
        ).use { buildSet { while (it.step()) add(it.getText(0)) } }
        assertEquals(names, BlueCardDatabase.TABLES.map { it.name }.toSet())
        BlueCardDatabase.TABLES.forEach {
            assertEquals(
                connection.readTableDefinition(it.name),
                it.definitionAt(version),
                "BlueCardDatabase.TABLES needs ${it.name} as of version $version"
            )
        }
    }

    /** The database file's Room version and identity hash. */
    private fun savedHeader() = BundledSQLiteDriver().open(file.toString()).use { sqlite ->
        val hash = sqlite.prepare("SELECT identity_hash FROM room_master_table").use {
            it.step()
            it.getText(0)
        }
        sqlite.longFor("PRAGMA user_version") to hash
    }

    /** Opens the database, as the next app run does, and checks Room refuses it as a bug. */
    private suspend fun assertRefusedByRoom() {
        val error = runCatching { open().readProgress() }.exceptionOrNull()

        // Room's error for a missing migration, which crashes the app.
        assertTrue(
            error is IllegalStateException && error.message.orEmpty().contains("migration"),
            "Expected Room's missing migration error, got $error"
        )
        assertEquals(emptyList<Path>(), copies())
    }

    /** Saves enough badges to fill several pages, then [changeFile]. */
    private suspend fun changeSavedProgress(vararg changes: String): ByteArray {
        saveProgress(open())
        return changeFile(*changes)
    }

    /**
     * Runs [changes] on the database file, which leave nothing `PRAGMA quick_check` finds. Gives
     * the file as it is after them.
     */
    private fun changeFile(vararg changes: String): ByteArray {
        val check = BundledSQLiteDriver().open(file.toString()).use { sqlite ->
            changes.forEach { sqlite.execSQL(it) }
            sqlite.prepare("PRAGMA quick_check").use {
                it.step()
                it.getText(0)
            }
        }
        assertEquals("ok", check)
        return readBytes(file)
    }

    /** Opens the database, as the next app run does, and checks it sets [damaged] aside. */
    private suspend fun assertSetAsideWhenItOpens(damaged: ByteArray) {
        val progress = open().readProgress()

        assertEquals(emptyList<BadgeProgressDetails>(), progress)
        assertContentEquals(damaged, readBytes(setAside()))
        assertTrue(damagedProgress.observeNoticePending().first())
    }

    private companion object {
        val VERSION: LocalDate = LocalDate(2026, 1, 1)

        // A hash no version of the database has had.
        const val UNKNOWN_HASH = "0123456789abcdef0123456789abcdef"

        /**
         * Changes [table]'s definition, as storage could, into SQL that still reads: [old] in it
         * becomes [new].
         */
        fun changeDefinition(table: String, old: String, new: String) = arrayOf(
            "PRAGMA writable_schema = ON",
            "UPDATE sqlite_master SET sql = replace(sql, '$old', '$new') WHERE name = '$table'",
            "PRAGMA writable_schema = OFF"
        )

        val RENAME_IDENTITY_COLUMN =
            changeDefinition("room_master_table", "identity_hash", "identity_hasj")

        val RENAME_COUNSELOR_COLUMN =
            changeDefinition("badge_progress", "counselor_name", "counselor_namf")
    }
}
