package com.pharrlabs.voice.bubble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class BubbleColorsTest {
    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }

    private val all = listOf(BubbleColors.IDLE, BubbleColors.LISTENING, BubbleColors.PROCESSING, BubbleColors.ERROR)

    @Test fun idleIsTheBrandOrange() {
        val r = (BubbleColors.IDLE shr 16) and 0xFF; val g = (BubbleColors.IDLE shr 8) and 0xFF; val b = BubbleColors.IDLE and 0xFF
        assertTrue("r=$r", r > 200); assertTrue("g=$g", g in 80..130); assertTrue("b=$b", b < 60)
    }

    @Test fun whiteGlyphIsVisibleOnEveryState() {
        for (c in all) assertTrue("#${Integer.toHexString(c)}", 1.05 / (lum(c) + 0.05) >= 3.0)
    }

    @Test fun statesCanBeToldApart() {
        for (i in all.indices) for (j in i + 1 until all.size) {
            val a = all[i]; val b = all[j]
            val d = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) + abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))
            assertTrue("states $i and $j too close ($d)", d >= 150)
        }
    }

    @Test fun mapsEveryState() {
        assertEquals(BubbleColors.IDLE, BubbleColors.of(BubbleState.Idle))
        assertEquals(BubbleColors.LISTENING, BubbleColors.of(BubbleState.Listening))
        assertEquals(BubbleColors.PROCESSING, BubbleColors.of(BubbleState.Processing))
        assertEquals(BubbleColors.ERROR, BubbleColors.of(BubbleState.Error("x")))
    }

    private fun dist(a: Int, b: Int) = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) + abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))

    @Test fun orangeIsTheDefaultTint() {
        assertEquals(BubbleTint.Orange, BubbleTint.parse(null))
        assertEquals(BubbleTint.Orange, BubbleTint.parse("nope"))
        assertEquals(BubbleColors.IDLE, BubbleTint.Orange.argb)
    }

    @Test fun tintsParseByName() = assertEquals(BubbleTint.Blue, BubbleTint.parse("Blue"))

    @Test fun everyTintHasALabelAndAVisibleWhiteGlyph() {
        for (t in BubbleTint.values()) {
            assertTrue(t.label.isNotBlank())
            assertTrue("${t.label} contrast", 1.05 / (lum(t.argb) + 0.05) >= 3.0)
        }
    }

    @Test fun noTintLooksLikeAStateColor() {
        for (t in BubbleTint.values()) for (s in listOf(BubbleColors.LISTENING, BubbleColors.PROCESSING, BubbleColors.ERROR))
            assertTrue("${t.label} too close to a state color (${dist(t.argb, s)})", dist(t.argb, s) >= 100)
    }

    @Test fun tintsAreDistinctFromEachOther() {
        val all = BubbleTint.values()
        for (i in all.indices) for (j in i + 1 until all.size) assertTrue("${all[i].label}/${all[j].label}", dist(all[i].argb, all[j].argb) >= 100)
    }

    @Test fun onlyTheIdleStateFollowsTheTint() {
        val blue = BubbleTint.Blue.argb
        assertEquals(blue, BubbleColors.of(BubbleState.Idle, blue))
        assertEquals(BubbleColors.LISTENING, BubbleColors.of(BubbleState.Listening, blue))
        assertEquals(BubbleColors.ERROR, BubbleColors.of(BubbleState.Error("x"), blue))
    }
}
