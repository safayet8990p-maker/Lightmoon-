package com.example.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.LightmoonRepository
import com.example.model.ColorLabel
import com.example.model.DevelopSettings
import com.example.model.ExportConfig
import com.example.model.PhotoItem
import com.example.model.PickFlag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("All"),
    PICKS("Picks"),
    RATED("3★+"),
    EDITED("Edited"),
    REJECTS("Rejects")
}

enum class LibrarySort(val label: String) {
    DATE_DESC("Newest"),
    RATING_DESC("Top Rated"),
    NAME_ASC("Name A-Z")
}

data class LibraryUiState(
    val photos: List<PhotoItem> = emptyList(),
    val totalCount: Int = 0,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val colorFilter: ColorLabel = ColorLabel.NONE,
    val sort: LibrarySort = LibrarySort.DATE_DESC,
    val searchQuery: String = "",
    val gridColumns: Int = 3,
    val selectedIds: Set<String> = emptySet(),
    val copiedSettings: DevelopSettings? = null,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val batchExportProgress: String? = null
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

class LibraryViewModel(
    private val repository: LightmoonRepository
) : ViewModel() {

    private val filterState = MutableStateFlow(LibraryFilter.ALL)
    private val colorFilterState = MutableStateFlow(ColorLabel.NONE)
    private val sortState = MutableStateFlow(LibrarySort.DATE_DESC)
    private val searchState = MutableStateFlow("")
    private val selectedIdsState = MutableStateFlow<Set<String>>(emptySet())
    private val copiedSettingsState = MutableStateFlow<DevelopSettings?>(null)
    private val busyState = MutableStateFlow(false)
    private val messageState = MutableStateFlow<String?>(null)
    private val batchProgressState = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    private data class QueryParams(
        val filter: LibraryFilter,
        val color: ColorLabel,
        val sort: LibrarySort,
        val query: String,
        val gridCols: Int
    )

    private val queryFlow = combine(
        filterState,
        colorFilterState,
        sortState,
        searchState,
        repository.preferences.gridColumns
    ) { filter, color, sort, query, cols ->
        QueryParams(filter, color, sort, query, cols)
    }

    private val interactionFlow = combine(
        selectedIdsState,
        copiedSettingsState,
        busyState,
        messageState,
        batchProgressState
    ) { selected, copied, busy, msg, batch ->
        InteractionState(selected, copied, busy, msg, batch)
    }

    private data class InteractionState(
        val selected: Set<String>,
        val copied: DevelopSettings?,
        val busy: Boolean,
        val msg: String?,
        val batch: String?
    )

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.observePhotos(),
        queryFlow,
        interactionFlow
    ) { allPhotos, q, inter ->
        val filtered = allPhotos.filter { item ->
            val matchesFilter = when (q.filter) {
                LibraryFilter.ALL -> item.flag != PickFlag.REJECT
                LibraryFilter.PICKS -> item.flag == PickFlag.PICK
                LibraryFilter.RATED -> item.rating >= 3
                LibraryFilter.EDITED -> item.isEdited
                LibraryFilter.REJECTS -> item.flag == PickFlag.REJECT
            }
            val matchesColor = q.color == ColorLabel.NONE || item.colorLabel == q.color
            val trimmed = q.query.trim()
            val matchesSearch = trimmed.isEmpty() ||
                item.title.contains(trimmed, ignoreCase = true) ||
                item.cameraModel.contains(trimmed, ignoreCase = true) ||
                item.lensInfo.contains(trimmed, ignoreCase = true)
            matchesFilter && matchesColor && matchesSearch
        }

        val sorted = when (q.sort) {
            LibrarySort.DATE_DESC -> filtered.sortedByDescending { it.capturedAtMillis }
            LibrarySort.RATING_DESC -> filtered.sortedWith(
                compareByDescending<PhotoItem> { it.rating }.thenByDescending { it.capturedAtMillis }
            )
            LibrarySort.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
        }

        LibraryUiState(
            photos = sorted,
            totalCount = allPhotos.size,
            filter = q.filter,
            colorFilter = q.color,
            sort = q.sort,
            searchQuery = q.query,
            gridColumns = q.gridCols,
            selectedIds = inter.selected,
            copiedSettings = inter.copied,
            isBusy = inter.busy,
            statusMessage = inter.msg,
            batchExportProgress = inter.batch
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = LibraryUiState()
    )

    fun setFilter(filter: LibraryFilter) {
        filterState.value = filter
    }

    fun setColorFilter(color: ColorLabel) {
        colorFilterState.value = if (colorFilterState.value == color) ColorLabel.NONE else color
    }

    fun setSort(sort: LibrarySort) {
        sortState.value = sort
    }

    fun setSearchQuery(query: String) {
        searchState.value = query
    }

    fun cycleGridColumns() {
        viewModelScope.launch {
            val next = when (uiState.value.gridColumns) {
                2 -> 3
                3 -> 4
                else -> 2
            }
            repository.preferences.setGridColumns(next)
        }
    }

    fun toggleSelection(photoId: String) {
        selectedIdsState.update { set ->
            if (photoId in set) set - photoId else set + photoId
        }
    }

    fun clearSelection() {
        selectedIdsState.value = emptySet()
    }

    fun selectAllVisible() {
        selectedIdsState.value = uiState.value.photos.map { it.id }.toSet()
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busyState.value = true
            val count = repository.importPhotos(uris)
            busyState.value = false
            messageState.value = if (count > 0) {
                "Imported $count photo${if (count == 1) "" else "s"} for offline editing"
            } else {
                "Could not decode selected image"
            }
        }
    }

    fun setPhotoRating(photoId: String, rating: Int) {
        viewModelScope.launch {
            repository.updateTriage(photoId, rating = rating.coerceIn(0, 5))
        }
    }

    fun setPhotoFlag(photoId: String, flag: PickFlag) {
        viewModelScope.launch {
            repository.updateTriage(photoId, flag = flag)
        }
    }

    fun setPhotoColorLabel(photoId: String, label: ColorLabel) {
        viewModelScope.launch {
            repository.updateTriage(photoId, colorLabel = label)
        }
    }

    fun copyDevelopSettings(photo: PhotoItem) {
        copiedSettingsState.value = photo.developSettings
        messageState.value = "Copied develop settings from ${photo.title}"
    }

    fun pasteSettingsToSelected() {
        val settings = copiedSettingsState.value ?: return
        val ids = selectedIdsState.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            busyState.value = true
            repository.pasteSettingsToPhotos(ids, settings)
            busyState.value = false
            selectedIdsState.value = emptySet()
            messageState.value = "Applied develop settings to ${ids.size} photos"
        }
    }

    fun batchSetRating(rating: Int) {
        val ids = selectedIdsState.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.batchUpdateTriage(ids, rating = rating)
            messageState.value = "Rated ${ids.size} photos ($rating★)"
        }
    }

    fun batchSetFlag(flag: PickFlag) {
        val ids = selectedIdsState.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.batchUpdateTriage(ids, flag = flag)
            messageState.value = "Updated flag for ${ids.size} photos"
        }
    }

    fun batchDeleteSelected() {
        val ids = selectedIdsState.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            busyState.value = true
            for (id in ids) repository.deletePhoto(id)
            selectedIdsState.value = emptySet()
            busyState.value = false
            messageState.value = "Removed ${ids.size} photos from Lightmoon"
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            repository.deletePhoto(photoId)
            messageState.value = "Photo removed from catalog"
        }
    }

    fun batchExportSelected(config: ExportConfig) {
        val ids = selectedIdsState.value
        val photos = uiState.value.photos.filter { it.id in ids }
        if (photos.isEmpty()) return
        viewModelScope.launch {
            busyState.value = true
            var ok = 0
            photos.forEachIndexed { idx, photo ->
                batchProgressState.value = "Exporting ${idx + 1} / ${photos.size}…"
                val res = repository.exportPhoto(photo, config)
                if (res.isSuccess) ok++
            }
            batchProgressState.value = null
            busyState.value = false
            selectedIdsState.value = emptySet()
            messageState.value = "Exported $ok of ${photos.size} photos to Pictures/Lightmoon"
        }
    }

    fun exportBackupPackage(uri: Uri) {
        viewModelScope.launch {
            busyState.value = true
            val res = repository.exportBackupPackageToUri(uri)
            busyState.value = false
            messageState.value = res.fold(
                onSuccess = { count -> "Exported sync bundle ($count presets & photo recipes)" },
                onFailure = { err -> "Backup failed: ${err.localizedMessage}" }
            )
        }
    }

    fun importBackupPackage(uri: Uri) {
        viewModelScope.launch {
            busyState.value = true
            val res = repository.importBackupPackageFromUri(uri)
            busyState.value = false
            messageState.value = res.fold(
                onSuccess = { (presets, photos) -> "Synced $presets presets & $photos photo recipes" },
                onFailure = { err -> "Restore failed: ${err.localizedMessage}" }
            )
        }
    }

    fun clearMessage() {
        messageState.value = null
    }

    class Factory(private val repository: LightmoonRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(repository) as T
        }
    }
}
