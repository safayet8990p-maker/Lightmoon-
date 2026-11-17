package com.example.data

import com.example.model.ColorGradingSettings
import com.example.model.ColorWheelTone
import com.example.model.CurvePoint
import com.example.model.DevelopPreset
import com.example.model.DevelopSettings
import com.example.model.HslBandAdjustment
import com.example.model.HslColorBand
import com.example.model.WhiteBalancePreset

object BuiltInPresets {

    private fun hslWith(vararg overrides: Pair<HslColorBand, Triple<Float, Float, Float>>): List<HslBandAdjustment> {
        val map = overrides.toMap()
        return HslColorBand.entries.map { band ->
            val triple = map[band]
            if (triple != null) {
                HslBandAdjustment(band, triple.first, triple.second, triple.third)
            } else {
                HslBandAdjustment(band)
            }
        }
    }

    val presets: List<DevelopPreset> = listOf(
        DevelopPreset(
            id = "preset_kodak_gold",
            name = "Solaris 200",
            category = "Film",
            isBuiltIn = true,
            settings = DevelopSettings(
                exposure = 0.15f,
                contrast = 14f,
                highlights = -24f,
                shadows = 22f,
                blacks = 8f,
                wbPreset = WhiteBalancePreset.CUSTOM,
                temperatureKelvin = 6100,
                tint = 4f,
                vibrance = 18f,
                saturation = -4f,
                hslBands = hslWith(
                    HslColorBand.RED to Triple(4f, 8f, 0f),
                    HslColorBand.YELLOW to Triple(-6f, 14f, 6f),
                    HslColorBand.BLUE to Triple(-10f, -12f, -6f)
                ),
                rgbCurve = listOf(
                    CurvePoint(0f, 0.04f),
                    CurvePoint(0.25f, 0.22f),
                    CurvePoint(0.75f, 0.78f),
                    CurvePoint(1f, 0.96f)
                ),
                colorGrading = ColorGradingSettings(
                    shadows = ColorWheelTone(hueDeg = 42f, saturation = 18f, luminance = 0f),
                    highlights = ColorWheelTone(hueDeg = 48f, saturation = 22f, luminance = 4f)
                ),
                grainAmount = 22f,
                grainSize = 28f,
                grainRoughness = 55f,
                vignetteAmount = -12f
            )
        ),
        DevelopPreset(
            id = "preset_cine_teal_amber",
            name = "Nocturne Teal",
            category = "Cinematic",
            isBuiltIn = true,
            settings = DevelopSettings(
                contrast = 22f,
                highlights = -35f,
                shadows = 18f,
                blacks = -10f,
                clarity = 18f,
                dehaze = 12f,
                wbPreset = WhiteBalancePreset.CUSTOM,
                temperatureKelvin = 5150,
                tint = -6f,
                vibrance = 14f,
                hslBands = hslWith(
                    HslColorBand.ORANGE to Triple(-6f, 18f, 4f),
                    HslColorBand.AQUA to Triple(-12f, 24f, -8f),
                    HslColorBand.BLUE to Triple(-18f, 16f, -14f)
                ),
                colorGrading = ColorGradingSettings(
                    shadows = ColorWheelTone(hueDeg = 198f, saturation = 38f, luminance = -4f),
                    midtones = ColorWheelTone(hueDeg = 190f, saturation = 10f, luminance = 0f),
                    highlights = ColorWheelTone(hueDeg = 38f, saturation = 28f, luminance = 2f)
                ),
                vignetteAmount = -24f,
                vignetteFeather = 62f
            )
        ),
        DevelopPreset(
            id = "preset_silver_gelatin",
            name = "Silver Gelatin B&W",
            category = "Monochrome",
            isBuiltIn = true,
            settings = DevelopSettings(
                contrast = 28f,
                highlights = -18f,
                shadows = 14f,
                whites = 16f,
                blacks = -16f,
                clarity = 24f,
                saturation = -100f,
                rgbCurve = listOf(
                    CurvePoint(0f, 0.02f),
                    CurvePoint(0.28f, 0.24f),
                    CurvePoint(0.72f, 0.77f),
                    CurvePoint(1f, 0.98f)
                ),
                grainAmount = 30f,
                grainSize = 32f,
                grainRoughness = 60f,
                vignetteAmount = -20f
            )
        ),
        DevelopPreset(
            id = "preset_velvet_portrait",
            name = "Velvet Portrait",
            category = "Portrait",
            isBuiltIn = true,
            settings = DevelopSettings(
                exposure = 0.20f,
                contrast = -8f,
                highlights = -28f,
                shadows = 28f,
                texture = -14f,
                clarity = -10f,
                wbPreset = WhiteBalancePreset.CUSTOM,
                temperatureKelvin = 5750,
                tint = 5f,
                vibrance = 12f,
                saturation = -6f,
                hslBands = hslWith(
                    HslColorBand.ORANGE to Triple(3f, -6f, 10f),
                    HslColorBand.RED to Triple(4f, -8f, 4f)
                ),
                rgbCurve = listOf(
                    CurvePoint(0f, 0.05f),
                    CurvePoint(0.25f, 0.26f),
                    CurvePoint(0.75f, 0.75f),
                    CurvePoint(1f, 0.95f)
                ),
                vignetteAmount = -10f
            )
        ),
        DevelopPreset(
            id = "preset_alpine_clarity",
            name = "Alpine Crisp",
            category = "Landscape",
            isBuiltIn = true,
            settings = DevelopSettings(
                contrast = 16f,
                highlights = -42f,
                shadows = 32f,
                whites = 12f,
                localToneMapping = 38f,
                clarity = 26f,
                texture = 18f,
                dehaze = 18f,
                vibrance = 24f,
                hslBands = hslWith(
                    HslColorBand.GREEN to Triple(8f, 12f, -4f),
                    HslColorBand.BLUE to Triple(0f, 18f, -10f)
                ),
                sharpenAmount = 32f
            )
        ),
        DevelopPreset(
            id = "preset_faded_matte",
            name = "Studio Matte",
            category = "Film",
            isBuiltIn = true,
            settings = DevelopSettings(
                contrast = -12f,
                highlights = -22f,
                shadows = 20f,
                blacks = 18f,
                saturation = -18f,
                rgbCurve = listOf(
                    CurvePoint(0f, 0.11f),
                    CurvePoint(0.25f, 0.28f),
                    CurvePoint(0.75f, 0.74f),
                    CurvePoint(1f, 0.91f)
                ),
                colorGrading = ColorGradingSettings(
                    shadows = ColorWheelTone(hueDeg = 225f, saturation = 16f, luminance = 4f),
                    highlights = ColorWheelTone(hueDeg = 44f, saturation = 14f, luminance = 0f)
                ),
                grainAmount = 16f
            )
        ),
        DevelopPreset(
            id = "preset_golden_hour",
            name = "Amber Horizon",
            category = "Landscape",
            isBuiltIn = true,
            settings = DevelopSettings(
                exposure = 0.10f,
                contrast = 12f,
                highlights = -30f,
                shadows = 24f,
                wbPreset = WhiteBalancePreset.CUSTOM,
                temperatureKelvin = 6700,
                tint = 8f,
                vibrance = 22f,
                colorGrading = ColorGradingSettings(
                    midtones = ColorWheelTone(hueDeg = 35f, saturation = 20f, luminance = 0f),
                    highlights = ColorWheelTone(hueDeg = 46f, saturation = 30f, luminance = 2f)
                ),
                vignetteAmount = -14f
            )
        ),
        DevelopPreset(
            id = "preset_high_key",
            name = "Editorial High Key",
            category = "Editorial",
            isBuiltIn = true,
            settings = DevelopSettings(
                exposure = 0.55f,
                contrast = -14f,
                highlights = -20f,
                shadows = 42f,
                whites = 20f,
                saturation = -14f,
                clarity = -6f
            )
        )
    )
}
