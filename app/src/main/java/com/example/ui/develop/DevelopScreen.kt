package com.example.ui.develop

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.R
import com.example.model.PickFlag
import com.example.model.ScopeMode
import com.example.ui.components.ExportSheet
import com.example.ui.components.ScopesOverlay
import com.example.ui.components.StudioCanvas
import com.example.ui.theme.DarkroomAmber
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOutlineVariant
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighest
import java.io.File

@Composable
fun DevelopScreen(
    viewModel: DevelopViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExportSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showFilmstrip by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(state.statusToast) {
        val msg = state.statusToast
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    Scaffold(
        containerColor = StudioBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTabletExpanded = maxWidth >= 720.dp

            if (isTabletExpanded) {
                // Canonical Tablet/Foldable Side-by-Side Studio Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    DevelopTopBar(
                        state = state,
                        onBack = onBack,
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onHistory = { showHistorySheet = true },
                        onCycleCompare = {
                            val next = when (state.compareMode) {
                                CompareMode.NONE -> CompareMode.SPLIT_WIPE
                                CompareMode.SPLIT_WIPE -> CompareMode.HOLD_ORIGINAL
                                CompareMode.HOLD_ORIGINAL -> CompareMode.NONE
                            }
                            viewModel.setCompareMode(next)
                        },
                        onReset = { viewModel.resetAll() },
                        onExport = {
                            viewModel.resetExportState()
                            showExportSheet = true
                        }
                    )
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            CanvasWithHud(state, viewModel, showFilmstrip, onToggleFilmstrip = { showFilmstrip = !showFilmstrip })
                        }
                        Column(
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight()
                                .background(StudioSurface)
                                .border(1.dp, StudioOutlineVariant)
                        ) {
                            ToolDockRow(
                                activeTab = state.activeTab,
                                isExpanded = true,
                                onSelectTab = { viewModel.setActiveTab(it) }
                            )
                            HorizontalDivider(color = StudioOutlineVariant)
                            DevelopToolPanelContent(
                                state = state,
                                viewModel = viewModel,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                // Phone-Optimized Layout: Upper 60-70% Photo Canvas + Thumb-Friendly Bottom Dock & Expandable Drawer
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.statusBarsPadding()) {
                        DevelopTopBar(
                            state = state,
                            onBack = onBack,
                            onUndo = { viewModel.undo() },
                            onRedo = { viewModel.redo() },
                            onHistory = { showHistorySheet = true },
                            onCycleCompare = {
                                val next = when (state.compareMode) {
                                    CompareMode.NONE -> CompareMode.SPLIT_WIPE
                                    CompareMode.SPLIT_WIPE -> CompareMode.HOLD_ORIGINAL
                                    CompareMode.HOLD_ORIGINAL -> CompareMode.NONE
                                }
                                viewModel.setCompareMode(next)
                            },
                            onReset = { viewModel.resetAll() },
                            onExport = {
                                viewModel.resetExportState()
                                showExportSheet = true
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        CanvasWithHud(state, viewModel, showFilmstrip, onToggleFilmstrip = { showFilmstrip = !showFilmstrip })
                    }

                    // Expandable Darkroom Drawer + Bottom Thumb Dock
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioSurface)
                            .navigationBarsPadding()
                    ) {
                        HorizontalDivider(color = StudioOutlineVariant)
                        AnimatedVisibility(visible = state.isDrawerExpanded) {
                            Column {
                                // Collapse handle & quick triage strip
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setDrawerExpanded(false) }
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    QuickTriageRow(state = state, viewModel = viewModel)
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Collapse tool drawer",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                HorizontalDivider(color = StudioOutlineVariant)
                                DevelopToolPanelContent(
                                    state = state,
                                    viewModel = viewModel,
                                    modifier = Modifier.heightIn(min = 190.dp, max = 264.dp)
                                )
                                HorizontalDivider(color = StudioOutlineVariant)
                            }
                        }

                        if (!state.isDrawerExpanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setDrawerExpanded(true) }
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QuickTriageRow(state = state, viewModel = viewModel)
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Expand tool drawer",
                                    tint = StudioCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HorizontalDivider(color = StudioOutlineVariant)
                        }

                        ToolDockRow(
                            activeTab = state.activeTab,
                            isExpanded = state.isDrawerExpanded,
                            onSelectTab = { viewModel.setActiveTab(it) }
                        )
                    }
                }
            }
        }
    }

    if (showExportSheet) {
        ExportSheet(
            title = "Export ${state.photo?.title ?: "Photo"}",
            exportState = state.exportState,
            onDismiss = { showExportSheet = false },
            onExport = { config -> viewModel.exportPhoto(config) }
        )
    }

    if (showHistorySheet) {
        HistoryAndSnapshotsSheet(
            state = state,
            onJumpHistory = { idx -> viewModel.jumpToHistory(idx) },
            onCreateSnapshot = { name -> viewModel.createSnapshot(name) },
            onRestoreSnapshot = { snap -> viewModel.restoreSnapshot(snap) },
            onDeleteSnapshot = { id -> viewModel.deleteSnapshot(id) },
            onDismiss = { showHistorySheet = false }
        )
    }
}

@Composable
private fun CanvasWithHud(
    state: DevelopUiState,
    viewModel: DevelopViewModel,
    showFilmstrip: Boolean,
    onToggleFilmstrip: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        StudioCanvas(
            previewBitmap = state.previewBitmap,
            originalBitmap = state.originalBitmap,
            settings = state.settings,
            activeTab = state.activeTab,
            activeMask = state.activeMask,
            cropGrid = state.cropGrid,
            compareMode = state.compareMode,
            splitFraction = state.splitFraction,
            isEyedropperActive = state.isEyedropperActive,
            brushSizeNorm = state.brushSizeNorm,
            onSplitFractionChange = { viewModel.setSplitFraction(it) },
            onPickWhiteBalance = { x, y -> viewModel.pickWhiteBalanceAt(x, y) },
            onUpdateCrop = { l, t, r, b, live ->
                val updater: (com.example.model.DevelopSettings) -> com.example.model.DevelopSettings = { s ->
                    s.copy(cropLeft = l, cropTop = t, cropRight = r, cropBottom = b)
                }
                if (live) viewModel.updateSettingsLive(updater)
                else viewModel.commitSettingsChange("Crop", updater)
            },
            onAppendBrushStroke = { xs, ys -> viewModel.appendBrushStroke(xs, ys) },
            onUpdateMaskHandles = { updated, live ->
                viewModel.updateActiveMask(live, "Mask Position") { updated }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top-right Live Scopes Overlay (or compact reopen pill when ScopeMode.OFF)
        if (state.scopeMode == ScopeMode.OFF) {
            Text(
                text = "SCOPES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioSurface.copy(alpha = 0.8f))
                    .border(1.dp, StudioOutlineVariant, RoundedCornerShape(8.dp))
                    .clickable { viewModel.cycleScopeMode() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        } else {
            ScopesOverlay(
                scopeData = state.scopeData,
                scopeMode = state.scopeMode,
                showClipping = state.showClipping,
                onCycleScopeMode = { viewModel.cycleScopeMode() },
                onToggleClipping = { viewModel.toggleClippingOverlay() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            )
        }

        // Top-left Filmstrip toggle & rendering spinner
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioSurface.copy(alpha = 0.82f))
                    .border(1.dp, StudioOutlineVariant, RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleFilmstrip)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = state.photo?.title ?: "Developing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(104.dp)
                )
                Icon(
                    if (showFilmstrip) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle filmstrip",
                    modifier = Modifier.size(16.dp)
                )
            }
            if (state.isRendering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = StudioCyan,
                    strokeWidth = 2.dp
                )
            }
        }

        if (showFilmstrip && state.allPhotos.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(StudioSurface.copy(alpha = 0.9f))
            ) {
                items(state.allPhotos, key = { it.id }) { item ->
                    val selected = item.id == state.photo?.id
                    Image(
                        painter = rememberAsyncImagePainter(File(item.thumbnailPath)),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) StudioCyan else StudioOutlineVariant,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { viewModel.selectPhoto(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DevelopTopBar(
    state: DevelopUiState,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onHistory: () -> Unit,
    onCycleCompare: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("develop_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            IconButton(onClick = onUndo, enabled = state.canUndo, modifier = Modifier.testTag("undo_button")) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.cd_undo))
            }
            IconButton(onClick = onRedo, enabled = state.canRedo, modifier = Modifier.testTag("redo_button")) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.cd_redo))
            }
            IconButton(onClick = onHistory, modifier = Modifier.testTag("history_button")) {
                Icon(Icons.Default.History, contentDescription = stringResource(R.string.cd_history))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCycleCompare, modifier = Modifier.testTag("compare_button")) {
                Icon(
                    Icons.Default.Compare,
                    contentDescription = stringResource(R.string.cd_compare),
                    tint = if (state.compareMode != CompareMode.NONE) StudioCyan else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onReset, modifier = Modifier.testTag("reset_all_button")) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_reset))
            }
            Button(
                onClick = onExport,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = MaterialTheme.colorScheme.onSecondary),
                modifier = Modifier
                    .height(36.dp)
                    .padding(end = 6.dp)
                    .testTag("open_export_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun QuickTriageRow(
    state: DevelopUiState,
    viewModel: DevelopViewModel
) {
    val photo = state.photo ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (star in 1..5) {
            Icon(
                imageVector = if (star <= photo.rating) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Rate $star stars",
                tint = if (star <= photo.rating) DarkroomAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .clickable {
                        val next = if (photo.rating == star) 0 else star
                        viewModel.updateTriage(rating = next)
                    }
                    .padding(2.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = stringResource(R.string.cd_pick_flag),
            tint = when (photo.flag) {
                PickFlag.PICK -> StudioSuccess
                PickFlag.REJECT -> MaterialTheme.colorScheme.error
                PickFlag.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .size(20.dp)
                .clickable {
                    val next = when (photo.flag) {
                        PickFlag.NONE -> PickFlag.PICK
                        PickFlag.PICK -> PickFlag.REJECT
                        PickFlag.REJECT -> PickFlag.NONE
                    }
                    viewModel.updateTriage(flag = next)
                }
        )
    }
}

@Composable
private fun ToolDockRow(
    activeTab: DevelopTab,
    isExpanded: Boolean,
    onSelectTab: (DevelopTab) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().background(StudioSurface)
    ) {
        items(DevelopTab.entries) { tab ->
            val selected = tab == activeTab && isExpanded
            val icon: ImageVector = when (tab) {
                DevelopTab.LIGHT -> Icons.Default.WbSunny
                DevelopTab.COLOR -> Icons.Default.Palette
                DevelopTab.CURVE -> Icons.Default.ShowChart
                DevelopTab.DETAIL -> Icons.Default.Tune
                DevelopTab.EFFECTS -> Icons.Default.Grain
                DevelopTab.MASKING -> Icons.Default.Layers
                DevelopTab.CROP -> Icons.Default.Crop
                DevelopTab.PRESETS -> Icons.Default.AutoAwesome
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .width(66.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) StudioSurfaceElevated else Color.Transparent)
                    .clickable { onSelectTab(tab) }
                    .testTag("tab_${tab.name.lowercase()}")
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = tab.label,
                    tint = if (selected) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryAndSnapshotsSheet(
    state: DevelopUiState,
    onJumpHistory: (Int) -> Unit,
    onCreateSnapshot: (String) -> Unit,
    onRestoreSnapshot: (com.example.model.EditSnapshot) -> Unit,
    onDeleteSnapshot: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showSnapDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("History & Snapshots", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { showSnapDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Snapshot")
                }
            }

            if (state.snapshots.isNotEmpty()) {
                Text("Saved Snapshots", style = MaterialTheme.typography.labelLarge, color = StudioCyan)
                Spacer(modifier = Modifier.height(6.dp))
                state.snapshots.forEach { snap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onRestoreSnapshot(snap)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = DarkroomAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(snap.name, style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = { onDeleteSnapshot(snap.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete snapshot", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HorizontalDivider(color = StudioOutlineVariant, modifier = Modifier.padding(vertical = 8.dp))
            }

            Text("Edit History (${state.history.size})", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                itemsIndexed(state.history.reversed()) { revIdx, entry ->
                    val actualIdx = state.history.lastIndex - revIdx
                    val isCurrent = actualIdx == state.historyIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) StudioCyan.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onJumpHistory(actualIdx) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Timeline,
                            contentDescription = null,
                            tint = if (isCurrent) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrent) StudioCyan else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    if (showSnapDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSnapDialog = false },
            title = { Text("Create Snapshot") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Snapshot Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onCreateSnapshot(name.ifBlank { "Look ${state.snapshots.size + 1}" })
                    showSnapDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSnapDialog = false }) { Text("Cancel") }
            }
        )
    }
}
