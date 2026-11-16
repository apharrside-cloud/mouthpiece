package com.pharrlabs.voice.bubble

import kotlin.math.hypot

sealed interface GestureEvent {
    data object Tap : GestureEvent
    data object HoldStart : GestureEvent
    data object HoldEnd : GestureEvent
    data object DragStart : GestureEvent
    /** Total finger movement since the initial press. */
    data class DragMove(val dx: Float, val dy: Float) : GestureEvent
    data object DragEnd : GestureEvent
}

/**
 * Turns raw touch input on the bubble into tap, press-and-hold (push-to-talk) and drag. A quick press is a tap,
 * holding still past [holdMs] is a hold, and moving more than [slopPx] before that is a drag. Time and
 * coordinates are passed in so the rules are unit-testable.
 */
class BubbleGesture(
    private val holdMs: Long = 350,
    private val slopPx: Float,
    private val holdEnabled: Boolean = true,
    private val tapEnabled: Boolean = true,
) {
    private enum class Phase { Idle, Pressed, Holding, Dragging }

    private var phase = Phase.Idle
    private var t0 = 0L
    private var x0 = 0f
    private var y0 = 0f

    fun down(t: Long, x: Float, y: Float): List<GestureEvent> {
        phase = Phase.Pressed; t0 = t; x0 = x; y0 = y
        return emptyList()
    }

    fun move(t: Long, x: Float, y: Float): List<GestureEvent> {
        val dx = x - x0
        val dy = y - y0
        return when (phase) {
            Phase.Pressed -> if (hypot(dx, dy) > slopPx) {
                phase = Phase.Dragging
                listOf(GestureEvent.DragStart, GestureEvent.DragMove(dx, dy))
            } else emptyList()
            Phase.Dragging -> listOf(GestureEvent.DragMove(dx, dy))
            else -> emptyList() // finger drift while holding is not a drag
        }
    }

    /** Call when the hold timer fires (or periodically). */
    fun tick(t: Long): List<GestureEvent> {
        if (phase == Phase.Pressed && holdEnabled && t - t0 >= holdMs) {
            phase = Phase.Holding
            return listOf(GestureEvent.HoldStart)
        }
        return emptyList()
    }

    fun up(t: Long): List<GestureEvent> {
        val p = phase
        phase = Phase.Idle
        return when (p) {
            Phase.Pressed -> if (tapEnabled) listOf(GestureEvent.Tap) else emptyList()
            Phase.Holding -> listOf(GestureEvent.HoldEnd)
            Phase.Dragging -> listOf(GestureEvent.DragEnd)
            Phase.Idle -> emptyList()
        }
    }

    fun cancel(): List<GestureEvent> {
        val p = phase
        phase = Phase.Idle
        return when (p) {
            Phase.Holding -> listOf(GestureEvent.HoldEnd)
            Phase.Dragging -> listOf(GestureEvent.DragEnd)
            else -> emptyList()
        }
    }
}
