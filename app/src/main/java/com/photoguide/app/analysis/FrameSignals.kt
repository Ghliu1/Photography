package com.photoguide.app.analysis

/**
 * A normalized snapshot of everything we know about the current camera frame.
 *
 * All coordinates are expressed as fractions in the range [0, 1] of the
 * *upright* (rotation-corrected, un-mirrored) image, where (0,0) is the
 * top-left corner. Keeping this struct free of Android types lets the
 * [GuidanceEngine] be plain Kotlin and easy to unit-test.
 */
data class FrameSignals(
    /** The detected subject (largest face), or null if none was found. */
    val subject: SubjectBox? = null,
    /** Mean luminance of the whole frame, 0 (black) .. 255 (white). */
    val meanLuma: Float = 128f,
    /** Mean luminance of the central region of the frame. */
    val centerLuma: Float = 128f,
    /** Mean luminance of the outer ring surrounding the center. */
    val surroundLuma: Float = 128f,
    /** Roll angle of the device in degrees; 0 means held perfectly upright. */
    val rollDegrees: Float = 0f,
    /** Pitch (forward/back tilt) in degrees; 0 means vertical. */
    val pitchDegrees: Float = 0f,
    /** Whether tilt data from the motion sensor is available. */
    val hasTilt: Boolean = false,
) {
    val hasSubject: Boolean get() = subject != null
}

/** Normalized bounding box of a subject in [0,1] upright-image space. */
data class SubjectBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    /** True when any edge of the box runs off the frame (subject cut off). */
    fun isCutOff(margin: Float = 0.01f): Boolean =
        left < margin || top < margin || right > 1f - margin || bottom > 1f - margin
}
