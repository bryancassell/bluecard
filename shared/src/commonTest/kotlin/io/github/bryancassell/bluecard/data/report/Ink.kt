package io.github.bryancassell.bluecard.data.report

/**
 * Where drawing darkened a white page, in pixels from its top left corner, and how dark it made it
 * altogether. [found] is false when nothing was drawn.
 */
class Ink(val left: Int, val right: Int, val top: Int, val bottom: Int, val amount: Int) {
    val found: Boolean get() = right >= left

    companion object {
        /**
         * The ink on a [width] by [height] page, whose pixel at column and row has [darkness],
         * from 0 for white to 255 for black. A pixel darker than half counts as ink.
         */
        fun of(width: Int, height: Int, darkness: (column: Int, row: Int) -> Int): Ink {
            var left = width
            var right = -1
            var top = height
            var bottom = -1
            var amount = 0
            for (row in 0 until height) {
                for (column in 0 until width) {
                    val dark = darkness(column, row)
                    if (dark > 128) {
                        left = minOf(left, column)
                        right = maxOf(right, column)
                        top = minOf(top, row)
                        bottom = maxOf(bottom, row)
                    }
                    amount += dark
                }
            }
            return Ink(left, right, top, bottom, amount)
        }
    }
}
