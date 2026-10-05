package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.BookEntity
import java.io.File

object CoverArtHelper {

    /**
     * Retrieves or synthesizes a 512x512 album artwork Bitmap for a book.
     * Prevents any squishing or flattening by formatting through formatBookCoverForSquareArtwork.
     */
    fun getOrGenerateCoverBitmap(
        context: Context,
        book: BookEntity,
        fitMode: CoverFitMode? = null
    ): Bitmap {
        val resolvedFitMode = fitMode ?: AppSettingsManager.getInstance(context).coverFitMode.value

        // 1. Try loading existing local cover image file if present
        if (!book.coverImagePath.isNullOrBlank()) {
            val file = File(book.coverImagePath)
            if (file.exists() && file.length() > 0) {
                try {
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

                    var inSampleSize = 1
                    while (boundsOptions.outWidth / (inSampleSize * 2) >= 512 &&
                        boundsOptions.outHeight / (inSampleSize * 2) >= 512
                    ) {
                        inSampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }

                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                    if (bitmap != null) {
                        return formatBookCoverForSquareArtwork(bitmap, resolvedFitMode, targetSize = 512)
                    }
                } catch (_: Throwable) {}
            }
        }

        // 2. Generate a gorgeous album cover with gradient, book emblem, and typography
        return generateStyledArtwork(
            title = book.title,
            author = book.author,
            colorStart = book.coverGradientStart.toInt(),
            colorEnd = book.coverGradientEnd.toInt()
        )
    }

    /**
     * Formats any book cover into a 512x512 square artwork without flattening or squishing.
     * Supports AMBIENT_BLUR_FIT (preserves original aspect ratio with blurred backdrop and 3D shadow)
     * and CENTER_CROP (clean 1:1 square crop).
     */
    fun formatBookCoverForSquareArtwork(
        original: Bitmap,
        fitMode: CoverFitMode = CoverFitMode.AMBIENT_BLUR_FIT,
        targetSize: Int = 512
    ): Bitmap {
        if (fitMode == CoverFitMode.CENTER_CROP) {
            val cropSize = minOf(original.width, original.height)
            val cropX = if (original.width > original.height) {
                (original.width - cropSize) / 2
            } else 0
            val cropY = if (original.height > original.width) {
                // Focus slightly above vertical center to preserve book titles
                ((original.height - cropSize) * 0.20f).toInt().coerceIn(0, original.height - cropSize)
            } else 0

            val cropped = Bitmap.createBitmap(original, cropX, cropY, cropSize, cropSize)
            return if (cropped.width == targetSize && cropped.height == targetSize) cropped
            else Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        }

        // AMBIENT_BLUR_FIT: Preserves original proportions without flattening!
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. Ambient Background: small copy stretched creates a smooth blur
        val thumbW = 40
        val thumbH = 40
        val ambientThumb = Bitmap.createScaledBitmap(original, thumbW, thumbH, true)
        val ambientScaled = Bitmap.createScaledBitmap(ambientThumb, targetSize, targetSize, true)
        canvas.drawBitmap(ambientScaled, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        // Atmospheric dark overlay
        val overlayPaint = Paint().apply {
            color = 0xAA0F172A.toInt()
        }
        canvas.drawRect(0f, 0f, targetSize.toFloat(), targetSize.toFloat(), overlayPaint)

        // 2. Aspect Ratio of the book cover or illustration
        val aspect = original.width.toFloat() / original.height.toFloat().coerceAtLeast(1f)
        val coverWidth: Int
        val coverHeight: Int

        if (aspect <= 1.0f) {
            // Portrait orientation (standard book cover)
            coverHeight = (targetSize * 0.86f).toInt()
            coverWidth = (coverHeight * aspect).toInt().coerceAtMost((targetSize * 0.88f).toInt())
        } else {
            // Landscape orientation (e.g. wide illustrations or maps)
            coverWidth = (targetSize * 0.88f).toInt()
            coverHeight = (coverWidth / aspect).toInt().coerceAtMost((targetSize * 0.86f).toInt())
        }

        val left = (targetSize - coverWidth) / 2f
        val top = (targetSize - coverHeight) / 2f

        // 3. Drop Shadow behind the book/illustration
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99000000.toInt()
        }
        val shadowRect = RectF(left - 4f, top + 8f, left + coverWidth + 4f, top + coverHeight + 12f)
        canvas.drawRoundRect(shadowRect, 18f, 18f, shadowPaint)

        // 4. Draw rounded book cover
        val scaledCover = Bitmap.createScaledBitmap(original, coverWidth, coverHeight, true)
        val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaledCover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.save()
        canvas.translate(left, top)
        val bookRect = RectF(0f, 0f, coverWidth.toFloat(), coverHeight.toFloat())
        canvas.drawRoundRect(bookRect, 16f, 16f, coverPaint)

        // Elegant subtle highlight border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0x33FFFFFF
        }
        canvas.drawRoundRect(bookRect, 16f, 16f, borderPaint)
        canvas.restore()

        return output
    }

    /**
     * Generates a 512x512 album cover with gradient, typography, and book branding.
     */
    fun generateStyledArtwork(
        title: String,
        author: String,
        colorStart: Int = 0xFF1E3A8A.toInt(),
        colorEnd: Int = 0xFF0284C7.toInt()
    ): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Gradient Background
        val gradient = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            colorStart, colorEnd,
            Shader.TileMode.CLAMP
        )
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Subtle decorative pattern / card overlay
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22000000
        }
        canvas.drawRoundRect(RectF(24f, 24f, (size - 24).toFloat(), (size - 24).toFloat()), 32f, 32f, overlayPaint)

        // Top Brand Header
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xBBFFFFFF.toInt()
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("VOXREADER AUDIOBOOK", (size / 2).toFloat(), 68f, brandPaint)

        // Decorative Book Spine / Bookmark Accent
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF.toInt()
        }
        canvas.drawRoundRect(RectF((size / 2 - 30).toFloat(), 86f, (size / 2 + 30).toFloat(), 92f), 3f, 3f, accentPaint)

        // Title (wrapped and centered)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = if (title.length > 28) 32f else 40f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val displayTitle = if (title.length > 55) title.take(52) + "…" else title
        val titleLayout = StaticLayout.Builder.obtain(
            displayTitle, 0, displayTitle.length, titlePaint, size - 80
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        val titleY = (size / 2 - titleLayout.height / 2 - 10).toFloat()
        canvas.save()
        canvas.translate((size / 2).toFloat(), titleY)
        titleLayout.draw(canvas)
        canvas.restore()

        // Author (bottom section)
        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEEF1F5F9.toInt()
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val displayAuthor = if (author.isNotBlank()) author else "Autor Desconhecido"
        canvas.drawText(displayAuthor, (size / 2).toFloat(), (size - 75).toFloat(), authorPaint)

        // Tag at bottom
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8894A3B8.toInt()
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("ÁUDIO NEURAL • ALTA FIDELIDADE", (size / 2).toFloat(), (size - 44).toFloat(), tagPaint)

        return bitmap
    }
}
