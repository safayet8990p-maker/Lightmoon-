package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.BuiltInPresets
import com.example.data.LightmoonJson
import com.example.engine.AutoToneEngine
import com.example.engine.LightmoonPipeline
import com.example.engine.ScopeAnalyzer
import com.example.model.BrushStroke
import com.example.model.CatalogBackupPackage
import com.example.model.ColorLabel
import com.example.model.DevelopSettings
import com.example.model.MaskAdjustments
import com.example.model.MaskLayer
import com.example.model.MaskType
import com.example.model.PhotoRatingSyncRecord
import com.example.model.PickFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Lightmoon`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Lightmoon", appName)
    }

    @Test
    fun `json round-trip preserves DevelopSettings masks and backup bundle`() {
        val base = BuiltInPresets.presets.first().settings.copy(
            masks = listOf(
                MaskLayer(
                    name = "Subject Pop",
                    type = MaskType.SMART_SUBJECT,
                    adjustments = MaskAdjustments(exposure = 0.4f, clarity = 18f)
                ),
                MaskLayer(
                    name = "Brush Dodge",
                    type = MaskType.BRUSH,
                    brushStrokes = listOf(
                        BrushStroke(
                            pointsX = listOf(0.2f, 0.4f, 0.6f),
                            pointsY = listOf(0.3f, 0.5f, 0.5f)
                        )
                    ),
                    adjustments = MaskAdjustments(shadows = 25f)
                )
            )
        )
        val encoded = LightmoonJson.encodeSettings(base)
        val decoded = LightmoonJson.decodeSettings(encoded)
        assertEquals(base, decoded)

        val pkg = CatalogBackupPackage(
            customPresets = listOf(BuiltInPresets.presets.first().copy(isBuiltIn = false)),
            photoStates = listOf(
                PhotoRatingSyncRecord(
                    title = "Test.dng",
                    rating = 5,
                    flag = PickFlag.PICK,
                    colorLabel = ColorLabel.BLUE,
                    developSettings = base,
                    updatedAtMillis = 123456789L
                )
            )
        )
        val pkgJson = LightmoonJson.encodeBackup(pkg)
        val restored = LightmoonJson.decodeBackup(pkgJson)
        assertNotNull(restored)
        assertEquals(1, restored?.customPresets?.size)
        assertEquals(5, restored?.photoStates?.first()?.rating)
    }

    @Test
    fun `pipeline renders full feature stack and computes scopes and auto tone`() {
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        for (y in 0 until 48) {
            for (x in 0 until 64) {
                val r = (x * 4).coerceIn(0, 255)
                val g = (y * 5).coerceIn(0, 255)
                val b = ((x + y) * 2).coerceIn(0, 255)
                bmp.setPixel(x, y, Color.rgb(r, g, b))
            }
        }

        val autoSettings = AutoToneEngine.computeAutoTone(bmp, DevelopSettings())
        val fullSettings = autoSettings.copy(
            localToneMapping = 25f,
            clarity = 20f,
            texture = 15f,
            dehaze = 12f,
            sharpenAmount = 30f,
            denoiseLuminance = 15f,
            denoiseColor = 20f,
            vignetteAmount = -18f,
            grainAmount = 15f,
            straightenAngle = 3.5f,
            masks = listOf(
                MaskLayer(
                    name = "Sky",
                    type = MaskType.SMART_SKY,
                    adjustments = MaskAdjustments(exposure = -0.3f, saturation = 15f)
                )
            )
        )

        val rendered = LightmoonPipeline.render(bmp, fullSettings)
        assertTrue(rendered.width > 0 && rendered.height > 0)

        val scope = ScopeAnalyzer.analyze(rendered)
        assertTrue(scope.lumaBins.sum() > 0)
        assertEquals(48, scope.paradeWidth)

        rendered.recycle()
        bmp.recycle()
    }

    @Test
    fun `eyedropper white balance, crop flip, and smart subject brush masks succeed`() {
        val bmp = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                bmp.setPixel(x, y, Color.rgb(120, 140, 180))
            }
        }
        val (kelvin, tint) = LightmoonPipeline.pickWhiteBalanceAt(bmp, 0.5f, 0.5f)
        assertTrue(kelvin in 2200..11000)
        assertTrue(tint in -80f..80f)

        val settings = DevelopSettings(
            temperatureKelvin = kelvin,
            tint = tint,
            rotationQuarterTurns = 1,
            flipHorizontal = true,
            cropLeft = 0.1f,
            cropTop = 0.1f,
            cropRight = 0.9f,
            cropBottom = 0.9f,
            masks = listOf(
                MaskLayer(
                    name = "Subject",
                    type = MaskType.SMART_SUBJECT,
                    adjustments = MaskAdjustments(exposure = 0.3f)
                ),
                MaskLayer(
                    name = "Brush",
                    type = MaskType.BRUSH,
                    brushStrokes = listOf(
                        BrushStroke(
                            pointsX = listOf(0.2f, 0.8f),
                            pointsY = listOf(0.5f, 0.5f)
                        )
                    ),
                    adjustments = MaskAdjustments(shadows = 20f)
                )
            )
        )
        val out = LightmoonPipeline.render(bmp, settings)
        assertTrue(out.width in 30..34 && out.height in 30..34)
        out.recycle()
        bmp.recycle()
    }
}
