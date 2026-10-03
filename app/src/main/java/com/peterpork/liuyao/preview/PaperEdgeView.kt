package com.peterpork.liuyao.preview

import android.content.Context
import android.graphics.*
import android.view.View
import android.view.ViewTreeObserver
import com.peterpork.liuyao.R
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.roundToInt

/** Native sibling above SurfaceView. Only the outer 12dp remains after readiness. */
internal class PaperEdgeView(context: Context, private val paperColor: Int) : View(context) {
    var paperBounds = Rect.Zero
        set(value) { if (field != value) { field = value; invalidate() } }
    var loading = true
        set(value) { field = value; invalidate() }
    val featherPixels = 12f * resources.displayMetrics.density
    private val paper = BitmapFactory.decodeResource(resources, R.drawable.paper_fiber_texture,
        BitmapFactory.Options().apply { inScaled = false })
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(paperColor, PorterDuff.Mode.MULTIPLY)
    }
    private val erase = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val location = IntArray(2)
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE
    private val positionObserver = ViewTreeObserver.OnPreDrawListener {
        getLocationInWindow(location)
        if (location[0] != lastX || location[1] != lastY) {
            lastX = location[0]; lastY = location[1]; invalidate()
        }
        true
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); viewTreeObserver.addOnPreDrawListener(positionObserver) }
    override fun onDetachedFromWindow() { viewTreeObserver.removeOnPreDrawListener(positionObserver); super.onDetachedFromWindow() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val colors = intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT)
        fun positions(size: Int): FloatArray {
            val fraction = (featherPixels / size).coerceAtMost(.49f)
            return floatArrayOf(0f, fraction, 1f - fraction, 1f)
        }
        erase.shader = ComposeShader(
            LinearGradient(0f, 0f, w.toFloat(), 0f, colors, positions(w), Shader.TileMode.CLAMP),
            LinearGradient(0f, 0f, 0f, h.toFloat(), colors, positions(h), Shader.TileMode.CLAMP),
            PorterDuff.Mode.MULTIPLY)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        getLocationInWindow(location)
        val saved = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawColor(paperColor)
        val bounds = paperBounds
        if (bounds.width > 0 && bounds.height > 0) {
            val scale = max(bounds.width / paper.width, bounds.height / paper.height)
            // Compose Image uses an integer-aligned centered ContentScale.Crop destination.
            val dw = (paper.width * scale).roundToInt().toFloat()
            val dh = (paper.height * scale).roundToInt().toFloat()
            val left = bounds.left + ((bounds.width - dw) / 2).roundToInt() - location[0]
            val top = bounds.top + ((bounds.height - dh) / 2).roundToInt() - location[1]
            canvas.drawBitmap(paper, null, RectF(left, top, left + dw, top + dh), bitmapPaint)
        }
        if (!loading) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), erase)
        canvas.restoreToCount(saved)
    }
}
