package com.pharrlabs.voice.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Lays children out left to right and wraps to a new line when they run out of width, so no option is ever hidden off-screen. */
class FlowLayout(context: Context, private val hGapPx: Int, private val vGapPx: Int) : ViewGroup(context) {
    private var slots: List<FlowMath.Slot> = emptyList()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
        val kids = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }
        kids.forEach { it.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)) }
        val r = FlowMath.layout(kids.map { it.measuredWidth }, kids.map { it.measuredHeight }, maxWidth, hGapPx, vGapPx)
        slots = r.slots
        setMeasuredDimension(
            resolveSize(r.width + paddingLeft + paddingRight, widthSpec),
            resolveSize(r.height + paddingTop + paddingBottom, heightSpec),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val kids = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }
        kids.forEachIndexed { i, v ->
            val s = slots.getOrNull(i) ?: return@forEachIndexed
            v.layout(paddingLeft + s.x, paddingTop + s.y, paddingLeft + s.x + v.measuredWidth, paddingTop + s.y + v.measuredHeight)
        }
    }
}
