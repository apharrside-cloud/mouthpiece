package com.pharrlabs.voice.bubble

data class Pos(val x: Int, val y: Int)
/** [bottom] is the true bottom edge of the display (below the keyboard and navigation bar). */
data class Screen(val width: Int, val statusBar: Int, val keyboardTop: Int, val bottom: Int = keyboardTop)
data class FieldBox(val left: Int, val top: Int, val right: Int)

/** Where the bubble goes relative to the focused field, plus the user's remembered drag offset. */
object BubblePlacement {
    /** Default spot above the field's top corner, nudged by [offX]/[offY] and kept on screen above the keyboard. */
    fun place(
        field: FieldBox, screen: Screen, size: Int, margin: Int, left: Boolean,
        offX: Int = 0, offY: Int = 0, corner: Boolean = false,
    ): Pos {
        if (corner) {
            // Fixed spot in the bottom-left corner of the display (by the navigation bar), whatever the field does.
            return clamp(Pos(margin + offX, screen.bottom - size - margin + offY), screen, size, toScreenBottom = true)
        }
        var y = field.top - size - margin
        if (y < screen.statusBar) y = field.top + margin // field touches the top: sit inside it
        y = y.coerceAtMost(screen.keyboardTop - size - margin).coerceAtLeast(screen.statusBar)
        // Horizontal position comes from the screen edge, not the field: chat boxes change width as text appears,
        // and the bubble would slide sideways while the user types.
        val x = if (left) margin else screen.width - size - margin
        return clamp(Pos(x + offX, y + offY), screen, size)
    }

    /** [toScreenBottom] lets the bubble sit below the keyboard (the corner option); otherwise it stops at the keyboard. */
    fun clamp(p: Pos, screen: Screen, size: Int, toScreenBottom: Boolean = false): Pos {
        val floor = if (toScreenBottom) maxOf(screen.bottom, screen.keyboardTop) else screen.keyboardTop
        return Pos(
            p.x.coerceIn(0, (screen.width - size).coerceAtLeast(0)),
            p.y.coerceIn(screen.statusBar, (floor - size).coerceAtLeast(screen.statusBar)),
        )
    }

    /** The offset that turns the default position [base] into [dragged]. */
    fun offsetFor(dragged: Pos, base: Pos): Pos = Pos(dragged.x - base.x, dragged.y - base.y)
}
