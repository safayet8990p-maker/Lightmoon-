package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.model.ColorWheelTone
import com.example.ui.theme.MonoReadoutStyle
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOutline
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

@Composable
fun ColorWheelControl(
    title: String,
    tone: ColorWheelTone,
    onToneChange: (ColorWheelTone, liveOnly: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestTone by rememberUpdatedState(tone)
    val latestCallback by rememberUpdatedState(onToneChange)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .size(96.dp)
                .pointerInput(title) {
                    detectTapGestures(
                        onDoubleTap = {
                            latestCallback(ColorWheelTone(0f, 0f, latestTone.luminance), false)
                        }
                    )
                }
                .pointerInput(title) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            change.consume()
                            val cx = size.width * 0.5f
                            val cy = size.height * 0.5f
                            val maxR = min(cx, cy) - 6f
                            val dx = change.position.x - cx
                            val dy = change.position.y - cy
                            val dist = hypot(dx, dy).coerceIn(0f, maxR)
                            var deg = (atan2(dy, dx) * 180f / PI.toFloat())
                            if (deg < 0f) deg += 360f
                            val sat = (dist / maxR) * 100f
                            latestCallback(latestTone.copy(hueDeg = deg, saturation = sat), true)
                        },
                        onDragEnd = {
                            latestCallback(latestTone, false)
                        }
                    )
                }
        ) {
            val radius = min(size.width, size.height) * 0.5f - 4f
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            val sweepColors = listOf(
                Color.Red,
                Color.Yellow,
                Color.Green,
                Color.Cyan,
                Color.Blue,
                Color.Magenta,
                Color.Red
            )
            drawCircle(
                brush = Brush.sweepGradient(sweepColors, center = center),
                radius = radius,
                center = center,
                alpha = 0.88f
            )
            // White center desaturation overlay
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF282830), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                color = StudioOutline,
                radius = radius,
                center = center,
                style = Stroke(width = 2f)
            )

            // Puck indicator
            val rad = tone.hueDeg * PI.toFloat() / 180f
            val rDist = (tone.saturation / 100f).coerceIn(0f, 1f) * (radius - 4f)
            val puck = Offset(center.x + cos(rad) * rDist, center.y + sin(rad) * rDist)
            drawCircle(color = Color.White, radius = 7f, center = puck)
            drawCircle(color = StudioCyan, radius = 7f, center = puck, style = Stroke(width = 2.5f))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${tone.hueDeg.toInt()}° · ${tone.saturation.toInt()}%",
            style = MonoReadoutStyle,
            color = if (tone.saturation > 1f) StudioCyan else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
