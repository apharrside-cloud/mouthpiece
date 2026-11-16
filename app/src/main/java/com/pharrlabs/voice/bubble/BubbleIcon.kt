package com.pharrlabs.voice.bubble

import kotlin.math.cos
import kotlin.math.sin

/** The symbol drawn on the floating button; chosen in Settings. */
enum class BubbleIcon(val label: String) {
    Mic("Microphone"),
    Bars("Sound bars"),
    Chat("Speech bubble"),
    Sparkle("Sparkle");

    companion object {
        fun parse(name: String?): BubbleIcon = values().firstOrNull { it.name == name } ?: Mic
    }
}

/** Pure geometry for the non-microphone symbols, all fitted to a square of side `size` centred on (cx, cy). */
object IconGeometry {
    class Chat(val body: BubbleShape.Box, val radius: Float, val tail: List<Pair<Float, Float>>)

    private val barHeights = listOf(0.40f, 0.70f, 1.0f, 0.70f, 0.40f)

    fun bars(cx: Float, cy: Float, size: Float): List<BubbleShape.Box> {
        val w = size * 0.12f
        val gap = size * 0.08f
        val total = barHeights.size * w + (barHeights.size - 1) * gap
        val left = cx - total / 2
        return barHeights.mapIndexed { i, hFrac ->
            val x = left + i * (w + gap)
            val h = size * hFrac
            BubbleShape.Box(x, cy - h / 2, x + w, cy + h / 2)
        }
    }

    /** A four-point star: vertices alternate outer, inner, starting at the top. */
    fun sparkle(cx: Float, cy: Float, size: Float): List<Pair<Float, Float>> {
        val outer = size / 2
        val inner = outer * 0.30f
        return (0 until 8).map { i ->
            val a = Math.toRadians(-90.0 + i * 45.0)
            val r = if (i % 2 == 0) outer else inner
            (cx + r * cos(a)).toFloat() to (cy + r * sin(a)).toFloat()
        }
    }

    fun chat(cx: Float, cy: Float, size: Float): Chat {
        val top = cy - size / 2
        val bottom = top + size * 0.72f
        val body = BubbleShape.Box(cx - size / 2, top, cx + size / 2, bottom)
        val tail = listOf(
            cx - size * 0.30f to bottom - size * 0.02f,
            cx - size * 0.08f to bottom - size * 0.02f,
            cx - size * 0.30f to cy + size / 2,
        )
        return Chat(body, size * 0.2f, tail)
    }
}
