package com.tirup.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64
import kotlin.math.max

object AiImageCompressor {

    const val MAX_IMAGE_DIMENSION = 1024
    const val JPEG_QUALITY = 75

    data class CompressedImage(
        val base64: String,
        val mimeType: String = "image/jpeg",
        val byteSize: Int
    )

    fun compressFromUri(context: Context, uri: Uri): CompressedImage? {
        return try {
            val orientation = getExifOrientation(context, uri)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val correctedBitmap = rotateBitmap(originalBitmap, orientation)
            val scaledBitmap = scaleDownIfNeeded(correctedBitmap, MAX_IMAGE_DIMENSION)

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val bytes = outputStream.toByteArray()

            if (correctedBitmap != originalBitmap) originalBitmap.recycle()
            if (scaledBitmap != correctedBitmap) correctedBitmap.recycle()
            scaledBitmap.recycle()

            val base64 = Base64.getEncoder().encodeToString(bytes)
            CompressedImage(base64 = base64, mimeType = "image/jpeg", byteSize = bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    fun compressBitmap(bitmap: Bitmap): CompressedImage {
        val scaled = scaleDownIfNeeded(bitmap, MAX_IMAGE_DIMENSION)
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val bytes = outputStream.toByteArray()
        if (scaled != bitmap) scaled.recycle()

        val base64 = Base64.getEncoder().encodeToString(bytes)
        return CompressedImage(base64 = base64, mimeType = "image/jpeg", byteSize = bytes.size)
    }

    private fun getExifOrientation(context: Context, uri: Uri): Int {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return ExifInterface.ORIENTATION_NORMAL
            val exif = ExifInterface(inputStream)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSide = max(width, height)
        if (maxSide <= maxDim) return bitmap

        val ratio = maxDim.toFloat() / maxSide
        val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}

