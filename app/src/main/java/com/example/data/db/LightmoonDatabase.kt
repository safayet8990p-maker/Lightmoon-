package com.example.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.LightmoonJson
import com.example.model.ColorLabel
import com.example.model.DevelopPreset
import com.example.model.EditSnapshot
import com.example.model.PhotoItem
import com.example.model.PickFlag
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
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
    val rating: Int,
    val flag: String,
    val colorLabel: String,
    val developSettingsJson: String,
    val isSample: Boolean,
    val updatedAtMillis: Long
) {
    fun toDomain(): PhotoItem = PhotoItem(
        id = id,
        title = title,
        localFilePath = localFilePath,
        thumbnailPath = thumbnailPath,
        mimeType = mimeType,
        width = width,
        height = height,
        capturedAtMillis = capturedAtMillis,
        cameraModel = cameraModel,
        lensInfo = lensInfo,
        iso = iso,
        aperture = aperture,
        shutterSpeed = shutterSpeed,
        focalLength = focalLength,
        rating = rating.coerceIn(0, 5),
        flag = PickFlag.entries.firstOrNull { it.name == flag } ?: PickFlag.NONE,
        colorLabel = ColorLabel.entries.firstOrNull { it.name == colorLabel } ?: ColorLabel.NONE,
        developSettings = LightmoonJson.decodeSettings(developSettingsJson),
        isSample = isSample,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(item: PhotoItem): PhotoEntity = PhotoEntity(
            id = item.id,
            title = item.title,
            localFilePath = item.localFilePath,
            thumbnailPath = item.thumbnailPath,
            mimeType = item.mimeType,
            width = item.width,
            height = item.height,
            capturedAtMillis = item.capturedAtMillis,
            cameraModel = item.cameraModel,
            lensInfo = item.lensInfo,
            iso = item.iso,
            aperture = item.aperture,
            shutterSpeed = item.shutterSpeed,
            focalLength = item.focalLength,
            rating = item.rating,
            flag = item.flag.name,
            colorLabel = item.colorLabel.name,
            developSettingsJson = LightmoonJson.encodeSettings(item.developSettings),
            isSample = item.isSample,
            updatedAtMillis = item.updatedAtMillis
        )
    }
}

@Entity(tableName = "custom_presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val developSettingsJson: String,
    val updatedAtMillis: Long
) {
    fun toDomain(): DevelopPreset = DevelopPreset(
        id = id,
        name = name,
        category = category,
        isBuiltIn = false,
        settings = LightmoonJson.decodeSettings(developSettingsJson),
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(preset: DevelopPreset): PresetEntity = PresetEntity(
            id = preset.id,
            name = preset.name,
            category = preset.category,
            developSettingsJson = LightmoonJson.encodeSettings(preset.settings),
            updatedAtMillis = preset.updatedAtMillis
        )
    }
}

@Entity(tableName = "edit_snapshots")
data class SnapshotEntity(
    @PrimaryKey val id: String,
    val photoId: String,
    val name: String,
    val developSettingsJson: String,
    val createdAtMillis: Long
) {
    fun toDomain(): EditSnapshot = EditSnapshot(
        id = id,
        photoId = photoId,
        name = name,
        settings = LightmoonJson.decodeSettings(developSettingsJson),
        createdAtMillis = createdAtMillis
    )

    companion object {
        fun fromDomain(snapshot: EditSnapshot): SnapshotEntity = SnapshotEntity(
            id = snapshot.id,
            photoId = snapshot.photoId,
            name = snapshot.name,
            developSettingsJson = LightmoonJson.encodeSettings(snapshot.settings),
            createdAtMillis = snapshot.createdAtMillis
        )
    }
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY capturedAtMillis DESC")
    fun observeAllPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :photoId LIMIT 1")
    fun observePhotoById(photoId: String): Flow<PhotoEntity?>

    @Query("SELECT * FROM photos WHERE id = :photoId LIMIT 1")
    suspend fun getPhotoById(photoId: String): PhotoEntity?

    @Query("SELECT * FROM photos")
    suspend fun getAllPhotosOnce(): List<PhotoEntity>

    @Query("SELECT COUNT(*) FROM photos")
    suspend fun countPhotos(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(photo: PhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhotos(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE id = :photoId")
    suspend fun deletePhotoById(photoId: String)
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM custom_presets ORDER BY updatedAtMillis DESC")
    fun observeCustomPresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM custom_presets")
    suspend fun getCustomPresetsOnce(): List<PresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreset(preset: PresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPresets(presets: List<PresetEntity>)

    @Query("DELETE FROM custom_presets WHERE id = :presetId")
    suspend fun deletePresetById(presetId: String)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM edit_snapshots WHERE photoId = :photoId ORDER BY createdAtMillis DESC")
    fun observeSnapshotEntities(photoId: String): Flow<List<SnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: SnapshotEntity)

    @Query("DELETE FROM edit_snapshots WHERE id = :snapshotId")
    suspend fun deleteSnapshotById(snapshotId: String)
}

@Database(
    entities = [PhotoEntity::class, PresetEntity::class, SnapshotEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LightmoonDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun presetDao(): PresetDao
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: LightmoonDatabase? = null

        fun getInstance(context: Context): LightmoonDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LightmoonDatabase::class.java,
                    "lightmoon_catalog.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
