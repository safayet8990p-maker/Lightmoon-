package com.example.engine

import android.graphics.Bitmap
import com.example.model.ScopeData
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ScopeAnalyzer {

    fun analyze(bitmap: Bitmap): ScopeData {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return ScopeData()

        // Downsample fast for real-time 60fps scope updates
        val sampleW = min(w, 192)
        val sampleH = max(1, (sampleW * h.toFloat() / w).roundToInt())
        val sampled = if (w > sampleW) Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false) else bitmap
        val pixels = IntArray(sampleW * sampleH)
        sampled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        if (sampled != bitmap) sampled.recycle()

        val redBins = IntArray(256)
        val greenBins = IntArray(256)
        val blueBins = IntArray(256)
        val lumaBins = IntArray(256)

        val paradeCols = 48
        val paradeRows = 32
        val paradeRed = FloatArray(paradeCols * paradeRows)
        val paradeGreen = FloatArray(paradeCols * paradeRows)
        val paradeBlue = FloatArray(paradeCols * paradeRows)
        val waveform = FloatArray(paradeCols * paradeRows)

        var shadowClipped = 0
        var highlightClipped = 0

        for (y in 0 until sampleH) {
            val rowOff = y * sampleW
            for (x in 0 until sampleW) {
                val p = pixels[rowOff + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt().coerceIn(0, 255)

                redBins[r]++
                greenBins[g]++
                blueBins[b]++
                lumaBins[luma]++

                if (r <= 2 && g <= 2 && b <= 2) shadowClipped++
                if (r >= 253 && g >= 253 && b >= 253) highlightClipped++

                val col = (x * paradeCols / sampleW).coerceIn(0, paradeCols - 1)
                val rBin = ((255 - r) * paradeRows / 256).coerceIn(0, paradeRows - 1)
                val gBin = ((255 - g) * paradeRows / 256).coerceIn(0, paradeRows - 1)
                val bBin = ((255 - b) * paradeRows / 256).coerceIn(0, paradeRows - 1)
                val lBin = ((255 - luma) * paradeRows / 256).coerceIn(0, paradeRows - 1)

                paradeRed[rBin * paradeCols + col] += 1f
                paradeGreen[gBin * paradeCols + col] += 1f
                paradeBlue[bBin * paradeCols + col] += 1f
                waveform[lBin * paradeCols + col] += 1f
            }
        }

        // Normalize parade and waveform columns for rendering
        val normScale = max(1f, sampleH / 10f)
        for (i in waveform.indices) {
            paradeRed[i] = (paradeRed[i] / normScale).coerceIn(0f, 1f)
            paradeGreen[i] = (paradeGreen[i] / normScale).coerceIn(0f, 1f)
            paradeBlue[i] = (paradeBlue[i] / normScale).coerceIn(0f, 1f)
            waveform[i] = (waveform[i] / normScale).coerceIn(0f, 1f)
        }

        val total = pixels.size.coerceAtLeast(1)
        return ScopeData(
            redBins = redBins,
            greenBins = greenBins,
            blueBins = blueBins,
            lumaBins = lumaBins,
            paradeWidth = paradeCols,
            paradeRed = paradeRed,
            paradeGreen = paradeGreen,
            paradeBlue = paradeBlue,
            waveform = waveform,
            shadowClippedPercent = shadowClipped * 100f / total,
            highlightClippedPercent = highlightClipped * 100f / total
        )
    }
}
