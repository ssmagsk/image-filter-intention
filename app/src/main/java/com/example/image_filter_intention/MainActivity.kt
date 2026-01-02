package com.example.image_filter_intention

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.image_filter_intention.converter.CPUImageConverter
import com.example.image_filter_intention.converter.GPUImageConverter
import com.example.image_filter_intention.converter.IImageConverter
import com.example.image_filter_intention.gpu.GPUBloom
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraXScreen(
                        filters = filterOptions(),
                        processBitmap = { bmp, filter ->
                            when (filter.id) {
                        FilterIds.Negative -> processWithNative(bmp, ::applyNegative)
                        FilterIds.Grayscale -> processWithNative(bmp, ::applyGrayscale)
                        FilterIds.Bloom -> processWithNative(bmp, ::applyBloom)
                        FilterIds.GPUBloom -> processWithGpuBloom(bmp)
                        FilterIds.YuvGrayscale -> processWithYuvGrayscale(bmp)
                        else -> processWithNative(bmp, ::applyNegative)
                            }
                        },
                        onBitmapCaptured = { /* hook for further actions if needed */ }
                    )
                }
            }
        }
    }

    /**
     * Native methods that are implemented by the 'image_filter_intention' native library,
     * which is packaged with this application.
     */
    external fun applyGrayscale(input: ByteArray, width: Int, height: Int): ByteArray
    external fun applyNegative(input: ByteArray, width: Int, height: Int): ByteArray
    external fun applyGrayscaleYuv(yPlane: ByteArray, width: Int, height: Int): ByteArray
    external fun applyBloom(input: ByteArray, width: Int, height: Int): ByteArray

    companion object {
        // Used to load the 'image_filter_intention' library on application startup.
        init {
            System.loadLibrary("image_filter_intention")
        }
    }

    private fun processWithNative(
        source: Bitmap,
        nativeFn: (ByteArray, Int, Int) -> ByteArray
    ): Bitmap? {
        val argb = if (source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, /* mutable = */ false)
        } ?: return null

        val width = argb.width
        val height = argb.height
        val capacity = width * height * 4
        val buffer = ByteBuffer.allocate(capacity)
        argb.copyPixelsToBuffer(buffer)
        val inputArray = buffer.array()

        return try {
            val outputArray = nativeFn(inputArray, width, height)
            if (outputArray.size != capacity) return null
            val outBuffer = ByteBuffer.wrap(outputArray)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                copyPixelsFromBuffer(outBuffer)
            }
        } catch (t: Throwable) {
            Log.e("NDK", "Native processing failed", t)
            null
        }
    }

    private fun processWithGpuBloom(source: Bitmap): Bitmap? {
        val argb = if (source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, /* mutable = */ false)
        } ?: return null

        val width = argb.width
        val height = argb.height
        val capacity = width * height * 4
        val buffer = ByteBuffer.allocate(capacity)
        argb.copyPixelsToBuffer(buffer)
        val inputArray = buffer.array()

        val gpuBloom = GPUBloom()
        val outBytes = gpuBloom.applyBloom(inputArray, width, height) ?: return null
        if (outBytes.size != capacity) return null
        val outBuffer = ByteBuffer.wrap(outBytes)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(outBuffer)
        }
    }

    private fun processWithYuvGrayscale(source: Bitmap): Bitmap? {
        val argb = if (source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, /* mutable = */ false)
        } ?: return null

        val width = argb.width
        val height = argb.height
        val pixelCount = width * height
        val ints = IntArray(pixelCount)
        argb.getPixels(ints, 0, width, 0, 0, width, height)

        val yPlane = ByteArray(pixelCount)
        for (i in 0 until pixelCount) {
            val px = ints[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val y = (299 * r + 587 * g + 114 * b + 500) / 1000
            yPlane[i] = y.toByte()
        }

        return try {
            val outputArray = applyGrayscaleYuv(yPlane, width, height)
            if (outputArray.size != pixelCount * 4) return null
            val outBuffer = ByteBuffer.wrap(outputArray)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                copyPixelsFromBuffer(outBuffer)
            }
        } catch (t: Throwable) {
            Log.e("NDK", "YUV grayscale processing failed", t)
            null
        }
    }
}

private object FilterIds {
    const val Negative = "negative"
    const val Grayscale = "grayscale"
    const val Bloom = "bloom"
    const val GPUBloom = "gpu_bloom"
    const val YuvGrayscale = "yuv_grayscale"
}

private data class FilterOption(val id: String, val label: String)

private fun filterOptions(): List<FilterOption> = listOf(
    FilterOption(FilterIds.Negative, "Negative"),
    FilterOption(FilterIds.Grayscale, "Grayscale"),
    FilterOption(FilterIds.Bloom, "Bloom"),
    FilterOption(FilterIds.GPUBloom, "GPU Bloom"),
    FilterOption(FilterIds.YuvGrayscale, "Y Gray (YUV)")
)

@Composable
private fun CameraXScreen(
    filters: List<FilterOption>,
    processBitmap: (Bitmap, FilterOption) -> Bitmap?,
    onBitmapCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val imageConverter: IImageConverter = remember { GPUImageConverter() }

    var hasPermission by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    var lastBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var liveBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedFilter by remember { mutableStateOf(filters.first()) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var imageRotation by remember { mutableFloatStateOf(-90f) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val previewView = remember { PreviewView(context) }
    val cameraManager = remember {
        CameraXManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            lensFacing = lensFacing
        ) { image: ImageProxy ->
            val rgba = imageConverter.yuvToRgba(image)
            liveBitmap = rgba?.let { imageConverter.rgbaToBitmap(it) }
            image.close()
        }
    }

    LaunchedEffect(hasPermission, permissionRequested) {
        if (!hasPermission && !permissionRequested) {
            permissionRequested = true
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            cameraManager.setImageCapture(imageCapture)
            cameraManager.start()
        }
    }

    LaunchedEffect(cameraManager.lensFacing) {
        imageRotation = if (cameraManager.lensFacing == CameraSelector.LENS_FACING_FRONT) -90f else 90f
    }

    DisposableEffect(Unit) {
        onDispose { cameraManager.stop() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(5f)
                    .padding(bottom = 16.dp)
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.matchParentSize()
                )
                Button(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            CameraSelector.LENS_FACING_BACK
                        } else {
                            CameraSelector.LENS_FACING_FRONT
                        }
                        cameraManager.switchLens()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(text = "Flip")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    val isSelected = filter.id == selectedFilter.id
                    Button(
                        onClick = { selectedFilter = filter },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    ) {
                        Text(text = filter.label)
                    }
                }
            }
            processBitmap(liveBitmap ?: return, selectedFilter)?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Filtered preview",
                    modifier = Modifier
                        // HACK(ATHON) BECAUSE IMAGE GETS CAPTURED AN AN ANGLE
                        .rotate(imageRotation)
                        .weight(5f)
                        .widthIn(max = 360.dp)
                        .padding(vertical = 16.dp)
                )
            }
        }
    }
}