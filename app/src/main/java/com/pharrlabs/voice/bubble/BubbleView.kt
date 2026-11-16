package com.pharrlabs.voice.bubble

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

/** The floating mic bubble: purple idle, green + pulse while listening, amber spinner while processing, red on error. */
class BubbleView(
    context: Context,
    private val gestureFactory: () -> BubbleGesture,
    private val onEvent: (GestureEvent) -> Unit,
) : View(context) {
    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3 * d; color = Color.WHITE }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.4f * d; strokeCap = Paint.Cap.ROUND
    }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()
    private var state: BubbleState = BubbleState.Idle
    private var phase = 0f
    private var animator: ValueAnimator? = null

    /** Opacity while idle (0.25..1); listening and processing are always fully opaque. */
    var idleAlpha = 1f
        set(v) { field = v; alpha = if (state == BubbleState.Idle) v else 1f }

    /** Outline chosen in Settings. */
    var style: BubbleStyle = BubbleStyle.Pill
        set(v) { field = v; invalidate() }

    /** Symbol chosen in Settings. */
    var symbol: BubbleIcon = BubbleIcon.Mic
        set(v) { field = v; invalidate() }

    /** False for the Settings preview: draws only, ignores touches. */
    var interactive = true

    /** Live mic level 0..1 while listening; widens the pulse ring. */
    private var level = 0f
    fun setLevel(v: Float) { level = v.coerceIn(0f, 1f); if (state == BubbleState.Listening) invalidate() }

    init {
        contentDescription = "Dictate"
    }

    private var gesture: BubbleGesture? = null
    private val holdTimer = Runnable { dispatch(gesture?.tick(android.os.SystemClock.uptimeMillis()).orEmpty()) }

    private fun dispatch(events: List<GestureEvent>) = events.forEach(onEvent)

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (!interactive) return false
        val t = e.eventTime
        // rawX/rawY: the window moves while dragging, so view-local coordinates would drift.
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val g = gestureFactory().also { gesture = it }
                dispatch(g.down(t, e.rawX, e.rawY))
                postDelayed(holdTimer, HOLD_MS)
            }
            android.view.MotionEvent.ACTION_MOVE -> dispatch(gesture?.move(t, e.rawX, e.rawY).orEmpty())
            android.view.MotionEvent.ACTION_UP -> {
                removeCallbacks(holdTimer)
                val events = gesture?.up(t).orEmpty()
                gesture = null
                if (events.contains(GestureEvent.Tap)) performClick()
                dispatch(events)
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(holdTimer)
                dispatch(gesture?.cancel().orEmpty())
                gesture = null
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    companion object { const val HOLD_MS = 350L }

    fun setState(s: BubbleState) {
        state = s
        alpha = if (s == BubbleState.Idle) idleAlpha else 1f
        level = 0f
        animator?.cancel()
        animator = null
        if (s == BubbleState.Listening || s == BubbleState.Processing) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = if (s == BubbleState.Listening) 1100 else 900
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val shape = BubbleShape.of(width.toFloat(), height.toFloat(), 3 * d, style)
        val p = shape.pill
        fill.color = when (state) {
            BubbleState.Idle -> Color.parseColor("#6750A4")
            BubbleState.Listening -> Color.parseColor("#1B8A5A")
            BubbleState.Processing -> Color.parseColor("#C77700")
            is BubbleState.Error -> Color.parseColor("#C62828")
        }
        if (state == BubbleState.Listening) {
            ring.alpha = ((1f - phase) * 200).toInt()
            val grow = (phase * 3 + level * 6) * d
            rect.set(p.left - grow, p.top - grow, p.right + grow, p.bottom + grow)
            canvas.drawRoundRect(rect, shape.radius + grow, shape.radius + grow, ring)
        }
        rect.set(p.left, p.top, p.right, p.bottom)
        canvas.drawRoundRect(rect, shape.radius, shape.radius, fill)
        if (state == BubbleState.Processing) {
            ring.alpha = 255
            val r = p.width() * 0.32f
            rect.set(shape.cx - r, shape.cy - r, shape.cx + r, shape.cy + r)
            canvas.drawArc(rect, phase * 360f, 100f, false, ring)
            return
        }
        when (symbol) {
            BubbleIcon.Mic -> drawMic(canvas, shape)
            BubbleIcon.Bars -> {
                val live = if (state == BubbleState.Listening) 0.6f + 0.4f * level else 1f
                for (b in IconGeometry.bars(shape.cx, shape.cy, shape.iconSize)) {
                    val half = b.height() / 2 * live
                    rect.set(b.left, shape.cy - half, b.right, shape.cy + half)
                    canvas.drawRoundRect(rect, b.width() / 2, b.width() / 2, iconFill)
                }
            }
            BubbleIcon.Chat -> {
                val c = IconGeometry.chat(shape.cx, shape.cy, shape.iconSize)
                rect.set(c.body.left, c.body.top, c.body.right, c.body.bottom)
                canvas.drawRoundRect(rect, c.radius, c.radius, iconFill)
                path.reset()
                c.tail.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.first, p.second) else path.lineTo(p.first, p.second) }
                path.close()
                canvas.drawPath(path, iconFill)
                val dotY = (c.body.top + c.body.bottom) / 2
                for (dx in listOf(-0.22f, 0f, 0.22f)) canvas.drawCircle(shape.cx + dx * shape.iconSize, dotY, shape.iconSize * 0.07f, fill)
            }
            BubbleIcon.Sparkle -> {
                path.reset()
                IconGeometry.sparkle(shape.cx, shape.cy, shape.iconSize).forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(p.first, p.second) else path.lineTo(p.first, p.second)
                }
                path.close()
                canvas.drawPath(path, iconFill)
            }
        }
    }

    private val path = android.graphics.Path()

    /** Microphone: capsule, cradle arc, stem and base. */
    private fun drawMic(canvas: Canvas, shape: BubbleShape) {
        val c = shape.capsule
        rect.set(c.left, c.top, c.right, c.bottom)
        canvas.drawRoundRect(rect, shape.capsuleRadius, shape.capsuleRadius, iconFill)
        val a = shape.cradle
        rect.set(a.left, a.top, a.right, a.bottom)
        canvas.drawArc(rect, 0f, 180f, false, icon)
        canvas.drawLine(shape.cx, a.bottom, shape.cx, shape.stemEndY, icon)
        canvas.drawLine(shape.cx - shape.baseHalfWidth, shape.baseY, shape.cx + shape.baseHalfWidth, shape.baseY, icon)
    }
}
