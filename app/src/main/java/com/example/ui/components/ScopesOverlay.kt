package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ScopeData
import com.example.model.ScopeMode
import com.example.ui.theme.DarkroomAmber
import com.example.ui.theme.MonoReadoutStyle
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioDanger
import com.example.ui.theme.StudioOutlineVariant
import com.example.ui.theme.StudioSurface
import kotlin.math.max

@Composable
fun ScopesOverlay(
    scopeData: ScopeData,
    scopeMode: ScopeMode,
    showClipping: Boolean,
    onCycleScopeMode: () -> Unit,
    onToggleClipping: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (scopeMode == ScopeMode.OFF) return

    Column(
        modifier = modifier
            .width(172.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(StudioSurface.copy(alpha = 0.86f))
            .border(1.dp, StudioOutlineVariant, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = scopeMode.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onCycleScopeMode)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("cycle_scope_button")
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onToggleClipping)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("toggle_clipping_button")
            ) {
                val shadowHot = scopeData.shadowClippedPercent > 0.5f
                val highHot = scopeData.highlightClippedPercent > 0.5f
                Text(
                    text = "▲",
                    style = MonoReadoutStyle,
                    color = if (showClipping || shadowHot) Color(0xFF60A5FA) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "▲",
                    style = MonoReadoutStyle,
                    color = if (showClipping || highHot) StudioDanger else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clickable(onClick = onCycleScopeMode)
        ) {
            val w = size.width
            val h = size.height

            // Subtle reference grid
            for (i in 1..3) {
                val gx = w * i / 4f
                drawLine(Color.White.copy(alpha = 0.08f), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
            }

            when (scopeMode) {
                ScopeMode.HISTOGRAM -> {
                    var maxVal = 1
                    for (i in 2 until 254) {
                        maxVal = max(maxVal, scopeData.redBins[i])
                        maxVal = max(maxVal, scopeData.greenBins[i])
                        maxVal = max(maxVal, scopeData.blueBins[i])
                    }
                    val invMax = 1f / maxVal

                    fun drawChannel(bins: IntArray, color: Color) {
                        val path = Path().apply {
                            moveTo(0f, h)
                            for (i in 0 until 256) {
                                val x = i / 255f * w
                                val y = h - (bins[i] * invMax).coerceIn(0f, 1f) * h
                                lineTo(x, y)
                            }
                            lineTo(w, h)
                            close()
                        }
                        drawPath(path, color = color, style = Fill, blendMode = BlendMode.Plus)
                    }

                    drawChannel(scopeData.redBins, Color(0x99EF4444))
                    drawChannel(scopeData.greenBins, Color(0x9922C55E))
                    drawChannel(scopeData.blueBins, Color(0x993B82F6))
                    drawChannel(scopeData.lumaBins, Color(0x33FFFFFF))
                }

                ScopeMode.RGB_PARADE -> {
                    val cols = scopeData.paradeWidth
                    val rows = 32
                    val thirdW = w / 3f
                    val cellW = thirdW / cols
                    val cellH = h / rows

                    fun drawParade(data: FloatArray, xOffset: Float, color: Color) {
                        for (r in 0 until rows) {
                            for (c in 0 until cols) {
                                val v = data[r * cols + c]
                                if (v > 0.03f) {
                                    drawRect(
                                        color = color.copy(alpha = (v * 0.9f).coerceIn(0.12f, 0.95f)),
                                        topLeft = Offset(xOffset + c * cellW, r * cellH),
                                        size = Size(cellW + 0.5f, cellH + 0.5f)
                                    )
                                }
                            }
                        }
                    }

                    drawParade(scopeData.paradeRed, 0f, Color(0xFFEF4444))
                    drawParade(scopeData.paradeGreen, thirdW, Color(0xFF22C55E))
                    drawParade(scopeData.paradeBlue, thirdW * 2f, Color(0xFF3B82F6))
                }

                ScopeMode.WAVEFORM -> {
                    val cols = scopeData.paradeWidth
                    val rows = 32
                    val cellW = w / cols
                    val cellH = h / rows
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            val v = scopeData.waveform[r * cols + c]
                            if (v > 0.03f) {
                                drawRect(
                                    color = StudioCyan.copy(alpha = (v * 0.9f).coerceIn(0.12f, 0.95f)),
                                    topLeft = Offset(c * cellW, r * cellH),
                                    size = Size(cellW + 0.5f, cellH + 0.5f)
                                )
                            }
                        }
                    }
                }

                ScopeMode.OFF -> Unit
            }
        }

        if (showClipping) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "S:${"%.1f".format(scopeData.shadowClippedPercent)}%  H:${"%.1f".format(scopeData.highlightClippedPercent)}%",
                style = MaterialTheme.typography.labelSmall,
                color = DarkroomAmber
            )
        }
    }
}
