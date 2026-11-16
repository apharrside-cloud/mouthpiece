package com.pharrlabs.voice.bubble

import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleGestureTest {
    private val g = BubbleGesture(holdMs = 350, slopPx = 20f)

    @Test fun quickPressIsTap() {
        assertEquals(emptyList<GestureEvent>(), g.down(0, 0f, 0f))
        assertEquals(listOf(GestureEvent.Tap), g.up(120))
    }

    @Test fun longPressStartsHoldAfterThreshold() {
        g.down(0, 0f, 0f)
        assertEquals(emptyList<GestureEvent>(), g.tick(200))
        assertEquals(listOf(GestureEvent.HoldStart), g.tick(350))
    }

    @Test fun releaseAfterHoldEndsHold() {
        g.down(0, 0f, 0f); g.tick(400)
        assertEquals(listOf(GestureEvent.HoldEnd), g.up(2000))
    }

    @Test fun holdStartsOnlyOnce() {
        g.down(0, 0f, 0f)
        g.tick(400)
        assertEquals(emptyList<GestureEvent>(), g.tick(500))
    }

    @Test fun movingBeyondSlopStartsDrag() {
        g.down(0, 100f, 100f)
        assertEquals(listOf(GestureEvent.DragStart, GestureEvent.DragMove(30f, 0f)), g.move(50, 130f, 100f))
    }

    @Test fun dragMovesAreRelativeToDown() {
        g.down(0, 100f, 100f); g.move(50, 130f, 100f)
        assertEquals(listOf(GestureEvent.DragMove(40f, -15f)), g.move(80, 140f, 85f))
    }

    @Test fun releaseAfterDragEndsDragNotTap() {
        g.down(0, 0f, 0f); g.move(50, 50f, 0f)
        assertEquals(listOf(GestureEvent.DragEnd), g.up(200))
    }

    @Test fun smallJitterStaysATap() {
        g.down(0, 100f, 100f)
        assertEquals(emptyList<GestureEvent>(), g.move(40, 105f, 103f))
        assertEquals(listOf(GestureEvent.Tap), g.up(100))
    }

    @Test fun noHoldWhileDragging() {
        g.down(0, 0f, 0f); g.move(50, 50f, 0f)
        assertEquals(emptyList<GestureEvent>(), g.tick(500))
    }

    @Test fun fingerDriftDuringHoldDoesNotDrag() {
        g.down(0, 0f, 0f); g.tick(400)
        assertEquals(emptyList<GestureEvent>(), g.move(500, 60f, 60f))
        assertEquals(listOf(GestureEvent.HoldEnd), g.up(900))
    }

    @Test fun cancelDuringHoldEndsHold() {
        g.down(0, 0f, 0f); g.tick(400)
        assertEquals(listOf(GestureEvent.HoldEnd), g.cancel())
    }

    @Test fun cancelBeforeAnythingIsSilent() {
        g.down(0, 0f, 0f)
        assertEquals(emptyList<GestureEvent>(), g.cancel())
    }

    @Test fun upWithoutDownIsIgnored() = assertEquals(emptyList<GestureEvent>(), g.up(10))

    @Test fun holdDisabledMakesLongPressATap() {
        val tapOnly = BubbleGesture(holdMs = 350, slopPx = 20f, holdEnabled = false)
        tapOnly.down(0, 0f, 0f)
        assertEquals(emptyList<GestureEvent>(), tapOnly.tick(1000))
        assertEquals(listOf(GestureEvent.Tap), tapOnly.up(1200))
    }

    @Test fun tapDisabledIgnoresShortPress() {
        val holdOnly = BubbleGesture(holdMs = 350, slopPx = 20f, tapEnabled = false)
        holdOnly.down(0, 0f, 0f)
        assertEquals(emptyList<GestureEvent>(), holdOnly.up(100))
    }
}
