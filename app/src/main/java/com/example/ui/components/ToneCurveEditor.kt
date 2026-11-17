package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.LightmoonPipeline
import com.example.model.CurvePoint
import com.example.model.ToneCurveChannel
import com.example.ui.theme.OpticalSilver
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOutlineVariant
import kotlin.math.hypot

@Composable
fun ToneCurveEditor(
    channel: ToneCurveChannel,
    points: List<CurvePoint>,
    lumaBins: IntArray,
    onPointsChange: (List<CurvePoint>, liveOnly: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestPoints by rememberUpdatedState(points)
    val latestCallback by rememberUpdatedState(onPointsChange)
    var activePointIndex by remember { mutableIntStateOf(-1) }

    val curveColor = when (channel) {
        ToneCurveChannel.RGB -> OpticalSilver
        ToneCurveChannel.RED -> Color(0xFFEF4444)
        ToneCurveChannel.GREEN -> Color(0xFF22C55E)
        ToneCurveChannel.BLUE -> Color(0xFF3B82F6)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(StudioBackground)
            .border(1.dp, StudioOutlineVariant, RoundedCornerShape(10.dp))
            .testTag("tone_curve_canvas")
            .pointerInput(channel) {
                detectTapGestures(
                    onTap = { pos ->
                        val nx = (pos.x / size.width).coerceIn(0.02f, 0.98f)
                        val ny = (1f - pos.y / size.height).coerceIn(0f, 1f)
                        val sorted = latestPoints.sortedBy { it.x }
                        // If tapping near existing intermediate point, select it; otherwise insert new point
                        val nearIdx = sorted.indexOfFirst {
                            hypot((it.x - nx) * size.width, ((1f - it.y) - (1f - ny)) * size.height) < 36f
                        }
                        if (nearIdx < 0 && sorted.size < 9) {
                            val updated = (sorted + CurvePoint(nx, ny)).sortedBy { it.x }
                            latestCallback(updated, false)
                        }
                    },
                    onDoubleTap = { pos ->
                        val nx = pos.x / size.width
                        val ny = 1f - pos.y / size.height
                        val sorted = latestPoints.sortedBy { it.x }
                        val nearIdx = sorted.indexOfFirst {
                            hypot((it.x - nx) * size.width, ((1f - it.y) - (1f - ny)) * size.height) < 42f
                        }
                        if (nearIdx > 0 && nearIdx < sorted.lastIndex) {
                            val updated = sorted.toMutableList().apply { removeAt(nearIdx) }
                            latestCallback(updated, false)
                        }
                    }
                )
            }
            .pointerInput(channel) {
                detectDragGestures(
                    onDragStart = { start ->
                        val sorted = latestPoints.sortedBy { it.x }
                        var bestIdx = -1
                        var bestDist = Float.MAX_VALUE
                        sorted.forEachIndexed { idx, pt ->
                            val px = pt.x * size.width
                            val py = (1f - pt.y) * size.height
                            val d = hypot(px - start.x, py - start.y)
                            if (d < bestDist) {
                                bestDist = d
                                bestIdx = idx
                            }
                        }
                        if (bestDist <= 64f) {
                            activePointIndex = bestIdx
                        } else if (sorted.size < 9) {
                            val nx = (start.x / size.width).coerceIn(0.02f, 0.98f)
                            val ny = (1f - start.y / size.height).coerceIn(0f, 1f)
                            val updated = (sorted + CurvePoint(nx, ny)).sortedBy { it.x }
                            activePointIndex = updated.indexOfFirst { it.x == nx && it.y == ny }
                            latestCallback(updated, true)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val idx = activePointIndex
                        val sorted = latestPoints.sortedBy { it.x }.toMutableList()
                        if (idx in sorted.indices) {
                            val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                            val ny = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            val clampedX = when (idx) {
                                0 -> nx.coerceIn(0f, (sorted.getOrNull(1)?.x ?: 1f) - 0.03f)
                                sorted.lastIndex -> nx.coerceIn((sorted.getOrNull(idx - 1)?.x ?: 0f) + 0.03f, 1f)
                                else -> nx.coerceIn(sorted[idx - 1].x + 0.03f, sorted[idx + 1].x - 0.03f)
                            }
                            sorted[idx] = CurvePoint(clampedX, ny)
                            latestCallback(sorted, true)
                        }
                    },
                    onDragEnd = {
                        if (activePointIndex >= 0) {
                            activePointIndex = -1
                            latestCallback(latestPoints, false)
                        }
                    },
                    onDragCancel = {
                        activePointIndex = -1
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height

        // Subtle backdrop histogram
        val maxLuma = lumaBins.maxOrNull()?.coerceAtLeast(1) ?: 1
        val histPath = Path().apply {
            moveTo(0f, h)
            for (i in 0 until 256 step 4) {
                val x = i / 255f * w
                val y = h - (lumaBins[i].toFloat() / maxLuma).coerceIn(0f, 1f) * h * 0.75f
                lineTo(x, y)
            }
            lineTo(w, h)
            close()
        }
        drawPath(histPath, color = Color.White.copy(alpha = 0.07f))

        // 4x4 grid + diagonal identity reference
        for (i in 1..3) {
            val gx = w * i / 4f
            val gy = h * i / 4f
            drawLine(Color.White.copy(alpha = 0.08f), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
        }
        drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, h), Offset(w, 0f), strokeWidth = 1.5f)

        // Smooth spline curve
        val sorted = points.sortedBy { it.x }
        val splinePath = Path()
        val steps = 64
        for (s in 0..steps) {
            val xNorm = s.toFloat() / steps
            val yNorm = LightmoonPipeline.evaluateCatmullRom(sorted, xNorm).coerceIn(0f, 1f)
            val px = xNorm * w
            val py = (1f - yNorm) * h
            if (s == 0) splinePath.moveTo(px, py) else splinePath.lineTo(px, py)
        }
        drawPath(splinePath, color = curveColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

        // Control point handles
        sorted.forEachIndexed { idx, pt ->
            val cx = pt.x * w
            val cy = (1f - pt.y) * h
            val isSelected = idx == activePointIndex
            drawCircle(
                color = if (isSelected) StudioCyan else StudioBackground,
                radius = if (isSelected) 11f else 8f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = if (isSelected) Color.White else curveColor,
                radius = if (isSelected) 11f else 8f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5f)
            )
        }
    }
}
