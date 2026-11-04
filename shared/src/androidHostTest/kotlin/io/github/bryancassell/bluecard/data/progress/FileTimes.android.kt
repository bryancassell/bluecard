package io.github.bryancassell.bluecard.data.progress

import java.io.File
import okio.Path

actual fun setLastModified(file: Path, millis: Long) {
    check(File(file.toString()).setLastModified(millis)) { "Couldn't date $file" }
}
