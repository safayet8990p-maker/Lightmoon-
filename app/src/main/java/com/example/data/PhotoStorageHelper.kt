package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.R
import com.example.engine.LightmoonPipeline
import com.example.model.DevelopSettings
import com.example.model.ExportConfig
import com.example.model.ExportFormat
import com.example.model.PhotoItem
import com.example.model.PickFlag
import com.example.model.WatermarkCorner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PhotoStorageHelper(private val context: Context) {

    private val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
    private val thumbsDir = File(context.filesDir, "thumbs").apply { mkdirs() }
    private val exportsCacheDir = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun seedSamplePhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        val samples = listOf(
            Triple(
                R.drawable.img_sample_alpine,
                "Alpine_Glacier_Dawn.dng",
                SampleExif("Leica Q3", "Summilux 28mm f/1.7", 100, "f/5.6", "1/250s", "28mm", 5)
            ),
            Triple(
                R.drawable.img_sample_portrait,
                "Workshop_Rangefinder.jpg",
                SampleExif("Fujifilm X-T5", "XF 35mm f/1.4 R", 400, "f/1.8", "1/160s", "35mm", 4)
            ),
            Triple(
                R.drawable.img_sample_twilight,
                "Tokyo_Rain_Lanterns.dng",
                SampleExif("Sony A7C II", "FE 40mm f/2.5 G", 800, "f/2.8", "1/80s", "40mm", 4)
            )
        )

        val result = mutableListOf<PhotoItem>()
        val now = System.currentTimeMillis()
        samples.forEachIndexed { idx, (resId, title, exif) ->
            val id = "sample_$idx"
            val photoFile = File(photosDir, "$id.jpg")
            val thumbFile = File(thumbsDir, "${id}_thumb.jpg")
            val bmp = BitmapFactory.decodeResource(context.resources, resId)
            if (bmp != null) {
                FileOutputStream(photoFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 96, out)
                }
                writeThumbnail(bmp, thumbFile)
                result.add(
                    PhotoItem(
                        id = id,
                        title = title,
                        localFilePath = photoFile.absolutePath,
                        thumbnailPath = thumbFile.absolutePath,
                        mimeType = if (title.endsWith(".dng")) "image/x-adobe-dng" else "image/jpeg",
                        width = bmp.width,
                        height = bmp.height,
                        capturedAtMillis = now - idx * 86_400_000L,
                        cameraModel = exif.camera,
                        lensInfo = exif.lens,
                        iso = exif.iso,
                        aperture = exif.aperture,
                        shutterSpeed = exif.shutter,
                        focalLength = exif.focal,
                        rating = exif.initialRating,
                        flag = if (idx == 0) PickFlag.PICK else PickFlag.NONE,
                        developSettings = DevelopSettings(),
                        isSample = true,
                        updatedAtMillis = now
                    )
                )
                bmp.recycle()
            }
        }
        result
    }

    suspend fun importFromUri(uri: Uri): PhotoItem? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            runCatching {
                resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val displayName = queryDisplayName(uri) ?: "IMG_${System.currentTimeMillis()}.jpg"
            val mimeType = resolver.getType(uri) ?: "image/jpeg"
            val id = UUID.randomUUID().toString()
            val ext = displayName.substringAfterLast('.', "jpg").lowercase(Locale.US)
            val destFile = File(photosDir, "$id.$ext")

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val exif = extractExif(destFile, mimeType)
            val rawBmp = decodeFileOriented(destFile.absolutePath, maxLongEdge = 2400)
                ?: return@withContext null

            // Normalize local working JPEG if source was DNG/HEIC so preview/export stay blazing fast
            val workingFile = if (ext in listOf("dng", "raw", "arw", "nef", "cr2")) {
                val cached = File(photosDir, "${id}_working.jpg")
                FileOutputStream(cached).use { out ->
                    rawBmp.compress(Bitmap.CompressFormat.JPEG, 96, out)
                }
                cached
            } else {
                destFile
            }

            val thumbFile = File(thumbsDir, "${id}_thumb.jpg")
            writeThumbnail(rawBmp, thumbFile)

            val item = PhotoItem(
                id = id,
                title = displayName,
                localFilePath = workingFile.absolutePath,
                thumbnailPath = thumbFile.absolutePath,
                mimeType = mimeType,
                width = rawBmp.width,
                height = rawBmp.height,
                capturedAtMillis = System.currentTimeMillis(),
                cameraModel = exif.camera,
                lensInfo = exif.lens,
                iso = exif.iso,
                aperture = exif.aperture,
                shutterSpeed = exif.shutter,
                focalLength = exif.focal,
                rating = 0,
                flag = PickFlag.NONE,
                developSettings = DevelopSettings(),
                isSample = false,
                updatedAtMillis = System.currentTimeMillis()
            )
            rawBmp.recycle()
            item
        }.getOrNull()
    }

    suspend fun refreshThumbnail(photo: PhotoItem, settings: DevelopSettings) = withContext(Dispatchers.IO) {
        runCatching {
            val base = decodeFileOriented(photo.localFilePath, maxLongEdge = 480) ?: return@withContext
            val rendered = LightmoonPipeline.render(base, settings)
            writeThumbnail(rendered, File(photo.thumbnailPath))
            if (rendered !== base) rendered.recycle()
            base.recycle()
        }
    }

    suspend fun loadWorkingBitmap(photo: PhotoItem, maxLongEdge: Int? = 1080): Bitmap? = withContext(Dispatchers.IO) {
        decodeFileOriented(photo.localFilePath, maxLongEdge = maxLongEdge)
    }

    suspend fun exportPhoto(photo: PhotoItem, config: ExportConfig): Result<ExportOutput> = withContext(Dispatchers.IO) {
        runCatching {
            val baseName = photo.title.substringBeforeLast('.')
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFileName = "${config.filenamePrefix}${baseName}_$stamp.${config.format.extension}"

            if (config.format == ExportFormat.SIDECAR_RECIPE) {
                val json = LightmoonJson.encodeSettings(photo.developSettings)
                val cacheFile = File(exportsCacheDir, outFileName)
                cacheFile.writeText(json)
                val shareUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
                return@runCatching ExportOutput(
                    displayName = outFileName,
                    mediaUri = shareUri,
                    shareUri = shareUri
                )
            }

            val source = decodeFileOriented(photo.localFilePath, maxLongEdge = config.resolution.maxLongEdgePx)
                ?: error("Unable to decode source image for export.")

            val effectiveSettings = if (config.sharpening.amount > 0f) {
                photo.developSettings.copy(
                    sharpenAmount = (photo.developSettings.sharpenAmount + config.sharpening.amount).coerceAtMost(150f)
                )
            } else {
                photo.developSettings
            }

            var rendered = LightmoonPipeline.render(source, effectiveSettings)
            if (rendered !== source) source.recycle()

            if (config.resolution.maxLongEdgePx != null) {
                val maxEdge = config.resolution.maxLongEdgePx
                val curMax = max(rendered.width, rendered.height)
                if (curMax > maxEdge) {
                    val scale = maxEdge.toFloat() / curMax
                    val scaled = Bitmap.createScaledBitmap(
                        rendered,
                        max(1, (rendered.width * scale).roundToInt()),
                        max(1, (rendered.height * scale).roundToInt()),
                        true
                    )
                    if (scaled !== rendered) rendered.recycle()
                    rendered = scaled
                }
            }

            if (config.watermarkEnabled && config.watermarkText.isNotBlank()) {
                val mutable = rendered.copy(Bitmap.Config.ARGB_8888, true)
                if (mutable !== rendered) rendered.recycle()
                drawWatermark(mutable, config.watermarkText, config.watermarkOpacity, config.watermarkCorner)
                rendered = mutable
            }

            val compressFormat = when (config.format) {
                ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                ExportFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                ExportFormat.SIDECAR_RECIPE -> Bitmap.CompressFormat.JPEG
            }

            // Write cache file for Share Intent
            val shareFile = File(exportsCacheDir, outFileName)
            FileOutputStream(shareFile).use { out ->
                rendered.compress(compressFormat, config.quality.coerceIn(50, 100), out)
            }
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )

            // Save to MediaStore Pictures/Lightmoon (zero storage permission needed on API 29+)
            val savedUri = saveToMediaStore(rendered, outFileName, config.format.mimeType, compressFormat, config.quality)
            rendered.recycle()

            ExportOutput(
                displayName = outFileName,
                mediaUri = savedUri ?: shareUri,
                shareUri = shareUri
            )
        }
    }

    private fun saveToMediaStore(
        bitmap: Bitmap,
        fileName: String,
        mimeType: String,
        format: Bitmap.CompressFormat,
        quality: Int
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Lightmoon")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(format, quality.coerceIn(50, 100), out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
        return uri
    }

    private fun drawWatermark(bitmap: Bitmap, text: String, opacity: Float, corner: WatermarkCorner) {
        val canvas = Canvas(bitmap)
        val textSize = max(24f, min(bitmap.width, bitmap.height) * 0.032f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            alpha = (opacity.coerceIn(0.1f, 1f) * 255).roundToInt()
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(textSize * 0.15f, 0f, 2f, android.graphics.Color.argb(160, 0, 0, 0))
        }
        val textWidth = paint.measureText(text)
        val pad = textSize * 1.1f
        val (x, y) = when (corner) {
            WatermarkCorner.BOTTOM_RIGHT -> Pair(bitmap.width - textWidth - pad, bitmap.height - pad)
            WatermarkCorner.BOTTOM_LEFT -> Pair(pad, bitmap.height - pad)
            WatermarkCorner.TOP_RIGHT -> Pair(bitmap.width - textWidth - pad, pad + textSize)
            WatermarkCorner.CENTER -> Pair((bitmap.width - textWidth) * 0.5f, (bitmap.height + textSize) * 0.5f)
        }
        canvas.drawText(text, x, y, paint)
    }

    private fun decodeFileOriented(path: String, maxLongEdge: Int?): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        var sampleSize = 1
        if (maxLongEdge != null) {
            val longEdge = max(opts.outWidth, opts.outHeight)
            while (longEdge / (sampleSize * 2) >= maxLongEdge) {
                sampleSize *= 2
            }
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, decodeOpts) ?: return null
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val oriented = applyExifOrientation(decoded, orientation)
        if (maxLongEdge != null) {
            val curLong = max(oriented.width, oriented.height)
            if (curLong > maxLongEdge) {
                val scale = maxLongEdge.toFloat() / curLong
                val scaled = Bitmap.createScaledBitmap(
                    oriented,
                    max(1, (oriented.width * scale).roundToInt()),
                    max(1, (oriented.height * scale).roundToInt()),
                    true
                )
                if (scaled !== oriented) oriented.recycle()
                return scaled
            }
        }
        return oriented
    }

    private fun applyExifOrientation(bmp: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bmp
        }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        if (rotated !== bmp) bmp.recycle()
        return rotated
    }

    private fun writeThumbnail(source: Bitmap, outFile: File) {
        val longEdge = max(source.width, source.height)
        val thumb = if (longEdge > 480) {
            val scale = 480f / longEdge
            Bitmap.createScaledBitmap(
                source,
                max(1, (source.width * scale).roundToInt()),
                max(1, (source.height * scale).roundToInt()),
                true
            )
        } else {
            source
        }
        FileOutputStream(outFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        if (thumb !== source) thumb.recycle()
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return null
    }

    private fun extractExif(file: File, mimeType: String): SampleExif {
        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
            val camera = listOf(make, model).filter { it.isNotBlank() }.distinct().joinToString(" ")
                .ifBlank { if (mimeType.contains("dng")) "RAW Digital" else "Mobile Camera" }
            val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.toIntOrNull() ?: 100
            val fNum = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" } ?: "f/2.0"
            val expTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.let { sec ->
                if (sec > 0 && sec < 1.0) "1/${(1.0 / sec).roundToInt()}s" else "${sec}s"
            } ?: "1/125s"
            val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).let { f ->
                if (f > 0.0) "${f.roundToInt()}mm" else "28mm"
            }
            SampleExif(camera, "Primary Lens", iso, fNum, expTime, focal, 0)
        }.getOrDefault(SampleExif("Digital Camera", "Standard Lens", 100, "f/2.2", "1/125s", "28mm", 0))
    }

    private data class SampleExif(
        val camera: String,
        val lens: String,
        val iso: Int,
        val aperture: String,
        val shutter: String,
        val focal: String,
        val initialRating: Int
    )

    data class ExportOutput(
        val displayName: String,
        val mediaUri: Uri,
        val shareUri: Uri
    )
}
