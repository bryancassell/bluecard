package io.github.bryancassell.bluecard.text

/**
 * [text] with each line break replaced with a space, as a single-line field replaces them
 * (`ui.LineBreaksAsSpaces`). "\r\n" counts as one line break, and so does each of
 * [LINE_BREAK_CHARS].
 */
fun lineBreaksAsSpaces(text: String): String = text.replace(LINE_BREAK, " ")

/**
 * Line feed, vertical tab, form feed, carriage return, next line, and line and paragraph
 * separators.
 */
const val LINE_BREAK_CHARS = "\n\u000B\u000C\r\u0085\u2028\u2029"

private val LINE_BREAK = Regex("\r\n|[$LINE_BREAK_CHARS]")
