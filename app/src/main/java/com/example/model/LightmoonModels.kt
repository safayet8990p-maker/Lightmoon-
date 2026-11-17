package com.example.model

import com.squareup.moshi.JsonClass
import java.util.UUID

enum class PickFlag {
    NONE,
    PICK,
    REJECT
}

enum class ColorLabel(val displayName: String) {
    NONE("None"),
    RED("Red"),
    YELLOW("Yellow"),
    GREEN("Green"),
    BLUE("Blue"),
    PURPLE("Purple")
}

enum class WhiteBalancePreset(val label: String, val kelvin: Int, val tint: Float) {
    AS_SHOT("As Shot", 5500, 0f),
    DAYLIGHT("Daylight", 5500, 2f),
    CLOUDY("Cloudy", 6500, 6f),
    SHADE("Shade", 7500, 8f),
    TUNGSTEN("Tungsten", 3200, -4f),
    FLUORESCENT("Fluorescent", 4000, 14f),
    CUSTOM("Custom", 5500, 0f)
}

enum class HslColorBand(val label: String, val centerHueDeg: Float, val swatchHex: Long) {
    RED("Red", 0f, 0xFFEF4444),
    ORANGE("Orange", 30f, 0xFFF97316),
    YELLOW("Yellow", 60f, 0xFFEAB308),
    GREEN("Green", 120f, 0xFF22C55E),
    AQUA("Aqua", 180f, 0xFF06B6D4),
    BLUE("Blue", 240f, 0xFF3B82F6),
    PURPLE("Purple", 275f, 0xFF8B5CF6),
    MAGENTA("Magenta", 315f, 0xFFEC4899)
}

enum class ToneCurveChannel(val label: String) {
    RGB("RGB"),
    RED("Red"),
    GREEN("Green"),
    BLUE("Blue")
}

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    ORIGINAL("Original", -1f),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_TWO("3:2", 3f / 2f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    FOUR_FIVE("4:5", 4f / 5f),
    NINE_SIXTEEN("9:16", 9f / 16f)
}

enum class CropOverlayGrid(val label: String) {
    THIRDS("Thirds"),
    GOLDEN("Golden"),
    GRID("Grid"),
    NONE("Off")
}

enum class MaskType(val label: String, val isSmart: Boolean) {
    SMART_SUBJECT("Subject", true),
    SMART_SKY("Sky", true),
    SMART_BACKGROUND("Background", true),
    SMART_LUMINANCE("Luminance", true),
    SMART_COLOR("Color Range", true),
    LINEAR_GRADIENT("Linear", false),
    RADIAL_GRADIENT("Radial", false),
    BRUSH("Brush", false)
}

enum class ScopeMode(val label: String) {
    HISTOGRAM("Histogram"),
    RGB_PARADE("Parade"),
    WAVEFORM("Waveform"),
    OFF("Hidden")
}

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    JPEG("JPEG", "jpg", "image/jpeg"),
    PNG("PNG (Lossless)", "png", "image/png"),
    WEBP("WebP", "webp", "image/webp"),
    SIDECAR_RECIPE("Recipe Sidecar (.lightmoon)", "json", "application/json")
}

enum class ExportResolution(val label: String, val maxLongEdgePx: Int?) {
    ORIGINAL("Original Full Res", null),
    UHD_4K("4096 px (4K)", 4096),
    WEB_2K("2048 px (Web)", 2048),
    SOCIAL_1080("1080 px (Fast)", 1080)
}

enum class OutputSharpening(val label: String, val amount: Float) {
    NONE("None", 0f),
    SCREEN_LOW("Screen • Low", 18f),
    SCREEN_STANDARD("Screen • Standard", 32f),
    PRINT_MATTE("Matte Print", 45f),
    PRINT_GLOSSY("Glossy Print", 38f)
}

enum class WatermarkCorner(val label: String) {
    BOTTOM_RIGHT("Bottom Right"),
    BOTTOM_LEFT("Bottom Left"),
    TOP_RIGHT("Top Right"),
    CENTER("Center")
}

@JsonClass(generateAdapter = true)
data class CurvePoint(
    val x: Float,
    val y: Float
)

@JsonClass(generateAdapter = true)
data class HslBandAdjustment(
    val band: HslColorBand,
    val hueShift: Float = 0f,
    val saturationShift: Float = 0f,
    val luminanceShift: Float = 0f
)

@JsonClass(generateAdapter = true)
data class ColorWheelTone(
    val hueDeg: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f
)

@JsonClass(generateAdapter = true)
data class ColorGradingSettings(
    val shadows: ColorWheelTone = ColorWheelTone(),
    val midtones: ColorWheelTone = ColorWheelTone(),
    val highlights: ColorWheelTone = ColorWheelTone(),
    val blending: Float = 50f,
    val balance: Float = 0f
)

@JsonClass(generateAdapter = true)
data class BrushStroke(
    val pointsX: List<Float>,
    val pointsY: List<Float>,
    val radiusNorm: Float = 0.06f,
    val feather: Float = 0.5f,
    val flow: Float = 0.8f,
    val erase: Boolean = false
)

@JsonClass(generateAdapter = true)
data class MaskAdjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val temperatureShift: Float = 0f,
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val clarity: Float = 0f,
    val sharpness: Float = 0f
)

@JsonClass(generateAdapter = true)
data class MaskLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: MaskType,
    val enabled: Boolean = true,
    val inverted: Boolean = false,
    val opacity: Float = 1f,
    val feather: Float = 0.5f,
    // Linear gradient endpoints in normalized [0..1]
    val startX: Float = 0.5f,
    val startY: Float = 0.15f,
    val endX: Float = 0.5f,
    val endY: Float = 0.65f,
    // Radial gradient center & radii in normalized [0..1]
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val radiusX: Float = 0.32f,
    val radiusY: Float = 0.28f,
    // Smart Luminance / Color Range targets
    val rangeCenter: Float = 0.5f,
    val rangeWidth: Float = 0.25f,
    // Brush strokes
    val brushStrokes: List<BrushStroke> = emptyList(),
    val adjustments: MaskAdjustments = MaskAdjustments()
)

@JsonClass(generateAdapter = true)
data class DevelopSettings(
    val processVersion: String = "V3",
    // Light Panel
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val localToneMapping: Float = 0f,
    // Color Panel
    val wbPreset: WhiteBalancePreset = WhiteBalancePreset.AS_SHOT,
    val temperatureKelvin: Int = 5500,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val saturation: Float = 0f,
    val hslBands: List<HslBandAdjustment> = HslColorBand.entries.map { HslBandAdjustment(it) },
    val colorGrading: ColorGradingSettings = ColorGradingSettings(),
    // Tone Curve Panel
    val rgbCurve: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val redCurve: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val greenCurve: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val blueCurve: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
    val parametricHighlights: Float = 0f,
    val parametricLights: Float = 0f,
    val parametricDarks: Float = 0f,
    val parametricShadows: Float = 0f,
    // Detail Panel
    val texture: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val sharpenAmount: Float = 0f,
    val sharpenRadius: Float = 1.0f,
    val sharpenDetail: Float = 25f,
    val denoiseLuminance: Float = 0f,
    val denoiseColor: Float = 0f,
    // Effects Panel
    val vignetteAmount: Float = 0f,
    val vignetteMidpoint: Float = 50f,
    val vignetteRoundness: Float = 0f,
    val vignetteFeather: Float = 50f,
    val vignetteHighlightPriority: Boolean = true,
    val grainAmount: Float = 0f,
    val grainSize: Float = 25f,
    val grainRoughness: Float = 50f,
    // Crop & Geometry
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val aspectRatio: CropAspectRatio = CropAspectRatio.FREE,
    val straightenAngle: Float = 0f,
    val rotationQuarterTurns: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    // Local Masking
    val masks: List<MaskLayer> = emptyList()
) {
    fun isDefault(): Boolean = this == DevelopSettings()

    fun blendedWith(baseline: DevelopSettings, amountFraction: Float): DevelopSettings {
        fun lerp(a: Float, b: Float) = a + (b - a) * amountFraction
        fun lerpInt(a: Int, b: Int) = (a + (b - a) * amountFraction).toInt()
        val blendedHsl = HslColorBand.entries.mapIndexed { idx, band ->
            val baseBand = baseline.hslBands.getOrNull(idx) ?: HslBandAdjustment(band)
            val targetBand = hslBands.getOrNull(idx) ?: HslBandAdjustment(band)
            HslBandAdjustment(
                band = band,
                hueShift = lerp(baseBand.hueShift, targetBand.hueShift),
                saturationShift = lerp(baseBand.saturationShift, targetBand.saturationShift),
                luminanceShift = lerp(baseBand.luminanceShift, targetBand.luminanceShift)
            )
        }
        return baseline.copy(
            exposure = lerp(baseline.exposure, exposure),
            contrast = lerp(baseline.contrast, contrast),
            highlights = lerp(baseline.highlights, highlights),
            shadows = lerp(baseline.shadows, shadows),
            whites = lerp(baseline.whites, whites),
            blacks = lerp(baseline.blacks, blacks),
            localToneMapping = lerp(baseline.localToneMapping, localToneMapping),
            wbPreset = if (amountFraction > 0.05f) wbPreset else baseline.wbPreset,
            temperatureKelvin = lerpInt(baseline.temperatureKelvin, temperatureKelvin),
            tint = lerp(baseline.tint, tint),
            vibrance = lerp(baseline.vibrance, vibrance),
            saturation = lerp(baseline.saturation, saturation),
            hslBands = blendedHsl,
            colorGrading = ColorGradingSettings(
                shadows = ColorWheelTone(
                    hueDeg = shadowsHueOrTarget(baseline.colorGrading.shadows, colorGrading.shadows, amountFraction),
                    saturation = lerp(baseline.colorGrading.shadows.saturation, colorGrading.shadows.saturation),
                    luminance = lerp(baseline.colorGrading.shadows.luminance, colorGrading.shadows.luminance)
                ),
                midtones = ColorWheelTone(
                    hueDeg = shadowsHueOrTarget(baseline.colorGrading.midtones, colorGrading.midtones, amountFraction),
                    saturation = lerp(baseline.colorGrading.midtones.saturation, colorGrading.midtones.saturation),
                    luminance = lerp(baseline.colorGrading.midtones.luminance, colorGrading.midtones.luminance)
                ),
                highlights = ColorWheelTone(
                    hueDeg = shadowsHueOrTarget(baseline.colorGrading.highlights, colorGrading.highlights, amountFraction),
                    saturation = lerp(baseline.colorGrading.highlights.saturation, colorGrading.highlights.saturation),
                    luminance = lerp(baseline.colorGrading.highlights.luminance, colorGrading.highlights.luminance)
                ),
                blending = lerp(baseline.colorGrading.blending, colorGrading.blending),
                balance = lerp(baseline.colorGrading.balance, colorGrading.balance)
            ),
            rgbCurve = if (amountFraction >= 0.5f) rgbCurve else baseline.rgbCurve,
            redCurve = if (amountFraction >= 0.5f) redCurve else baseline.redCurve,
            greenCurve = if (amountFraction >= 0.5f) greenCurve else baseline.greenCurve,
            blueCurve = if (amountFraction >= 0.5f) blueCurve else baseline.blueCurve,
            parametricHighlights = lerp(baseline.parametricHighlights, parametricHighlights),
            parametricLights = lerp(baseline.parametricLights, parametricLights),
            parametricDarks = lerp(baseline.parametricDarks, parametricDarks),
            parametricShadows = lerp(baseline.parametricShadows, parametricShadows),
            texture = lerp(baseline.texture, texture),
            clarity = lerp(baseline.clarity, clarity),
            dehaze = lerp(baseline.dehaze, dehaze),
            sharpenAmount = lerp(baseline.sharpenAmount, sharpenAmount),
            sharpenRadius = lerp(baseline.sharpenRadius, sharpenRadius),
            sharpenDetail = lerp(baseline.sharpenDetail, sharpenDetail),
            denoiseLuminance = lerp(baseline.denoiseLuminance, denoiseLuminance),
            denoiseColor = lerp(baseline.denoiseColor, denoiseColor),
            vignetteAmount = lerp(baseline.vignetteAmount, vignetteAmount),
            vignetteMidpoint = lerp(baseline.vignetteMidpoint, vignetteMidpoint),
            vignetteRoundness = lerp(baseline.vignetteRoundness, vignetteRoundness),
            vignetteFeather = lerp(baseline.vignetteFeather, vignetteFeather),
            vignetteHighlightPriority = vignetteHighlightPriority,
            grainAmount = lerp(baseline.grainAmount, grainAmount),
            grainSize = lerp(baseline.grainSize, grainSize),
            grainRoughness = lerp(baseline.grainRoughness, grainRoughness)
        )
    }

    private fun shadowsHueOrTarget(base: ColorWheelTone, target: ColorWheelTone, t: Float): Float {
        return if (base.saturation < 0.01f) target.hueDeg else base.hueDeg + (target.hueDeg - base.hueDeg) * t
    }
}

@JsonClass(generateAdapter = true)
data class PhotoItem(
    val id: String,
    val title: String,
    val localFilePath: String,
    val thumbnailPath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val capturedAtMillis: Long,
    val cameraModel: String,
    val lensInfo: String,
    val iso: Int,
    val aperture: String,
    val shutterSpeed: String,
    val focalLength: String,
    val rating: Int = 0,
    val flag: PickFlag = PickFlag.NONE,
    val colorLabel: ColorLabel = ColorLabel.NONE,
    val developSettings: DevelopSettings = DevelopSettings(),
    val isSample: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val isEdited: Boolean
        get() = !developSettings.isDefault()
}

@JsonClass(generateAdapter = true)
data class DevelopPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val isBuiltIn: Boolean = false,
    val settings: DevelopSettings,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class EditSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val photoId: String,
    val name: String,
    val settings: DevelopSettings,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class HistoryEntry(
    val label: String,
    val settings: DevelopSettings,
    val timestampMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class ExportConfig(
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 92,
    val resolution: ExportResolution = ExportResolution.ORIGINAL,
    val sharpening: OutputSharpening = OutputSharpening.SCREEN_STANDARD,
    val includeMetadata: Boolean = true,
    val watermarkEnabled: Boolean = false,
    val watermarkText: String = "Lightmoon Studio",
    val watermarkOpacity: Float = 0.6f,
    val watermarkCorner: WatermarkCorner = WatermarkCorner.BOTTOM_RIGHT,
    val filenamePrefix: String = "LM_"
)

data class ScopeData(
    val redBins: IntArray = IntArray(256),
    val greenBins: IntArray = IntArray(256),
    val blueBins: IntArray = IntArray(256),
    val lumaBins: IntArray = IntArray(256),
    val paradeWidth: Int = 48,
    val paradeRed: FloatArray = FloatArray(48 * 32),
    val paradeGreen: FloatArray = FloatArray(48 * 32),
    val paradeBlue: FloatArray = FloatArray(48 * 32),
    val waveform: FloatArray = FloatArray(48 * 32),
    val shadowClippedPercent: Float = 0f,
    val highlightClippedPercent: Float = 0f
)

@JsonClass(generateAdapter = true)
data class PhotoRatingSyncRecord(
    val title: String,
    val rating: Int,
    val flag: PickFlag,
    val colorLabel: ColorLabel,
    val developSettings: DevelopSettings,
    val updatedAtMillis: Long
)

@JsonClass(generateAdapter = true)
data class CatalogBackupPackage(
    val version: Int = 1,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val customPresets: List<DevelopPreset>,
    val photoStates: List<PhotoRatingSyncRecord>
)
