package com.pharrlabs.voice.bubble

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** The symbol drawn on the floating button; chosen in Settings. */
enum class BubbleIcon(val label: String) {
    Mic("Microphone"),
    Bars("Sound bars"),
    Chat("Speech bubble"),
    Sparkle("Sparkle"),
    Lips("Lips");

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

    /** Open talking lips: the lip outline, the dark mouth opening inside it, and a strip of teeth along the top of the opening. */
    class Lips(val outer: List<Pair<Float, Float>>, val mouth: List<Pair<Float, Float>>, val teeth: BubbleShape.Box)

    /** [open] (0.3..1) is how wide the mouth is open; the bubble uses it to talk along with the voice level. */
    fun lips(cx: Float, cy: Float, size: Float, open: Float = 1f): Lips {
        val o = open.coerceIn(0.3f, 1f)
        val xs = (0 until LIP_POINTS).map { -1f + 2f * it / (LIP_POINTS - 1) }
        // Outline: upper edge left to right (with the dip of the cupid's bow), then the fuller lower edge right to left.
        fun top(s: Float) = size * (0.27f * (1f - s.pow(1.8f)).pow(0.85f) - 0.075f * exp(-(s / 0.16f) * (s / 0.16f)))
        fun bottom(s: Float) = size * 0.33f * (1f - s.pow(2.2f)).pow(0.85f)
        val outer = xs.map { x -> (cx + x * size / 2) to (cy - top(abs(x))) } + xs.reversed().map { x -> (cx + x * size / 2) to (cy + bottom(abs(x))) }
        val hw = size * 0.36f
        val upper = xs.map { x -> (cx + x * hw) to (cy - size * 0.045f * (1f - x * x).pow(0.8f)) }
        val lower = xs.reversed().map { x -> (cx + x * hw) to (cy + size * 0.17f * o * (1f - x * x).pow(0.9f)) }
        // Teeth hang from the upper lip: their top edge sits on the mouth's upper edge at the strip's corners.
        val teethTop = cy - size * 0.0365f
        val teethH = size * 0.06f * (0.4f + 0.6f * o)
        return Lips(outer, upper + lower, BubbleShape.Box(cx - size * 0.17f, teethTop, cx + size * 0.17f, teethTop + teethH))
    }

    private const val LIP_POINTS = 25

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
