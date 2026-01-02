package com.example.image_filter_intention.converter

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

data class RgbaBuffer(
    val bytes: ByteArray,
    val width: Int,
    val height: Int
)

interface IImageConverter {
    fun yuvToRgba(image: ImageProxy): RgbaBuffer?
    fun rgbaToBitmap(buffer: RgbaBuffer): Bitmap
}

