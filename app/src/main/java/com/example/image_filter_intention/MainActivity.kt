package com.example.image_filter_intention

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.io.File

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
                        nativeBanner = stringFromJNI(),
                        filters = filterOptions(),
                        processBitmap = { bmp, filter ->
                            when (filter.id) {
                                FilterIds.Negative -> processWithNative(bmp, ::applyNegative)
                                FilterIds.Grayscale -> processWithNative(bmp, ::applyGrayscale)
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
     * A native method that is implemented by the 'image_filter_intention' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String
    external fun applyGrayscale(input: ByteArray, width: Int, height: Int): ByteArray
    external fun applyNegative(input: ByteArray, width: Int, height: Int): ByteArray

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
}

private object FilterIds {
    const val Negative = "negative"
    const val Grayscale = "grayscale"
}

private data class FilterOption(val id: String, val label: String)

private fun filterOptions(): List<FilterOption> = listOf(
    FilterOption(FilterIds.Negative, "Negative"),
    FilterOption(FilterIds.Grayscale, "Grayscale")
)

@Composable
private fun CameraXScreen(
    nativeBanner: String,
    filters: List<FilterOption>,
    processBitmap: (Bitmap, FilterOption) -> Bitmap?,
    onBitmapCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    var lastBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedFilter by remember { mutableStateOf(filters.first()) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }

    val imageRotation = if (lensFacing == CameraSelector.LENS_FACING_FRONT) -90f else 90f

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val cameraSelector = remember(lensFacing) {
        CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val previewView = remember { PreviewView(context) }

    LaunchedEffect(hasPermission, permissionRequested) {
        if (!hasPermission && !permissionRequested) {
            permissionRequested = true
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner, hasPermission, lensFacing) {
        if (!hasPermission) {
            return@DisposableEffect onDispose { }
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            cameraProvider.unbindAll()
            try {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (_: Exception) {
                // TODO: surface to UI/log if needed
            }
        }
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
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
            lastBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Last captured preview",
                    modifier = Modifier
                        // HACK(ATHON) BECAUSE IMAGE GETS CAPTURED AN AN ANGLE
                        .rotate(imageRotation)
                        .weight(5f)
                        .widthIn(max = 360.dp)
                        .padding(vertical = 16.dp)
                )
            }

            Button(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 24.dp)
                    .widthIn(min = 240.dp),
                onClick = {
                    if (!hasPermission) {
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        val output = File.createTempFile(
                            "capture-",
                            ".jpg",
                            context.cacheDir
                        )
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(output).build()
                        val executor = ContextCompat.getMainExecutor(context)
                        imageCapture.takePicture(
                            outputOptions,
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    coroutineScope.launch {
                                        runCatching {
                                            BitmapFactory.decodeFile(output.absolutePath)
                                        }.getOrNull()?.let { bmp ->
                                            val processed = processBitmap(bmp, selectedFilter)
                                            lastBitmap = processed ?: bmp
                                            onBitmapCaptured(lastBitmap!!)
                                        }
                                        output.delete()
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    // TODO: show error UI/log
                                }
                            }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = "Snappity Snap")
            }
        }
    }
}