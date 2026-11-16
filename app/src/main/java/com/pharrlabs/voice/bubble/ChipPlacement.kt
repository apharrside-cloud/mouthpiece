package com.pharrlabs.voice.bubble

data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun intersects(o: Area) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

/** Where the Undo / Remember chip goes: next to the bubble, but never on top of the text field being dictated into. */
object ChipPlacement {
    fun place(anchor: Pos, bubbleSize: Int, w: Int, h: Int, screen: Screen, field: Area?, gap: Int): Pos {
        val bubble = Area(anchor.x, anchor.y, anchor.x + bubbleSize, anchor.y + bubbleSize)
        // The visible text box is usually a little bigger than the node the app reports, so keep a margin around it.
        val box = field?.let { Area(it.left - gap, it.top - gap, it.right + gap, it.bottom + gap) }
        val avoid = listOfNotNull(box, bubble)
        fun clampX(x: Int) = x.coerceIn(0, (screen.width - w).coerceAtLeast(0))
        val rightAligned = clampX(anchor.x + bubbleSize - w)
        val candidates = buildList {
            add(Pos(rightAligned, anchor.y - h - gap))                               // above the bubble: furthest from the text
            add(Pos(anchor.x - w - gap, anchor.y + (bubbleSize - h) / 2))           // beside the bubble
            if (box != null) {
                add(Pos(anchor.x - w - gap, box.top - h))                            // beside it, lifted clear of the field
                add(Pos(rightAligned, box.top - h))                                  // above the field
                add(Pos(rightAligned, box.bottom))                                   // below the field
            }
        }
        fun fits(p: Pos): Boolean {
            val a = Area(p.x, p.y, p.x + w, p.y + h)
            return p.x >= 0 && a.right <= screen.width && p.y >= screen.statusBar && a.bottom <= screen.keyboardTop &&
                avoid.none { a.intersects(it) }
        }
        candidates.firstOrNull(::fits)?.let { return it }
        val first = candidates.first()
        return Pos(clampX(first.x), first.y.coerceIn(screen.statusBar, (screen.keyboardTop - h).coerceAtLeast(screen.statusBar)))
    }
}
