package io.github.bryancassell.bluecard.data

// #348 adds the class that holds the NSURL the file picker gives.
actual abstract class DocumentUri {
    actual abstract override fun toString(): String
}
