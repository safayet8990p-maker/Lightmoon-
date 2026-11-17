package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.example.model.ColorWheelTone
import com.example.model.CurvePoint
import com.example.model.DevelopSettings
import com.example.model.HslColorBand
import com.example.model.WhiteBalancePreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

object LightmoonPipeline {

    /**
     * Renders a non-destructive preview or full-res export bitmap using Lightmoon's
     * scene-referred V3 pipeline.
     */
    fun render(
        source: Bitmap,
        settings: DevelopSettings,
        showClippingOverlay: Boolean = false,
        activeMaskIdOverlay: String? = null,
        skipCrop: Boolean = false
    ): Bitmap {
        val geoBitmap = applyGeometry(source, settings, skipCrop = skipCrop)
        val width = geoBitmap.width
        val height = geoBitmap.height
        val count = width * height
        val pixels = IntArray(count)
        geoBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Ensure we don't mutate the caller's source bitmap if applyGeometry returned source directly
        val outBitmap = if (geoBitmap === source) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } else {
            geoBitmap
        }

        // Fast short-circuit if completely unedited and no overlays requested
        if (settings.isDefault() && !showClippingOverlay && activeMaskIdOverlay == null) {
            outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return outBitmap
        }

        // 1. Precompute White Balance gains (linear relative to 5500K neutral)
        val tempDelta = (settings.temperatureKelvin - 5500) / 3500f
        val tintDelta = settings.tint / 100f
        val wbR = (1f + tempDelta * 0.34f + tintDelta * 0.14f).coerceIn(0.45f, 1.85f)
        val wbG = (1f - tintDelta * 0.22f).coerceIn(0.55f, 1.55f)
        val wbB = (1f - tempDelta * 0.36f + tintDelta * 0.14f).coerceIn(0.45f, 1.85f)

        // 2. Precompute 256-entry Tone & Curve LUTs
        val expMult = 2f.pow(settings.exposure.coerceIn(-5f, 5f))
        val contrastK = (settings.contrast / 100f) * 1.35f
        val highlightsK = settings.highlights / 100f
        val shadowsK = settings.shadows / 100f
        val whitesK = settings.whites / 100f
        val blacksK = settings.blacks / 100f

        val pShadows = settings.parametricShadows / 100f
        val pDarks = settings.parametricDarks / 100f
        val pLights = settings.parametricLights / 100f
        val pHighlights = settings.parametricHighlights / 100f

        val rgbCurveLut = buildCurveLut(settings.rgbCurve)
        val redCurveLut = buildCurveLut(settings.redCurve)
        val greenCurveLut = buildCurveLut(settings.greenCurve)
        val blueCurveLut = buildCurveLut(settings.blueCurve)

        // 3. Precompute 3-Way Color Grading RGB tint vectors
        val shadowTint = wheelToRgbOffset(settings.colorGrading.shadows)
        val midTint = wheelToRgbOffset(settings.colorGrading.midtones)
        val highTint = wheelToRgbOffset(settings.colorGrading.highlights)
        val hasGrading = settings.colorGrading.shadows.saturation > 0.5f ||
            settings.colorGrading.midtones.saturation > 0.5f ||
            settings.colorGrading.highlights.saturation > 0.5f ||
            abs(settings.colorGrading.shadows.luminance) > 0.5f ||
            abs(settings.colorGrading.midtones.luminance) > 0.5f ||
            abs(settings.colorGrading.highlights.luminance) > 0.5f
        val gradeBalance = (settings.colorGrading.balance / 100f) * 0.25f
        val gradeBlending = (settings.colorGrading.blending / 100f).coerceIn(0.05f, 1f)

        // 4. Precompute 8-band HSL lookup
        val hasHsl = settings.hslBands.any {
            abs(it.hueShift) > 0.2f || abs(it.saturationShift) > 0.2f || abs(it.luminanceShift) > 0.2f
        }
        val hueTable = FloatArray(360)
        val satTable = FloatArray(360)
        val lumTable = FloatArray(360)
        if (hasHsl) {
            for (deg in 0 until 360) {
                var dh = 0f
                var ds = 0f
                var dl = 0f
                for (bandAdj in settings.hslBands) {
                    val weight = bandWeight(deg.toFloat(), bandAdj.band.centerHueDeg)
                    if (weight > 0.001f) {
                        dh += weight * bandAdj.hueShift * 0.35f
                        ds += weight * bandAdj.saturationShift / 100f
                        dl += weight * bandAdj.luminanceShift / 100f
                    }
                }
                hueTable[deg] = dh
                satTable[deg] = ds
                lumTable[deg] = dl
            }
        }

        // 5. Optional spatial buffer for Clarity / Texture / LocalToneMapping / Dehaze / Sharpen / Denoise
        val needsSpatial = abs(settings.localToneMapping) > 0.5f ||
            abs(settings.clarity) > 0.5f ||
            abs(settings.texture) > 0.5f ||
            abs(settings.dehaze) > 0.5f ||
            settings.sharpenAmount > 0.5f ||
            settings.denoiseLuminance > 0.5f ||
            settings.denoiseColor > 0.5f

        val lumaBuf = if (needsSpatial) FloatArray(count) else null
        val blurBroad = if (needsSpatial) FloatArray(count) else null
        val blurFine = if (needsSpatial && (abs(settings.texture) > 0.5f || settings.sharpenAmount > 0.5f)) FloatArray(count) else null

        if (needsSpatial && lumaBuf != null && blurBroad != null) {
            for (i in 0 until count) {
                val p = pixels[i]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                lumaBuf[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b
            }
            val broadRadius = max(4, min(width, height) / 40)
            fastBoxBlur(lumaBuf, blurBroad, width, height, broadRadius)
            if (blurFine != null) {
                val fineRadius = max(1, settings.sharpenRadius.roundToInt())
                fastBoxBlur(lumaBuf, blurFine, width, height, fineRadius)
            }
        }

        val denoiseColorK = (settings.denoiseColor / 100f).coerceIn(0f, 1f)
        val colorBlurR = if (denoiseColorK > 0.01f) FloatArray(count) else null
        val colorBlurB = if (denoiseColorK > 0.01f) FloatArray(count) else null
        if (denoiseColorK > 0.01f && colorBlurR != null && colorBlurB != null) {
            val rawCr = FloatArray(count)
            val rawCb = FloatArray(count)
            for (i in 0 until count) {
                val p = pixels[i]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
                rawCr[i] = r - y
                rawCb[i] = b - y
            }
            val cRad = max(2, (denoiseColorK * 5f).roundToInt())
            fastBoxBlur(rawCr, colorBlurR, width, height, cRad)
            fastBoxBlur(rawCb, colorBlurB, width, height, cRad)
        }

        // 6. Precompute Dehaze dark-channel atmospheric floor
        val dehazeK = settings.dehaze / 100f
        val localToneK = settings.localToneMapping / 100f
        val clarityK = settings.clarity / 100f
        val textureK = settings.texture / 100f
        val sharpenK = settings.sharpenAmount / 100f
        val sharpenDetailDamp = 1f - (settings.sharpenDetail / 100f) * 0.45f
        val denoiseLumK = (settings.denoiseLuminance / 100f) * 0.65f

        val vibranceK = settings.vibrance / 100f
        val satK = 1f + settings.saturation / 100f

        // 7. Primary Per-Pixel Scene-Referred Pass
        for (i in 0 until count) {
            val argb = pixels[i]
            val a = argb ushr 24
            var r = ((argb shr 16) and 0xFF) / 255f
            var g = ((argb shr 8) and 0xFF) / 255f
            var b = (argb and 0xFF) / 255f

            // Color Denoise (blend chroma toward blurred chroma)
            if (denoiseColorK > 0.01f && colorBlurR != null && colorBlurB != null) {
                val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val targetR = y + colorBlurR[i]
                val targetB = y + colorBlurB[i]
                val targetG = (y - 0.2126f * targetR - 0.0722f * targetB) / 0.7152f
                r += (targetR - r) * denoiseColorK
                g += (targetG - g) * denoiseColorK
                b += (targetB - b) * denoiseColorK
            }

            // Linearize approximate sRGB -> scene-referred working space
            r = (r * wbR).coerceAtLeast(0f)
            g = (g * wbG).coerceAtLeast(0f)
            b = (b * wbB).coerceAtLeast(0f)

            // Exposure EV with soft filmic highlight rolloff (V3 scene-referred feel)
            if (abs(settings.exposure) > 0.005f) {
                r = applyFilmicExposure(r, expMult)
                g = applyFilmicExposure(g, expMult)
                b = applyFilmicExposure(b, expMult)
            }

            // Tone shaping (Highlights / Shadows / Whites / Blacks / Contrast / Parametric)
            r = applyToneShaping(r, contrastK, highlightsK, shadowsK, whitesK, blacksK, pShadows, pDarks, pLights, pHighlights)
            g = applyToneShaping(g, contrastK, highlightsK, shadowsK, whitesK, blacksK, pShadows, pDarks, pLights, pHighlights)
            b = applyToneShaping(b, contrastK, highlightsK, shadowsK, whitesK, blacksK, pShadows, pDarks, pLights, pHighlights)

            // Dehaze (dark-channel contrast + saturation recovery or atmospheric haze)
            if (abs(dehazeK) > 0.005f) {
                if (dehazeK > 0f) {
                    val minC = min(r, min(g, b))
                    val t = (1f - dehazeK * 0.62f * minC).coerceIn(0.28f, 1f)
                    val atmos = 0.88f
                    r = ((r - atmos) / t + atmos * (1f - dehazeK * 0.25f)).coerceIn(0f, 1f)
                    g = ((g - atmos) / t + atmos * (1f - dehazeK * 0.25f)).coerceIn(0f, 1f)
                    b = ((b - atmos) / t + atmos * (1f - dehazeK * 0.25f)).coerceIn(0f, 1f)
                } else {
                    val haze = -dehazeK * 0.45f
                    r = r + (0.90f - r) * haze
                    g = g + (0.91f - g) * haze
                    b = b + (0.94f - b) * haze
                }
            }

            // Spatial Detail: Local Tone Mapping, Clarity, Texture, Sharpening, Lum Denoise
            if (needsSpatial && lumaBuf != null && blurBroad != null) {
                val origL = lumaBuf[i]
                val broadL = blurBroad[i]
                var deltaL = 0f

                if (abs(localToneK) > 0.005f) {
                    // Edge-aware local tone compression: lift dark areas under bright neighbors, recover bright skies
                    val logDiff = broadL - origL
                    deltaL += logDiff * localToneK * 0.35f
                }
                if (abs(clarityK) > 0.005f) {
                    val midWeight = (1f - abs(origL * 2f - 1f)).coerceIn(0.15f, 1f)
                    deltaL += (origL - broadL) * clarityK * 0.85f * midWeight
                }
                if (blurFine != null) {
                    val fineDiff = origL - blurFine[i]
                    if (abs(textureK) > 0.005f) {
                        deltaL += fineDiff * textureK * 0.75f
                    }
                    if (sharpenK > 0.005f) {
                        val damped = fineDiff * (1f - min(0.75f, abs(fineDiff) * 4f * sharpenDetailDamp))
                        deltaL += damped * sharpenK * 1.6f
                    }
                }
                if (denoiseLumK > 0.005f) {
                    val smoothTarget = blurFine?.get(i) ?: broadL
                    val edgeGuard = (1f - min(1f, abs(origL - smoothTarget) * 8f))
                    deltaL += (smoothTarget - origL) * denoiseLumK * edgeGuard
                }

                if (abs(deltaL) > 0.0005f) {
                    r = (r + deltaL).coerceIn(0f, 1f)
                    g = (g + deltaL).coerceIn(0f, 1f)
                    b = (b + deltaL).coerceIn(0f, 1f)
                }
            }

            // RGB & Channel Tone Curves (LUT lookup with linear interpolation)
            r = sampleLut(rgbCurveLut, sampleLut(redCurveLut, r))
            g = sampleLut(rgbCurveLut, sampleLut(greenCurveLut, g))
            b = sampleLut(rgbCurveLut, sampleLut(blueCurveLut, b))

            // 8-Band HSL Color Mixer
            if (hasHsl) {
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val delta = maxC - minC
                if (delta > 0.003f) {
                    var hue = when (maxC) {
                        r -> 60f * (((g - b) / delta) % 6f)
                        g -> 60f * (((b - r) / delta) + 2f)
                        else -> 60f * (((r - g) / delta) + 4f)
                    }
                    if (hue < 0f) hue += 360f
                    val deg = hue.toInt().coerceIn(0, 359)
                    val hShift = hueTable[deg]
                    val sShift = satTable[deg]
                    val lShift = lumTable[deg]
                    if (abs(hShift) > 0.05f || abs(sShift) > 0.002f || abs(lShift) > 0.002f) {
                        val l = (maxC + minC) * 0.5f
                        val s = if (l > 0.5f) delta / max(0.001f, 2f - maxC - minC) else delta / max(0.001f, maxC + minC)
                        val newH = ((hue + hShift) % 360f + 360f) % 360f
                        val newS = (s * (1f + sShift)).coerceIn(0f, 1f)
                        val newL = (l + lShift * 0.25f * min(1f, s * 2.5f)).coerceIn(0f, 1f)
                        val rgbOut = hslToRgb(newH, newS, newL)
                        r = rgbOut[0]
                        g = rgbOut[1]
                        b = rgbOut[2]
                    }
                }
            }

            // Vibrance (skin-tone-protected saturation) & Global Saturation
            if (abs(vibranceK) > 0.005f || abs(satK - 1f) > 0.005f) {
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val sat = maxC - minC
                // Skin tones sit around red-orange (r > g > b) -> attenuate vibrance boost there
                val skinProtect = if (r > g && g > b) 0.55f else 1f
                val vibFactor = 1f + vibranceK * (1f - sat) * skinProtect
                val totalSat = (satK * vibFactor).coerceIn(0f, 2.5f)
                r = (luma + (r - luma) * totalSat).coerceIn(0f, 1f)
                g = (luma + (g - luma) * totalSat).coerceIn(0f, 1f)
                b = (luma + (b - luma) * totalSat).coerceIn(0f, 1f)
            }

            // 3-Way Color Grading Wheels (Shadows / Midtones / Highlights)
            if (hasGrading) {
                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b + gradeBalance).coerceIn(0f, 1f)
                val shW = ((1f - luma / (0.45f + 0.25f * gradeBlending)).coerceIn(0f, 1f)).pow(1.4f)
                val hiW = (((luma - (0.55f - 0.25f * gradeBlending)) / (0.45f + 0.25f * gradeBlending)).coerceIn(0f, 1f)).pow(1.4f)
                val midW = (1f - shW - hiW).coerceIn(0f, 1f)

                val lumShift = (shW * settings.colorGrading.shadows.luminance +
                    midW * settings.colorGrading.midtones.luminance +
                    hiW * settings.colorGrading.highlights.luminance) / 300f

                r = (r + shW * shadowTint[0] + midW * midTint[0] + hiW * highTint[0] + lumShift).coerceIn(0f, 1f)
                g = (g + shW * shadowTint[1] + midW * midTint[1] + hiW * highTint[1] + lumShift).coerceIn(0f, 1f)
                b = (b + shW * shadowTint[2] + midW * midTint[2] + hiW * highTint[2] + lumShift).coerceIn(0f, 1f)
            }

            val ir = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
            val ig = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
            val ib = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (ir shl 16) or (ig shl 8) or ib
        }

        // 8. Local Masking Layers Pass
        for (mask in settings.masks) {
            if (!mask.enabled) continue
            val adj = mask.adjustments
            val hasMaskEdits = abs(adj.exposure) > 0.01f ||
                abs(adj.contrast) > 0.5f ||
                abs(adj.highlights) > 0.5f ||
                abs(adj.shadows) > 0.5f ||
                abs(adj.temperatureShift) > 0.5f ||
                abs(adj.tint) > 0.5f ||
                abs(adj.saturation) > 0.5f ||
                abs(adj.clarity) > 0.5f
            val showOverlay = mask.id == activeMaskIdOverlay
            if (!hasMaskEdits && !showOverlay) continue

            val weights = SmartMaskEngine.computeMaskWeights(pixels, width, height, mask)
            val mExp = 2f.pow(adj.exposure.coerceIn(-4f, 4f))
            val mCon = (adj.contrast / 100f) * 1.2f
            val mHigh = adj.highlights / 100f
            val mShad = adj.shadows / 100f
            val mTemp = adj.temperatureShift / 100f
            val mTint = adj.tint / 100f
            val mSat = 1f + adj.saturation / 100f

            for (i in 0 until count) {
                val w = weights[i]
                if (w < 0.005f) continue
                val argb = pixels[i]
                val a = argb ushr 24
                var r = ((argb shr 16) and 0xFF) / 255f
                var g = ((argb shr 8) and 0xFF) / 255f
                var b = (argb and 0xFF) / 255f

                if (hasMaskEdits) {
                    var mr = r * (1f + mTemp * 0.25f + mTint * 0.1f)
                    var mg = g * (1f - mTint * 0.15f)
                    var mb = b * (1f - mTemp * 0.25f + mTint * 0.1f)

                    if (abs(adj.exposure) > 0.01f) {
                        mr = applyFilmicExposure(mr, mExp)
                        mg = applyFilmicExposure(mg, mExp)
                        mb = applyFilmicExposure(mb, mExp)
                    }
                    mr = applyToneShaping(mr, mCon, mHigh, mShad, 0f, 0f, 0f, 0f, 0f, 0f)
                    mg = applyToneShaping(mg, mCon, mHigh, mShad, 0f, 0f, 0f, 0f, 0f, 0f)
                    mb = applyToneShaping(mb, mCon, mHigh, mShad, 0f, 0f, 0f, 0f, 0f, 0f)

                    if (abs(adj.saturation) > 0.5f) {
                        val l = 0.2126f * mr + 0.7152f * mg + 0.0722f * mb
                        mr = (l + (mr - l) * mSat).coerceIn(0f, 1f)
                        mg = (l + (mg - l) * mSat).coerceIn(0f, 1f)
                        mb = (l + (mb - l) * mSat).coerceIn(0f, 1f)
                    }

                    r = r + (mr - r) * w
                    g = g + (mg - g) * w
                    b = b + (mb - b) * w
                }

                // Rubylith red overlay for active mask preview
                if (showOverlay) {
                    val overlayAlpha = w * 0.42f
                    r = r * (1f - overlayAlpha) + 0.92f * overlayAlpha
                    g = g * (1f - overlayAlpha) + 0.12f * overlayAlpha
                    b = b * (1f - overlayAlpha) + 0.28f * overlayAlpha
                }

                val ir = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
                val ig = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
                val ib = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
                pixels[i] = (a shl 24) or (ir shl 16) or (ig shl 8) or ib
            }
        }

        // 9. Post-Crop Vignette & Film Grain Pass
        val hasVignette = abs(settings.vignetteAmount) > 0.5f
        val hasGrain = settings.grainAmount > 0.5f
        if (hasVignette || hasGrain) {
            val vigK = settings.vignetteAmount / 100f
            val vigMid = (settings.vignetteMidpoint / 100f).coerceIn(0.05f, 0.95f)
            val vigRound = (settings.vignetteRoundness / 100f).coerceIn(-1f, 1f)
            val vigFeather = (settings.vignetteFeather / 100f).coerceIn(0.08f, 1f)
            val grainAmp = (settings.grainAmount / 100f) * 0.16f
            val grainScale = max(1, (settings.grainSize / 25f).roundToInt())
            val grainRough = 0.5f + (settings.grainRoughness / 100f) * 0.8f

            val aspectRatio = width.toFloat() / max(1, height)
            val invHalfW = 2f / max(1, width)
            val invHalfH = 2f / max(1, height)

            for (y in 0 until height) {
                val ny = y * invHalfH - 1f
                val rowOff = y * width
                for (x in 0 until width) {
                    val nx = x * invHalfW - 1f
                    val idx = rowOff + x
                    val argb = pixels[idx]
                    val a = argb ushr 24
                    var r = ((argb shr 16) and 0xFF) / 255f
                    var g = ((argb shr 8) and 0xFF) / 255f
                    var b = (argb and 0xFF) / 255f

                    if (hasVignette) {
                        val ax = if (vigRound >= 0f) nx * (1f + vigRound * (aspectRatio - 1f) * 0.5f) else nx
                        val d = kotlin.math.sqrt(ax * ax + ny * ny) / 1.15f
                        val edge0 = (vigMid - vigFeather * 0.5f).coerceAtLeast(0f)
                        val edge1 = (vigMid + vigFeather * 0.65f).coerceAtLeast(edge0 + 0.05f)
                        val t = ((d - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
                        val falloff = t * t * (3f - 2f * t)
                        if (vigK < 0f) {
                            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                            val highlightProtect = if (settings.vignetteHighlightPriority) {
                                1f - (luma * luma * 0.45f)
                            } else 1f
                            val mult = (1f + vigK * falloff * 0.88f * highlightProtect).coerceIn(0.05f, 1f)
                            r *= mult
                            g *= mult
                            b *= mult
                        } else {
                            val lift = vigK * falloff * 0.75f
                            r = r + (1f - r) * lift
                            g = g + (1f - g) * lift
                            b = b + (1f - b) * lift
                        }
                    }

                    if (hasGrain) {
                        val gx = x / grainScale
                        val gy = y / grainScale
                        var hash = gx * 374761393 + gy * 668265263
                        hash = (hash xor (hash shr 13)) * 1274126177
                        val noise = (((hash xor (hash shr 16)) and 0xFFFF) / 32768f - 1f) * grainAmp * grainRough
                        // Film grain concentrates in midtones, gentler in deep shadows/pure highlights
                        val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                        val midMask = 1f - abs(luma * 2f - 1f) * 0.5f
                        val n = noise * midMask
                        r = (r + n).coerceIn(0f, 1f)
                        g = (g + n).coerceIn(0f, 1f)
                        b = (b + n).coerceIn(0f, 1f)
                    }

                    val ir = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val ig = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val ib = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
                    pixels[idx] = (a shl 24) or (ir shl 16) or (ig shl 8) or ib
                }
            }
        }

        // 10. Optional Clipping Indicator Overlay
        if (showClippingOverlay) {
            for (i in 0 until count) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r >= 254 && g >= 254 && b >= 254) {
                    pixels[i] = 0xFFEF4444.toInt() // Highlight clip red
                } else if (r <= 1 && g <= 1 && b <= 1) {
                    pixels[i] = 0xFF3B82F6.toInt() // Shadow clip blue
                }
            }
        }

        outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    /**
     * Click/Tap-to-Neutralize White Balance eyedropper at normalized coordinates [0..1].
     */
    fun pickWhiteBalanceAt(source: Bitmap, normX: Float, normY: Float): Pair<Int, Float> {
        val cx = (normX.coerceIn(0f, 1f) * (source.width - 1)).roundToInt()
        val cy = (normY.coerceIn(0f, 1f) * (source.height - 1)).roundToInt()
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0
        for (dy in -2..2) {
            for (dx in -2..2) {
                val px = (cx + dx).coerceIn(0, source.width - 1)
                val py = (cy + dy).coerceIn(0, source.height - 1)
                val p = source.getPixel(px, py)
                sumR += (p shr 16) and 0xFF
                sumG += (p shr 8) and 0xFF
                sumB += p and 0xFF
                count++
            }
        }
        val avgR = (sumR / count).coerceAtLeast(1.0)
        val avgG = (sumG / count).coerceAtLeast(1.0)
        val avgB = (sumB / count).coerceAtLeast(1.0)

        // If sample is blue-heavy, warm up Kelvin; if red-heavy, cool down Kelvin
        val kelvin = (5500 + ((avgB - avgR) / 255.0 * 4800.0)).toInt().coerceIn(2200, 11000)
        val tint = (((avgR + avgB) * 0.5 - avgG) / 255.0 * 110.0).toFloat().coerceIn(-80f, 80f)
        return Pair(kelvin, tint.roundToInt().toFloat())
    }

    fun applyGeometry(source: Bitmap, settings: DevelopSettings, skipCrop: Boolean = false): Bitmap {
        val quarterTurns = ((settings.rotationQuarterTurns % 4) + 4) % 4
        val hasRotation = quarterTurns != 0 ||
            abs(settings.straightenAngle) > 0.05f ||
            settings.flipHorizontal ||
            settings.flipVertical

        val cl = settings.cropLeft.coerceIn(0f, 0.95f)
        val ct = settings.cropTop.coerceIn(0f, 0.95f)
        val cr = settings.cropRight.coerceIn(cl + 0.05f, 1f)
        val cb = settings.cropBottom.coerceIn(ct + 0.05f, 1f)
        val applyCrop = !skipCrop && !(cl <= 0.002f && ct <= 0.002f && cr >= 0.998f && cb >= 0.998f)

        if (!hasRotation && !applyCrop) {
            return source
        }

        val matrix = Matrix()
        val totalDeg = quarterTurns * 90f + settings.straightenAngle
        matrix.postTranslate(-source.width * 0.5f, -source.height * 0.5f)
        if (abs(totalDeg) > 0.01f) {
            matrix.postRotate(totalDeg)
        }
        if (settings.flipHorizontal) matrix.postScale(-1f, 1f)
        if (settings.flipVertical) matrix.postScale(1f, -1f)

        val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        matrix.mapRect(bounds)
        val rotW = max(2, bounds.width().roundToInt())
        val rotH = max(2, bounds.height().roundToInt())

        val (frameW, frameH) = if (abs(settings.straightenAngle) > 0.05f) {
            val rad = abs(settings.straightenAngle) * PI / 180.0
            val sinA = sin(rad)
            val cosA = cos(rad)
            val scale = min(
                1.0,
                1.0 / (cosA + sinA * max(rotW.toDouble() / rotH, rotH.toDouble() / rotW))
            ).coerceIn(0.45, 1.0)
            max(2, (rotW * scale).roundToInt()) to max(2, (rotH * scale).roundToInt())
        } else {
            rotW to rotH
        }

        val cropX = if (applyCrop) (cl * frameW).roundToInt().coerceIn(0, frameW - 2) else 0
        val cropY = if (applyCrop) (ct * frameH).roundToInt().coerceIn(0, frameH - 2) else 0
        val outW = if (applyCrop) ((cr - cl) * frameW).roundToInt().coerceIn(2, frameW - cropX) else frameW
        val outH = if (applyCrop) ((cb - ct) * frameH).roundToInt().coerceIn(2, frameH - cropY) else frameH

        matrix.postTranslate(frameW * 0.5f - cropX, frameH * 0.5f - cropY)

        val dst = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dst)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return dst
    }

    private fun applyFilmicExposure(v: Float, mult: Float): Float {
        val linear = v * mult
        if (mult <= 1f) return linear.coerceIn(0f, 1f)
        // Soft highlight shoulder rolloff above 0.72 so highlights retain detail like film
        return if (linear <= 0.72f) {
            linear
        } else {
            val excess = linear - 0.72f
            (0.72f + 0.28f * (1f - exp(-excess * 1.95f))).coerceIn(0f, 1f)
        }
    }

    private fun applyToneShaping(
        vIn: Float,
        contrastK: Float,
        highlightsK: Float,
        shadowsK: Float,
        whitesK: Float,
        blacksK: Float,
        pShadows: Float,
        pDarks: Float,
        pLights: Float,
        pHighlights: Float
    ): Float {
        var v = vIn.coerceIn(0f, 1f)

        // Blacks & Whites endpoint shifts
        if (abs(blacksK) > 0.002f) {
            val shadowW = (1f - v).pow(2.2f)
            v += blacksK * 0.18f * shadowW
        }
        if (abs(whitesK) > 0.002f) {
            val whiteW = v.pow(2.2f)
            v += whitesK * 0.18f * whiteW
        }

        // Shadows & Highlights recovery
        if (abs(shadowsK) > 0.002f) {
            val sMask = ((1f - v) * 1.4f).coerceIn(0f, 1f).pow(1.8f)
            v += shadowsK * 0.28f * sMask * (0.25f + v)
        }
        if (abs(highlightsK) > 0.002f) {
            val hMask = (v * 1.35f - 0.35f).coerceIn(0f, 1f).pow(1.6f)
            v += highlightsK * 0.30f * hMask
        }

        // Parametric Curve Regions (Shadows 0.15, Darks 0.38, Lights 0.65, Highlights 0.88)
        if (abs(pShadows) > 0.002f) v += pShadows * 0.20f * gaussian(v, 0.15f, 0.16f)
        if (abs(pDarks) > 0.002f) v += pDarks * 0.20f * gaussian(v, 0.38f, 0.18f)
        if (abs(pLights) > 0.002f) v += pLights * 0.20f * gaussian(v, 0.65f, 0.18f)
        if (abs(pHighlights) > 0.002f) v += pHighlights * 0.20f * gaussian(v, 0.88f, 0.16f)

        // Contrast S-Curve around mid-gray
        if (abs(contrastK) > 0.002f) {
            val centered = v - 0.5f
            val shaped = 0.5f + centered * (1f + contrastK * (1f - 2.2f * centered * centered))
            v = shaped
        }

        return v.coerceIn(0f, 1f)
    }

    private fun gaussian(x: Float, mu: Float, sigma: Float): Float {
        val d = (x - mu) / sigma
        return exp(-0.5f * d * d)
    }

    private fun buildCurveLut(points: List<CurvePoint>): FloatArray {
        val lut = FloatArray(256)
        if (points.size < 2) {
            for (i in 0..255) lut[i] = i / 255f
            return lut
        }
        val sorted = points.sortedBy { it.x }
        val isIdentity = sorted.size == 2 &&
            abs(sorted[0].x) < 0.002f && abs(sorted[0].y) < 0.002f &&
            abs(sorted[1].x - 1f) < 0.002f && abs(sorted[1].y - 1f) < 0.002f
        if (isIdentity) {
            for (i in 0..255) lut[i] = i / 255f
            return lut
        }

        for (i in 0..255) {
            val x = i / 255f
            lut[i] = evaluateCatmullRom(sorted, x).coerceIn(0f, 1f)
        }
        return lut
    }

    fun evaluateCatmullRom(sorted: List<CurvePoint>, x: Float): Float {
        if (x <= sorted.first().x) return sorted.first().y
        if (x >= sorted.last().x) return sorted.last().y

        var seg = 0
        for (i in 0 until sorted.size - 1) {
            if (x >= sorted[i].x && x <= sorted[i + 1].x) {
                seg = i
                break
            }
        }
        val p1 = sorted[seg]
        val p2 = sorted[seg + 1]
        val p0 = if (seg > 0) sorted[seg - 1] else p1
        val p3 = if (seg + 2 < sorted.size) sorted[seg + 2] else p2

        val span = max(0.0001f, p2.x - p1.x)
        val t = ((x - p1.x) / span).coerceIn(0f, 1f)
        val t2 = t * t
        val t3 = t2 * t

        return 0.5f * (
            (2f * p1.y) +
                (-p0.y + p2.y) * t +
                (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3
            )
    }

    private fun sampleLut(lut: FloatArray, x: Float): Float {
        val pos = (x.coerceIn(0f, 1f) * 255f)
        val i0 = pos.toInt().coerceIn(0, 254)
        val frac = pos - i0
        return lut[i0] + (lut[i0 + 1] - lut[i0]) * frac
    }

    private fun bandWeight(hueDeg: Float, centerDeg: Float): Float {
        var diff = abs(hueDeg - centerDeg)
        if (diff > 180f) diff = 360f - diff
        val span = 42f
        if (diff >= span) return 0f
        val t = 1f - diff / span
        return t * t * (3f - 2f * t)
    }

    private fun hslToRgb(hDeg: Float, s: Float, l: Float): FloatArray {
        val c = (1f - abs(2f * l - 1f)) * s
        val hPrime = hDeg / 60f
        val x = c * (1f - abs(hPrime % 2f - 1f))
        val (r1, g1, b1) = when {
            hPrime < 1f -> Triple(c, x, 0f)
            hPrime < 2f -> Triple(x, c, 0f)
            hPrime < 3f -> Triple(0f, c, x)
            hPrime < 4f -> Triple(0f, x, c)
            hPrime < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c * 0.5f
        return floatArrayOf(
            (r1 + m).coerceIn(0f, 1f),
            (g1 + m).coerceIn(0f, 1f),
            (b1 + m).coerceIn(0f, 1f)
        )
    }

    private fun wheelToRgbOffset(tone: ColorWheelTone): FloatArray {
        val sat = (tone.saturation / 100f).coerceIn(0f, 1f) * 0.24f
        if (sat <= 0.001f) return floatArrayOf(0f, 0f, 0f)
        val rgb = hslToRgb(tone.hueDeg.mod(360f), 1f, 0.5f)
        val avg = (rgb[0] + rgb[1] + rgb[2]) / 3f
        return floatArrayOf(
            (rgb[0] - avg) * sat,
            (rgb[1] - avg) * sat,
            (rgb[2] - avg) * sat
        )
    }

    private fun fastBoxBlur(src: FloatArray, dst: FloatArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0) {
            src.copyInto(dst)
            return
        }
        val tmp = FloatArray(src.size)
        val diam = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var sum = 0f
            for (kx in -radius..radius) {
                sum += src[row + kx.coerceIn(0, width - 1)]
            }
            for (x in 0 until width) {
                tmp[row + x] = sum / diam
                val rm = (x - radius).coerceIn(0, width - 1)
                val add = (x + radius + 1).coerceIn(0, width - 1)
                sum += src[row + add] - src[row + rm]
            }
        }
        for (x in 0 until width) {
            var sum = 0f
            for (ky in -radius..radius) {
                sum += tmp[ky.coerceIn(0, height - 1) * width + x]
            }
            for (y in 0 until height) {
                dst[y * width + x] = sum / diam
                val rm = (y - radius).coerceIn(0, height - 1)
                val add = (y + radius + 1).coerceIn(0, height - 1)
                sum += tmp[add * width + x] - tmp[rm * width + x]
            }
        }
    }
}
