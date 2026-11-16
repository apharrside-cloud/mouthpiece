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

    private fun inside(poly: List<Pair<Float, Float>>, x: Float, y: Float): Boolean {
        var c = false; var j = poly.size - 1
        for (i in poly.indices) {
            val (xi, yi) = poly[i]; val (xj, yj) = poly[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }

    @Test fun lipsIsOfferedAsAnIcon() = assertEquals(BubbleIcon.Lips, BubbleIcon.parse("Lips"))

    @Test fun lipsStayInsideTheIconSquare() {
        val l = IconGeometry.lips(cx, cy, size)
        for ((x, y) in l.outer + l.mouth) assertTrue("$x,$y", x >= cx - size / 2 - 0.01f && x <= cx + size / 2 + 0.01f && y >= cy - size / 2 && y <= cy + size / 2)
    }

    @Test fun lipsAreMirroredLeftToRight() {
        val l = IconGeometry.lips(cx, cy, size)
        for (pts in listOf(l.outer, l.mouth)) for ((x, y) in pts)
            assertTrue(pts.any { (x2, y2) -> kotlin.math.abs(x2 - (2 * cx - x)) < 0.05f && kotlin.math.abs(y2 - y) < 0.05f })
    }

    @Test fun mouthSitsInsideTheLipsAndTeethInsideTheMouth() {
        val l = IconGeometry.lips(cx, cy, size)
        for ((x, y) in l.mouth) assertTrue(inside(l.outer, x, y))
        val t = l.teeth
        for ((x, y) in listOf(t.left to t.top, t.right to t.top, t.left to t.bottom, t.right to t.bottom)) assertTrue("$x,$y", inside(l.mouth, x, y))
    }

    @Test fun lipsOpenWiderWhenLoud() {
        val quiet = IconGeometry.lips(cx, cy, size, open = 0.3f).mouth
        val loud = IconGeometry.lips(cx, cy, size, open = 1f).mouth
        assertTrue(loud.maxOf { it.second } - loud.minOf { it.second } > quiet.maxOf { it.second } - quiet.minOf { it.second })
    }

    @Test fun teethStayInsideEvenWhenTheMouthIsNearlyClosed() {
        val l = IconGeometry.lips(cx, cy, size, open = 0.3f)
        val t = l.teeth
        for ((x, y) in listOf(t.left to t.top, t.right to t.bottom)) assertTrue("$x,$y", inside(l.mouth, x, y))
    }
}
