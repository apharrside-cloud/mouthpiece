package com.pharrlabs.voice.bubble

import org.junit.Assert.assertEquals
import org.junit.Test

class BubblePlacementTest {
    private val screen = Screen(width = 1344, statusBar = 100, keyboardTop = 2000)
    private val field = FieldBox(left = 100, top = 800, right = 1200)
    private val size = 150
    private val margin = 16

    @Test fun defaultSitsAboveFieldTopRight() {
        val p = BubblePlacement.place(field, screen, size, margin, left = false)
        assertEquals(Pos(1344 - 150 - 16, 800 - 150 - 16), p)
    }

    @Test fun leftOptionUsesTheScreenLeftEdge() {
        val p = BubblePlacement.place(field, screen, size, margin, left = true)
        assertEquals(16, p.x)
    }

    @Test fun horizontalPositionIgnoresTheFieldWidth() {
        // Chat boxes get narrower or wider as text appears (send button, attach icons); the bubble must not follow.
        val empty = BubblePlacement.place(FieldBox(100, 800, 1000), screen, size, margin, left = false, offX = -200)
        val typed = BubblePlacement.place(FieldBox(100, 800, 1250), screen, size, margin, left = false, offX = -200)
        assertEquals(empty.x, typed.x)
        val emptyL = BubblePlacement.place(FieldBox(300, 800, 1000), screen, size, margin, left = true, offX = 120)
        val typedL = BubblePlacement.place(FieldBox(100, 800, 1000), screen, size, margin, left = true, offX = 120)
        assertEquals(emptyL.x, typedL.x)
    }

    @Test fun cornerSitsAtTheBottomLeftOfTheScreenNotTheField() {
        val s = Screen(width = 1344, statusBar = 100, keyboardTop = 1500, bottom = 2992)
        val a = BubblePlacement.place(field, s, size, margin, left = false, corner = true)
        val b = BubblePlacement.place(FieldBox(300, 400, 900), s, size, margin, left = false, corner = true)
        assertEquals(Pos(16, 2992 - 150 - 16), a)
        assertEquals(a, b)
    }

    @Test fun cornerIgnoresTheLeftRightChoiceAndNoKeyboard() {
        val s = Screen(width = 1344, statusBar = 100, keyboardTop = 2992, bottom = 2992)
        assertEquals(
            BubblePlacement.place(field, s, size, margin, left = true, corner = true),
            BubblePlacement.place(field, s, size, margin, left = false, corner = true),
        )
    }

    @Test fun cornerOffsetStaysOnScreenAndBelowKeyboardIsAllowed() {
        val s = Screen(width = 1344, statusBar = 100, keyboardTop = 1500, bottom = 2992)
        val p = BubblePlacement.place(field, s, size, margin, left = false, offX = -50, offY = 5000, corner = true)
        assertEquals(2992 - 150, p.y)
        assertEquals(0, p.x)
    }

    @Test fun normalClampStillStopsAtTheKeyboard() {
        val s = Screen(width = 1344, statusBar = 100, keyboardTop = 1500, bottom = 2992)
        assertEquals(1500 - 150, BubblePlacement.clamp(Pos(50, 2900), s, size).y)
    }

    @Test fun itStillFollowsTheFieldVertically() {
        val low = BubblePlacement.place(FieldBox(100, 1000, 1200), screen, size, margin, left = false)
        val high = BubblePlacement.place(FieldBox(100, 800, 1200), screen, size, margin, left = false)
        assertEquals(200, low.y - high.y)
    }

    @Test fun fieldAtTopPutsBubbleInsideIt() {
        val p = BubblePlacement.place(FieldBox(100, 120, 1200), screen, size, margin, left = false)
        assertEquals(120 + 16, p.y)
    }

    @Test fun neverBelowKeyboard() {
        val p = BubblePlacement.place(FieldBox(100, 2200, 1200), screen, size, margin, left = false)
        assertEquals(2000 - 150 - 16, p.y)
    }

    @Test fun offsetMovesBubble() {
        val base = BubblePlacement.place(field, screen, size, margin, left = false)
        val moved = BubblePlacement.place(field, screen, size, margin, left = false, offX = -300, offY = 40)
        assertEquals(Pos(base.x - 300, base.y + 40), moved)
    }

    @Test fun offsetIsClampedToScreen() {
        val p = BubblePlacement.place(field, screen, size, margin, left = false, offX = 5000, offY = -5000)
        assertEquals(1344 - 150, p.x)
        assertEquals(100, p.y)
    }

    @Test fun offsetForRoundTrips() {
        val base = BubblePlacement.place(field, screen, size, margin, left = false)
        val off = BubblePlacement.offsetFor(Pos(base.x - 123, base.y + 77), base)
        assertEquals(Pos(-123, 77), off)
        assertEquals(Pos(base.x - 123, base.y + 77), BubblePlacement.place(field, screen, size, margin, false, off.x, off.y))
    }

    @Test fun clampKeepsDraggedBubbleOnScreen() {
        assertEquals(Pos(0, 100), BubblePlacement.clamp(Pos(-50, 5), screen, size))
        assertEquals(Pos(1344 - 150, 2000 - 150), BubblePlacement.clamp(Pos(9999, 9999), screen, size))
    }
}
