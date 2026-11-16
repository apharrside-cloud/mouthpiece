package com.pharrlabs.voice.bubble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BubbleIconTest {
    private val cx = 50f
    private val cy = 60f
    private val size = 100f

    @Test fun fiveBarsMirroredAroundTheCentre() {
        val bars = IconGeometry.bars(cx, cy, size)
        assertEquals(5, bars.size)
        for (i in 0..4) assertEquals(2 * cx, bars[i].left + bars[4 - i].right, 0.01f)
    }

    @Test fun middleBarIsTheTallestAndAllAreCenteredVertically() {
        val bars = IconGeometry.bars(cx, cy, size)
        assertTrue(bars.all { it.height() <= size && it.height() > 0f })
        assertTrue(bars[2].height() > bars[1].height() && bars[1].height() > bars[0].height())
        for (b in bars) assertEquals(cy, (b.top + b.bottom) / 2, 0.01f)
    }

    @Test fun barsStayInsideTheIconSquare() {
        val bars = IconGeometry.bars(cx, cy, size)
        assertTrue(bars.first().left >= cx - size / 2 && bars.last().right <= cx + size / 2)
    }

    @Test fun sparkleIsAFourPointStar() {
        val pts = IconGeometry.sparkle(cx, cy, size)
        assertEquals(8, pts.size)
        assertEquals(cx, pts[0].first, 0.01f)
        assertEquals(cy - size / 2, pts[0].second, 0.01f)
        for ((i, p) in pts.withIndex()) {
            val r = hypot(p.first - cx, p.second - cy)
            if (i % 2 == 0) assertEquals(size / 2, r, 0.01f) else assertTrue(r < size / 2 * 0.5f)
        }
    }

    @Test fun speechBubbleHasATailBelowItsBody() {
        val c = IconGeometry.chat(cx, cy, size)
        assertTrue(c.body.width() == size && c.body.height() < size)
        assertEquals(cy + size / 2, c.tail.maxOf { it.second }, 0.01f)
        assertTrue(c.tail.all { it.first < cx })
        assertTrue(c.body.top >= cy - size / 2)
    }

    @Test fun iconSizeFitsInsideEveryBubbleShape() {
        for (style in BubbleStyle.values()) {
            val s = BubbleShape.of(168f, 168f, 9f, style)
            assertTrue("$style", s.iconSize > 0f && s.iconSize < s.pill.height() && s.iconSize < s.pill.height())
        }
    }

    @Test fun iconParsingFallsBackToTheMic() {
        assertEquals(BubbleIcon.Bars, BubbleIcon.parse("Bars"))
        assertEquals(BubbleIcon.Mic, BubbleIcon.parse("nope"))
        assertEquals(BubbleIcon.Mic, BubbleIcon.parse(null))
        assertTrue(BubbleIcon.values().all { it.label.isNotBlank() })
    }
}
