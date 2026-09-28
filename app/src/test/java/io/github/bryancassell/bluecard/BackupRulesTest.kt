package io.github.bryancassell.bluecard

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.profile.DataStoreProfileRepository
import io.github.bryancassell.bluecard.data.progress.BlueCardDatabase
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Checks that each set of Auto Backup rules covers the files where the app keeps the
 * scout's data, so moving or renaming one can't silently drop it from backups.
 * https://developer.android.com/identity/data/autobackup#IncludingFiles
 */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = context.getDatabasePath(BlueCardDatabase.NAME)

    // Room uses write-ahead logging, so recent changes may be only in the -wal file.
    private val dataFiles = listOf(
        database,
        File("${database.path}-wal"),
        File("${database.path}-shm"),
        context.preferencesDataStoreFile(DataStoreProfileRepository.FILE_NAME)
    )

    @Test
    fun fullBackupContent_backsUpScoutData() {
        assertBacksUpDataFiles(readRules(R.xml.backup_rules, "full-backup-content"))
    }

    @Test
    fun cloudBackup_backsUpScoutData() {
        assertBacksUpDataFiles(readRules(R.xml.data_extraction_rules, "cloud-backup"))
    }

    @Test
    fun deviceTransfer_backsUpScoutData() {
        assertBacksUpDataFiles(readRules(R.xml.data_extraction_rules, "device-transfer"))
    }

    /** An `<include>` or `<exclude>` rule, resolved to the file or directory it names. */
    private data class Rule(val include: Boolean, val file: File)

    /** Reads the rules inside the [section] element of the XML resource [id]. */
    private fun readRules(id: Int, section: String): List<Rule> {
        val parser = context.resources.getXml(id)
        val rules = mutableListOf<Rule>()
        var inSection = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.name == section) {
                inSection = parser.eventType == XmlPullParser.START_TAG
            } else if (inSection && parser.eventType == XmlPullParser.START_TAG) {
                val directory = domainDirectory(parser.getAttributeValue(null, "domain"))
                val path = parser.getAttributeValue(null, "path")
                rules += Rule(parser.name == "include", File(directory, path).canonicalFile)
            }
        }
        return rules
    }

    private fun domainDirectory(domain: String): File = when (domain) {
        "database" -> database.parentFile!!
        "file" -> context.filesDir
        else -> error("Add the directory for backup domain \"$domain\" to this test.")
    }

    /**
     * With no `<include>` rules everything is backed up; otherwise only what they name.
     * `<exclude>` rules then remove files from that set.
     */
    private fun assertBacksUpDataFiles(rules: List<Rule>) {
        val includes = rules.filter { it.include }
        val excludes = rules.filterNot { it.include }
        for (file in dataFiles.map { it.canonicalFile }) {
            val included = includes.isEmpty() || includes.any { file.startsWith(it.file) }
            val excluded = excludes.any { file.startsWith(it.file) }
            assertTrue("$file is not backed up", included && !excluded)
        }
    }
}
