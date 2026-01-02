package com.example.image_filter_intention

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraXManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    var lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
    private val analyzerCallback: (ImageProxy) -> Unit
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                cameraProvider = future.get()
                bind()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun setImageCapture(capture: ImageCapture) {
        imageCapture = capture
        bind()
    }

    fun switchLens() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        bind()
    }

    fun stop() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
    }

    private fun bind() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { imageAnalysis ->
                imageAnalysis.setAnalyzer(analysisExecutor) { image ->
                    analyzerCallback(image)
                }
            }

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        imageCapture?.let { capture ->
            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
        } ?: provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
    }
}

