package com.pharrlabs.voice.bubble

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

data class ChipAction(val label: String, val onClick: () -> Unit)

/**
 * A small dark pill shown next to the bubble after a dictation: an optional message and tappable actions
 * (Undo, Redo, Remember...). It never takes focus, so the field underneath keeps its cursor and keyboard.
 */
class ChipOverlay(private val context: Context, private val wm: WindowManager) {
    private val main = Handler(Looper.getMainLooper())
    private val d = context.resources.displayMetrics.density
    private var view: View? = null
    private val dismiss = Runnable { hide() }

    val isShowing: Boolean get() = view != null

    /**
     * @param anchorX,anchorY screen position of the bubble's top-left; the chip sits beside it, but [ChipPlacement]
     * moves it clear of the text [field] being dictated into.
     */
    fun show(
        message: String?, actions: List<ChipAction>, anchorX: Int, anchorY: Int, bubbleSizePx: Int, timeoutMs: Long,
        screen: Screen? = null, field: Area? = null,
    ) {
        hide()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(14), px(8), px(6), px(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F0262626"))
                cornerRadius = px(22).toFloat()
            }
            elevation = px(6).toFloat()
        }
        if (message != null) {
            row.addView(TextView(context).apply {
                text = message
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxWidth = px(220)
                setPadding(0, 0, px(8), 0)
            })
        }
        for (a in actions) {
            row.addView(TextView(context).apply {
                text = a.label
                setTextColor(Color.parseColor("#D0BCFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(px(10), px(6), px(10), px(6))
                setOnClickListener { hide(); a.onClick() }
            })
        }
        row.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val w = row.measuredWidth
        val h = row.measuredHeight
        val bounds = wm.currentWindowMetrics.bounds
        val box = screen ?: Screen(bounds.width(), 0, bounds.bottom)
        val pos = ChipPlacement.place(Pos(anchorX, anchorY), bubbleSizePx, w, h, box, field, px(8))
        val x = pos.x
        val y = pos.y
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            fitInsetsTypes = 0
            this.x = x
            this.y = y
        }
        wm.addView(row, lp)
        view = row
        if (timeoutMs > 0) main.postDelayed(dismiss, timeoutMs)
    }

    fun hide() {
        main.removeCallbacks(dismiss)
        view?.let { v -> runCatching { wm.removeView(v) } }
        view = null
    }

    private fun px(dp: Int) = (dp * d).toInt()
}
