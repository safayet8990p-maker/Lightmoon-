package com.example.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.R
import com.example.model.ColorLabel
import com.example.model.PhotoItem
import com.example.model.PickFlag
import com.example.ui.components.ExportSheet
import com.example.ui.develop.ExportUiState
import com.example.ui.theme.DarkroomAmber
import com.example.ui.theme.LabelBlue
import com.example.ui.theme.LabelGreen
import com.example.ui.theme.LabelPurple
import com.example.ui.theme.LabelRed
import com.example.ui.theme.LabelYellow
import com.example.ui.theme.MonoReadoutStyle
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOutlineVariant
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenPhoto: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortMenu by remember { mutableStateOf(false) }
    var showImportChooser by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showBatchExportSheet by remember { mutableStateOf(false) }
    var inspectPhoto by remember { mutableStateOf<PhotoItem?>(null) }

    // Play-compliant zero-permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importUris(uris)
    }

    // SAF OpenDocument for RAW / DNG / TIFF / Image files
    val rawFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importUris(uris)
    }

    // SAF CreateDocument / OpenDocument for cross-device .lightmoon preset & rating sync bundle
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportBackupPackage(it) }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBackupPackage(it) }
    }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = StudioBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.isSelectionMode) {
                FloatingActionButton(
                    onClick = { showImportChooser = true },
                    containerColor = StudioCyan,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.testTag("import_fab")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.cd_import_photos))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isSelectionMode) {
                BatchSelectionBar(
                    selectedCount = state.selectedIds.size,
                    hasCopiedSettings = state.copiedSettings != null,
                    onClear = { viewModel.clearSelection() },
                    onSelectAll = { viewModel.selectAllVisible() },
                    onPaste = { viewModel.pasteSettingsToSelected() },
                    onFlagPick = { viewModel.batchSetFlag(PickFlag.PICK) },
                    onBatchRate5 = { viewModel.batchSetRating(5) },
                    onBatchExport = { showBatchExportSheet = true },
                    onBatchDelete = { viewModel.batchDeleteSelected() }
                )
            } else {
                LibraryHeaderBar(
                    photoCount = state.totalCount,
                    onCycleGrid = { viewModel.cycleGridColumns() },
                    onOpenSync = { showSyncDialog = true },
                    onOpenSort = { showSortMenu = true },
                    sortMenuExpanded = showSortMenu,
                    currentSort = state.sort,
                    onSelectSort = {
                        viewModel.setSort(it)
                        showSortMenu = false
                    },
                    onDismissSort = { showSortMenu = false }
                )
            }

            if (state.isBusy || state.batchExportProgress != null) {
                LinearProgressIndicator(
                    color = StudioCyan,
                    modifier = Modifier.fillMaxWidth()
                )
                state.batchExportProgress?.let { progressText ->
                    Text(
                        text = progressText,
                        style = MonoReadoutStyle,
                        color = StudioCyan,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Search & Filter Strip
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search filename, camera, lens…", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("library_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibraryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                listOf(
                    ColorLabel.RED to LabelRed,
                    ColorLabel.YELLOW to LabelYellow,
                    ColorLabel.GREEN to LabelGreen,
                    ColorLabel.BLUE to LabelBlue,
                    ColorLabel.PURPLE to LabelPurple
                ).forEach { (label, color) ->
                    val selected = state.colorFilter == label
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = if (selected) 1f else 0.45f))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color.White else StudioOutlineVariant,
                                shape = CircleShape
                            )
                            .clickable { viewModel.setColorFilter(label) }
                    )
                }
            }

            // Photo Catalog Grid
            if (state.photos.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = StudioCyan,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.label_no_photos_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.label_no_photos_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Text("Import from Gallery")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.gridColumns),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(state.photos, key = { it.id }) { photo ->
                        PhotoGridCard(
                            photo = photo,
                            isSelected = photo.id in state.selectedIds,
                            selectionMode = state.isSelectionMode,
                            onClick = {
                                if (state.isSelectionMode) viewModel.toggleSelection(photo.id)
                                else onOpenPhoto(photo.id)
                            },
                            onLongClick = { viewModel.toggleSelection(photo.id) },
                            onInspect = { inspectPhoto = photo },
                            onCopySettings = { viewModel.copyDevelopSettings(photo) }
                        )
                    }
                }
            }
        }
    }

    if (showImportChooser) {
        AlertDialog(
            onDismissRequest = { showImportChooser = false },
            title = { Text("Import into Lightmoon") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Photos are cached locally for 100% offline non-destructive editing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            showImportChooser = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("pick_gallery_button")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.label_import_picker))
                    }
                    OutlinedButton(
                        onClick = {
                            showImportChooser = false
                            rawFileLauncher.launch(arrayOf("image/*", "image/x-adobe-dng", "application/octet-stream"))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("pick_raw_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.label_import_raw))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportChooser = false }) { Text("Close") }
            }
        )
    }

    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            title = { Text("Offline Catalog & Preset Sync") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Lightmoon runs offline-first on your device. Export a compact .lightmoon sync bundle to transfer custom presets, star ratings, flags, and edit recipes across devices or cloud folders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            showSyncDialog = false
                            exportBackupLauncher.launch("lightmoon_sync_bundle.lightmoon.json")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Presets & Ratings Bundle")
                    }
                    OutlinedButton(
                        onClick = {
                            showSyncDialog = false
                            importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import & Merge Sync Bundle")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSyncDialog = false }) { Text("Done") }
            }
        )
    }

    if (showBatchExportSheet) {
        ExportSheet(
            title = "Batch Export (${state.selectedIds.size} Photos)",
            exportState = ExportUiState.Idle,
            isBatchMode = true,
            onDismiss = { showBatchExportSheet = false },
            onExport = { cfg ->
                showBatchExportSheet = false
                viewModel.batchExportSelected(cfg)
            }
        )
    }

    inspectPhoto?.let { photo ->
        PhotoInspectorSheet(
            photo = photo,
            onRate = { r -> viewModel.setPhotoRating(photo.id, r) },
            onFlag = { f -> viewModel.setPhotoFlag(photo.id, f) },
            onColorLabel = { c -> viewModel.setPhotoColorLabel(photo.id, c) },
            onCopySettings = { viewModel.copyDevelopSettings(photo) },
            onDelete = {
                viewModel.deletePhoto(photo.id)
                inspectPhoto = null
            },
            onOpenDevelop = {
                inspectPhoto = null
                onOpenPhoto(photo.id)
            },
            onDismiss = { inspectPhoto = null }
        )
    }
}

@Composable
private fun LibraryHeaderBar(
    photoCount: Int,
    onCycleGrid: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSort: () -> Unit,
    sortMenuExpanded: Boolean,
    currentSort: LibrarySort,
    onSelectSort: (LibrarySort) -> Unit,
    onDismissSort: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lightmoon", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(StudioSurfaceElevated)
                        .clickable(onClick = onOpenSync)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.label_offline_ready), style = MaterialTheme.typography.labelSmall, color = StudioCyan)
                }
            }
            Text(
                text = "$photoCount photos in local darkroom",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenSync, modifier = Modifier.testTag("sync_bundle_button")) {
                Icon(Icons.Default.SyncAlt, contentDescription = stringResource(R.string.cd_backup_sync))
            }
            IconButton(onClick = onCycleGrid, modifier = Modifier.testTag("cycle_grid_button")) {
                Icon(Icons.Default.GridView, contentDescription = "Grid columns")
            }
            Box {
                IconButton(onClick = onOpenSort, modifier = Modifier.testTag("sort_button")) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort catalog")
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = onDismissSort) {
                    LibrarySort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(if (sort == currentSort) "✓ ${sort.label}" else sort.label) },
                            onClick = { onSelectSort(sort) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchSelectionBar(
    selectedCount: Int,
    hasCopiedSettings: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onPaste: () -> Unit,
    onFlagPick: () -> Unit,
    onBatchRate5: () -> Unit,
    onBatchExport: () -> Unit,
    onBatchDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurfaceElevated)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
            Text("$selectedCount selected", style = MaterialTheme.typography.titleSmall, color = StudioCyan)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
            }
            if (hasCopiedSettings) {
                IconButton(onClick = onPaste) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste develop settings", tint = StudioCyan)
                }
            }
            IconButton(onClick = onFlagPick) {
                Icon(Icons.Default.Flag, contentDescription = "Flag selected as Pick", tint = StudioSuccess)
            }
            IconButton(onClick = onBatchRate5) {
                Icon(Icons.Default.Star, contentDescription = "Rate selected 5 stars", tint = DarkroomAmber)
            }
            IconButton(onClick = onBatchExport) {
                Icon(Icons.Default.Download, contentDescription = "Batch export")
            }
            IconButton(onClick = onBatchDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridCard(
    photo: PhotoItem,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInspect: () -> Unit,
    onCopySettings: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(StudioSurface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) StudioCyan else StudioOutlineVariant,
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("photo_card_${photo.id}")
    ) {
        Image(
            painter = rememberAsyncImagePainter(File(photo.thumbnailPath)),
            contentDescription = photo.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Top badges: Color label + DNG/Edited
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (photo.colorLabel != ColorLabel.NONE) {
                val dotColor = when (photo.colorLabel) {
                    ColorLabel.RED -> LabelRed
                    ColorLabel.YELLOW -> LabelYellow
                    ColorLabel.GREEN -> LabelGreen
                    ColorLabel.BLUE -> LabelBlue
                    ColorLabel.PURPLE -> LabelPurple
                    ColorLabel.NONE -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(1.dp, Color.White, CircleShape)
                )
            }
            if (photo.mimeType.contains("dng", ignoreCase = true) || photo.title.endsWith(".dng", ignoreCase = true)) {
                Text(
                    text = "RAW",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            if (photo.isEdited) {
                Text(
                    text = "EDITED",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioCyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        if (selectionMode) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = if (isSelected) "Selected" else "Unselected",
                tint = if (isSelected) StudioCyan else Color.White.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
            )
        } else {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("EXIF & Triage") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onInspect()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy Develop Settings") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCopySettings()
                        }
                    )
                }
            }
        }

        // Bottom strip: Rating & Pick/Reject flag
        if (photo.rating > 0 || photo.flag != PickFlag.NONE) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (photo.rating > 0) {
                    Text(
                        text = "★".repeat(photo.rating),
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkroomAmber
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                if (photo.flag != PickFlag.NONE) {
                    Icon(
                        Icons.Default.Flag,
                        contentDescription = photo.flag.name,
                        tint = if (photo.flag == PickFlag.PICK) StudioSuccess else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoInspectorSheet(
    photo: PhotoItem,
    onRate: (Int) -> Unit,
    onFlag: (PickFlag) -> Unit,
    onColorLabel: (ColorLabel) -> Unit,
    onCopySettings: () -> Unit,
    onDelete: () -> Unit,
    onOpenDevelop: () -> Unit,
    onDismiss: () -> Unit
) {
    var rating by remember(photo.id) { mutableStateOf(photo.rating) }
    var flag by remember(photo.id) { mutableStateOf(photo.flag) }
    var label by remember(photo.id) { mutableStateOf(photo.colorLabel) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = StudioSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(photo.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${photo.width} × ${photo.height} px · ${photo.cameraModel} · ${photo.lensInfo}",
                style = MonoReadoutStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "ISO ${photo.iso}  ·  ${photo.focalLength}  ·  ${photo.aperture}  ·  ${photo.shutterSpeed}",
                style = MonoReadoutStyle,
                color = StudioCyan
            )
            val dateStr = remember(photo.capturedAtMillis) {
                SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(photo.capturedAtMillis))
            }
            Text("Captured: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider(color = StudioOutlineVariant)

            // Rating & Flag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    for (s in 1..5) {
                        IconButton(onClick = {
                            val next = if (rating == s) 0 else s
                            rating = next
                            onRate(next)
                        }) {
                            Icon(
                                if (s <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Rate $s",
                                tint = if (s <= rating) DarkroomAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = flag == PickFlag.PICK,
                        onClick = {
                            val next = if (flag == PickFlag.PICK) PickFlag.NONE else PickFlag.PICK
                            flag = next
                            onFlag(next)
                        },
                        label = { Text("Pick") }
                    )
                    FilterChip(
                        selected = flag == PickFlag.REJECT,
                        onClick = {
                            val next = if (flag == PickFlag.REJECT) PickFlag.NONE else PickFlag.REJECT
                            flag = next
                            onFlag(next)
                        },
                        label = { Text("Reject") }
                    )
                }
            }

            // Color Labels
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Color Label", style = MaterialTheme.typography.labelMedium)
                listOf(
                    ColorLabel.NONE to Color.Gray,
                    ColorLabel.RED to LabelRed,
                    ColorLabel.YELLOW to LabelYellow,
                    ColorLabel.GREEN to LabelGreen,
                    ColorLabel.BLUE to LabelBlue,
                    ColorLabel.PURPLE to LabelPurple
                ).forEach { (cLabel, color) ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = if (cLabel == ColorLabel.NONE) 0.2f else 1f))
                            .border(
                                if (label == cLabel) 2.dp else 1.dp,
                                if (label == cLabel) Color.White else StudioOutlineVariant,
                                CircleShape
                            )
                            .clickable {
                                label = cLabel
                                onColorLabel(cLabel)
                            }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onCopySettings()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Edits")
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove photo", modifier = Modifier.size(16.dp))
                }
            }

            Button(
                onClick = onOpenDevelop,
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = MaterialTheme.colorScheme.onSecondary),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Open in Develop Studio")
            }
        }
    }
}
