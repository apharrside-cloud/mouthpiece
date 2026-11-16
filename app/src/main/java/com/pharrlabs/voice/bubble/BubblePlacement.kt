package com.pharrlabs.voice.bubble

data class Pos(val x: Int, val y: Int)
data class Screen(val width: Int, val statusBar: Int, val keyboardTop: Int)
data class FieldBox(val left: Int, val top: Int, val right: Int)

/** Where the bubble goes relative to the focused field, plus the user's remembered drag offset. */
object BubblePlacement {
    /** Default spot above the field's top corner, nudged by [offX]/[offY] and kept on screen above the keyboard. */
    fun place(field: FieldBox, screen: Screen, size: Int, margin: Int, left: Boolean, offX: Int = 0, offY: Int = 0): Pos {
        var y = field.top - size - margin
        if (y < screen.statusBar) y = field.top + margin // field touches the top: sit inside it
        y = y.coerceAtMost(screen.keyboardTop - size - margin).coerceAtLeast(screen.statusBar)
        // Horizontal position comes from the screen edge, not the field: chat boxes change width as text appears,
        // and the bubble would slide sideways while the user types.
        val x = if (left) margin else screen.width - size - margin
        return clamp(Pos(x + offX, y + offY), screen, size)
    }

    fun clamp(p: Pos, screen: Screen, size: Int): Pos = Pos(
        p.x.coerceIn(0, (screen.width - size).coerceAtLeast(0)),
        p.y.coerceIn(screen.statusBar, (screen.keyboardTop - size).coerceAtLeast(screen.statusBar)),
    )

    /** The offset that turns the default position [base] into [dragged]. */
    fun offsetFor(dragged: Pos, base: Pos): Pos = Pos(dragged.x - base.x, dragged.y - base.y)
}
