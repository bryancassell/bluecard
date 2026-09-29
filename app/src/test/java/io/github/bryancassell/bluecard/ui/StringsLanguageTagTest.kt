package io.github.bryancassell.bluecard.ui

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Checks that each strings.xml names its own language in `strings_language`. The app loads and
 * formats its strings in that language (ProvideStringsLanguageResources), so a wrong tag, such
 * as "en" left in a translation, would replace the whole translation with English. Lint
 * doesn't check it.
 */
class StringsLanguageTagTest {
    private val stringsFiles = File("src/main/res")
        .listFiles { folder -> folder.name.startsWith("values") }!!
        .map { File(it, "strings.xml") }
        .filter { it.exists() }

    @Test
    fun defaultStrings_areChecked() {
        assertTrue(File("src/main/res/values/strings.xml") in stringsFiles)
    }

    @Test
    fun stringsLanguage_isWellFormedTagInItsFoldersLanguage() {
        for (file in stringsFiles) {
            val tag = stringsLanguage(file)
            val locale = Locale.forLanguageTag(tag)

            assertEquals("$file: not a well-formed tag", tag, locale.toLanguageTag())
            assertEquals(
                "$file: not the folder's language",
                Locale.forLanguageTag(folderLanguage(file.parentFile!!.name)).language,
                locale.language
            )
        }
    }

    private fun stringsLanguage(file: File): String {
        val strings = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
            .getElementsByTagName("string")
        return (0 until strings.length)
            .map { strings.item(it) as Element }
            .single { it.getAttribute("name") == "strings_language" }
            .textContent
    }

    /**
     * The language in a values folder's qualifiers, such as "pt" for values-pt-rBR or "sr" for
     * values-b+sr+Latn. A folder without one holds the default strings, which are English.
     */
    private fun folderLanguage(folder: String): String {
        val language = folder.split("-").drop(1)
            .firstOrNull { it.matches(Regex("[a-z]{2}|b\\+.+")) }
            ?: return "en"
        return language.removePrefix("b+").substringBefore("+")
    }
}
