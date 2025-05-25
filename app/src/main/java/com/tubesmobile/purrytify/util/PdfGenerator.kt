
package com.tubesmobile.purrytify.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.tubesmobile.purrytify.R // Untuk placeholder image
import com.tubesmobile.purrytify.data.model.MonthlySoundCapsuleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object PdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val TEXT_SIZE_TITLE = 20f
    private const val TEXT_SIZE_SUBTITLE = 16f
    private const val TEXT_SIZE_NORMAL = 12f
    private const val TEXT_SIZE_SMALL = 10f
    private const val LINE_SPACING_MULTIPLIER = 1.2f

    suspend fun generateCapsulePdf(context: Context, capsule: MonthlySoundCapsuleData, username: String): Boolean {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val contentWidth = PAGE_WIDTH - 2 * MARGIN

        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = TEXT_SIZE_TITLE
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = TEXT_SIZE_SUBTITLE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = TEXT_SIZE_NORMAL
            isAntiAlias = true
        }
        val smallTextPaint = TextPaint().apply {
            color = Color.GRAY
            textSize = TEXT_SIZE_SMALL
            isAntiAlias = true
        }
        val linkPaint = TextPaint().apply {
            color = Color.BLUE
            textSize = TEXT_SIZE_NORMAL
            isAntiAlias = true
            isUnderlineText = true
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var currentY = MARGIN

        drawTextWithMaxWidth("Purrytify Sound Capsule", canvas, titlePaint, MARGIN, currentY, contentWidth)
        currentY += titlePaint.textSize * LINE_SPACING_MULTIPLIER
        drawTextWithMaxWidth("$username - ${capsule.monthYear}", canvas, subtitlePaint, MARGIN, currentY, contentWidth)
        currentY += subtitlePaint.textSize * LINE_SPACING_MULTIPLIER * 2

        if (capsule.timeListenedMinutes != null && capsule.timeListenedMinutes > 0) {
            drawTextWithMaxWidth("Time Listened", canvas, subtitlePaint, MARGIN, currentY, contentWidth)
            currentY += subtitlePaint.textSize * LINE_SPACING_MULTIPLIER
            val hours = capsule.timeListenedMinutes / 60
            val minutes = capsule.timeListenedMinutes % 60
            drawTextWithMaxWidth(
                "${capsule.timeListenedMinutes} minutes (${hours}h ${minutes}m)",
                canvas, textPaint, MARGIN, currentY, contentWidth
            )
            currentY += textPaint.textSize * LINE_SPACING_MULTIPLIER
            capsule.dailyAverageMinutes?.let {
                drawTextWithMaxWidth("Daily Average: $it minutes", canvas, smallTextPaint, MARGIN, currentY, contentWidth)
                currentY += smallTextPaint.textSize * LINE_SPACING_MULTIPLIER
            }
            currentY += textPaint.textSize // Extra space
        }

        if (capsule.topArtistName != null) {
            drawTextWithMaxWidth("Your Top Artist", canvas, subtitlePaint, MARGIN, currentY, contentWidth)
            currentY += subtitlePaint.textSize * LINE_SPACING_MULTIPLIER

            val imageSize = 60f
            drawTextWithMaxWidth(capsule.topArtistName, canvas, textPaint, MARGIN, currentY, contentWidth)
            currentY += textPaint.textSize * LINE_SPACING_MULTIPLIER
            capsule.totalArtistsListenedThisMonth?.let {
                drawTextWithMaxWidth("You listened to $it artists this month.", canvas, smallTextPaint, MARGIN, currentY, contentWidth)
                currentY += smallTextPaint.textSize * LINE_SPACING_MULTIPLIER
            }
            currentY += textPaint.textSize // Extra space
        }

        // --- Top Song ---
        if (capsule.topSongName != null) {
            drawTextWithMaxWidth("Your Top Song", canvas, subtitlePaint, MARGIN, currentY, contentWidth)
            currentY += subtitlePaint.textSize * LINE_SPACING_MULTIPLIER

            val songBitmap = capsule.topSongImageUrl?.let { url ->
                loadImage(context, url)
            }
            val imageSize = 60f

            drawTextWithMaxWidth("${capsule.topSongName}", canvas, textPaint, MARGIN, currentY, contentWidth)
            currentY += textPaint.textSize * LINE_SPACING_MULTIPLIER

            capsule.totalSongsPlayedThisMonth?.let {
                drawTextWithMaxWidth("You played $it unique songs this month.", canvas, smallTextPaint, MARGIN, currentY, contentWidth)
                currentY += smallTextPaint.textSize * LINE_SPACING_MULTIPLIER
            }
            currentY += textPaint.textSize
        }

        if (capsule.dayStreakCount != null && capsule.dayStreakCount >= 2) {
            drawTextWithMaxWidth("Listening Streak", canvas, subtitlePaint, MARGIN, currentY, contentWidth)
            currentY += subtitlePaint.textSize * LINE_SPACING_MULTIPLIER
            capsule.dayStreakFullText?.let {
                currentY = drawTextWithMaxWidth(it, canvas, textPaint, MARGIN, currentY, contentWidth, returnActualY = true)
                currentY += textPaint.textSize * LINE_SPACING_MULTIPLIER
            }
            capsule.dayStreakDateRange?.let {
                drawTextWithMaxWidth("Streak Range: $it", canvas, smallTextPaint, MARGIN, currentY, contentWidth)
                currentY += smallTextPaint.textSize * LINE_SPACING_MULTIPLIER
            }
            currentY += textPaint.textSize
        }


        capsule.topArtistsList?.take(3)?.let { artists ->
            if (artists.isNotEmpty()) {
                if (currentY + (subtitlePaint.textSize * LINE_SPACING_MULTIPLIER) + (artists.size * (textPaint.textSize * LINE_SPACING_MULTIPLIER)) > PAGE_HEIGHT - MARGIN) {
                    Log.w("PdfGenerator", "Content might overflow, new page logic not fully implemented.")
                }

                drawTextWithMaxWidth("Top Artists List:", canvas, subtitlePaint, MARGIN, currentY, contentWidth)
                currentY += subtitlePaint.textSize * LINE_SPACING_MULTIPLIER
                artists.forEach { artist ->
                    drawTextWithMaxWidth("${artist.rank}. ${artist.name}", canvas, textPaint, MARGIN + 10f, currentY, contentWidth -10f)
                    currentY += textPaint.textSize * LINE_SPACING_MULTIPLIER
                }
                currentY += textPaint.textSize
            }
        }

        val dateGenerated = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())
        val footerText = "Generated by Purrytify on $dateGenerated"
        val footerY = PAGE_HEIGHT - MARGIN + smallTextPaint.textSize
        drawTextWithMaxWidth(footerText, canvas, smallTextPaint, MARGIN, footerY, contentWidth, alignment = Layout.Alignment.ALIGN_CENTER)


        pdfDocument.finishPage(page)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Purrytify_Capsule_${capsule.monthYear.replace(" ", "_")}_$timestamp.pdf"

        var outputStream: OutputStream? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    outputStream = resolver.openOutputStream(it)
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, fileName)
                outputStream = FileOutputStream(file)
            }

            outputStream?.let {
                pdfDocument.writeTo(it)
                Log.i("PdfGenerator", "PDF saved: $fileName")
                return true
            } ?: run {
                Log.e("PdfGenerator", "Failed to get output stream for PDF.")
                return false
            }
        } catch (e: IOException) {
            Log.e("PdfGenerator", "Error writing PDF", e)
            return false
        } finally {
            outputStream?.close()
            pdfDocument.close()
        }
    }

    private fun drawTextWithMaxWidth(
        text: String,
        canvas: Canvas,
        paint: TextPaint,
        x: Float,
        y: Float,
        maxWidth: Float,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        returnActualY: Boolean = false // If true, returns Y after this text block
    ): Float {
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.toInt())
            .setAlignment(alignment)
            .setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(x, y)
        staticLayout.draw(canvas)
        canvas.restore()
        return if (returnActualY) y + staticLayout.height else y // Return original y if not specified
    }

    private suspend fun loadImage(context: Context, url: String, targetWidth: Int = 100, targetHeight: Int = 100, isCircle: Boolean = false): Bitmap? {
        return try {
            val requestBuilder = ImageRequest.Builder(context)
                .data(url)
                .size(targetWidth, targetHeight) // Resize for PDF to save memory
                .allowHardware(false) // Important for drawing on Canvas
                .placeholder(R.drawable.ic_launcher_foreground) // Your placeholder
                .error(R.drawable.ic_launcher_foreground) // Your error placeholder

            if (isCircle) {
                requestBuilder.transformations(CircleCropTransformation())
            }

            val result = context.imageLoader.execute(requestBuilder.build())
            val drawable = result.drawable
            if (drawable != null) {
                // Convert Drawable to Bitmap
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1), // ensure width > 0
                    drawable.intrinsicHeight.coerceAtLeast(1), // ensure height > 0
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("PdfGenerator", "Failed to load image: $url", e)
            // Return a placeholder bitmap on error
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground)
        }
    }
}
