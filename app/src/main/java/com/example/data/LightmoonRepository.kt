package com.example.data

import android.content.Context
import android.net.Uri
import com.example.data.db.LightmoonDatabase
import com.example.data.db.PhotoEntity
import com.example.data.db.PresetEntity
import com.example.data.db.SnapshotEntity
import com.example.model.CatalogBackupPackage
import com.example.model.ColorLabel
import com.example.model.DevelopPreset
import com.example.model.DevelopSettings
import com.example.model.EditSnapshot
import com.example.model.ExportConfig
import com.example.model.PhotoItem
import com.example.model.PhotoRatingSyncRecord
import com.example.model.PickFlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LightmoonRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = LightmoonDatabase.getInstance(appContext)
    private val photoDao = db.photoDao()
    private val presetDao = db.presetDao()
    private val snapshotDao = db.snapshotDao()
    val storage = PhotoStorageHelper(appContext)
    val preferences = StudioPreferences(appContext)

    fun observePhotos(): Flow<List<PhotoItem>> =
        photoDao.observeAllPhotos().map { list -> list.map { it.toDomain() } }

    fun observePhoto(photoId: String): Flow<PhotoItem?> =
        photoDao.observePhotoById(photoId).map { it?.toDomain() }

    fun observeAllPresets(): Flow<List<DevelopPreset>> =
        presetDao.observeCustomPresets().map { custom ->
            BuiltInPresets.presets + custom.map { it.toDomain() }
        }

    fun observeSnapshots(photoId: String): Flow<List<EditSnapshot>> =
        snapshotDao.observeSnapshotEntities(photoId).map { list -> list.map { it.toDomain() } }

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (photoDao.countPhotos() == 0) {
            val samples = storage.seedSamplePhotos()
            if (samples.isNotEmpty()) {
                photoDao.upsertPhotos(samples.map { PhotoEntity.fromDomain(it) })
            }
        }
    }

    suspend fun importPhotos(uris: List<Uri>): Int {
        var imported = 0
        for (uri in uris) {
            val item = storage.importFromUri(uri)
            if (item != null) {
                photoDao.upsertPhoto(PhotoEntity.fromDomain(item))
                imported++
            }
        }
        return imported
    }

    suspend fun updateDevelopSettings(photoId: String, settings: DevelopSettings, refreshThumb: Boolean = true) {
        val current = photoDao.getPhotoById(photoId)?.toDomain() ?: return
        val updated = current.copy(
            developSettings = settings,
            updatedAtMillis = System.currentTimeMillis()
        )
        photoDao.upsertPhoto(PhotoEntity.fromDomain(updated))
        if (refreshThumb) {
            storage.refreshThumbnail(updated, settings)
        }
    }

    suspend fun updateTriage(
        photoId: String,
        rating: Int? = null,
        flag: PickFlag? = null,
        colorLabel: ColorLabel? = null
    ) {
        val current = photoDao.getPhotoById(photoId)?.toDomain() ?: return
        val updated = current.copy(
            rating = rating ?: current.rating,
            flag = flag ?: current.flag,
            colorLabel = colorLabel ?: current.colorLabel,
            updatedAtMillis = System.currentTimeMillis()
        )
        photoDao.upsertPhoto(PhotoEntity.fromDomain(updated))
    }

    suspend fun batchUpdateTriage(
        photoIds: Set<String>,
        rating: Int? = null,
        flag: PickFlag? = null,
        colorLabel: ColorLabel? = null
    ) {
        for (id in photoIds) {
            updateTriage(id, rating, flag, colorLabel)
        }
    }

    suspend fun pasteSettingsToPhotos(photoIds: Set<String>, settings: DevelopSettings) {
        for (id in photoIds) {
            updateDevelopSettings(id, settings, refreshThumb = true)
        }
    }

    suspend fun deletePhoto(photoId: String) {
        val current = photoDao.getPhotoById(photoId)
        if (current != null) {
            runCatching { File(current.localFilePath).delete() }
            runCatching { File(current.thumbnailPath).delete() }
            photoDao.deletePhotoById(photoId)
        }
    }

    suspend fun saveCustomPreset(name: String, category: String, settings: DevelopSettings) {
        val preset = DevelopPreset(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Custom Preset" },
            category = category.trim().ifBlank { "Custom" },
            isBuiltIn = false,
            settings = settings,
            updatedAtMillis = System.currentTimeMillis()
        )
        presetDao.upsertPreset(PresetEntity.fromDomain(preset))
    }

    suspend fun deleteCustomPreset(presetId: String) {
        presetDao.deletePresetById(presetId)
    }

    suspend fun createSnapshot(photoId: String, name: String, settings: DevelopSettings) {
        val snapshot = EditSnapshot(
            id = UUID.randomUUID().toString(),
            photoId = photoId,
            name = name.trim().ifBlank { "Snapshot" },
            settings = settings,
            createdAtMillis = System.currentTimeMillis()
        )
        snapshotDao.upsertSnapshot(SnapshotEntity.fromDomain(snapshot))
    }

    suspend fun deleteSnapshot(snapshotId: String) {
        snapshotDao.deleteSnapshotById(snapshotId)
    }

    suspend fun exportBackupPackageToUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val presets = presetDao.getCustomPresetsOnce().map { it.toDomain() }
            val photos = photoDao.getAllPhotosOnce().map { it.toDomain() }
            val records = photos.map {
                PhotoRatingSyncRecord(
                    title = it.title,
                    rating = it.rating,
                    flag = it.flag,
                    colorLabel = it.colorLabel,
                    developSettings = it.developSettings,
                    updatedAtMillis = it.updatedAtMillis
                )
            }
            val pkg = CatalogBackupPackage(
                customPresets = presets,
                photoStates = records
            )
            val json = LightmoonJson.encodeBackup(pkg)
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("Cannot write backup package")
            presets.size + records.size
        }
    }

    suspend fun importBackupPackageFromUri(uri: Uri): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = appContext.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: error("Cannot read backup file")
            val pkg = LightmoonJson.decodeBackup(json) ?: error("Invalid Lightmoon backup format")

            if (pkg.customPresets.isNotEmpty()) {
                presetDao.upsertPresets(pkg.customPresets.map { PresetEntity.fromDomain(it) })
            }

            val existing = photoDao.getAllPhotosOnce().map { it.toDomain() }
            var matchedPhotos = 0
            for (record in pkg.photoStates) {
                val match = existing.firstOrNull { it.title == record.title }
                if (match != null && record.updatedAtMillis >= match.updatedAtMillis) {
                    val updated = match.copy(
                        rating = record.rating,
                        flag = record.flag,
                        colorLabel = record.colorLabel,
                        developSettings = record.developSettings,
                        updatedAtMillis = record.updatedAtMillis
                    )
                    photoDao.upsertPhoto(PhotoEntity.fromDomain(updated))
                    storage.refreshThumbnail(updated, updated.developSettings)
                    matchedPhotos++
                }
            }
            Pair(pkg.customPresets.size, matchedPhotos)
        }
    }

    suspend fun exportPhoto(photo: PhotoItem, config: ExportConfig) =
        storage.exportPhoto(photo, config)
}
