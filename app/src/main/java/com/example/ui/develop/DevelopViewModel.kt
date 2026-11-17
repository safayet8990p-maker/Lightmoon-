package com.example.ui.develop

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.LightmoonRepository
import com.example.engine.AutoToneEngine
import com.example.engine.LightmoonPipeline
import com.example.engine.ScopeAnalyzer
import com.example.model.BrushStroke
import com.example.model.ColorLabel
import com.example.model.ColorWheelTone
import com.example.model.CropAspectRatio
import com.example.model.CropOverlayGrid
import com.example.model.CurvePoint
import com.example.model.DevelopPreset
import com.example.model.DevelopSettings
import com.example.model.EditSnapshot
import com.example.model.ExportConfig
import com.example.model.HistoryEntry
import com.example.model.HslBandAdjustment
import com.example.model.HslColorBand
import com.example.model.MaskAdjustments
import com.example.model.MaskLayer
import com.example.model.MaskType
import com.example.model.PhotoItem
import com.example.model.PickFlag
import com.example.model.ScopeData
import com.example.model.ScopeMode
import com.example.model.ToneCurveChannel
import com.example.model.WhiteBalancePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class DevelopTab(val label: String) {
    LIGHT("Light"),
    COLOR("Color"),
    CURVE("Curve"),
    DETAIL("Detail"),
    EFFECTS("Effects"),
    MASKING("Masking"),
    CROP("Crop"),
    PRESETS("Presets")
}

enum class CompareMode {
    NONE,
    HOLD_ORIGINAL,
    SPLIT_WIPE
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data object Exporting : ExportUiState
    data class Success(val fileName: String, val shareUri: Uri) : ExportUiState
    data class Error(val message: String) : ExportUiState
}

data class DevelopUiState(
    val photo: PhotoItem? = null,
    val allPhotos: List<PhotoItem> = emptyList(),
    val settings: DevelopSettings = DevelopSettings(),
    val previewBitmap: Bitmap? = null,
    val originalBitmap: Bitmap? = null,
    val scopeData: ScopeData = ScopeData(),
    val scopeMode: ScopeMode = ScopeMode.HISTOGRAM,
    val showClipping: Boolean = false,
    val activeTab: DevelopTab = DevelopTab.LIGHT,
    val isDrawerExpanded: Boolean = true,
    val activeHslBand: HslColorBand = HslColorBand.RED,
    val activeCurveChannel: ToneCurveChannel = ToneCurveChannel.RGB,
    val activeMaskId: String? = null,
    val showMaskOverlay: Boolean = true,
    val brushSizeNorm: Float = 0.06f,
    val brushFeather: Float = 0.55f,
    val brushEraseMode: Boolean = false,
    val cropGrid: CropOverlayGrid = CropOverlayGrid.THIRDS,
    val compareMode: CompareMode = CompareMode.NONE,
    val splitFraction: Float = 0.5f,
    val isEyedropperActive: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val historyIndex: Int = -1,
    val presets: List<DevelopPreset> = emptyList(),
    val activePresetId: String? = null,
    val presetAmount: Float = 1.0f,
    val snapshots: List<EditSnapshot> = emptyList(),
    val isRendering: Boolean = false,
    val exportState: ExportUiState = ExportUiState.Idle,
    val statusToast: String? = null
) {
    val canUndo: Boolean get() = historyIndex > 0
    val canRedo: Boolean get() = historyIndex >= 0 && historyIndex < history.size - 1
    val activeMask: MaskLayer? get() = settings.masks.firstOrNull { it.id == activeMaskId }
}

class DevelopViewModel(
    private val repository: LightmoonRepository,
    initialPhotoId: String
) : ViewModel() {

    private val currentPhotoId = MutableStateFlow(initialPhotoId)
    private val workingSourceBitmap = MutableStateFlow<Bitmap?>(null)
    private val previewBitmapState = MutableStateFlow<Bitmap?>(null)
    private val originalBitmapState = MutableStateFlow<Bitmap?>(null)
    private val scopeDataState = MutableStateFlow(ScopeData())
    private val settingsState = MutableStateFlow(DevelopSettings())

    private val activeTabState = MutableStateFlow(DevelopTab.LIGHT)
    private val drawerExpandedState = MutableStateFlow(true)
    private val activeHslBandState = MutableStateFlow(HslColorBand.RED)
    private val activeCurveChannelState = MutableStateFlow(ToneCurveChannel.RGB)
    private val activeMaskIdState = MutableStateFlow<String?>(null)
    private val showMaskOverlayState = MutableStateFlow(true)
    private val brushSizeState = MutableStateFlow(0.06f)
    private val brushFeatherState = MutableStateFlow(0.55f)
    private val brushEraseState = MutableStateFlow(false)
    private val cropGridState = MutableStateFlow(CropOverlayGrid.THIRDS)
    private val compareModeState = MutableStateFlow(CompareMode.NONE)
    private val splitFractionState = MutableStateFlow(0.5f)
    private val eyedropperState = MutableStateFlow(false)
    private val historyState = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val historyIndexState = MutableStateFlow(-1)
    private val activePresetIdState = MutableStateFlow<String?>(null)
    private val presetBaselineState = MutableStateFlow<DevelopSettings?>(null)
    private val presetAmountState = MutableStateFlow(1.0f)
    private val isRenderingState = MutableStateFlow(false)
    private val exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    private val toastState = MutableStateFlow<String?>(null)

    private var renderJob: Job? = null
    private var persistJob: Job? = null
    private var loadedPhotoId: String? = null

    private data class ToolChromeState(
        val activeTab: DevelopTab,
        val expanded: Boolean,
        val hslBand: HslColorBand,
        val curveChannel: ToneCurveChannel,
        val activeMaskId: String?,
        val showMaskOverlay: Boolean,
        val brushSize: Float,
        val brushFeather: Float,
        val brushErase: Boolean,
        val cropGrid: CropOverlayGrid,
        val compareMode: CompareMode,
        val splitFraction: Float,
        val eyedropper: Boolean
    )

    private val toolChromeFlow = combine(
        combine(activeTabState, drawerExpandedState, activeHslBandState, activeCurveChannelState) { t, e, b, c ->
            Quad(t, e, b, c)
        },
        combine(activeMaskIdState, showMaskOverlayState, brushSizeState, brushFeatherState, brushEraseState) { m, o, bs, bf, be ->
            Quint(m, o, bs, bf, be)
        },
        combine(cropGridState, compareModeState, splitFractionState, eyedropperState) { cg, cm, sf, ed ->
            Quad(cg, cm, sf, ed)
        }
    ) { a, b, c ->
        ToolChromeState(
            activeTab = a.first,
            expanded = a.second,
            hslBand = a.third,
            curveChannel = a.fourth,
            activeMaskId = b.first,
            showMaskOverlay = b.second,
            brushSize = b.third,
            brushFeather = b.fourth,
            brushErase = b.fifth,
            cropGrid = c.first,
            compareMode = c.second,
            splitFraction = c.third,
            eyedropper = c.fourth
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    private val mediaFlow = combine(
        previewBitmapState,
        originalBitmapState,
        scopeDataState,
        repository.preferences.scopeMode,
        repository.preferences.showClipping
    ) { preview, orig, scope, mode, clip ->
        Quint(preview, orig, scope, mode, clip)
    }

    private val historyAndPresetFlow = combine(
        historyState,
        historyIndexState,
        repository.observeAllPresets(),
        activePresetIdState,
        presetAmountState
    ) { hist, hIdx, presets, pId, pAmt ->
        Quint(hist, hIdx, presets, pId, pAmt)
    }

    private val miscFlow = combine(
        isRenderingState,
        exportState,
        toastState
    ) { rendering, exp, toast ->
        Triple(rendering, exp, toast)
    }

    val uiState: StateFlow<DevelopUiState> = combine(
        combine(currentPhotoId, repository.observePhotos(), settingsState) { id, all, settings ->
            Triple(all.firstOrNull { it.id == id }, all, settings)
        },
        mediaFlow,
        toolChromeFlow,
        historyAndPresetFlow,
        miscFlow
    ) { photoInfo, media, chrome, histPresets, misc ->
        val photo = photoInfo.first
        DevelopUiState(
            photo = photo,
            allPhotos = photoInfo.second,
            settings = photoInfo.third,
            previewBitmap = media.first,
            originalBitmap = media.second,
            scopeData = media.third,
            scopeMode = media.fourth,
            showClipping = media.fifth,
            activeTab = chrome.activeTab,
            isDrawerExpanded = chrome.expanded,
            activeHslBand = chrome.hslBand,
            activeCurveChannel = chrome.curveChannel,
            activeMaskId = chrome.activeMaskId,
            showMaskOverlay = chrome.showMaskOverlay,
            brushSizeNorm = chrome.brushSize,
            brushFeather = chrome.brushFeather,
            brushEraseMode = chrome.brushErase,
            cropGrid = chrome.cropGrid,
            compareMode = chrome.compareMode,
            splitFraction = chrome.splitFraction,
            isEyedropperActive = chrome.eyedropper,
            history = histPresets.first,
            historyIndex = histPresets.second,
            presets = histPresets.third,
            activePresetId = histPresets.fourth,
            presetAmount = histPresets.fifth,
            snapshots = emptyList(), // Observed separately below
            isRendering = misc.first,
            exportState = misc.second,
            statusToast = misc.third
        )
    }.combine(
        currentPhotoId.let { idFlow ->
            kotlinx.coroutines.flow.flow {
                idFlow.collect { id ->
                    repository.observeSnapshots(id).collect { emit(it) }
                }
            }
        }
    ) { state, snapshots ->
        state.copy(snapshots = snapshots)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DevelopUiState()
    )

    init {
        viewModelScope.launch {
            combine(currentPhotoId, repository.observePhotos()) { id, list ->
                list.firstOrNull { it.id == id }
            }.collect { item ->
                if (item != null && item.id != loadedPhotoId) {
                    loadPhotoIntoStudio(item)
                }
            }
        }
    }

    fun selectPhoto(photoId: String) {
        if (photoId == currentPhotoId.value) return
        currentPhotoId.value = photoId
    }

    private fun loadPhotoIntoStudio(item: PhotoItem) {
        loadedPhotoId = item.id
        settingsState.value = item.developSettings
        activeMaskIdState.value = item.developSettings.masks.firstOrNull()?.id
        activePresetIdState.value = null
        presetBaselineState.value = null
        presetAmountState.value = 1f
        historyState.value = listOf(HistoryEntry("Original / Opened", item.developSettings))
        historyIndexState.value = 0

        viewModelScope.launch {
            isRenderingState.value = true
            val working = repository.storage.loadWorkingBitmap(item, maxLongEdge = 1080)
            workingSourceBitmap.value = working
            scheduleRender(immediate = true)
        }
    }

    fun setActiveTab(tab: DevelopTab) {
        if (activeTabState.value == tab) {
            drawerExpandedState.value = !drawerExpandedState.value
        } else {
            activeTabState.value = tab
            drawerExpandedState.value = true
        }
        if (tab != DevelopTab.COLOR) eyedropperState.value = false
        scheduleRender()
    }

    fun setDrawerExpanded(expanded: Boolean) {
        drawerExpandedState.value = expanded
    }

    fun cycleScopeMode() {
        viewModelScope.launch {
            val modes = ScopeMode.entries
            val next = modes[(modes.indexOf(uiState.value.scopeMode) + 1) % modes.size]
            repository.preferences.setScopeMode(next)
        }
    }

    fun toggleClippingOverlay() {
        viewModelScope.launch {
            repository.preferences.setShowClipping(!uiState.value.showClipping)
            scheduleRender(immediate = true)
        }
    }

    fun toggleEyedropper() {
        eyedropperState.value = !eyedropperState.value
    }

    fun pickWhiteBalanceAt(normX: Float, normY: Float) {
        val src = workingSourceBitmap.value ?: return
        val geo = LightmoonPipeline.applyGeometry(src, settingsState.value)
        val (kelvin, tint) = LightmoonPipeline.pickWhiteBalanceAt(geo, normX, normY)
        if (geo !== src) geo.recycle()
        eyedropperState.value = false
        updateSettings(
            settingsState.value.copy(
                wbPreset = WhiteBalancePreset.CUSTOM,
                temperatureKelvin = kelvin,
                tint = tint
            ),
            historyLabel = "WB Eyedropper ($kelvin K)"
        )
    }

    fun runAutoTone() {
        val src = workingSourceBitmap.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val auto = AutoToneEngine.computeAutoTone(src, settingsState.value)
            withContext(Dispatchers.Main) {
                updateSettings(auto, historyLabel = "Auto Tone & WB")
                toastState.value = "Applied On-Device Auto Tone"
            }
        }
    }

    fun updateSettingsLive(transform: (DevelopSettings) -> DevelopSettings) {
        settingsState.update(transform)
        scheduleRender()
    }

    fun commitSettingsChange(label: String, transform: (DevelopSettings) -> DevelopSettings) {
        val next = transform(settingsState.value)
        updateSettings(next, historyLabel = label)
    }

    fun commitCurrentSettingsToHistory(label: String) {
        val current = settingsState.value
        val curList = historyState.value
        val curIdx = historyIndexState.value
        if (curIdx in curList.indices && curList[curIdx].settings == current) return
        val trimmed = if (curIdx >= 0 && curIdx < curList.size - 1) curList.subList(0, curIdx + 1) else curList
        val nextList = (trimmed + HistoryEntry(label, current)).takeLast(40)
        historyState.value = nextList
        historyIndexState.value = nextList.lastIndex
        persistSettingsDebounced()
    }

    private fun updateSettings(next: DevelopSettings, historyLabel: String) {
        settingsState.value = next
        val curList = historyState.value
        val curIdx = historyIndexState.value
        val trimmed = if (curIdx >= 0 && curIdx < curList.size - 1) curList.subList(0, curIdx + 1) else curList
        val nextList = (trimmed + HistoryEntry(historyLabel, next)).takeLast(40)
        historyState.value = nextList
        historyIndexState.value = nextList.lastIndex
        scheduleRender(immediate = true)
        persistSettingsDebounced()
    }

    fun undo() {
        val idx = historyIndexState.value
        if (idx <= 0) return
        val target = historyState.value[idx - 1]
        historyIndexState.value = idx - 1
        settingsState.value = target.settings
        scheduleRender(immediate = true)
        persistSettingsDebounced()
    }

    fun redo() {
        val idx = historyIndexState.value
        val list = historyState.value
        if (idx < 0 || idx >= list.lastIndex) return
        val target = list[idx + 1]
        historyIndexState.value = idx + 1
        settingsState.value = target.settings
        scheduleRender(immediate = true)
        persistSettingsDebounced()
    }

    fun jumpToHistory(index: Int) {
        val list = historyState.value
        if (index !in list.indices) return
        historyIndexState.value = index
        settingsState.value = list[index].settings
        scheduleRender(immediate = true)
        persistSettingsDebounced()
    }

    fun resetAll() {
        updateSettings(DevelopSettings(), "Reset All Adjustments")
        activeMaskIdState.value = null
        activePresetIdState.value = null
        presetBaselineState.value = null
        toastState.value = "Reset to Original"
    }

    fun setCompareMode(mode: CompareMode) {
        compareModeState.value = mode
    }

    fun setSplitFraction(fraction: Float) {
        splitFractionState.value = fraction.coerceIn(0.08f, 0.92f)
    }

    fun setActiveHslBand(band: HslColorBand) {
        activeHslBandState.value = band
    }

    fun updateHslBand(band: HslColorBand, liveOnly: Boolean, transform: (HslBandAdjustment) -> HslBandAdjustment) {
        val updater: (DevelopSettings) -> DevelopSettings = { s ->
            val updated = s.hslBands.map { if (it.band == band) transform(it) else it }
            s.copy(hslBands = updated)
        }
        if (liveOnly) updateSettingsLive(updater) else commitSettingsChange("HSL ${band.label}", updater)
    }

    fun setActiveCurveChannel(channel: ToneCurveChannel) {
        activeCurveChannelState.value = channel
    }

    fun updateCurvePoints(channel: ToneCurveChannel, points: List<CurvePoint>, liveOnly: Boolean) {
        val updater: (DevelopSettings) -> DevelopSettings = { s ->
            when (channel) {
                ToneCurveChannel.RGB -> s.copy(rgbCurve = points)
                ToneCurveChannel.RED -> s.copy(redCurve = points)
                ToneCurveChannel.GREEN -> s.copy(greenCurve = points)
                ToneCurveChannel.BLUE -> s.copy(blueCurve = points)
            }
        }
        if (liveOnly) updateSettingsLive(updater) else commitSettingsChange("${channel.label} Curve", updater)
    }

    fun resetActiveCurve() {
        val ch = activeCurveChannelState.value
        val identity = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
        updateCurvePoints(ch, identity, liveOnly = false)
    }

    fun setColorWheel(target: String, tone: ColorWheelTone, liveOnly: Boolean) {
        val updater: (DevelopSettings) -> DevelopSettings = { s ->
            val g = s.colorGrading
            val updated = when (target) {
                "Shadows" -> g.copy(shadows = tone)
                "Midtones" -> g.copy(midtones = tone)
                else -> g.copy(highlights = tone)
            }
            s.copy(colorGrading = updated)
        }
        if (liveOnly) updateSettingsLive(updater) else commitSettingsChange("Grade $target", updater)
    }

    // Masking controls
    fun addMask(type: MaskType) {
        val count = settingsState.value.masks.size + 1
        val mask = MaskLayer(
            id = UUID.randomUUID().toString(),
            name = "${type.label} $count",
            type = type
        )
        activeMaskIdState.value = mask.id
        showMaskOverlayState.value = true
        updateSettings(
            settingsState.value.copy(masks = settingsState.value.masks + mask),
            historyLabel = "Add ${type.label} Mask"
        )
    }

    fun selectMask(maskId: String?) {
        activeMaskIdState.value = maskId
        scheduleRender(immediate = true)
    }

    fun toggleMaskOverlay() {
        showMaskOverlayState.value = !showMaskOverlayState.value
        scheduleRender(immediate = true)
    }

    fun setBrushParams(sizeNorm: Float? = null, feather: Float? = null, erase: Boolean? = null) {
        sizeNorm?.let { brushSizeState.value = it.coerceIn(0.015f, 0.18f) }
        feather?.let { brushFeatherState.value = it.coerceIn(0.1f, 1f) }
        erase?.let { brushEraseState.value = it }
    }

    fun appendBrushStroke(pointsX: List<Float>, pointsY: List<Float>) {
        val maskId = activeMaskIdState.value ?: return
        if (pointsX.isEmpty()) return
        val stroke = BrushStroke(
            pointsX = pointsX,
            pointsY = pointsY,
            radiusNorm = brushSizeState.value,
            feather = brushFeatherState.value,
            flow = 0.85f,
            erase = brushEraseState.value
        )
        commitSettingsChange("Brush Stroke") { s ->
            s.copy(
                masks = s.masks.map { m ->
                    if (m.id == maskId) m.copy(brushStrokes = m.brushStrokes + stroke) else m
                }
            )
        }
    }

    fun updateActiveMask(liveOnly: Boolean, label: String = "Mask Edit", transform: (MaskLayer) -> MaskLayer) {
        val maskId = activeMaskIdState.value ?: return
        val updater: (DevelopSettings) -> DevelopSettings = { s ->
            s.copy(masks = s.masks.map { if (it.id == maskId) transform(it) else it })
        }
        if (liveOnly) updateSettingsLive(updater) else commitSettingsChange(label, updater)
    }

    fun updateActiveMaskAdjustments(liveOnly: Boolean, transform: (MaskAdjustments) -> MaskAdjustments) {
        updateActiveMask(liveOnly, "Mask Adjustment") { mask ->
            mask.copy(adjustments = transform(mask.adjustments))
        }
    }

    fun deleteMask(maskId: String) {
        val remaining = settingsState.value.masks.filterNot { it.id == maskId }
        if (activeMaskIdState.value == maskId) {
            activeMaskIdState.value = remaining.firstOrNull()?.id
        }
        updateSettings(settingsState.value.copy(masks = remaining), "Delete Mask")
    }

    // Crop & Geometry
    fun setCropAspectRatio(ratio: CropAspectRatio) {
        val src = workingSourceBitmap.value
        val imgAspect = if (src != null && src.height > 0) src.width.toFloat() / src.height else 1.5f
        val targetAspect = when (ratio) {
            CropAspectRatio.FREE -> null
            CropAspectRatio.ORIGINAL -> imgAspect
            else -> ratio.ratio
        }
        commitSettingsChange("Crop ${ratio.label}") { s ->
            if (targetAspect == null) {
                s.copy(aspectRatio = ratio)
            } else {
                val curW = s.cropRight - s.cropLeft
                val curH = s.cropBottom - s.cropTop
                val cx = (s.cropLeft + s.cropRight) * 0.5f
                val cy = (s.cropTop + s.cropBottom) * 0.5f
                // Convert target pixel aspect ratio into normalized image coordinates
                val normRatio = targetAspect / imgAspect
                var newW = curW
                var newH = newW / normRatio
                if (newH > 1f) {
                    newH = 1f
                    newW = newH * normRatio
                }
                if (newW > 1f) {
                    newW = 1f
                    newH = newW / normRatio
                }
                val l = (cx - newW * 0.5f).coerceIn(0f, 1f - newW)
                val t = (cy - newH * 0.5f).coerceIn(0f, 1f - newH)
                s.copy(
                    aspectRatio = ratio,
                    cropLeft = l,
                    cropTop = t,
                    cropRight = (l + newW).coerceIn(l + 0.05f, 1f),
                    cropBottom = (t + newH).coerceIn(t + 0.05f, 1f)
                )
            }
        }
    }

    fun cycleCropGrid() {
        val grids = CropOverlayGrid.entries
        cropGridState.value = grids[(grids.indexOf(cropGridState.value) + 1) % grids.size]
    }

    fun rotate90(delta: Int) {
        commitSettingsChange("Rotate 90°") { s ->
            s.copy(rotationQuarterTurns = (s.rotationQuarterTurns + delta + 4) % 4)
        }
    }

    fun resetCrop() {
        commitSettingsChange("Reset Crop") { s ->
            s.copy(
                cropLeft = 0f,
                cropTop = 0f,
                cropRight = 1f,
                cropBottom = 1f,
                aspectRatio = CropAspectRatio.FREE,
                straightenAngle = 0f,
                rotationQuarterTurns = 0,
                flipHorizontal = false,
                flipVertical = false
            )
        }
    }

    // Presets & Snapshots
    fun applyPreset(preset: DevelopPreset) {
        val baseline = presetBaselineState.value ?: settingsState.value.also {
            presetBaselineState.value = it
        }
        activePresetIdState.value = preset.id
        presetAmountState.value = 1.0f
        val blended = preset.settings.blendedWith(baseline, 1.0f).copy(
            cropLeft = baseline.cropLeft,
            cropTop = baseline.cropTop,
            cropRight = baseline.cropRight,
            cropBottom = baseline.cropBottom,
            aspectRatio = baseline.aspectRatio,
            straightenAngle = baseline.straightenAngle,
            rotationQuarterTurns = baseline.rotationQuarterTurns,
            flipHorizontal = baseline.flipHorizontal,
            flipVertical = baseline.flipVertical,
            masks = baseline.masks
        )
        updateSettings(blended, "Preset: ${preset.name}")
    }

    fun setPresetAmount(amount: Float, liveOnly: Boolean) {
        val clamped = amount.coerceIn(0f, 1.5f)
        presetAmountState.value = clamped
        val presetId = activePresetIdState.value ?: return
        val preset = uiState.value.presets.firstOrNull { it.id == presetId } ?: return
        val baseline = presetBaselineState.value ?: DevelopSettings()
        val blended = preset.settings.blendedWith(baseline, clamped).copy(
            cropLeft = baseline.cropLeft,
            cropTop = baseline.cropTop,
            cropRight = baseline.cropRight,
            cropBottom = baseline.cropBottom,
            aspectRatio = baseline.aspectRatio,
            straightenAngle = baseline.straightenAngle,
            rotationQuarterTurns = baseline.rotationQuarterTurns,
            flipHorizontal = baseline.flipHorizontal,
            flipVertical = baseline.flipVertical,
            masks = baseline.masks
        )
        if (liveOnly) {
            settingsState.value = blended
            scheduleRender()
        } else {
            updateSettings(blended, "Preset ${preset.name} (${(clamped * 100).toInt()}%)")
        }
    }

    fun saveCustomPreset(name: String, category: String) {
        viewModelScope.launch {
            repository.saveCustomPreset(name, category, settingsState.value)
            toastState.value = "Saved preset \"$name\""
        }
    }

    fun deleteCustomPreset(presetId: String) {
        viewModelScope.launch {
            repository.deleteCustomPreset(presetId)
            if (activePresetIdState.value == presetId) activePresetIdState.value = null
            toastState.value = "Deleted custom preset"
        }
    }

    fun createSnapshot(name: String) {
        val photoId = currentPhotoId.value
        viewModelScope.launch {
            repository.createSnapshot(photoId, name, settingsState.value)
            toastState.value = "Snapshot \"$name\" saved"
        }
    }

    fun restoreSnapshot(snapshot: EditSnapshot) {
        updateSettings(snapshot.settings, "Snapshot: ${snapshot.name}")
    }

    fun deleteSnapshot(snapshotId: String) {
        viewModelScope.launch {
            repository.deleteSnapshot(snapshotId)
        }
    }

    fun updateTriage(rating: Int? = null, flag: PickFlag? = null, colorLabel: ColorLabel? = null) {
        val id = currentPhotoId.value
        viewModelScope.launch {
            repository.updateTriage(id, rating, flag, colorLabel)
        }
    }

    fun exportPhoto(config: ExportConfig) {
        val photo = uiState.value.photo ?: return
        viewModelScope.launch {
            exportState.value = ExportUiState.Exporting
            // Persist latest settings first
            repository.updateDevelopSettings(photo.id, settingsState.value, refreshThumb = true)
            val updatedPhoto = photo.copy(developSettings = settingsState.value)
            val res = repository.exportPhoto(updatedPhoto, config)
            exportState.value = res.fold(
                onSuccess = { out -> ExportUiState.Success(out.displayName, out.shareUri) },
                onFailure = { err -> ExportUiState.Error(err.localizedMessage ?: "Export failed") }
            )
        }
    }

    fun resetExportState() {
        exportState.value = ExportUiState.Idle
    }

    fun clearToast() {
        toastState.value = null
    }

    private fun scheduleRender(immediate: Boolean = false) {
        val src = workingSourceBitmap.value ?: return
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            if (!immediate) delay(16L)
            isRenderingState.value = true
            val currentSettings = settingsState.value
            val showClip = uiState.value.showClipping
            val maskOverlayId = if (activeTabState.value == DevelopTab.MASKING && showMaskOverlayState.value) {
                activeMaskIdState.value
            } else null
            val skipCrop = activeTabState.value == DevelopTab.CROP

            val (rendered, originalGeo, scope) = withContext(Dispatchers.Default) {
                val out = LightmoonPipeline.render(
                    source = src,
                    settings = currentSettings,
                    showClippingOverlay = showClip,
                    activeMaskIdOverlay = maskOverlayId,
                    skipCrop = skipCrop
                )
                val orig = LightmoonPipeline.applyGeometry(src, currentSettings, skipCrop = skipCrop)
                val sc = ScopeAnalyzer.analyze(out)
                Triple(out, orig, sc)
            }
            previewBitmapState.value = rendered
            originalBitmapState.value = originalGeo
            scopeDataState.value = scope
            isRenderingState.value = false
        }
    }

    private fun persistSettingsDebounced() {
        val photoId = currentPhotoId.value
        val settings = settingsState.value
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(350L)
            repository.updateDevelopSettings(photoId, settings, refreshThumb = true)
        }
    }

    class Factory(
        private val repository: LightmoonRepository,
        private val photoId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DevelopViewModel(repository, photoId) as T
        }
    }
}
