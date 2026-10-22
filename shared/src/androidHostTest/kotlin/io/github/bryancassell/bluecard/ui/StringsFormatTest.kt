package io.github.bryancassell.bluecard.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Checks each strings.xml against Compose Multiplatform's string format (see the top of
 * values/strings.xml). Android Lint checked Android's strings this way (StringFormatInvalid,
 * StringFormatCount, MissingQuantity) but doesn't read these, and Compose Multiplatform shows an
 * Android escape or a malformed argument as written, and throws for an argument it isn't given.
 */
class StringsFormatTest {
    private val stringsFiles = File("src/commonMain/composeResources")
        .listFiles { folder -> folder.name.startsWith("values") }!!
        .map { File(it, "strings.xml") }
        .filter { it.exists() }

    private val defaultStrings = File("src/commonMain/composeResources/values/strings.xml")

    @Test
    fun defaultStrings_areChecked() {
        assertTrue(defaultStrings in stringsFiles)
    }

    // Compose Multiplatform turns only \n, \t, \uXXXX and \\ into characters, takes quotes as
    // written, and fills in only "%1$s" and "%1$d", so Android's \' and "%%" would show as written.
    @Test
    fun strings_useComposeMultiplatformsFormat() {
        for (file in stringsFiles) {
            for ((name, text) in texts(file)) {
                val where = "$file, $name: \"$text\""
                assertTrue("$where: an Android escape", ANDROID_ESCAPE !in text)
                assertTrue(
                    "$where: Android's quotes",
                    !(text.length > 1 && text.startsWith('"') && text.endsWith('"'))
                )
                assertTrue("$where: not \"%1\$s\" or \"%1\$d\"", MALFORMED_ARGUMENT !in text)
            }
        }
    }

    @Test
    fun arguments_areNumberedFromOne() {
        for (file in stringsFiles) {
            for ((name, text) in texts(file)) {
                val numbers = arguments(text)
                assertEquals("$file, $name", (1..numbers.size).toSet(), numbers)
            }
        }
    }

    @Test
    fun plurals_haveOther_andTheSameArgumentsInEachForm() {
        for (file in stringsFiles) {
            for (plurals in elements(file, "plurals")) {
                val name = plurals.getAttribute("name")
                val forms = items(plurals)
                assertTrue("$file, $name: no \"other\"", "other" in forms)
                assertEquals("$file, $name", 1, forms.values.map(::arguments).toSet().size)
            }
        }
    }

    @Test
    fun translations_haveTheDefaultStringsArguments() {
        val default = texts(defaultStrings).toMap()
        for (file in stringsFiles - defaultStrings) {
            for ((name, text) in texts(file)) {
                val defaultText = default[name] ?: continue
                assertEquals("$file, $name", arguments(defaultText), arguments(text))
            }
        }
    }

    /** Each string's and plural form's text, named as "name" or "name/quantity". */
    private fun texts(file: File): List<Pair<String, String>> =
        elements(file, "string").map { it.getAttribute("name") to it.textContent } +
            elements(file, "plurals").flatMap { plurals ->
                items(plurals).map { (quantity, text) ->
                    "${plurals.getAttribute("name")}/$quantity" to text
                }
            }

    private fun elements(file: File, tag: String): List<Element> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName(tag)
        return List(nodes.length) { nodes.item(it) as Element }
    }

    /** A plural's forms' text by quantity, such as "one" and "other". */
    private fun items(plurals: Element): Map<String, String> {
        val nodes = plurals.getElementsByTagName("item")
        return List(nodes.length) { nodes.item(it) as Element }
            .associate { it.getAttribute("quantity") to it.textContent }
    }

    private fun arguments(text: String): Set<Int> =
        ARGUMENT.findAll(text).map { it.groupValues[1].toInt() }.toSet()

    private companion object {
        val ARGUMENT = Regex("""%(\d+)\$[sd]""")

        // A backslash before anything but n, t, uXXXX or another backslash, such as \' or \".
        val ANDROID_ESCAPE = Regex("""(?<!\\)\\(?![nt\\]|u[0-9a-fA-F]{4})""")

        // A "%" before something that isn't a space or the end, and isn't a whole argument, such
        // as "%%", "%s" or "%1$.1f". A "%" on its own, as in "40% done", is text.
        val MALFORMED_ARGUMENT = Regex("""%(?!\d+\$[sd])(?=\S)""")
    }
}
