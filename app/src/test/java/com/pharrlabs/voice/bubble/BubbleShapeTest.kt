package com.pharrlabs.voice.bubble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleShapeTest {
    private val s = BubbleShape.of(width = 168f, height = 168f, pad = 9f)

    @Test fun bubbleIsTallerThanWide() {
        assertTrue(s.pill.height() > s.pill.width() * 1.4f)
    }

    @Test fun bubbleIsCenteredAndInsideTheWindow() {
        assertEquals(84f, (s.pill.left + s.pill.right) / 2, 0.01f)
        assertEquals(84f, (s.pill.top + s.pill.bottom) / 2, 0.01f)
        assertEquals(9f, s.pill.top, 0.01f)
        assertEquals(159f, s.pill.bottom, 0.01f)
    }

    @Test fun endsAreFullyRound() = assertEquals(s.pill.width() / 2, s.radius, 0.01f)

    @Test fun micFillsTheBubbleButStaysInsideIt() {
        for (r in listOf(s.capsule, s.cradle)) {
            assertTrue(r.left > s.pill.left && r.right < s.pill.right)
            assertTrue(r.top > s.pill.top)
        }
        assertTrue(s.baseY < s.pill.bottom - s.pill.height() * 0.1f)
        assertTrue(s.capsule.height() > s.pill.height() * 0.35f)
    }

    @Test fun capsuleIsAnElongatedPillToo() {
        assertTrue(s.capsule.height() > s.capsule.width() * 1.5f)
        assertEquals(s.capsule.width() / 2, s.capsuleRadius, 0.01f)
    }

    @Test fun micPartsAreStackedTopToBottom() {
        assertTrue(s.capsule.top < s.capsule.bottom)
        assertTrue(s.capsule.bottom < s.cradle.bottom)
        assertTrue(s.cradle.bottom < s.stemEndY)
        assertTrue(s.stemEndY <= s.baseY)
    }

    @Test fun wideWindowStillGivesATallBubble() {
        val w = BubbleShape.of(width = 400f, height = 100f, pad = 5f)
        assertEquals(90f, w.pill.height(), 0.01f)
        assertTrue(w.pill.width() < w.pill.height())
    }

    @Test fun tinyWindowDoesNotGoNegative() {
        val t = BubbleShape.of(width = 6f, height = 6f, pad = 9f)
        assertTrue(t.pill.width() >= 0f && t.pill.height() >= 0f)
    }

    // ---- selectable shapes

    @Test fun circleIsRoundAndSquare() {
        val c = BubbleShape.of(168f, 168f, 9f, BubbleStyle.Circle)
        assertEquals(c.pill.width(), c.pill.height(), 0.01f)
        assertEquals(c.pill.width() / 2, c.radius, 0.01f)
        assertEquals(150f, c.pill.width(), 0.01f)
    }

    @Test fun roundedSquareHasSoftCorners() {
        val c = BubbleShape.of(168f, 168f, 9f, BubbleStyle.Rounded)
        assertEquals(c.pill.width(), c.pill.height(), 0.01f)
        assertTrue(c.radius > c.pill.width() * 0.15f && c.radius < c.pill.width() * 0.4f)
    }

    @Test fun squareHasAlmostSharpCorners() {
        val c = BubbleShape.of(168f, 168f, 9f, BubbleStyle.Square)
        assertEquals(c.pill.width(), c.pill.height(), 0.01f)
        assertTrue(c.radius > 0f && c.radius < c.pill.width() * 0.15f)
    }

    @Test fun micKeepsItsProportionsInEveryShape() {
        for (style in BubbleStyle.values()) {
            val c = BubbleShape.of(168f, 168f, 9f, style)
            assertTrue("$style capsule", c.capsule.height() > c.capsule.width() * 1.5f)
            assertTrue("$style inside", c.capsule.left > c.pill.left && c.cradle.right < c.pill.right)
            assertTrue("$style base", c.baseY < c.pill.bottom)
            assertTrue("$style top", c.capsule.top > c.pill.top)
            assertTrue("$style stack", c.capsule.bottom < c.cradle.bottom && c.cradle.bottom < c.stemEndY && c.stemEndY <= c.baseY)
        }
    }

    @Test fun micIsCenteredInEveryShape() {
        for (style in BubbleStyle.values()) {
            val c = BubbleShape.of(168f, 168f, 9f, style)
            val mid = (c.capsule.top + c.baseY) / 2
            assertEquals("$style", 84f, mid, 6f)
            assertEquals(84f, (c.capsule.left + c.capsule.right) / 2, 0.01f)
        }
    }

    @Test fun styleParsingFallsBackToPill() {
        assertEquals(BubbleStyle.Circle, BubbleStyle.parse("Circle"))
        assertEquals(BubbleStyle.Pill, BubbleStyle.parse("nonsense"))
        assertEquals(BubbleStyle.Pill, BubbleStyle.parse(null))
    }

    @Test fun everyStyleHasALabel() = assertTrue(BubbleStyle.values().all { it.label.isNotBlank() })
}
