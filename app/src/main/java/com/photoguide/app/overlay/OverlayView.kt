package com.photoguide.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.photoguide.app.analysis.AnalysisResult
import com.photoguide.app.analysis.ArrowDirection
import kotlin.math.abs
import kotlin.math.min

/**
 * Transparent view drawn on top of the camera preview. It renders, in order:
 *  - a rule-of-thirds grid,
 *  - a spirit-level indicator driven by device roll,
 *  - the detected subject's bounding box,
 *  - a large directional arrow for the most urgent coaching tip.
 *
 * Subject coordinates arrive normalized in upright-image space and are mapped
 * onto this view assuming the preview uses FILL_CENTER scaling.
 */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private var result: AnalysisResult? = null

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val boxGood = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 76, 217, 100)
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }
    private val boxWarn = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 204, 0)
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 0f, Color.argb(180, 0, 0, 0))
    }

    init {
        // Enable the arrow's shadow layer.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setResult(r: AnalysisResult) {
        result = r
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawRuleOfThirds(canvas)

        val r = result ?: return
        drawLevel(canvas, r)
        drawSubject(canvas, r)
        drawArrow(canvas, r)
    }

    // ---- Rule-of-thirds grid -------------------------------------------------

    private fun drawRuleOfThirds(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawLine(w / 3f, 0f, w / 3f, h, gridPaint)
        canvas.drawLine(2f * w / 3f, 0f, 2f * w / 3f, h, gridPaint)
        canvas.drawLine(0f, h / 3f, w, h / 3f, gridPaint)
        canvas.drawLine(0f, 2f * h / 3f, w, 2f * h / 3f, gridPaint)
    }

    // ---- Spirit level --------------------------------------------------------

    private fun drawLevel(canvas: Canvas, r: AnalysisResult) {
        if (!r.signals.hasTilt) return
        val roll = r.signals.rollDegrees
        val level = abs(roll) <= 3f
        levelPaint.color =
            if (level) Color.argb(230, 76, 217, 100) else Color.argb(230, 255, 59, 48)

        val cx = width / 2f
        val cy = height / 2f
        val halfLen = width * 0.18f

        // Fixed reference ticks.
        gridPaint.let {
            canvas.drawLine(cx - halfLen - 24f, cy, cx - halfLen, cy, it)
            canvas.drawLine(cx + halfLen, cy, cx + halfLen + 24f, cy, it)
        }
        // Rotating bubble line: rotate opposite to roll so it shows true level.
        canvas.save()
        canvas.rotate(-roll, cx, cy)
        canvas.drawLine(cx - halfLen, cy, cx + halfLen, cy, levelPaint)
        canvas.restore()
    }

    // ---- Subject box ---------------------------------------------------------

    private fun drawSubject(canvas: Canvas, r: AnalysisResult) {
        val box = r.signals.subject ?: return
        if (r.uprightWidth == 0 || r.uprightHeight == 0) return

        val m = mapper(r)
        val left = m.x(box.left * r.uprightWidth)
        val right = m.x(box.right * r.uprightWidth)
        val top = m.y(box.top * r.uprightHeight)
        val bottom = m.y(box.bottom * r.uprightHeight)
        val rect = RectF(min(left, right), top, maxOf(left, right), bottom)

        val paint = if (r.guidance.tips.any {
                it.category == com.photoguide.app.analysis.TipCategory.FRAMING ||
                    it.category == com.photoguide.app.analysis.TipCategory.COMPOSITION
            }) boxWarn else boxGood
        canvas.drawRoundRect(rect, 28f, 28f, paint)
    }

    // ---- Guidance arrow ------------------------------------------------------

    private fun drawArrow(canvas: Canvas, r: AnalysisResult) {
        var dir = r.guidance.primary?.arrow ?: return
        if (dir == ArrowDirection.NONE) return
        // Mirror left/right hints for the front (selfie) camera.
        if (r.isFrontFacing) {
            dir = when (dir) {
                ArrowDirection.LEFT -> ArrowDirection.RIGHT
                ArrowDirection.RIGHT -> ArrowDirection.LEFT
                else -> dir
            }
        }

        val cx = width / 2f
        val cy = height / 2f
        val s = width * 0.09f
        when (dir) {
            ArrowDirection.UP -> triangle(canvas, cx, cy - s * 1.6f, s, 0f)
            ArrowDirection.DOWN -> triangle(canvas, cx, cy + s * 1.6f, s, 180f)
            ArrowDirection.LEFT -> triangle(canvas, cx - s * 1.6f, cy, s, 270f)
            ArrowDirection.RIGHT -> triangle(canvas, cx + s * 1.6f, cy, s, 90f)
            ArrowDirection.ROTATE_CW -> rotationHint(canvas, cx, cy, s, true)
            ArrowDirection.ROTATE_CCW -> rotationHint(canvas, cx, cy, s, false)
            ArrowDirection.NONE -> {}
        }
    }

    /** Draws a filled triangle centered at (x,y), rotated by [deg] (0 = up). */
    private fun triangle(canvas: Canvas, x: Float, y: Float, size: Float, deg: Float) {
        val path = Path().apply {
            moveTo(0f, -size)
            lineTo(size * 0.85f, size * 0.7f)
            lineTo(-size * 0.85f, size * 0.7f)
            close()
        }
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(deg)
        canvas.drawPath(path, arrowPaint)
        canvas.restore()
    }

    private fun rotationHint(canvas: Canvas, cx: Float, cy: Float, s: Float, cw: Boolean) {
        val oval = RectF(cx - s, cy - s, cx + s, cy + s)
        val start = if (cw) -200f else 20f
        val sweep = if (cw) 160f else -160f
        val stroke = Paint(arrowPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 12f
        }
        canvas.drawArc(oval, start, sweep, false, stroke)
        // Arrowhead at the sweeping end.
        val endDeg = start + sweep
        val rad = Math.toRadians(endDeg.toDouble())
        val ex = cx + s * kotlin.math.cos(rad).toFloat()
        val ey = cy + s * kotlin.math.sin(rad).toFloat()
        triangle(canvas, ex, ey, s * 0.45f, if (cw) endDeg + 90f else endDeg - 90f)
    }

    // ---- Coordinate mapping (upright image -> view, FILL_CENTER) -------------

    private inner class Mapper(
        val scale: Float, val offX: Float, val offY: Float, val front: Boolean,
    ) {
        fun x(px: Float): Float {
            val vx = px * scale - offX
            return if (front) width - vx else vx
        }
        fun y(py: Float): Float = py * scale - offY
    }

    private fun mapper(r: AnalysisResult): Mapper {
        val viewAspect = width.toFloat() / height
        val imgAspect = r.uprightWidth.toFloat() / r.uprightHeight
        val scale: Float; var offX = 0f; var offY = 0f
        if (viewAspect > imgAspect) {
            scale = width.toFloat() / r.uprightWidth
            offY = (r.uprightHeight * scale - height) / 2f
        } else {
            scale = height.toFloat() / r.uprightHeight
            offX = (r.uprightWidth * scale - width) / 2f
        }
        return Mapper(scale, offX, offY, r.isFrontFacing)
    }
}
