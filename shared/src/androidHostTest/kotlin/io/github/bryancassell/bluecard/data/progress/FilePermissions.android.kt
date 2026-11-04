package io.github.bryancassell.bluecard.data.progress

import java.io.File
import okio.Path

actual fun setWritable(path: Path, writable: Boolean) = File(path.toString()).setWritable(writable)
