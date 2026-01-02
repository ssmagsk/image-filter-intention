package com.example.image_filter_intention.face

import android.annotation.SuppressLint
import android.graphics.PointF
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

data class FaceLandmarks(
    val leftEye: PointF?,
    val rightEye: PointF?,
    val mouthBottom: PointF?,
    val mouthRight: PointF?,
    val mouthLeft: PointF?,
)

class FaceRecognizer(
    private val detector: FaceDetector = defaultDetector(),
    private val throttleEveryNFrames: Int = 2
) {
    private var frameCount = 0

    @SuppressLint("UnsafeOptInUsageError")
    fun process(image: ImageProxy, onResult: (FaceLandmarks?) -> Unit) {
        // Throttle
        frameCount = (frameCount + 1) % throttleEveryNFrames
        if (frameCount != 0) {
            image.close()
            return
        }

        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            onResult(null)
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)

        detector.process(input)
            .addOnSuccessListener { faces ->
                val first = faces.firstOrNull()
                onResult(first?.toLandmarks())
            }
            .addOnFailureListener {
                onResult(null)
            }
            .addOnCompleteListener {
                image.close()
            }
    }

    private fun Face.toLandmarks(): FaceLandmarks {
        val left = getLandmark(FaceLandmarksConst.LEFT_EYE)?.position
        val right = getLandmark(FaceLandmarksConst.RIGHT_EYE)?.position
        val mouthBot = getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
            // HACK ADDING THE 25
            ?.let { PointF(it.x, it.y - 25f) }
        val mouthR = getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
            ?.let { PointF(it.x, it.y - 25f) }
        val mouthL = getLandmark(FaceLandmark.MOUTH_LEFT)?.position
            ?.let { PointF(it.x, it.y - 25f) }
        return FaceLandmarks(left, right, mouthBot, mouthR, mouthL)
    }

    companion object {
        private fun defaultDetector(): FaceDetector {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .enableTracking()
                .build()
            return FaceDetection.getClient(options)
        }
    }
}

private object FaceLandmarksConst {
    const val LEFT_EYE = FaceLandmarksIds.LEFT_EYE
    const val RIGHT_EYE = FaceLandmarksIds.RIGHT_EYE
    const val MOUTH_BOTTOM = FaceLandmarksIds.MOUTH_BOTTOM
}

private object FaceLandmarksIds {
    const val LEFT_EYE = FaceLandmarksIdsInternal.LEFT_EYE
    const val RIGHT_EYE = FaceLandmarksIdsInternal.RIGHT_EYE
    const val MOUTH_BOTTOM = FaceLandmarksIdsInternal.MOUTH_BOTTOM
}

private object FaceLandmarksIdsInternal {
    const val LEFT_EYE = 4
    const val RIGHT_EYE = 10
    const val MOUTH_BOTTOM = 0
    const val MOUTH_RIGHT = 5
    const val MOUTH_LEFT = 11
}

