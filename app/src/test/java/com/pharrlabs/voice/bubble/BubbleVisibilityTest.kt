package com.pharrlabs.voice.bubble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleVisibilityTest {
    private val now = 1_000_000L
    private val min = 60_000L

    @Test fun activeWhenOnAndNotSnoozed() = assertTrue(BubbleVisibility.active(on = true, hiddenUntil = 0, now = now))

    @Test fun offMeansHidden() = assertFalse(BubbleVisibility.active(on = false, hiddenUntil = 0, now = now))

    @Test fun snoozedMeansHiddenUntilItEnds() {
        assertFalse(BubbleVisibility.active(on = true, hiddenUntil = now + 5 * min, now = now))
        assertTrue(BubbleVisibility.active(on = true, hiddenUntil = now - 1, now = now))
    }

    @Test fun snoozeIsTenMinutes() = assertEquals(now + 10 * min, BubbleVisibility.snoozeUntil(now))

    @Test fun tileTurnsAnActiveBubbleOff() {
        val s = BubbleVisibility.toggle(on = true, hiddenUntil = 0, now = now)
        assertFalse(s.on)
    }

    @Test fun tileBringsASnoozedBubbleBackAndClearsTheSnooze() {
        val s = BubbleVisibility.toggle(on = true, hiddenUntil = now + 5 * min, now = now)
        assertTrue(s.on)
        assertEquals(0L, s.hiddenUntil)
    }

    @Test fun tileTurnsAnOffBubbleOn() {
        val s = BubbleVisibility.toggle(on = false, hiddenUntil = 0, now = now)
        assertTrue(s.on)
    }

    @Test fun tileSubtitle() {
        assertEquals("On", BubbleVisibility.subtitle(on = true, hiddenUntil = 0, now = now))
        assertEquals("Off", BubbleVisibility.subtitle(on = false, hiddenUntil = 0, now = now))
        assertEquals("Hidden 5 min", BubbleVisibility.subtitle(on = true, hiddenUntil = now + 5 * min, now = now))
        assertEquals("Hidden 1 min", BubbleVisibility.subtitle(on = true, hiddenUntil = now + 20_000, now = now))
    }

    // ---- drag onto the "hide" target

    private val screen = Screen(width = 1000, statusBar = 50, keyboardTop = 2000)
    private val size = 160

    @Test fun targetSitsCenteredJustAboveTheKeyboard() {
        val t = BubbleVisibility.hideTarget(screen, widthPx = 600, heightPx = 140, marginPx = 60)
        assertEquals(Area(200, 1800, 800, 1940), t)
    }

    @Test fun bubbleCenteredOnTheTargetIsOver() {
        val t = BubbleVisibility.hideTarget(screen, 600, 140, 60)
        assertTrue(BubbleVisibility.overTarget(Pos(500 - size / 2, 1870 - size / 2), size, t))
    }

    @Test fun bubbleOnlyTouchingTheEdgeIsNotOver() {
        val t = BubbleVisibility.hideTarget(screen, 600, 140, 60)
        assertFalse(BubbleVisibility.overTarget(Pos(200 - size, 1800), size, t))
        assertFalse(BubbleVisibility.overTarget(Pos(500 - size / 2, 1000), size, t))
    }

    @Test fun dragToTheBottomOfTheScreenAloneDoesNotHide() {
        // The lowest place the bubble can go is just above the keyboard; the old rule hid it from there by accident.
        val t = BubbleVisibility.hideTarget(screen, 600, 140, 60)
        assertFalse(BubbleVisibility.overTarget(Pos(100, 2000 - size), size, t))
    }

    @Test fun targetIsReachableByTheLargestAndSmallestBubble() {
        val t = BubbleVisibility.hideTarget(screen, 600, 140, 60)
        for (sz in listOf(100, 160, 220)) {
            val lowest = Pos(500 - sz / 2, 2000 - sz) // as low as the bubble may go
            val centerY = lowest.y + sz / 2
            assertTrue("size $sz", centerY in t.top..t.bottom || BubbleVisibility.overTarget(Pos(lowest.x, t.top), sz, t))
        }
    }

    // ---- telling the user it is hidden

    @Test fun hiddenNoticeNamesTheTimeLeft() {
        assertEquals("Hidden for 5 more min", BubbleVisibility.notice(on = true, hiddenUntil = now + 5 * min, now = now))
        assertEquals("Hidden for 1 more min", BubbleVisibility.notice(on = true, hiddenUntil = now + 10_000, now = now))
    }

    @Test fun switchedOffNotice() = assertEquals("Switched off", BubbleVisibility.notice(on = false, hiddenUntil = 0, now = now))

    @Test fun noNoticeWhenShowing() {
        assertEquals(null, BubbleVisibility.notice(on = true, hiddenUntil = 0, now = now))
        assertEquals(null, BubbleVisibility.notice(on = true, hiddenUntil = now - 1, now = now))
    }
}
