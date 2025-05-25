package com.tubesmobile.purrytify.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageUtils {

    suspend fun createBitmapFromComposable(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        content: @Composable () -> Unit
    ): Bitmap? = withContext(Dispatchers.Main) {
        val composeView = ComposeView(context).apply {
            setContent(content)
        }

        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

        if (composeView.measuredWidth <= 0 || composeView.measuredHeight <= 0) {
            return@withContext null
        }
        val bitmap = Bitmap.createBitmap(composeView.measuredWidth, composeView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        composeView.draw(canvas)
        return@withContext bitmap
    }

    suspend fun saveBitmapToTempFile(context: Context, bitmap: Bitmap, fileName: String = "shared_capsule.png"): File? = withContext(Dispatchers.IO) {
        val imagePath = File(context.cacheDir, "images")
        if (!imagePath.exists()) {
            imagePath.mkdirs()
        }
        val imageFile = File(imagePath, fileName)
        try {
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            return@withContext imageFile
        } catch (e: IOException) {
            e.printStackTrace()
            return@withContext null
        }
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }
}