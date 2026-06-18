package com.photoguide.app.analysis

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Computes coarse luminance statistics straight from the Y (luma) plane of a
 * YUV_420_888 [ImageProxy]. No bitmap conversion is needed, so this is cheap
 * enough to run on every analysed frame.
 *
 * We report three numbers, all rotation-tolerant:
 *  - [meanLuma]    : overall exposure.
 *  - [centerLuma]  : luminance of the central region (where subjects sit).
 *  - [surroundLuma]: luminance of the outer ring (the background).
 *
 * Comparing center vs surround is enough to flag a backlit subject without
 * needing to map the face box back into raw sensor coordinates.
 */
object LuminanceAnalyzer {

    data class Result(val meanLuma: Float, val centerLuma: Float, val surroundLuma: Float)

    // Sample stride: only inspect every Nth pixel for speed.
    private const val STEP = 7

    fun analyze(image: ImageProxy): Result {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        // Central third of the frame.
        val cx0 = width / 3
        val cx1 = width * 2 / 3
        val cy0 = height / 3
        val cy1 = height * 2 / 3

        var total = 0L; var totalCount = 0
        var center = 0L; var centerCount = 0
        var surround = 0L; var surroundCount = 0

        var y = 0
        while (y < height) {
            val rowStart = y * rowStride
            var x = 0
            while (x < width) {
                val luma = buffer.get(rowStart + x * pixelStride).toInt() and 0xFF
                total += luma; totalCount++
                if (x in cx0 until cx1 && y in cy0 until cy1) {
                    center += luma; centerCount++
                } else {
                    surround += luma; surroundCount++
                }
                x += STEP
            }
            y += STEP
        }

        fun avg(sum: Long, count: Int) = if (count > 0) sum.toFloat() / count else 128f
        return Result(
            meanLuma = avg(total, totalCount),
            centerLuma = avg(center, centerCount),
            surroundLuma = avg(surround, surroundCount),
        )
    }
}
