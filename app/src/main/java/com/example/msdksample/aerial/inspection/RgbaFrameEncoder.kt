package com.example.msdksample.aerial.inspection

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

object RgbaFrameEncoder {

    fun toJpeg(
        rgba: ByteArray,
        offset: Int,
        length: Int,
        width: Int,
        height: Int,
        maximumBytes: Int = 768 * 1024
    ): ByteArray? {
        if (width <= 0 || height <= 0 || offset < 0 || length < 0) return null
        val expectedBytes = width.toLong() * height * 4
        if (expectedBytes > length || offset.toLong() + expectedBytes > rgba.size) return null

        val scale = minOf(1f, MAX_WIDTH.toFloat() / width, MAX_HEIGHT.toFloat() / height)
        val outputWidth = (width * scale).toInt().coerceAtLeast(1)
        val outputHeight = (height * scale).toInt().coerceAtLeast(1)
        val pixels = IntArray(outputWidth * outputHeight)
        for (targetY in 0 until outputHeight) {
            val sourceY = ((targetY + 0.5f) * height / outputHeight).toInt().coerceIn(0, height - 1)
            for (targetX in 0 until outputWidth) {
                val sourceX = ((targetX + 0.5f) * width / outputWidth).toInt().coerceIn(0, width - 1)
                val sourceOffset = offset + (sourceY * width + sourceX) * 4
                val red = rgba[sourceOffset].toInt() and 0xff
                val green = rgba[sourceOffset + 1].toInt() and 0xff
                val blue = rgba[sourceOffset + 2].toInt() and 0xff
                pixels[targetY * outputWidth + targetX] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, outputWidth, outputHeight, Bitmap.Config.ARGB_8888)

        return try {
            for (quality in JPEG_QUALITIES) {
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                val jpeg = output.toByteArray()
                if (jpeg.size <= maximumBytes) return jpeg
            }
            null
        } finally {
            bitmap.recycle()
        }
    }

    private const val MAX_WIDTH = 960
    private const val MAX_HEIGHT = 540
    private val JPEG_QUALITIES = intArrayOf(72, 60, 48)
}
