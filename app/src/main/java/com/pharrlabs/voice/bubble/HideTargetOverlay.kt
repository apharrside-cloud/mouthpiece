package com.pharrlabs.voice.bubble

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * The "drop here to hide" pill shown only while the bubble is being dragged. It is added to the window stack
 * before the bubble so the bubble stays on top of it, and it never takes touches.
 */
class HideTargetOverlay(private val context: Context, private val wm: WindowManager) {
    private val d = context.resources.displayMetrics.density
    private var view: TextView? = null

    /** Where the target is while shown, in screen pixels. */
    var area: Area? = null
        private set

    private fun px(dp: Int) = (dp * d).toInt()

    fun attach() {
        if (view != null) return
        val v = TextView(context).apply {
            text = "Drop here to hide for 10 min"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            visibility = View.INVISIBLE
        }
        paint(v, over = false)
        wm.addView(v, params(0, 0, px(260), px(56)))
        view = v
    }

    fun show(screen: Screen) {
        val v = view ?: return
        val a = BubbleVisibility.hideTarget(screen, px(260), px(56), px(28))
        area = a
        paint(v, over = false)
        wm.updateViewLayout(v, params(a.left, a.top, px(260), px(56)))
        v.visibility = View.VISIBLE
    }

    fun highlight(over: Boolean) {
        val v = view ?: return
        if (v.visibility != View.VISIBLE) return
        paint(v, over)
        v.text = if (over) "Release to hide" else "Drop here to hide for 10 min"
    }

    fun hide() {
        view?.visibility = View.INVISIBLE
        area = null
    }

    fun detach() {
        view?.let { v -> runCatching { wm.removeView(v) } }
        view = null
        area = null
    }

    private fun paint(v: TextView, over: Boolean) {
        v.background = GradientDrawable().apply {
            setColor(if (over) Color.parseColor("#E0B3261E") else Color.parseColor("#E0262626"))
            cornerRadius = px(28).toFloat()
            setStroke(px(2), if (over) Color.parseColor("#FFFFB4AB") else Color.parseColor("#80D0BCFF"))
        }
    }

    private fun params(x: Int, y: Int, w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        fitInsetsTypes = 0
        this.x = x
        this.y = y
    }
}
