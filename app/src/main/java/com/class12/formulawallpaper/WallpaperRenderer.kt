package com.class12.formulawallpaper

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.SubscriptSpan

object WallpaperRenderer {

    fun render(context: Context, card: FormulaCard): Bitmap {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(1080)
        val h = dm.heightPixels.coerceAtLeast(2400)

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.BLACK)

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

        val accent = when (card.subject.uppercase()) {
            "PHYSICS" -> Color.parseColor("#93C5FD")
            "CHEMISTRY" -> Color.parseColor("#86EFAC")
            else -> Color.parseColor("#D8B4FE")
        }

        val marginX = w * 0.09f
        val textWidth = (w - marginX * 2f).toInt()
        var y = h * 0.14f

        y = drawTextBlock(
            canvas, formatBookText("${card.subject}  •  ${card.chapter.uppercase()}"),
            marginX, y, textWidth,
            size = w * 0.030f, color = accent, bold = true
        ) + 18f

        y = drawTextBlock(
            canvas, formatBookText(card.title),
            marginX, y, textWidth,
            size = w * 0.056f, color = Color.parseColor("#F1F5F9"), bold = true
        ) + 34f

        y = drawBookFormula(canvas, card, marginX, y, textWidth.toFloat(), w, accent) + 28f

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 226, 232, 240)
            strokeWidth = 2.5f
        }
        canvas.drawLine(marginX, y, marginX + w * 0.28f, y, dividerPaint)
        y += 24f

        drawTextBlock(
            canvas, formatBookText(card.takeaway),
            marginX, y, textWidth,
            size = w * 0.035f, color = Color.parseColor("#94A3B8"), bold = false
        )

        return bitmap
    }

    private fun formatBookText(raw: String): CharSequence {
        // Remove multiplication asterisks between terms while keeping spaces clean
        val cleaned = raw
            .replace(Regex("\\s*\\*\\s*"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val sb = SpannableStringBuilder()
        var i = 0
        while (i < cleaned.length) {
            if (cleaned[i] == '_' && i + 1 < cleaned.length && !cleaned[i + 1].isWhitespace()) {
                val start = sb.length
                var j = i + 1
                while (j < cleaned.length && cleaned[j].isLetterOrDigit()) {
                    j++
                }
                if (j > i + 1) {
                    val subPart = cleaned.substring(i + 1, j)
                    sb.append(subPart)
                    val end = sb.length
                    sb.setSpan(SubscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(0.68f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = j
                    continue
                }
            }
            sb.append(cleaned[i])
            i++
        }
        return sb
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

        val prefixSpanned = formatBookText(card.prefix)
        val numSpanned = formatBookText(card.numerator)
        val denSpanned = formatBookText(card.denominator)

        val longestPart = maxOf(
            eqPaint.measureText(prefixSpanned.toString() + numSpanned.toString()),
            eqPaint.measureText(prefixSpanned.toString() + denSpanned.toString())
        )
        if (longestPart > maxWidth * 0.92f && card.layout.lowercase() != "simple") {
            eqSize *= (maxWidth * 0.90f / longestPart).coerceAtLeast(0.62f)
            eqPaint.textSize = eqSize
        }

        var cursorX = x
        val centerY = y + eqSize * 1.15f

        if (prefixSpanned.isNotEmpty()) {
            drawInlineSpanned(canvas, prefixSpanned, cursorX, y, eqSize, Color.WHITE, serif = true)
            cursorX += eqPaint.measureText(prefixSpanned.toString()) + 12f
        }

        return when (card.layout.lowercase()) {
            "fraction" -> {
                val topW = eqPaint.measureText(numSpanned.toString())
                val botW = eqPaint.measureText(denSpanned.toString())
                val barW = maxOf(topW, botW) + 34f
                val barY = centerY - eqSize * 0.25f

                drawInlineSpanned(canvas, numSpanned, cursorX + (barW - topW) / 2f, barY - eqSize * 1.05f, eqSize, Color.WHITE, serif = true)
                canvas.drawLine(cursorX, barY, cursorX + barW, barY, linePaint)
                drawInlineSpanned(canvas, denSpanned, cursorX + (barW - botW) / 2f, barY + 8f, eqSize, Color.WHITE, serif = true)
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
                    canvas.drawText(formatBookText(card.upperLimit).toString(), midX + 18f, topCurveY + subSize, limPaint)
                }
                if (card.lowerLimit.isNotBlank()) {
                    canvas.drawText(formatBookText(card.lowerLimit).toString(), midX + 18f, botCurveY, limPaint)
                }

                val bodyX = cursorX + signW + 40f
                val bodySpanned = formatBookText("${card.numerator} ${card.denominator}".trim())
                drawInlineSpanned(canvas, bodySpanned, bodyX, y, eqSize, Color.WHITE, serif = true)
                botCurveY + 20f
            }

            "root" -> {
                val innerRaw = card.numerator.ifBlank { card.denominator }
                val innerSpanned = formatBookText(innerRaw)
                val innerW = eqPaint.measureText(innerSpanned.toString()) + 24f
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
                drawInlineSpanned(canvas, innerSpanned, startX + 52f, topY + 6f, eqSize, Color.WHITE, serif = true)
                botY + 20f
            }

            else -> {
                val fullSpanned = formatBookText("${card.numerator} ${card.denominator}".trim().ifBlank { card.prefix })
                drawTextBlock(
                    canvas, fullSpanned, cursorX, y,
                    (maxWidth - (cursorX - x)).toInt().coerceAtLeast(200),
                    eqSize, Color.WHITE, bold = true, serif = true
                ) + 12f
            }
        }
    }

    private fun drawInlineSpanned(
        canvas: Canvas,
        text: CharSequence,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        serif: Boolean
    ) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (serif) Typeface.create(Typeface.SERIF, Typeface.BOLD) else Typeface.DEFAULT_BOLD
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 2000)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawTextBlock(
        canvas: Canvas,
        text: CharSequence,
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
