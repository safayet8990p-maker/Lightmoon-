package com.example.ui.develop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CropAspectRatio
import com.example.model.DevelopPreset
import com.example.model.DevelopSettings
import com.example.model.HslColorBand
import com.example.model.MaskType
import com.example.model.ToneCurveChannel
import com.example.model.WhiteBalancePreset
import com.example.ui.components.ColorWheelControl
import com.example.ui.components.StudioSlider
import com.example.ui.components.ToneCurveEditor
import com.example.ui.theme.DarkroomAmber
import com.example.ui.theme.MonoReadoutStyle
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioOutlineVariant
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighest
import kotlin.math.roundToInt

@Composable
fun DevelopToolPanelContent(
    state: DevelopUiState,
    viewModel: DevelopViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        when (state.activeTab) {
            DevelopTab.LIGHT -> LightPanel(state.settings, viewModel)
            DevelopTab.COLOR -> ColorPanel(state, viewModel)
            DevelopTab.CURVE -> CurvePanel(state, viewModel)
            DevelopTab.DETAIL -> DetailPanel(state.settings, viewModel)
            DevelopTab.EFFECTS -> EffectsPanel(state.settings, viewModel)
            DevelopTab.MASKING -> MaskingPanel(state, viewModel)
            DevelopTab.CROP -> CropPanel(state, viewModel)
            DevelopTab.PRESETS -> PresetsPanel(state, viewModel)
        }
    }
}

@Composable
private fun LightPanel(
    s: DevelopSettings,
    vm: DevelopViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Light & Dynamic Range", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.runAutoTone() },
                modifier = Modifier.height(36.dp).testTag("auto_tone_button")
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Auto Tone", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = {
                    vm.commitSettingsChange("Reset Light") {
                        it.copy(
                            exposure = 0f, contrast = 0f, highlights = 0f,
                            shadows = 0f, whites = 0f, blacks = 0f, localToneMapping = 0f
                        )
                    }
                },
                modifier = Modifier.height(36.dp)
            ) {
                Text("Reset", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))

    StudioSlider(
        label = "Exposure",
        value = s.exposure,
        valueRange = -3.0f..3.0f,
        valueText = "${if (s.exposure >= 0) "+" else ""}${"%.2f".format(s.exposure)} EV",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(exposure = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Exposure") },
        testTag = "slider_exposure"
    )
    StudioSlider(
        label = "Contrast",
        value = s.contrast,
        valueRange = -100f..100f,
        valueText = signedInt(s.contrast),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(contrast = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Contrast") },
        testTag = "slider_contrast"
    )
    StudioSlider(
        label = "Highlights",
        value = s.highlights,
        valueRange = -100f..100f,
        valueText = signedInt(s.highlights),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(highlights = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Highlights") }
    )
    StudioSlider(
        label = "Shadows",
        value = s.shadows,
        valueRange = -100f..100f,
        valueText = signedInt(s.shadows),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(shadows = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Shadows") }
    )
    StudioSlider(
        label = "Whites",
        value = s.whites,
        valueRange = -100f..100f,
        valueText = signedInt(s.whites),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(whites = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Whites") }
    )
    StudioSlider(
        label = "Blacks",
        value = s.blacks,
        valueRange = -100f..100f,
        valueText = signedInt(s.blacks),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(blacks = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Blacks") }
    )
    StudioSlider(
        label = "Local Tone Mapping",
        value = s.localToneMapping,
        valueRange = 0f..100f,
        valueText = "${s.localToneMapping.roundToInt()}",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(localToneMapping = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Local Tone Mapping") }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPanel(
    state: DevelopUiState,
    vm: DevelopViewModel
) {
    val s = state.settings
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("White Balance & Color", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(
            onClick = { vm.toggleEyedropper() },
            colors = if (state.isEyedropperActive) {
                ButtonDefaults.outlinedButtonColors(containerColor = StudioCyan.copy(alpha = 0.2f))
            } else ButtonDefaults.outlinedButtonColors(),
            modifier = Modifier.height(36.dp).testTag("wb_eyedropper_button")
        ) {
            Icon(
                Icons.Default.Colorize,
                contentDescription = null,
                tint = if (state.isEyedropperActive) StudioCyan else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (state.isEyedropperActive) "Tap Image" else "Eyedropper",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WhiteBalancePreset.entries.forEach { preset ->
            FilterChip(
                selected = s.wbPreset == preset,
                onClick = {
                    vm.commitSettingsChange("WB ${preset.label}") { cur ->
                        if (preset == WhiteBalancePreset.CUSTOM) cur.copy(wbPreset = preset)
                        else cur.copy(wbPreset = preset, temperatureKelvin = preset.kelvin, tint = preset.tint)
                    }
                },
                label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }

    StudioSlider(
        label = "Temperature",
        value = s.temperatureKelvin.toFloat(),
        valueRange = 2000f..12000f,
        defaultValue = 5500f,
        valueText = "${s.temperatureKelvin} K",
        trackBrush = Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFFE5E7EB), Color(0xFFF59E0B))),
        onValueChange = { v ->
            vm.updateSettingsLive { it.copy(wbPreset = WhiteBalancePreset.CUSTOM, temperatureKelvin = v.roundToInt()) }
        },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Temperature") }
    )
    StudioSlider(
        label = "Tint",
        value = s.tint,
        valueRange = -100f..100f,
        valueText = signedInt(s.tint),
        trackBrush = Brush.horizontalGradient(listOf(Color(0xFF22C55E), Color(0xFFE5E7EB), Color(0xFFEC4899))),
        onValueChange = { v ->
            vm.updateSettingsLive { it.copy(wbPreset = WhiteBalancePreset.CUSTOM, tint = v) }
        },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Tint") }
    )
    StudioSlider(
        label = "Vibrance (Skin-Safe)",
        value = s.vibrance,
        valueRange = -100f..100f,
        valueText = signedInt(s.vibrance),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(vibrance = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Vibrance") }
    )
    StudioSlider(
        label = "Saturation",
        value = s.saturation,
        valueRange = -100f..100f,
        valueText = signedInt(s.saturation),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(saturation = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Saturation") }
    )

    Spacer(modifier = Modifier.height(10.dp))
    HorizontalDivider(color = StudioOutlineVariant)
    Spacer(modifier = Modifier.height(10.dp))

    // 8-Band HSL Color Mixer
    Text("8-Band Color Mixer (HSL)", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HslColorBand.entries.forEach { band ->
            val selected = state.activeHslBand == band
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) StudioSurfaceHighest else StudioBackground)
                    .border(
                        1.dp,
                        if (selected) Color(band.swatchHex) else StudioOutlineVariant,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { vm.setActiveHslBand(band) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(band.swatchHex))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(band.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    val activeBandAdj = s.hslBands.firstOrNull { it.band == state.activeHslBand }
    if (activeBandAdj != null) {
        Spacer(modifier = Modifier.height(6.dp))
        StudioSlider(
            label = "${activeBandAdj.band.label} Hue",
            value = activeBandAdj.hueShift,
            valueRange = -100f..100f,
            valueText = signedInt(activeBandAdj.hueShift),
            onValueChange = { v -> vm.updateHslBand(activeBandAdj.band, true) { it.copy(hueShift = v) } },
            onValueChangeFinished = { vm.commitCurrentSettingsToHistory("HSL ${activeBandAdj.band.label} Hue") }
        )
        StudioSlider(
            label = "${activeBandAdj.band.label} Saturation",
            value = activeBandAdj.saturationShift,
            valueRange = -100f..100f,
            valueText = signedInt(activeBandAdj.saturationShift),
            onValueChange = { v -> vm.updateHslBand(activeBandAdj.band, true) { it.copy(saturationShift = v) } },
            onValueChangeFinished = { vm.commitCurrentSettingsToHistory("HSL ${activeBandAdj.band.label} Sat") }
        )
        StudioSlider(
            label = "${activeBandAdj.band.label} Luminance",
            value = activeBandAdj.luminanceShift,
            valueRange = -100f..100f,
            valueText = signedInt(activeBandAdj.luminanceShift),
            onValueChange = { v -> vm.updateHslBand(activeBandAdj.band, true) { it.copy(luminanceShift = v) } },
            onValueChangeFinished = { vm.commitCurrentSettingsToHistory("HSL ${activeBandAdj.band.label} Lum") }
        )
    }

    Spacer(modifier = Modifier.height(10.dp))
    HorizontalDivider(color = StudioOutlineVariant)
    Spacer(modifier = Modifier.height(10.dp))

    // 3-Way Color Grading Wheels
    Text("3-Way Color Grading", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ColorWheelControl(
            title = "Shadows",
            tone = s.colorGrading.shadows,
            onToneChange = { tone, live -> vm.setColorWheel("Shadows", tone, live) }
        )
        ColorWheelControl(
            title = "Midtones",
            tone = s.colorGrading.midtones,
            onToneChange = { tone, live -> vm.setColorWheel("Midtones", tone, live) }
        )
        ColorWheelControl(
            title = "Highlights",
            tone = s.colorGrading.highlights,
            onToneChange = { tone, live -> vm.setColorWheel("Highlights", tone, live) }
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    StudioSlider(
        label = "Grading Blending",
        value = s.colorGrading.blending,
        valueRange = 0f..100f,
        defaultValue = 50f,
        valueText = "${s.colorGrading.blending.roundToInt()}%",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(colorGrading = it.colorGrading.copy(blending = v)) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Grading Blending") }
    )
    StudioSlider(
        label = "Shadow / Highlight Balance",
        value = s.colorGrading.balance,
        valueRange = -100f..100f,
        valueText = signedInt(s.colorGrading.balance),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(colorGrading = it.colorGrading.copy(balance = v)) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Grading Balance") }
    )
}

@Composable
private fun CurvePanel(
    state: DevelopUiState,
    vm: DevelopViewModel
) {
    val s = state.settings
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToneCurveChannel.entries.forEach { ch ->
                FilterChip(
                    selected = state.activeCurveChannel == ch,
                    onClick = { vm.setActiveCurveChannel(ch) },
                    label = { Text(ch.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        TextButton(onClick = { vm.resetActiveCurve() }) {
            Text("Reset", style = MaterialTheme.typography.labelMedium)
        }
    }

    val activePoints = when (state.activeCurveChannel) {
        ToneCurveChannel.RGB -> s.rgbCurve
        ToneCurveChannel.RED -> s.redCurve
        ToneCurveChannel.GREEN -> s.greenCurve
        ToneCurveChannel.BLUE -> s.blueCurve
    }

    ToneCurveEditor(
        channel = state.activeCurveChannel,
        points = activePoints,
        lumaBins = state.scopeData.lumaBins,
        onPointsChange = { pts, live -> vm.updateCurvePoints(state.activeCurveChannel, pts, live) }
    )
    Text(
        text = "Tap curve to add point · Drag to shape · Double-tap point to remove",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp)
    )

    Text("Parametric Tone Regions", style = MaterialTheme.typography.titleSmall)
    StudioSlider(
        label = "Highlights Region",
        value = s.parametricHighlights,
        valueRange = -100f..100f,
        valueText = signedInt(s.parametricHighlights),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(parametricHighlights = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Parametric Highlights") }
    )
    StudioSlider(
        label = "Lights Region",
        value = s.parametricLights,
        valueRange = -100f..100f,
        valueText = signedInt(s.parametricLights),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(parametricLights = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Parametric Lights") }
    )
    StudioSlider(
        label = "Darks Region",
        value = s.parametricDarks,
        valueRange = -100f..100f,
        valueText = signedInt(s.parametricDarks),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(parametricDarks = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Parametric Darks") }
    )
    StudioSlider(
        label = "Shadows Region",
        value = s.parametricShadows,
        valueRange = -100f..100f,
        valueText = signedInt(s.parametricShadows),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(parametricShadows = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Parametric Shadows") }
    )
}

@Composable
private fun DetailPanel(
    s: DevelopSettings,
    vm: DevelopViewModel
) {
    Text("Presence & Atmosphere", style = MaterialTheme.typography.titleSmall)
    StudioSlider(
        label = "Texture",
        value = s.texture,
        valueRange = -100f..100f,
        valueText = signedInt(s.texture),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(texture = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Texture") }
    )
    StudioSlider(
        label = "Clarity",
        value = s.clarity,
        valueRange = -100f..100f,
        valueText = signedInt(s.clarity),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(clarity = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Clarity") }
    )
    StudioSlider(
        label = "Dehaze",
        value = s.dehaze,
        valueRange = -100f..100f,
        valueText = signedInt(s.dehaze),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(dehaze = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Dehaze") }
    )

    Spacer(modifier = Modifier.height(6.dp))
    HorizontalDivider(color = StudioOutlineVariant)
    Spacer(modifier = Modifier.height(6.dp))
    Text("Pixel-Scale Sharpening", style = MaterialTheme.typography.titleSmall)

    StudioSlider(
        label = "Sharpening Amount",
        value = s.sharpenAmount,
        valueRange = 0f..150f,
        valueText = "${s.sharpenAmount.roundToInt()}",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(sharpenAmount = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Sharpening") }
    )
    StudioSlider(
        label = "Radius",
        value = s.sharpenRadius,
        valueRange = 0.5f..3.0f,
        defaultValue = 1.0f,
        valueText = "${"%.1f".format(s.sharpenRadius)} px",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(sharpenRadius = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Sharpen Radius") }
    )
    StudioSlider(
        label = "Detail (Halo Damping)",
        value = s.sharpenDetail,
        valueRange = 0f..100f,
        defaultValue = 25f,
        valueText = "${s.sharpenDetail.roundToInt()}",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(sharpenDetail = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Sharpen Detail") }
    )

    Spacer(modifier = Modifier.height(6.dp))
    HorizontalDivider(color = StudioOutlineVariant)
    Spacer(modifier = Modifier.height(6.dp))
    Text("Noise Reduction", style = MaterialTheme.typography.titleSmall)

    StudioSlider(
        label = "Luminance Denoise",
        value = s.denoiseLuminance,
        valueRange = 0f..100f,
        valueText = "${s.denoiseLuminance.roundToInt()}",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(denoiseLuminance = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Luminance Denoise") }
    )
    StudioSlider(
        label = "Color Denoise",
        value = s.denoiseColor,
        valueRange = 0f..100f,
        valueText = "${s.denoiseColor.roundToInt()}",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(denoiseColor = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Color Denoise") }
    )
}

@Composable
private fun EffectsPanel(
    s: DevelopSettings,
    vm: DevelopViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Post-Crop Vignette", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Pipeline ${s.processVersion}",
            style = MonoReadoutStyle,
            color = StudioCyan,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(StudioCyan.copy(alpha = 0.14f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }

    StudioSlider(
        label = "Vignette Amount",
        value = s.vignetteAmount,
        valueRange = -100f..100f,
        valueText = signedInt(s.vignetteAmount),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(vignetteAmount = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Vignette Amount") }
    )
    StudioSlider(
        label = "Midpoint",
        value = s.vignetteMidpoint,
        valueRange = 0f..100f,
        defaultValue = 50f,
        valueText = "${s.vignetteMidpoint.roundToInt()}",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(vignetteMidpoint = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Vignette Midpoint") }
    )
    StudioSlider(
        label = "Roundness",
        value = s.vignetteRoundness,
        valueRange = -100f..100f,
        valueText = signedInt(s.vignetteRoundness),
        onValueChange = { v -> vm.updateSettingsLive { it.copy(vignetteRoundness = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Vignette Roundness") }
    )
    StudioSlider(
        label = "Feather",
        value = s.vignetteFeather,
        valueRange = 0f..100f,
        defaultValue = 50f,
        valueText = "${s.vignetteFeather.roundToInt()}",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(vignetteFeather = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Vignette Feather") }
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Highlight Priority", style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = s.vignetteHighlightPriority,
            onCheckedChange = { checked ->
                vm.commitSettingsChange("Vignette Priority") { it.copy(vignetteHighlightPriority = checked) }
            }
        )
    }

    Spacer(modifier = Modifier.height(6.dp))
    HorizontalDivider(color = StudioOutlineVariant)
    Spacer(modifier = Modifier.height(6.dp))
    Text("Film Grain", style = MaterialTheme.typography.titleSmall)

    StudioSlider(
        label = "Grain Amount",
        value = s.grainAmount,
        valueRange = 0f..100f,
        valueText = "${s.grainAmount.roundToInt()}",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(grainAmount = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Film Grain") }
    )
    StudioSlider(
        label = "Grain Size",
        value = s.grainSize,
        valueRange = 0f..100f,
        defaultValue = 25f,
        valueText = "${s.grainSize.roundToInt()}",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(grainSize = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Grain Size") }
    )
    StudioSlider(
        label = "Roughness",
        value = s.grainRoughness,
        valueRange = 0f..100f,
        defaultValue = 50f,
        valueText = "${s.grainRoughness.roundToInt()}",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(grainRoughness = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Grain Roughness") }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaskingPanel(
    state: DevelopUiState,
    vm: DevelopViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Local & Smart Masking", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Overlay", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(6.dp))
            Switch(
                checked = state.showMaskOverlay,
                onCheckedChange = { vm.toggleMaskOverlay() }
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text("Add Smart or Manual Mask", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(6.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MaskType.entries.forEach { type ->
            OutlinedButton(
                onClick = { vm.addMask(type) },
                modifier = Modifier.height(36.dp).testTag("add_mask_${type.name.lowercase()}")
            ) {
                Icon(
                    if (type.isSmart) Icons.Default.AutoFixHigh else Icons.Default.Layers,
                    contentDescription = null,
                    tint = if (type.isSmart) StudioCyan else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(type.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (state.settings.masks.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            state.settings.masks.forEach { mask ->
                val isSelected = mask.id == state.activeMaskId
                FilterChip(
                    selected = isSelected,
                    onClick = { vm.selectMask(mask.id) },
                    label = { Text(mask.name, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    }

    val activeMask = state.activeMask
    if (activeMask == null) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Tap any Smart Mask (Subject, Sky, Background) or Brush/Gradient above to apply targeted local adjustments offline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(activeMask.name, style = MaterialTheme.typography.labelLarge, color = StudioCyan)
        Row {
            IconButton(onClick = {
                vm.updateActiveMask(false, "Toggle Mask") { it.copy(enabled = !it.enabled) }
            }) {
                Icon(
                    if (activeMask.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle mask visibility"
                )
            }
            IconButton(onClick = {
                vm.updateActiveMask(false, "Invert Mask") { it.copy(inverted = !it.inverted) }
            }) {
                Icon(
                    Icons.Default.InvertColors,
                    contentDescription = "Invert mask",
                    tint = if (activeMask.inverted) DarkroomAmber else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { vm.deleteMask(activeMask.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete mask", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (activeMask.type == MaskType.BRUSH) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Paint directly on photo above", style = MaterialTheme.typography.bodySmall, color = StudioCyan)
            FilterChip(
                selected = state.brushEraseMode,
                onClick = { vm.setBrushParams(erase = !state.brushEraseMode) },
                label = { Text(if (state.brushEraseMode) "Eraser On" else "Paint Mode") }
            )
        }
        StudioSlider(
            label = "Brush Size",
            value = state.brushSizeNorm * 100f,
            valueRange = 2f..18f,
            defaultValue = 6f,
            valueText = "${(state.brushSizeNorm * 100).roundToInt()}",
            onValueChange = { v -> vm.setBrushParams(sizeNorm = v / 100f) },
            onValueChangeFinished = {}
        )
    } else if (activeMask.type == MaskType.SMART_LUMINANCE || activeMask.type == MaskType.SMART_COLOR) {
        StudioSlider(
            label = if (activeMask.type == MaskType.SMART_LUMINANCE) "Target Luminance" else "Target Hue",
            value = activeMask.rangeCenter * 100f,
            valueRange = 0f..100f,
            defaultValue = 50f,
            valueText = "${(activeMask.rangeCenter * 100).roundToInt()}%",
            onValueChange = { v -> vm.updateActiveMask(true) { it.copy(rangeCenter = v / 100f) } },
            onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Mask Target") }
        )
        StudioSlider(
            label = "Range Falloff",
            value = activeMask.rangeWidth * 100f,
            valueRange = 5f..60f,
            defaultValue = 25f,
            valueText = "${(activeMask.rangeWidth * 100).roundToInt()}%",
            onValueChange = { v -> vm.updateActiveMask(true) { it.copy(rangeWidth = v / 100f) } },
            onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Mask Range") }
        )
    }

    val adj = activeMask.adjustments
    StudioSlider(
        label = "Mask Exposure",
        value = adj.exposure,
        valueRange = -2.5f..2.5f,
        valueText = "${if (adj.exposure >= 0) "+" else ""}${"%.2f".format(adj.exposure)} EV",
        onValueChange = { v -> vm.updateActiveMaskAdjustments(true) { it.copy(exposure = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Mask Exposure") }
    )
    StudioSlider(
        label = "Mask Contrast",
        value = adj.contrast,
        valueRange = -100f..100f,
        valueText = signedInt(adj.contrast),
        onValueChange = { v -> vm.updateActiveMaskAdjustments(true) { it.copy(contrast = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Mask Contrast") }
    )
    StudioSlider(
        label = "Mask Highlights",
        value = adj.highlights,
        valueRange = -100f..100f,
        valueText = signedInt(adj.highlights),
        onValueChange = { v -> vm.updateActiveMaskAdjustments(true) { it.copy(highlights = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Mask Highlights") }
    )
    StudioSlider(
        label = "Mask Shadows",
        value = adj.shadows,
        valueRange = -100f..100f,
        valueText = signedInt(adj.shadows),
        onValueChange = { v -> vm.updateActiveMaskAdjustments(true) { it.copy(shadows = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Mask Shadows") }
    )
    StudioSlider(
        label = "Mask Warmth / Temp",
        value = adj.temperatureShift,
        valueRange = -100f..100f,
        valueText = signedInt(adj.temperatureShift),
        onValueChange = { v -> vm.updateActiveMaskAdjustments(true) { it.copy(temperatureShift = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Mask Temp") }
    )
    StudioSlider(
        label = "Mask Saturation",
        value = adj.saturation,
        valueRange = -100f..100f,
        valueText = signedInt(adj.saturation),
        onValueChange = { v -> vm.updateActiveMaskAdjustments(true) { it.copy(saturation = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Mask Saturation") }
    )
}

@Composable
private fun CropPanel(
    state: DevelopUiState,
    vm: DevelopViewModel
) {
    val s = state.settings
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Crop & Geometry", style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { vm.resetCrop() }) {
            Text("Reset Crop", style = MaterialTheme.typography.labelMedium)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CropAspectRatio.entries.forEach { ratio ->
            FilterChip(
                selected = s.aspectRatio == ratio,
                onClick = { vm.setCropAspectRatio(ratio) },
                label = { Text(ratio.label, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    StudioSlider(
        label = "Straighten Angle",
        value = s.straightenAngle,
        valueRange = -45f..45f,
        valueText = "${"%.1f".format(s.straightenAngle)}°",
        onValueChange = { v -> vm.updateSettingsLive { it.copy(straightenAngle = v) } },
        onValueChangeFinished = { vm.commitCurrentSettingsToHistory("Straighten") }
    )

    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(onClick = { vm.rotate90(-1) }) {
            Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Rotate 90 left")
        }
        IconButton(onClick = { vm.rotate90(1) }) {
            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90 right")
        }
        IconButton(onClick = {
            vm.commitSettingsChange("Flip Horizontal") { it.copy(flipHorizontal = !it.flipHorizontal) }
        }) {
            Icon(
                Icons.Default.Flip,
                contentDescription = "Flip horizontal",
                tint = if (s.flipHorizontal) StudioCyan else MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = { vm.cycleCropGrid() }) {
            Icon(Icons.Default.GridOn, contentDescription = "Cycle crop overlay grid", tint = StudioCyan)
        }
    }
    Text(
        text = "Overlay: ${state.cropGrid.label} · Drag corners on canvas to frame",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PresetsPanel(
    state: DevelopUiState,
    vm: DevelopViewModel
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val categories = remember(state.presets) {
        listOf("All") + state.presets.map { it.category }.distinct()
    }
    val filtered = remember(state.presets, selectedCategory) {
        if (selectedCategory == "All") state.presets else state.presets.filter { it.category == selectedCategory }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Preset Library", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.height(36.dp).testTag("save_preset_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Save Preset", style = MaterialTheme.typography.labelMedium)
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        categories.forEach { cat ->
            FilterChip(
                selected = selectedCategory == cat,
                onClick = { selectedCategory = cat },
                label = { Text(cat, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }

    if (state.activePresetId != null) {
        Spacer(modifier = Modifier.height(6.dp))
        StudioSlider(
            label = "Preset Amount",
            value = state.presetAmount * 100f,
            valueRange = 0f..150f,
            defaultValue = 100f,
            valueText = "${(state.presetAmount * 100).roundToInt()}%",
            onValueChange = { v -> vm.setPresetAmount(v / 100f, true) },
            onValueChangeFinished = { vm.setPresetAmount(state.presetAmount, false) }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        filtered.forEach { preset ->
            PresetTile(
                preset = preset,
                isActive = preset.id == state.activePresetId,
                onApply = { vm.applyPreset(preset) },
                onDelete = if (!preset.isBuiltIn) { { vm.deleteCustomPreset(preset.id) } } else null
            )
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Custom") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Save Custom Preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Preset Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.saveCustomPreset(name.ifBlank { "My Look" }, category.ifBlank { "Custom" })
                    showCreateDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PresetTile(
    preset: DevelopPreset,
    isActive: Boolean,
    onApply: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) StudioCyan.copy(alpha = 0.16f) else StudioBackground)
            .border(
                1.dp,
                if (isActive) StudioCyan else StudioOutlineVariant,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onApply)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = preset.category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onDelete != null) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete preset",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onDelete)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        val summary = buildString {
            if (preset.settings.saturation <= -90f) append("B&W · ")
            if (preset.settings.contrast != 0f) append("C${signedInt(preset.settings.contrast)} ")
            if (preset.settings.grainAmount > 0f) append("Grain")
            if (isEmpty()) append("Color Grade")
        }
        Text(
            text = summary,
            style = MonoReadoutStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

private fun signedInt(v: Float): String {
    val i = v.roundToInt()
    return if (i > 0) "+$i" else "$i"
}
