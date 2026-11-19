package br.anotaquestoes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** Mostra o número e uma amostra do traço com a espessura/cor atuais. */
class WidthPreview(ctx: Context, private val draw: DrawingView) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dp = ctx.resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#ECEFF1")
        c.drawRoundRect(2 * dp, 4 * dp, w - 2 * dp, h - 4 * dp, 8 * dp, 8 * dp, p)

        val size = draw.widthDp
        val tool = draw.tool
        p.color = Color.parseColor("#455A64")
        p.textSize = 10 * dp
        p.textAlign = Paint.Align.CENTER
        c.drawText(if (tool == DrawingView.Tool.MOVE) "✋" else size.toInt().toString(), w / 2, 16 * dp, p)

        val cy = h * 0.66f
        when (tool) {
            DrawingView.Tool.HIGHLIGHTER -> {
                p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.SQUARE
                p.color = draw.hlColor; p.alpha = 130
                p.strokeWidth = min(size * dp, 16 * dp)
                c.drawLine(10 * dp, cy, w - 10 * dp, cy, p)
            }
            DrawingView.Tool.ERASER, DrawingView.Tool.ERASER_PART -> {
                p.style = Paint.Style.STROKE
                p.color = Color.parseColor("#78909C"); p.strokeWidth = 1.5f * dp
                c.drawCircle(w / 2, cy, min(size * dp / 2f, 11 * dp), p)
            }
            DrawingView.Tool.TEXT -> {
                p.style = Paint.Style.FILL; p.color = Color.BLACK
                p.textSize = min(size * dp, 20 * dp)
                c.drawText("Aa", w / 2, cy + 6 * dp, p)
            }
            DrawingView.Tool.MOVE -> {}
            else -> {
                p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
                p.color = if (tool == DrawingView.Tool.LASER) Color.parseColor("#FF1744") else draw.penColor
                p.strokeWidth = min(size * dp, 16 * dp)
                c.drawLine(12 * dp, cy, w - 12 * dp, cy, p)
            }
        }
    }
}
