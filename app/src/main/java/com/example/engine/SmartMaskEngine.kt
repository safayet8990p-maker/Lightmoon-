package com.example.engine

import com.example.model.MaskLayer
import com.example.model.MaskType
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object SmartMaskEngine {

    /**
     * Generates a per-pixel mask alpha array [0f..1f] for the given working image pixels.
     * Runs 100% on-device with zero network dependencies.
     */
    fun computeMaskWeights(
        pixels: IntArray,
        width: Int,
        height: Int,
        mask: MaskLayer
    ): FloatArray {
        val weights = FloatArray(width * height)
        if (width <= 0 || height <= 0) return weights

        when (mask.type) {
            MaskType.LINEAR_GRADIENT -> computeLinearGradient(weights, width, height, mask)
            MaskType.RADIAL_GRADIENT -> computeRadialGradient(weights, width, height, mask)
            MaskType.BRUSH -> computeBrushMask(weights, width, height, mask)
            MaskType.SMART_LUMINANCE -> computeLuminanceMask(pixels, weights, mask)
            MaskType.SMART_COLOR -> computeColorMask(pixels, weights, mask)
            MaskType.SMART_SKY -> computeSmartSkyMask(pixels, width, height, weights, mask)
            MaskType.SMART_SUBJECT -> computeSmartSubjectMask(pixels, width, height, weights, mask)
            MaskType.SMART_BACKGROUND -> {
                computeSmartSubjectMask(pixels, width, height, weights, mask)
                for (i in weights.indices) {
                    weights[i] = 1f - weights[i]
                }
            }
        }

        val opacity = mask.opacity.coerceIn(0f, 1f)
        if (mask.inverted || opacity < 0.999f) {
            for (i in weights.indices) {
                var w = weights[i].coerceIn(0f, 1f)
                if (mask.inverted) w = 1f - w
                weights[i] = w * opacity
            }
        }
        return weights
    }

    private fun computeLinearGradient(
        weights: FloatArray,
        width: Int,
        height: Int,
        mask: MaskLayer
    ) {
        val sx = mask.startX
        val sy = mask.startY
        val dx = mask.endX - sx
        val dy = mask.endY - sy
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-5f) return

        val invW = 1f / max(1, width - 1)
        val invH = 1f / max(1, height - 1)
        for (y in 0 until height) {
            val ny = y * invH
            val rowOffset = y * width
            for (x in 0 until width) {
                val nx = x * invW
                val proj = ((nx - sx) * dx + (ny - sy) * dy) / lenSq
                val t = (1f - proj).coerceIn(0f, 1f)
                // Smoothstep falloff
                weights[rowOffset + x] = t * t * (3f - 2f * t)
            }
        }
    }

    private fun computeRadialGradient(
        weights: FloatArray,
        width: Int,
        height: Int,
        mask: MaskLayer
    ) {
        val cx = mask.centerX
        val cy = mask.centerY
        val rx = max(0.02f, mask.radiusX)
        val ry = max(0.02f, mask.radiusY)
        val feather = mask.feather.coerceIn(0.05f, 1f)
        val innerEdge = (1f - feather).coerceIn(0f, 0.95f)

        val invW = 1f / max(1, width - 1)
        val invH = 1f / max(1, height - 1)
        for (y in 0 until height) {
            val dy = (y * invH - cy) / ry
            val dy2 = dy * dy
            val rowOffset = y * width
            for (x in 0 until width) {
                val dx = (x * invW - cx) / rx
                val dist = sqrt(dx * dx + dy2)
                val w = when {
                    dist <= innerEdge -> 1f
                    dist >= 1f -> 0f
                    else -> {
                        val t = 1f - (dist - innerEdge) / max(0.001f, 1f - innerEdge)
                        t * t * (3f - 2f * t)
                    }
                }
                weights[rowOffset + x] = w
            }
        }
    }

    private fun computeBrushMask(
        weights: FloatArray,
        width: Int,
        height: Int,
        mask: MaskLayer
    ) {
        if (mask.brushStrokes.isEmpty()) return
        val minDim = min(width, height).toFloat()
        for (stroke in mask.brushStrokes) {
            val radiusPx = max(2f, stroke.radiusNorm * minDim)
            val radiusSq = radiusPx * radiusPx
            val feather = stroke.feather.coerceIn(0.1f, 1f)
            val innerR = radiusPx * (1f - feather)
            val count = min(stroke.pointsX.size, stroke.pointsY.size)
            for (p in 0 until count) {
                val px = stroke.pointsX[p] * (width - 1)
                val py = stroke.pointsY[p] * (height - 1)
                val x0 = max(0, (px - radiusPx).toInt())
                val x1 = min(width - 1, (px + radiusPx).toInt())
                val y0 = max(0, (py - radiusPx).toInt())
                val y1 = min(height - 1, (py + radiusPx).toInt())

                for (y in y0..y1) {
                    val dy = y - py
                    val dy2 = dy * dy
                    val rowOffset = y * width
                    for (x in x0..x1) {
                        val dx = x - px
                        val d2 = dx * dx + dy2
                        if (d2 < radiusSq) {
                            val d = sqrt(d2)
                            val alpha = if (d <= innerR) {
                                stroke.flow
                            } else {
                                val t = 1f - (d - innerR) / max(0.001f, radiusPx - innerR)
                                t * t * (3f - 2f * t) * stroke.flow
                            }
                            val idx = rowOffset + x
                            weights[idx] = if (stroke.erase) {
                                max(0f, weights[idx] - alpha)
                            } else {
                                min(1f, max(weights[idx], alpha))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun computeLuminanceMask(
        pixels: IntArray,
        weights: FloatArray,
        mask: MaskLayer
    ) {
        val center = mask.rangeCenter.coerceIn(0f, 1f)
        val halfWidth = max(0.04f, mask.rangeWidth.coerceIn(0.04f, 0.8f))
        val sigmaSq = 2f * halfWidth * halfWidth
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val diff = luma - center
            weights[i] = exp(-(diff * diff) / sigmaSq)
        }
    }

    private fun computeColorMask(
        pixels: IntArray,
        weights: FloatArray,
        mask: MaskLayer
    ) {
        val targetHue = mask.rangeCenter.coerceIn(0f, 1f) * 360f
        val tolerance = max(12f, mask.rangeWidth.coerceIn(0.05f, 0.6f) * 180f)
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val sat = hsv[1]
            if (sat < 0.08f) {
                weights[i] = 0f
                continue
            }
            var dHue = abs(hsv[0] - targetHue)
            if (dHue > 180f) dHue = 360f - dHue
            val hueMatch = (1f - dHue / tolerance).coerceIn(0f, 1f)
            val satWeight = ((sat - 0.08f) / 0.25f).coerceIn(0f, 1f)
            weights[i] = hueMatch * hueMatch * satWeight
        }
    }

    /**
     * On-device smart sky mask: evaluates upper-frame probability, sky chromaticity (blue/cyan/warm sunset),
     * higher luminance, and low local texture variance with smooth horizon feathering.
     */
    private fun computeSmartSkyMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        weights: FloatArray,
        mask: MaskLayer
    ) {
        val featherBoost = mask.feather.coerceIn(0.1f, 1f)
        for (y in 0 until height) {
            val ny = y.toFloat() / max(1, height - 1)
            val verticalPrior = (1f - (ny / 0.72f).pow(1.6f)).coerceIn(0f, 1f)
            if (verticalPrior <= 0.001f) continue
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x
                val p = pixels[idx]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val blueSkyScore = ((b - g * 0.55f - r * 0.35f) * 2.4f + 0.45f).coerceIn(0f, 1f)
                val brightSkyScore = ((luma - 0.32f) / 0.45f).coerceIn(0f, 1f)
                // Edge penalty so sharp buildings/trees in upper frame drop out cleanly
                val edgePenalty = if (x > 0 && y > 0) {
                    val left = pixels[idx - 1]
                    val up = pixels[idx - width]
                    val dl = abs(((left shr 8) and 0xFF) - ((p shr 8) and 0xFF)) / 255f
                    val du = abs(((up shr 8) and 0xFF) - ((p shr 8) and 0xFF)) / 255f
                    (1f - (dl + du) * 3.2f).coerceIn(0.15f, 1f)
                } else 1f
                val raw = verticalPrior * max(blueSkyScore * 0.85f, brightSkyScore * 0.9f) * edgePenalty
                weights[idx] = smoothThreshold(raw, 0.28f, 0.22f + 0.2f * featherBoost)
            }
        }
        boxBlurWeights(weights, width, height, radius = max(2, min(width, height) / 48))
    }

    /**
     * On-device smart subject mask: combines color-distance from image border palette,
     * local edge/detail saliency, and soft center-bias prior, refined by spatial smoothing.
     */
    private fun computeSmartSubjectMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        weights: FloatArray,
        mask: MaskLayer
    ) {
        // Sample border pixels to estimate background color distribution
        var bgR = 0f
        var bgG = 0f
        var bgB = 0f
        var count = 0
        val stepX = max(1, width / 24)
        val stepY = max(1, height / 24)
        for (x in 0 until width step stepX) {
            val top = pixels[x]
            val bot = pixels[(height - 1) * width + x]
            bgR += ((top shr 16) and 0xFF) + ((bot shr 16) and 0xFF)
            bgG += ((top shr 8) and 0xFF) + ((bot shr 8) and 0xFF)
            bgB += (top and 0xFF) + (bot and 0xFF)
            count += 2
        }
        for (y in 0 until height step stepY) {
            val left = pixels[y * width]
            val right = pixels[y * width + width - 1]
            bgR += ((left shr 16) and 0xFF) + ((right shr 16) and 0xFF)
            bgG += ((left shr 8) and 0xFF) + ((right shr 8) and 0xFF)
            bgB += (left and 0xFF) + (right and 0xFF)
            count += 2
        }
        bgR /= max(1, count) * 255f
        bgG /= max(1, count) * 255f
        bgB /= max(1, count) * 255f

        val invW = 1f / max(1, width - 1)
        val invH = 1f / max(1, height - 1)

        for (y in 0 until height) {
            val ny = y * invH - 0.5f
            val rowOffset = y * width
            for (x in 0 until width) {
                val nx = x * invW - 0.5f
                val idx = rowOffset + x
                val p = pixels[idx]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f

                val colorDist = hypot(r - bgR, hypot(g - bgG, b - bgB))
                val centerPrior = exp(-(nx * nx / 0.14f + ny * ny / 0.16f))
                val edgeEnergy = if (x > 0 && y > 0) {
                    val left = pixels[idx - 1]
                    val up = pixels[idx - width]
                    val dr = abs(((left shr 16) and 0xFF) - ((p shr 16) and 0xFF)) / 255f
                    val dg = abs(((up shr 8) and 0xFF) - ((p shr 8) and 0xFF)) / 255f
                    min(1f, (dr + dg) * 2.5f)
                } else 0f

                val score = colorDist * 0.65f + centerPrior * 0.58f + edgeEnergy * 0.25f
                weights[idx] = smoothThreshold(score, 0.42f, 0.28f + 0.15f * mask.feather)
            }
        }
        boxBlurWeights(weights, width, height, radius = max(3, min(width, height) / 36))
    }

    private fun smoothThreshold(value: Float, center: Float, width: Float): Float {
        val low = center - width * 0.5f
        val high = center + width * 0.5f
        val t = ((value - low) / max(0.001f, high - low)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun boxBlurWeights(weights: FloatArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0) return
        val temp = FloatArray(weights.size)
        val diam = radius * 2 + 1
        // Horizontal pass
        for (y in 0 until height) {
            val row = y * width
            var sum = 0f
            for (kx in -radius..radius) {
                sum += weights[row + kx.coerceIn(0, width - 1)]
            }
            for (x in 0 until width) {
                temp[row + x] = sum / diam
                val removeX = (x - radius).coerceIn(0, width - 1)
                val addX = (x + radius + 1).coerceIn(0, width - 1)
                sum += weights[row + addX] - weights[row + removeX]
            }
        }
        // Vertical pass
        for (x in 0 until width) {
            var sum = 0f
            for (ky in -radius..radius) {
                sum += temp[ky.coerceIn(0, height - 1) * width + x]
            }
            for (y in 0 until height) {
                weights[y * width + x] = (sum / diam).coerceIn(0f, 1f)
                val removeY = (y - radius).coerceIn(0, height - 1)
                val addY = (y + radius + 1).coerceIn(0, height - 1)
                sum += temp[addY * width + x] - temp[removeY * width + x]
            }
        }
    }
}
