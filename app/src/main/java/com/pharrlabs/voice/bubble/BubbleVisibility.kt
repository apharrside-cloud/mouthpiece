package com.pharrlabs.voice.bubble

/** Whether the floating button may show: the Quick Settings tile switch plus the 10-minute hide from dragging it down. */
object BubbleVisibility {
    const val SNOOZE_MS = 10 * 60_000L

    data class State(val on: Boolean, val hiddenUntil: Long)

    fun active(on: Boolean, hiddenUntil: Long, now: Long) = on && now >= hiddenUntil

    fun snoozeUntil(now: Long) = now + SNOOZE_MS

    /** What tapping the tile does: an active bubble is switched off; anything hidden comes back right away. */
    fun toggle(on: Boolean, hiddenUntil: Long, now: Long): State =
        if (active(on, hiddenUntil, now)) State(on = false, hiddenUntil = 0) else State(on = true, hiddenUntil = 0)

    fun subtitle(on: Boolean, hiddenUntil: Long, now: Long): String = when {
        !on -> "Off"
        now < hiddenUntil -> "Hidden ${maxOf(1L, (hiddenUntil - now + 59_999) / 60_000)} min"
        else -> "On"
    }

    /** The drop target shown while dragging: centred, just above the keyboard (or screen bottom). */
    fun hideTarget(screen: Screen, widthPx: Int, heightPx: Int, marginPx: Int): Area {
        val left = (screen.width - widthPx) / 2
        val bottom = screen.keyboardTop - marginPx
        return Area(left, bottom - heightPx, left + widthPx, bottom)
    }

    /** True when the middle of the bubble is inside the target, so hiding needs a deliberate drop on it. */
    fun overTarget(pos: Pos, size: Int, target: Area): Boolean {
        val cx = pos.x + size / 2
        val cy = pos.y + size / 2
        return cx > target.left && cx < target.right && cy > target.top && cy < target.bottom
    }

    /** Why the bubble is not showing, or null when it is. */
    fun notice(on: Boolean, hiddenUntil: Long, now: Long): String? = when {
        !on -> "Switched off"
        now < hiddenUntil -> "Hidden for ${maxOf(1L, (hiddenUntil - now + 59_999) / 60_000)} more min"
        else -> null
    }
}
