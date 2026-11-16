package com.pharrlabs.voice.bubble

/** The bubble outlines the user can pick in Settings. */
enum class BubbleStyle(val label: String) {
    Pill("Tall pill"),
    Circle("Circle"),
    Rounded("Rounded square"),
    Square("Square");

    companion object {
        fun parse(name: String?): BubbleStyle = values().firstOrNull { it.name == name } ?: Pill
    }
}

/** Pure geometry for the floating bubble: an outline plus a microphone sized to fit it. No Android types, so it is unit-tested. */
class BubbleShape private constructor(
    val pill: Box,
    val radius: Float,
    val capsule: Box,
    val capsuleRadius: Float,
    /** Oval whose lower half is the cradle arc around the capsule. */
    val cradle: Box,
    val stemEndY: Float,
    val baseY: Float,
    val baseHalfWidth: Float,
    val cx: Float,
    val cy: Float,
    /** Side of the square the symbol on the bubble is fitted to. */
    val iconSize: Float,
) {
    class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun width() = right - left
        fun height() = bottom - top
    }

    companion object {
        /** Tall pill width as a share of its height: clearly elongated, like a microphone body. */
        const val ASPECT = 0.62f

        fun of(width: Float, height: Float, pad: Float, style: BubbleStyle = BubbleStyle.Pill): BubbleShape {
            val cx = width / 2
            val cy = height / 2
            val h = (height - 2 * pad).coerceAtLeast(0f)
            val maxW = (width - 2 * pad).coerceAtLeast(0f)
            val w = when (style) {
                BubbleStyle.Pill -> (h * ASPECT).coerceAtMost(maxW)
                else -> minOf(h, maxW)
            }
            val bubbleH = if (style == BubbleStyle.Pill) h else w
            val radius = when (style) {
                BubbleStyle.Pill, BubbleStyle.Circle -> w / 2
                BubbleStyle.Rounded -> w * 0.28f
                BubbleStyle.Square -> w * 0.08f
            }
            val pill = Box(cx - w / 2, cy - bubbleH / 2, cx + w / 2, cy + bubbleH / 2)
            // Microphone height (capsule top to base), in the same proportions for every outline.
            val m = bubbleH * if (style == BubbleStyle.Pill) 0.64f else 0.58f
            val cw = m * 0.349f
            val capsule = Box(cx - cw / 2, cy - m * 0.53f, cx + cw / 2, cy + m * 0.095f)
            val cradle = Box(cx - m * 0.30f, cy - m * 0.25f, cx + m * 0.30f, cy + m * 0.28f)
            return BubbleShape(
                pill = pill, radius = radius,
                capsule = capsule, capsuleRadius = cw / 2,
                cradle = cradle,
                stemEndY = cy + m * 0.4375f, baseY = cy + m * 0.47f, baseHalfWidth = m * 0.165f,
                cx = cx, cy = cy, iconSize = m,
            )
        }
    }
}
