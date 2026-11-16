package com.pharrlabs.voice.ui

/** Pure wrapping math for [FlowLayout]: where each item goes when items fill a row and then wrap. */
object FlowMath {
    data class Slot(val x: Int, val y: Int)
    data class Result(val slots: List<Slot>, val height: Int, val width: Int)

    fun layout(widths: List<Int>, heights: List<Int>, maxWidth: Int, hGap: Int, vGap: Int): Result {
        val slots = ArrayList<Slot>(widths.size)
        var x = 0
        var y = 0
        var rowHeight = 0
        var usedWidth = 0
        for (i in widths.indices) {
            val w = widths[i]
            val h = heights[i]
            if (x > 0 && x + w > maxWidth) { // wrap: this item does not fit on the current row
                y += rowHeight + vGap
                x = 0
                rowHeight = 0
            }
            slots += Slot(x, y)
            x += w + hGap
            rowHeight = maxOf(rowHeight, h)
            usedWidth = maxOf(usedWidth, x - hGap)
        }
        return Result(slots, if (widths.isEmpty()) 0 else y + rowHeight, usedWidth)
    }
}
