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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                        processBitmap = ::processWithNativeNegative,
                        onBitmapCaptured = { bitmap ->
                            // TODO: Pass bitmap to NDK for processing (grayscale filter)
                        }
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

    private fun processWithNativeGrayscale(source: Bitmap): Bitmap? {
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
            val outputArray = applyGrayscale(inputArray, width, height)
            if (outputArray.size != capacity) return null
            val outBuffer = ByteBuffer.wrap(outputArray)
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            result.copyPixelsFromBuffer(outBuffer)
            result
        } catch (t: Throwable) {
            Log.e("NDK", "Grayscale processing failed", t)
            null
        }
    }

    private fun processWithNativeNegative(source: Bitmap): Bitmap? {
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
            val outputArray = applyNegative(inputArray, width, height)
            if (outputArray.size != capacity) return null
            val outBuffer = ByteBuffer.wrap(outputArray)
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            result.copyPixelsFromBuffer(outBuffer)
            result
        } catch (t: Throwable) {
            Log.e("NDK", "Negative processing failed", t)
            null
        }
    }
}

@Composable
private fun CameraXScreen(
    nativeBanner: String,
    processBitmap: (Bitmap) -> Bitmap?,
    onBitmapCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(false) }
    var lastBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val cameraSelector = remember {
        CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner, hasPermission) {
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
            Text(text = nativeBanner, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 16.dp)
            )
            lastBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Last captured preview",
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .padding(vertical = 16.dp)
                )
            }
        }

        Button(
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
                                        val processed = processBitmap(bmp)
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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .widthIn(min = 240.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text = "Open Camera")
        }
    }
}