package io.github.bryancassell.bluecard.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import platform.Foundation.NSURL

class FileUrlTest {
    private val path = "/private/var/mobile/BlueCard export.json"

    @Test
    fun fileUrls_forTheSameUrl_areEqual() {
        val url = FileUrl(NSURL.fileURLWithPath(path))

        assertEquals(url, FileUrl(NSURL.fileURLWithPath(path)))
        assertEquals(url.hashCode(), FileUrl(NSURL.fileURLWithPath(path)).hashCode())
        assertNotEquals(url, FileUrl(NSURL.fileURLWithPath("/private/var/mobile/Other.json")))
    }

    @Test
    fun toString_isTheUrl() {
        assertEquals(
            "file:///private/var/mobile/BlueCard%20export.json",
            FileUrl(NSURL.fileURLWithPath(path)).toString()
        )
    }
}
