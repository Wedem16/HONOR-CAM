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
    val histogram: IntArray = IntArray(0),
    val overlayBitmap: Bitmap? = null
)

class AssistAnalyzer(
    private val isHistogramEnabled: () -> Boolean,
    private val isZebraEnabled: () -> Boolean,
    private val isPeakingEnabled: () -> Boolean,
    private val onResult: (AssistData) -> Unit
) : ImageAnalysis.Analyzer {

    private var frameIndex = 0
    private var reusableBuffer = ByteArray(0)
    private var wasEmptySent = false

    override fun analyze(image: ImageProxy) {
        try {
            val histOn = isHistogramEnabled()
            val zebraOn = isZebraEnabled()
            val peakOn = isPeakingEnabled()

            if (!histOn && !zebraOn && !peakOn) {
                if (!wasEmptySent) {
                    wasEmptySent = true
                    onResult(AssistData())
                }
                return
            }
            wasEmptySent = false

            frameIndex++
            if (frameIndex % 3 != 0) {
                return
            }

            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            buffer.rewind()
            val limit = buffer.remaining()
            if (limit <= 0) return
            if (reusableBuffer.size < limit) {
                reusableBuffer = ByteArray(limit)
            }
            buffer.get(reusableBuffer, 0, limit)

            val imgWidth = image.width
            val imgHeight = image.height
            val rawCrop: Rect = image.cropRect
            val cropLeft = rawCrop.left.coerceIn(0, (imgWidth - 1).coerceAtLeast(0))
            val cropTop = rawCrop.top.coerceIn(0, (imgHeight - 1).coerceAtLeast(0))
            val cropRight = rawCrop.right.coerceIn((cropLeft + 1).coerceAtMost(imgWidth), imgWidth)
            val cropBottom = rawCrop.bottom.coerceIn((cropTop + 1).coerceAtMost(imgHeight), imgHeight)

            val cropW = cropRight - cropLeft
            val cropH = cropBottom - cropTop
            if (cropW <= 4 || cropH <= 4) return

            val rotation = ((image.imageInfo.rotationDegrees % 360) + 360) % 360

            val step = max(2, min(cropW, cropH) / 160)
            val gridW = cropW / step
            val gridH = cropH / step
            if (gridW <= 2 || gridH <= 2) return

            val lumaGrid = IntArray(gridW * gridH)
            val histogram = if (histOn) IntArray(64) else IntArray(0)

            for (gy in 0 until gridH) {
                val srcY = cropTop + gy * step
                val rowOffset = srcY * rowStride
                val gridRowOffset = gy * gridW
                for (gx in 0 until gridW) {
                    val srcX = cropLeft + gx * step
                    val idx = rowOffset + srcX * pixelStride
                    val luma = if (idx in 0 until limit) {
                        reusableBuffer[idx].toInt() and 0xFF
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
                val zebraColorA = Color.argb(195, 255, 168, 0)
                val zebraColorB = Color.argb(180, 20, 20, 20)
                val peakingColor = Color.argb(230, 0, 230, 118)

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
                            val left = lumaGrid[rowOff + gx - 1]
                            val right = lumaGrid[rowOff + gx + 1]
                            val up = lumaGrid[(gy - 1) * gridW + gx]
                            val down = lumaGrid[(gy + 1) * gridW + gx]
                            val grad = abs(right - left) + abs(down - up)
                            if (grad >= 42) {
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
                    histogram = histogram,
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
