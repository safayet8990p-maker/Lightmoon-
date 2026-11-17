package com.example.engine

import android.graphics.Bitmap
import com.example.model.DevelopSettings
import com.example.model.WhiteBalancePreset
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

object AutoToneEngine {

    /**
     * Analyzes image luminance percentiles and gray-world illuminant balance on-device
     * to compute scene-referred Auto-Tone and Auto-WB starting points.
     */
    fun computeAutoTone(source: Bitmap, current: DevelopSettings): DevelopSettings {
        val sampleW = 160
        val sampleH = max(1, (sampleW * source.height.toFloat() / max(1, source.width)).roundToInt())
        val scaled = Bitmap.createScaledBitmap(source, sampleW, sampleH, true)
        val pixels = IntArray(sampleW * sampleH)
        scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        if (scaled != source) scaled.recycle()

        val lumaHist = IntArray(256)
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var shadowSum = 0.0
        var shadowCount = 0
        var highlightSum = 0.0
        var highlightCount = 0

        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sumR += r
            sumG += g
            sumB += b
            val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b).roundToInt().coerceIn(0, 255)
            lumaHist[luma]++
            if (luma < 64) {
                shadowSum += luma / 255.0
                shadowCount++
            } else if (luma > 192) {
                highlightSum += luma / 255.0
                highlightCount++
            }
        }

        val total = pixels.size.coerceAtLeast(1)
        fun percentile(targetFrac: Float): Int {
            val target = (total * targetFrac).toInt()
            var acc = 0
            for (i in 0..255) {
                acc += lumaHist[i]
                if (acc >= target) return i
            }
            return 255
        }

        val p02 = percentile(0.02f) / 255f
        val p50 = percentile(0.50f) / 255f
        val p98 = percentile(0.98f) / 255f

        // Target mid-gray around 0.44 for filmic punch
        val safeMid = p50.coerceIn(0.08f, 0.92f)
        val exposureEv = (ln(0.44f / safeMid) / ln(2f)).coerceIn(-1.6f, 1.8f)

        val dynamicSpan = (p98 - p02).coerceIn(0.15f, 1f)
        val contrast = ((0.82f - dynamicSpan) * 55f).coerceIn(-15f, 32f)
        val highlights = if (p98 > 0.90f) -((p98 - 0.88f) * 320f).coerceIn(10f, 55f) else 0f
        val shadows = if (p02 < 0.08f && shadowCount > total * 0.18f) {
            ((0.12f - p02) * 360f).coerceIn(10f, 48f)
        } else 12f
        val whites = if (p98 < 0.86f) ((0.90f - p98) * 140f).coerceIn(0f, 28f) else -8f
        val blacks = if (p02 > 0.07f) -((p02 - 0.03f) * 180f).coerceIn(5f, 30f) else 0f

        // Gray-world illuminant correction
        val avgR = (sumR / total).coerceAtLeast(1.0)
        val avgG = (sumG / total).coerceAtLeast(1.0)
        val avgB = (sumB / total).coerceAtLeast(1.0)
        val wbBias = ((avgB - avgR) / 255.0 * 2800.0).toInt()
        val autoKelvin = (5500 + wbBias).coerceIn(3600, 7600)
        val greenBalance = ((avgR + avgB) * 0.5 - avgG) / 255.0 * 80.0
        val autoTint = greenBalance.toFloat().coerceIn(-24f, 24f)

        return current.copy(
            exposure = (exposureEv * 100f).roundToInt() / 100f,
            contrast = contrast.roundToInt().toFloat(),
            highlights = highlights.roundToInt().toFloat(),
            shadows = shadows.roundToInt().toFloat(),
            whites = whites.roundToInt().toFloat(),
            blacks = blacks.roundToInt().toFloat(),
            wbPreset = WhiteBalancePreset.CUSTOM,
            temperatureKelvin = autoKelvin,
            tint = autoTint.roundToInt().toFloat(),
            vibrance = max(current.vibrance, 16f)
        )
    }
}
