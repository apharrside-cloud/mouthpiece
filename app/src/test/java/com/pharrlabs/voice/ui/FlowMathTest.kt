package com.pharrlabs.voice.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FlowMathTest {
    @Test fun emptyHasNoHeight() = assertEquals(0, FlowMath.layout(emptyList(), emptyList(), 500, 8, 8).height)

    @Test fun itemsThatFitStayOnOneRow() {
        val r = FlowMath.layout(listOf(100, 100, 100), listOf(40, 40, 40), 500, 10, 8)
        assertEquals(listOf(0, 110, 220), r.slots.map { it.x })
        assertEquals(listOf(0, 0, 0), r.slots.map { it.y })
        assertEquals(40, r.height)
    }

    @Test fun wrapsWhenNextItemDoesNotFit() {
        val r = FlowMath.layout(listOf(200, 200, 200), listOf(40, 40, 40), 500, 10, 8)
        assertEquals(listOf(FlowMath.Slot(0, 0), FlowMath.Slot(210, 0), FlowMath.Slot(0, 48)), r.slots)
        assertEquals(88, r.height)
    }

    @Test fun exactFitDoesNotWrap() {
        val r = FlowMath.layout(listOf(245, 245), listOf(40, 40), 500, 10, 8)
        assertEquals(0, r.slots[1].y)
    }

    @Test fun oversizedItemGetsItsOwnRow() {
        val r = FlowMath.layout(listOf(100, 900, 100), listOf(40, 40, 40), 500, 10, 8)
        assertEquals(listOf(0, 48, 96), r.slots.map { it.y })
        assertEquals(136, r.height)
    }

    @Test fun rowHeightIsTheTallestItem() {
        val r = FlowMath.layout(listOf(100, 100, 400), listOf(30, 50, 40), 500, 10, 8)
        assertEquals(58, r.slots[2].y)
        assertEquals(98, r.height)
    }

    @Test fun reportsWidestRow() {
        val r = FlowMath.layout(listOf(200, 200, 100), listOf(40, 40, 40), 500, 10, 8)
        assertEquals(410, r.width)
    }
}
