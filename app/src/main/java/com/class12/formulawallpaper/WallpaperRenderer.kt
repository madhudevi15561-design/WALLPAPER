package com.class12.formulawallpaper

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

object WallpaperRenderer {

    fun render(context: Context, card: FormulaCard): Bitmap {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(1080)
        val h = dm.heightPixels.coerceAtLeast(2400)

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)

        // 1. Pure black base
        canvas.drawColor(Color.BLACK)

        // 2. Draw fixed spider lily background aligned to bottom (keeps upper black space open)
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bg = BitmapFactory.decodeResource(context.resources, R.drawable.bg_wallpaper, opts)
        if (bg != null) {
            val scale = w.toFloat() / bg.width.toFloat()
            val scaledH = bg.height * scale
            val dst = RectF(0f, h - scaledH, w.toFloat(), h.toFloat())
            canvas.drawBitmap(bg, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            bg.recycle()
        }

        // Subtle subject accent that complements the monochrome aesthetic
        val accent = when (card.subject.uppercase()) {
            "PHYSICS" -> Color.parseColor("#93C5FD")    // Soft ice blue
            "CHEMISTRY" -> Color.parseColor("#86EFAC")  // Soft pale mint
            else -> Color.parseColor("#D8B4FE")         // Soft lavender
        }

        // 3. Render cleanly in the empty upper black area (14% to 45% of screen height)
        val marginX = w * 0.09f
        val textWidth = (w - marginX * 2f).toInt()
        var y = h * 0.14f

        // Subject & chapter
        y = drawTextBlock(
            canvas, "${card.subject}  •  ${card.chapter.uppercase()}",
            marginX, y, textWidth,
            size = w * 0.030f, color = accent, bold = true
        ) + 18f

        // Concept title
        y = drawTextBlock(
            canvas, card.title,
            marginX, y, textWidth,
            size = w * 0.056f, color = Color.parseColor("#F1F5F9"), bold = true
        ) + 34f

        // Book-style formula
        y = drawBookFormula(canvas, card, marginX, y, textWidth.toFloat(), w, accent) + 28f

        // Thin silver divider
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 226, 232, 240)
            strokeWidth = 2.5f
        }
        canvas.drawLine(marginX, y, marginX + w * 0.28f, y, dividerPaint)
        y += 24f

        // Short plain-English takeaway
        drawTextBlock(
            canvas, card.takeaway,
            marginX, y, textWidth,
            size = w * 0.035f, color = Color.parseColor("#94A3B8"), bold = false
        )

        return bitmap
    }

    private fun drawBookFormula(
        canvas: Canvas,
        card: FormulaCard,
        x: Float,
        y: Float,
        maxWidth: Float,
        screenW: Int,
        accent: Int
    ): Float {
        var eqSize = screenW * 0.062f
        val subSize = screenW * 0.034f
        val eqPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = eqSize
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val limPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = subSize
            color = accent
            typeface = Typeface.DEFAULT_BOLD
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 4.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        // Auto-shrink font slightly if single-line expression is too wide
        val longestPart = maxOf(
            eqPaint.measureText(card.prefix + card.numerator),
            eqPaint.measureText(card.prefix + card.denominator)
        )
        if (longestPart > maxWidth * 0.92f && card.layout.lowercase() != "simple") {
            eqSize *= (maxWidth * 0.90f / longestPart).coerceAtLeast(0.62f)
            eqPaint.textSize = eqSize
        }

        var cursorX = x
        val centerY = y + eqSize * 1.15f

        if (card.prefix.isNotBlank()) {
            canvas.drawText(card.prefix, cursorX, centerY, eqPaint)
            cursorX += eqPaint.measureText(card.prefix) + 12f
        }

        return when (card.layout.lowercase()) {
            "fraction" -> {
                val topW = eqPaint.measureText(card.numerator)
                val botW = eqPaint.measureText(card.denominator)
                val barW = maxOf(topW, botW) + 34f
                val barY = centerY - eqSize * 0.25f

                canvas.drawText(card.numerator, cursorX + (barW - topW) / 2f, barY - 18f, eqPaint)
                canvas.drawLine(cursorX, barY, cursorX + barW, barY, linePaint)
                canvas.drawText(card.denominator, cursorX + (barW - botW) / 2f, barY + eqSize * 0.95f, eqPaint)
                barY + eqSize * 1.25f
            }

            "integral" -> {
                val signW = eqSize * 0.72f
                val topCurveY = y + 8f
                val botCurveY = y + eqSize * 2.15f
                val midX = cursorX + signW * 0.5f

                val path = Path().apply {
                    moveTo(midX + signW * 0.35f, topCurveY + 12f)
                    cubicTo(midX + signW * 0.1f, topCurveY - 14f, midX - signW * 0.15f, topCurveY + 8f, midX, topCurveY + 42f)
                    lineTo(midX, botCurveY - 42f)
                    cubicTo(midX + signW * 0.15f, botCurveY - 8f, midX - signW * 0.1f, botCurveY + 14f, midX - signW * 0.35f, botCurveY - 12f)
                }
                canvas.drawPath(path, linePaint)

                if (card.upperLimit.isNotBlank()) {
                    canvas.drawText(card.upperLimit, midX + 18f, topCurveY + subSize, limPaint)
                }
                if (card.lowerLimit.isNotBlank()) {
                    canvas.drawText(card.lowerLimit, midX + 18f, botCurveY, limPaint)
                }

                val bodyX = cursorX + signW + 40f
                val bodyText = "${card.numerator} ${card.denominator}".trim()
                canvas.drawText(bodyText, bodyX, centerY, eqPaint)
                botCurveY + 20f
            }

            "root" -> {
                val inner = card.numerator.ifBlank { card.denominator }
                val innerW = eqPaint.measureText(inner) + 24f
                val topY = y + 16f
                val botY = y + eqSize * 1.50f
                val startX = cursorX

                val rootPath = Path().apply {
                    moveTo(startX, botY - eqSize * 0.45f)
                    lineTo(startX + 18f, botY)
                    lineTo(startX + 40f, topY)
                    lineTo(startX + 40f + innerW, topY)
                }
                canvas.drawPath(rootPath, linePaint)
                canvas.drawText(inner, startX + 52f, topY + eqSize * 0.95f, eqPaint)
                botY + 20f
            }

            else -> {
                val full = "${card.numerator} ${card.denominator}".trim().ifBlank { card.prefix }
                drawTextBlock(
                    canvas, full, cursorX, y,
                    (maxWidth - (cursorX - x)).toInt().coerceAtLeast(200),
                    eqSize, Color.WHITE, bold = true, serif = true
                ) + 12f
            }
        }
    }

    private fun drawTextBlock(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Int,
        size: Float,
        color: Int,
        bold: Boolean,
        serif: Boolean = false
    ): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = when {
                serif -> Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
                bold -> Typeface.DEFAULT_BOLD
                else -> Typeface.DEFAULT
            }
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1.12f)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }
}
