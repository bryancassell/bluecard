package io.github.bryancassell.bluecard

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.profile.DataStoreProfileRepository
import io.github.bryancassell.bluecard.data.progress.BlueCardDatabase
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser

/**
 * Checks that the manifest uses the app's Auto Backup rules, and that each set of rules covers
 * the files where the app keeps the scout's data. DatabaseModuleTest and ProfileModuleTest
 * check that the app really stores its data in those files.
 * https://developer.android.com/identity/data/autobackup#IncludingFiles
 */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = context.getDatabasePath(BlueCardDatabase.NAME)

    // Room uses write-ahead logging, so recent changes may be only in the -wal file. The
    // -shm file isn't needed: SQLite rebuilds it from the -wal file.
    // https://www.sqlite.org/walformat.html
    private val dataFiles = listOf(
        database,
        File("${database.path}-wal"),
        context.preferencesDataStoreFile(DataStoreProfileRepository.FILE_NAME)
    )

    // Read only by the tests that use them, so a broken section fails only those tests.
    private val fullBackupContent by lazy { readRules(R.xml.backup_rules, "full-backup-content") }
    private val cloudBackup by lazy { readRules(R.xml.data_extraction_rules, "cloud-backup") }
    private val deviceTransfer by lazy { readRules(R.xml.data_extraction_rules, "device-transfer") }

    @Test
    fun manifest_usesTheseRules() {
        // Read from the manifest source, because Robolectric doesn't load dataExtractionRules.
        val application = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
            .getElementsByTagName("application")
            .item(0) as Element
        val android = "http://schemas.android.com/apk/res/android"

        assertEquals("true", application.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(android, "fullBackupContent"))
        assertEquals(
            "@xml/data_extraction_rules",
            application.getAttributeNS(android, "dataExtractionRules")
        )
    }

    @Test
    fun fullBackupContent_backsUpScoutData() {
        assertBacksUpDataFiles(fullBackupContent)
    }

    @Test
    fun cloudBackup_backsUpScoutData() {
        assertBacksUpDataFiles(cloudBackup)
    }

    @Test
    fun deviceTransfer_backsUpScoutData() {
        assertBacksUpDataFiles(deviceTransfer)
    }

    @Test
    fun ruleSets_areTheSame() {
        // Every Android version and kind of backup copies the same files.
        assertEquals(fullBackupContent, cloudBackup)
        assertEquals(fullBackupContent, deviceTransfer)
    }

    @Test
    fun ruleSets_haveNoConditions() {
        // Backup isn't limited to phones that can encrypt it (ARCHITECTURE.md, "Backup").
        for (ruleSet in listOf(fullBackupContent, cloudBackup, deviceTransfer)) {
            assertEquals(emptyList<String>(), ruleSet.conditions)
        }
    }

    /** An `<include>` or `<exclude>` rule, resolved to the file or directory it names. */
    private data class Rule(val include: Boolean, val file: File)

    /** The rules in one section, and any conditions that limit when backup happens. */
    private data class RuleSet(val rules: Set<Rule>, val conditions: List<String>)

    /** Reads the rules inside the [section] element of the XML resource [id]. */
    private fun readRules(id: Int, section: String): RuleSet =
        context.resources.getXml(id).use { parser ->
            val rules = mutableSetOf<Rule>()
            val conditions = mutableListOf<String>()
            var found = false
            var inSection = false
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.name == section) inSection = parser.eventType == XmlPullParser.START_TAG
                if (!inSection || parser.eventType != XmlPullParser.START_TAG) continue
                found = true
                if (parser.getAttributeValue(null, "disableIfNoEncryptionCapabilities") == "true") {
                    conditions += "disableIfNoEncryptionCapabilities"
                }
                parser.getAttributeValue(null, "requireFlags")?.let { flags ->
                    conditions += "requireFlags=$flags"
                }
                if (parser.name != section) {
                    val directory = domainDirectory(parser.getAttributeValue(null, "domain"))
                    val path = parser.getAttributeValue(null, "path")
                    rules += Rule(parser.name == "include", File(directory, path).canonicalFile)
                }
            }
            assertTrue("The backup rules have no <$section> section", found)
            RuleSet(rules, conditions)
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
    private fun assertBacksUpDataFiles(ruleSet: RuleSet) {
        val includes = ruleSet.rules.filter { it.include }
        val excludes = ruleSet.rules.filterNot { it.include }
        for (file in dataFiles.map { it.canonicalFile }) {
            val included = includes.isEmpty() || includes.any { file.startsWith(it.file) }
            val excluded = excludes.any { file.startsWith(it.file) }
            assertTrue("$file is not backed up", included && !excluded)
        }
    }
}
