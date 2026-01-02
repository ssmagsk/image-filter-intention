package com.example.image_filter_intention.converter

import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class CPUImageConverter : IImageConverter {

    override fun yuvToRgba(image: ImageProxy): RgbaBuffer? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val out = ByteArray(width * height * 4)
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        var outOffset = 0
        for (row in 0 until height) {
            val yRow = row * yRowStride
            val uvRow = (row shr 1) * uvRowStride
            for (col in 0 until width) {
                val yIndex = yRow + col
                val uvIndex = uvRow + (col shr 1) * uvPixelStride

                val y = (yBuffer.get(yIndex).toInt() and 0xFF)
                val u = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                val v = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                val r = (y + 1.370705f * v).toInt().coerceIn(0, 255)
                val g = (y - 0.337633f * u - 0.698001f * v).toInt().coerceIn(0, 255)
                val b = (y + 1.732446f * u).toInt().coerceIn(0, 255)

                out[outOffset] = r.toByte()
                out[outOffset + 1] = g.toByte()
                out[outOffset + 2] = b.toByte()
                out[outOffset + 3] = 0xFF.toByte()
                outOffset += 4
            }
        }
        return RgbaBuffer(out, width, height)
    }

    override fun rgbaToBitmap(buffer: RgbaBuffer): Bitmap {
        val outBuffer = ByteBuffer.wrap(buffer.bytes)
        return Bitmap.createBitmap(buffer.width, buffer.height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(outBuffer)
        }
    }
}

