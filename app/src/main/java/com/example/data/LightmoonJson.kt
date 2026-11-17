package com.example.data

import com.example.model.CatalogBackupPackage
import com.example.model.DevelopSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object LightmoonJson {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val settingsAdapter = moshi.adapter(DevelopSettings::class.java)
    private val backupAdapter = moshi.adapter(CatalogBackupPackage::class.java).indent("  ")

    fun encodeSettings(settings: DevelopSettings): String = settingsAdapter.toJson(settings)

    fun decodeSettings(json: String): DevelopSettings =
        runCatching { settingsAdapter.fromJson(json) }.getOrNull() ?: DevelopSettings()

    fun encodeBackup(pkg: CatalogBackupPackage): String = backupAdapter.toJson(pkg)

    fun decodeBackup(json: String): CatalogBackupPackage? =
        runCatching { backupAdapter.fromJson(json) }.getOrNull()
}
