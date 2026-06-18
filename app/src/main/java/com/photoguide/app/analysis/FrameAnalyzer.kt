package com.photoguide.app.analysis

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Result handed to the UI for every analysed frame. The subject box is in
 * normalized upright-image space; [uprightWidth]/[uprightHeight] and
 * [isFrontFacing] let the overlay map it onto the preview.
 */
data class AnalysisResult(
    val guidance: Guidance,
    val signals: FrameSignals,
    val uprightWidth: Int,
    val uprightHeight: Int,
    val isFrontFacing: Boolean,
)

/**
 * CameraX [ImageAnalysis.Analyzer] that, per frame, runs ML Kit face
 * detection and luminance analysis, folds in the latest tilt reading, asks
 * the [GuidanceEngine] for coaching tips, and delivers an [AnalysisResult].
 */
class FrameAnalyzer(
    private val tiltSensor: TiltSensor,
    private val engine: GuidanceEngine = GuidanceEngine(),
    private val onResult: (AnalysisResult) -> Unit,
) : ImageAnalysis.Analyzer {

    /** Updated by the activity whenever the camera lens is switched. */
    @Volatile var isFrontFacing: Boolean = false

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.1f)
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val swap = rotation == 90 || rotation == 270
        val uprightW = if (swap) mediaImage.height else mediaImage.width
        val uprightH = if (swap) mediaImage.width else mediaImage.height

        // Luminance is read synchronously from the buffer before detection.
        val luma = LuminanceAnalyzer.analyze(imageProxy)

        val input = InputImage.fromMediaImage(mediaImage, rotation)
        detector.process(input)
            .addOnSuccessListener { faces ->
                val largest = faces.maxByOrNull {
                    it.boundingBox.width() * it.boundingBox.height()
                }
                val subject = largest?.let {
                    val b = it.boundingBox
                    SubjectBox(
                        left = (b.left.toFloat() / uprightW).coerceIn(0f, 1f),
                        top = (b.top.toFloat() / uprightH).coerceIn(0f, 1f),
                        right = (b.right.toFloat() / uprightW).coerceIn(0f, 1f),
                        bottom = (b.bottom.toFloat() / uprightH).coerceIn(0f, 1f),
                    )
                }

                val signals = FrameSignals(
                    subject = subject,
                    meanLuma = luma.meanLuma,
                    centerLuma = luma.centerLuma,
                    surroundLuma = luma.surroundLuma,
                    rollDegrees = tiltSensor.rollDegrees,
                    pitchDegrees = tiltSensor.pitchDegrees,
                    hasTilt = tiltSensor.available,
                )

                onResult(
                    AnalysisResult(
                        guidance = engine.analyze(signals),
                        signals = signals,
                        uprightWidth = uprightW,
                        uprightHeight = uprightH,
                        isFrontFacing = isFrontFacing,
                    )
                )
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() = detector.close()
}
