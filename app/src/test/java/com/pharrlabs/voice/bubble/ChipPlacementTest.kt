package com.pharrlabs.voice.bubble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipPlacementTest {
    private val screen = Screen(width = 1000, statusBar = 50, keyboardTop = 1600)
    private val gap = 10
    private val size = 120

    private fun place(anchor: Pos, w: Int = 300, h: Int = 100, field: Area? = Area(100, 800, 900, 1000), s: Screen = screen) =
        ChipPlacement.place(anchor, size, w, h, s, field, gap)

    private fun chipArea(p: Pos, w: Int = 300, h: Int = 100) = Area(p.x, p.y, p.x + w, p.y + h)

    @Test fun aboveTheBubbleWhenThereIsRoom() =
        assertEquals(Pos(520, 540), place(Pos(700, 650)))

    @Test fun staysOutOfTheFieldEvenWhenItsBoundsAreSlightlySmallerThanTheBox() {
        // The visible text box is usually a little bigger than the field node reports.
        val p = place(Pos(700, 690), h = 160)
        assertFalse(chipArea(p, h = 160).intersects(Area(100 - gap, 800 - gap, 900 + gap, 1000 + gap)))
    }

    @Test fun besideTheBubbleWhenThereIsNoRoomAbove() =
        assertEquals(Pos(390, 70), place(Pos(700, 60)))

    @Test fun goesBelowAFieldThatTouchesTheTopOfTheScreen() {
        val field = Area(0, 60, 1000, 300)
        val p = place(Pos(850, 70), field = field)
        assertFalse(chipArea(p).intersects(Area(field.left - gap, field.top - gap, field.right + gap, field.bottom + gap)))
        assertFalse(chipArea(p).intersects(Area(850, 70, 970, 190)))
    }

    @Test fun neverCoversTheFieldWhenTheBubbleWasDraggedOverIt() {
        val field = Area(100, 800, 900, 1000)
        val p = place(Pos(400, 850), field = field)
        assertFalse(chipArea(p).intersects(field))
        assertFalse(chipArea(p).intersects(Area(400, 850, 520, 970)))
    }

    @Test fun worksWithoutAKnownField() = assertEquals(Pos(520, 540), place(Pos(700, 650), field = null))

    @Test fun staysOnScreenWhenNothingFits() {
        val tiny = Screen(width = 320, statusBar = 20, keyboardTop = 200)
        val p = ChipPlacement.place(Pos(100, 100), 120, 300, 150, tiny, Area(0, 0, 320, 200), 10)
        assertTrue(p.x in 0..20)
        assertTrue(p.y >= 20)
    }
}
