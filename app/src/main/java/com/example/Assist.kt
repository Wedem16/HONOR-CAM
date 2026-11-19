package com.example

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class AssistData(
    val histogram: IntArray = IntArray(64),
    val overlayBitmap: Bitmap? = null
)

class AssistAnalyzer(
    private val isHistogramEnabled: () -> Boolean,
    private val isZebraEnabled: () -> Boolean,
    private val isPeakingEnabled: () -> Boolean,
    private val onResult: (AssistData) -> Unit
) : ImageAnalysis.Analyzer {

    private var frameIndex = 0

    override fun analyze(image: ImageProxy) {
        try {
            val histOn = isHistogramEnabled()
            val zebraOn = isZebraEnabled()
            val peakOn = isPeakingEnabled()

            if (!histOn && !zebraOn && !peakOn) {
                return
            }

            frameIndex++
            if (frameIndex % 3 != 0) {
                return
            }

            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            val imgWidth = image.width
            val imgHeight = image.height
            val rawCrop: Rect = image.cropRect
            val cropLeft = rawCrop.left.coerceIn(0, imgWidth - 1)
            val cropTop = rawCrop.top.coerceIn(0, imgHeight - 1)
            val cropRight = rawCrop.right.coerceIn(cropLeft + 1, imgWidth)
            val cropBottom = rawCrop.bottom.coerceIn(cropTop + 1, imgHeight)

            val cropW = cropRight - cropLeft
            val cropH = cropBottom - cropTop
            if (cropW <= 4 || cropH <= 4) return

            val rotation = ((image.imageInfo.rotationDegrees % 360) + 360) % 360

            // Downsample factor to keep overlay generation fast & crisp (~160-240px grid)
            val step = max(2, min(cropW, cropH) / 160)
            val gridW = cropW / step
            val gridH = cropH / step
            if (gridW <= 2 || gridH <= 2) return

            val lumaGrid = IntArray(gridW * gridH)
            val histogram = IntArray(64)
            val limit = buffer.limit()

            for (gy in 0 until gridH) {
                val srcY = cropTop + gy * step
                val rowOffset = srcY * rowStride
                val gridRowOffset = gy * gridW
                for (gx in 0 until gridW) {
                    val srcX = cropLeft + gx * step
                    val idx = rowOffset + srcX * pixelStride
                    val luma = if (idx in 0 until limit) {
                        buffer.get(idx).toInt() and 0xFF
                    } else {
                        0
                    }
                    lumaGrid[gridRowOffset + gx] = luma
                    if (histOn) {
                        val bin = (luma shr 2).coerceIn(0, 63)
                        histogram[bin]++
                    }
                }
            }

            var overlay: Bitmap? = null
            if (zebraOn || peakOn) {
                val outW = if (rotation == 90 || rotation == 270) gridH else gridW
                val outH = if (rotation == 90 || rotation == 270) gridW else gridH
                val pixels = IntArray(outW * outH)
                val stripePhase = (frameIndex / 3) % 6
                val zebraColorA = Color.argb(190, 255, 168, 0) // HONOR Orange stripe
                val zebraColorB = Color.argb(180, 20, 20, 20)
                val peakingColor = Color.argb(225, 0, 230, 118) // Crisp green peaking edge

                for (gy in 1 until gridH - 1) {
                    val rowOff = gy * gridW
                    for (gx in 1 until gridW - 1) {
                        val luma = lumaGrid[rowOff + gx]
                        var outColor = Color.TRANSPARENT

                        if (zebraOn && luma >= 240) {
                            val stripe = ((gx + gy + stripePhase) % 4) < 2
                            outColor = if (stripe) zebraColorA else zebraColorB
                        }

                        if (peakOn) {
                            // Sobel-like luma gradient magnitude
                            val left = lumaGrid[rowOff + gx - 1]
                            val right = lumaGrid[rowOff + gx + 1]
                            val up = lumaGrid[(gy - 1) * gridW + gx]
                            val down = lumaGrid[(gy + 1) * gridW + gx]
                            val grad = abs(right - left) + abs(down - up)
                            if (grad >= 44) {
                                outColor = peakingColor
                            }
                        }

                        if (outColor != Color.TRANSPARENT) {
                            val (rx, ry) = when (rotation) {
                                90 -> (gridH - 1 - gy) to gx
                                180 -> (gridW - 1 - gx) to (gridH - 1 - gy)
                                270 -> gy to (gridW - 1 - gx)
                                else -> gx to gy
                            }
                            if (rx in 0 until outW && ry in 0 until outH) {
                                pixels[ry * outW + rx] = outColor
                            }
                        }
                    }
                }
                overlay = Bitmap.createBitmap(pixels, outW, outH, Bitmap.Config.ARGB_8888)
            }

            onResult(
                AssistData(
                    histogram = if (histOn) histogram else IntArray(0),
                    overlayBitmap = overlay
                )
            )
        } catch (_: Throwable) {
            // Ignore transient plane read issues during camera rebind
        } finally {
            image.close()
        }
    }
}
