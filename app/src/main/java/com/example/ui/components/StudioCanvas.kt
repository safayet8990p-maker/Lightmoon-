package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.model.CropAspectRatio
import com.example.model.CropOverlayGrid
import com.example.model.DevelopSettings
import com.example.model.MaskLayer
import com.example.model.MaskType
import com.example.ui.develop.CompareMode
import com.example.ui.develop.DevelopTab
import com.example.ui.theme.DarkroomAmber
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSurface
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun StudioCanvas(
    previewBitmap: Bitmap?,
    originalBitmap: Bitmap?,
    settings: DevelopSettings,
    activeTab: DevelopTab,
    activeMask: MaskLayer?,
    cropGrid: CropOverlayGrid,
    compareMode: CompareMode,
    splitFraction: Float,
    isEyedropperActive: Boolean,
    brushSizeNorm: Float,
    onSplitFractionChange: (Float) -> Unit,
    onPickWhiteBalance: (Float, Float) -> Unit,
    onUpdateCrop: (left: Float, top: Float, right: Float, bottom: Float, liveOnly: Boolean) -> Unit,
    onAppendBrushStroke: (List<Float>, List<Float>) -> Unit,
    onUpdateMaskHandles: (MaskLayer, liveOnly: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var isHoldingOriginal by remember { mutableStateOf(false) }

    val liveBrushX = remember { mutableStateListOf<Float>() }
    val liveBrushY = remember { mutableStateListOf<Float>() }

    val latestSettings by rememberUpdatedState(settings)
    val latestMask by rememberUpdatedState(activeMask)
    val latestSplit by rememberUpdatedState(splitFraction)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBackground)
            .testTag("studio_canvas"),
        contentAlignment = Alignment.Center
    ) {
        val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val bmp = previewBitmap ?: originalBitmap

        if (bmp != null) {
            val imgAspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
            val boxAspect = boxW / boxH
            val drawW: Float
            val drawH: Float
            if (imgAspect > boxAspect) {
                drawW = boxW
                drawH = boxW / imgAspect
            } else {
                drawH = boxH
                drawW = boxH * imgAspect
            }
            val imgLeft = (boxW - drawW) * 0.5f
            val imgTop = (boxH - drawH) * 0.5f

            val isBrushMode = activeTab == DevelopTab.MASKING && activeMask?.type == MaskType.BRUSH
            val isGradientMaskMode = activeTab == DevelopTab.MASKING &&
                (activeMask?.type == MaskType.LINEAR_GRADIENT || activeMask?.type == MaskType.RADIAL_GRADIENT)
            val isCropMode = activeTab == DevelopTab.CROP

            // Reset zoom if switching into interactive overlay tools
            val effectiveScale = if (isCropMode || isBrushMode || isGradientMaskMode || isEyedropperActive) 1f else scale
            val effectivePan = if (effectiveScale <= 1.01f) Offset.Zero else panOffset

            val displayBmp = if (isHoldingOriginal || compareMode == CompareMode.HOLD_ORIGINAL) {
                originalBitmap ?: bmp
            } else {
                bmp
            }

            val gestureModifier = when {
                isEyedropperActive -> Modifier.pointerInput(imgLeft, imgTop, drawW, drawH) {
                    detectTapGestures { pos ->
                        val nx = ((pos.x - imgLeft) / drawW).coerceIn(0f, 1f)
                        val ny = ((pos.y - imgTop) / drawH).coerceIn(0f, 1f)
                        onPickWhiteBalance(nx, ny)
                    }
                }

                isCropMode -> Modifier.pointerInput(imgLeft, imgTop, drawW, drawH, settings.aspectRatio) {
                    var handleMode = 0 // 1=TL, 2=TR, 3=BL, 4=BR, 5=MOVE
                    var dragStartL = 0f
                    var dragStartT = 0f
                    var dragStartR = 1f
                    var dragStartB = 1f
                    detectDragGestures(
                        onDragStart = { start ->
                            val s = latestSettings
                            dragStartL = s.cropLeft
                            dragStartT = s.cropTop
                            dragStartR = s.cropRight
                            dragStartB = s.cropBottom
                            val lx = imgLeft + s.cropLeft * drawW
                            val rx = imgLeft + s.cropRight * drawW
                            val ty = imgTop + s.cropTop * drawH
                            val by = imgTop + s.cropBottom * drawH
                            val tol = 72f
                            handleMode = when {
                                hypot(start.x - lx, start.y - ty) < tol -> 1
                                hypot(start.x - rx, start.y - ty) < tol -> 2
                                hypot(start.x - lx, start.y - by) < tol -> 3
                                hypot(start.x - rx, start.y - by) < tol -> 4
                                start.x in lx..rx && start.y in ty..by -> 5
                                else -> 0
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (handleMode == 0) return@detectDragGestures
                            change.consume()
                            val dx = dragAmount.x / drawW
                            val dy = dragAmount.y / drawH
                            val s = latestSettings
                            var l = s.cropLeft
                            var t = s.cropTop
                            var r = s.cropRight
                            var b = s.cropBottom
                            val minSpan = 0.12f

                            when (handleMode) {
                                1 -> {
                                    l = (l + dx).coerceIn(0f, r - minSpan)
                                    t = (t + dy).coerceIn(0f, b - minSpan)
                                }
                                2 -> {
                                    r = (r + dx).coerceIn(l + minSpan, 1f)
                                    t = (t + dy).coerceIn(0f, b - minSpan)
                                }
                                3 -> {
                                    l = (l + dx).coerceIn(0f, r - minSpan)
                                    b = (b + dy).coerceIn(t + minSpan, 1f)
                                }
                                4 -> {
                                    r = (r + dx).coerceIn(l + minSpan, 1f)
                                    b = (b + dy).coerceIn(t + minSpan, 1f)
                                }
                                5 -> {
                                    val w = r - l
                                    val h = b - t
                                    l = (l + dx).coerceIn(0f, 1f - w)
                                    t = (t + dy).coerceIn(0f, 1f - h)
                                    r = l + w
                                    b = t + h
                                }
                            }
                            onUpdateCrop(l, t, r, b, true)
                        },
                        onDragEnd = {
                            if (handleMode != 0) {
                                val s = latestSettings
                                onUpdateCrop(s.cropLeft, s.cropTop, s.cropRight, s.cropBottom, false)
                                handleMode = 0
                            }
                        }
                    )
                }

                isBrushMode -> Modifier.pointerInput(imgLeft, imgTop, drawW, drawH, activeMask?.id) {
                    detectDragGestures(
                        onDragStart = { start ->
                            liveBrushX.clear()
                            liveBrushY.clear()
                            liveBrushX.add(((start.x - imgLeft) / drawW).coerceIn(0f, 1f))
                            liveBrushY.add(((start.y - imgTop) / drawH).coerceIn(0f, 1f))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val nx = ((change.position.x - imgLeft) / drawW).coerceIn(0f, 1f)
                            val ny = ((change.position.y - imgTop) / drawH).coerceIn(0f, 1f)
                            liveBrushX.add(nx)
                            liveBrushY.add(ny)
                        },
                        onDragEnd = {
                            onAppendBrushStroke(liveBrushX.toList(), liveBrushY.toList())
                            liveBrushX.clear()
                            liveBrushY.clear()
                        },
                        onDragCancel = {
                            liveBrushX.clear()
                            liveBrushY.clear()
                        }
                    )
                }

                isGradientMaskMode -> Modifier.pointerInput(imgLeft, imgTop, drawW, drawH, activeMask?.id) {
                    var activeHandle = 0 // 1=start/center, 2=end/radius
                    detectDragGestures(
                        onDragStart = { start ->
                            val m = latestMask ?: return@detectDragGestures
                            val nx = ((start.x - imgLeft) / drawW).coerceIn(0f, 1f)
                            val ny = ((start.y - imgTop) / drawH).coerceIn(0f, 1f)
                            activeHandle = if (m.type == MaskType.LINEAR_GRADIENT) {
                                val d1 = hypot((nx - m.startX) * drawW, (ny - m.startY) * drawH)
                                val d2 = hypot((nx - m.endX) * drawW, (ny - m.endY) * drawH)
                                if (d1 < d2) 1 else 2
                            } else {
                                val dc = hypot((nx - m.centerX) * drawW, (ny - m.centerY) * drawH)
                                val dr = hypot((nx - (m.centerX + m.radiusX)) * drawW, (ny - m.centerY) * drawH)
                                if (dc < dr) 1 else 2
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val m = latestMask ?: return@detectDragGestures
                            val nx = ((change.position.x - imgLeft) / drawW).coerceIn(0f, 1f)
                            val ny = ((change.position.y - imgTop) / drawH).coerceIn(0f, 1f)
                            val updated = if (m.type == MaskType.LINEAR_GRADIENT) {
                                if (activeHandle == 1) m.copy(startX = nx, startY = ny) else m.copy(endX = nx, endY = ny)
                            } else {
                                if (activeHandle == 1) {
                                    m.copy(centerX = nx, centerY = ny)
                                } else {
                                    m.copy(
                                        radiusX = abs(nx - m.centerX).coerceIn(0.06f, 0.9f),
                                        radiusY = abs(ny - m.centerY).coerceIn(0.06f, 0.9f)
                                    )
                                }
                            }
                            onUpdateMaskHandles(updated, true)
                        },
                        onDragEnd = {
                            latestMask?.let { onUpdateMaskHandles(it, false) }
                            activeHandle = 0
                        }
                    )
                }

                compareMode == CompareMode.SPLIT_WIPE -> Modifier.pointerInput(imgLeft, drawW) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val frac = ((change.position.x - imgLeft) / drawW).coerceIn(0.08f, 0.92f)
                        onSplitFractionChange(frac)
                    }
                }

                else -> Modifier
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = nextScale
                            panOffset = if (nextScale <= 1.02f) Offset.Zero else panOffset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    scale = 2.2f
                                }
                            },
                            onPress = {
                                isHoldingOriginal = true
                                tryAwaitRelease()
                                isHoldingOriginal = false
                            }
                        )
                    }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(gestureModifier)
            ) {
                if (compareMode == CompareMode.SPLIT_WIPE && originalBitmap != null) {
                    // Split screen: Left = Original, Right = Edited
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val splitX = imgLeft + drawW * latestSplit
                        clipRect(left = 0f, top = 0f, right = splitX, bottom = boxH) {
                            drawImage(
                                image = originalBitmap.asImageBitmap(),
                                dstOffset = IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(drawW.roundToInt(), drawH.roundToInt())
                            )
                        }
                        clipRect(left = splitX, top = 0f, right = boxW, bottom = boxH) {
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstOffset = IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(drawW.roundToInt(), drawH.roundToInt())
                            )
                        }
                        drawLine(
                            color = StudioCyan,
                            start = Offset(splitX, imgTop),
                            end = Offset(splitX, imgTop + drawH),
                            strokeWidth = 3f
                        )
                        drawCircle(
                            color = StudioCyan,
                            radius = 16f,
                            center = Offset(splitX, imgTop + drawH * 0.5f)
                        )
                    }
                } else {
                    Image(
                        bitmap = displayBmp.asImageBitmap(),
                        contentDescription = "Edited photograph canvas",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = effectiveScale,
                                scaleY = effectiveScale,
                                translationX = effectivePan.x,
                                translationY = effectivePan.y
                            )
                    )
                }

                // Crop overlay
                if (isCropMode) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cl = imgLeft + settings.cropLeft * drawW
                        val ct = imgTop + settings.cropTop * drawH
                        val cr = imgLeft + settings.cropRight * drawW
                        val cb = imgTop + settings.cropBottom * drawH
                        val cw = cr - cl
                        val ch = cb - ct

                        // Dim outside crop
                        val dim = Color.Black.copy(alpha = 0.62f)
                        drawRect(dim, Offset(imgLeft, imgTop), Size(drawW, ct - imgTop))
                        drawRect(dim, Offset(imgLeft, cb), Size(drawW, (imgTop + drawH) - cb))
                        drawRect(dim, Offset(imgLeft, ct), Size(cl - imgLeft, ch))
                        drawRect(dim, Offset(cr, ct), Size((imgLeft + drawW) - cr, ch))

                        // Grid lines inside crop
                        when (cropGrid) {
                            CropOverlayGrid.THIRDS -> {
                                for (i in 1..2) {
                                    val x = cl + cw * i / 3f
                                    val y = ct + ch * i / 3f
                                    drawLine(Color.White.copy(alpha = 0.45f), Offset(x, ct), Offset(x, cb), 1.5f)
                                    drawLine(Color.White.copy(alpha = 0.45f), Offset(cl, y), Offset(cr, y), 1.5f)
                                }
                            }
                            CropOverlayGrid.GOLDEN -> {
                                val phi = 0.382f
                                val x1 = cl + cw * phi
                                val x2 = cl + cw * (1f - phi)
                                val y1 = ct + ch * phi
                                val y2 = ct + ch * (1f - phi)
                                drawLine(DarkroomAmber.copy(alpha = 0.55f), Offset(x1, ct), Offset(x1, cb), 1.5f)
                                drawLine(DarkroomAmber.copy(alpha = 0.55f), Offset(x2, ct), Offset(x2, cb), 1.5f)
                                drawLine(DarkroomAmber.copy(alpha = 0.55f), Offset(cl, y1), Offset(cr, y1), 1.5f)
                                drawLine(DarkroomAmber.copy(alpha = 0.55f), Offset(cl, y2), Offset(cr, y2), 1.5f)
                            }
                            CropOverlayGrid.GRID -> {
                                for (i in 1..5) {
                                    val x = cl + cw * i / 6f
                                    val y = ct + ch * i / 6f
                                    drawLine(Color.White.copy(alpha = 0.25f), Offset(x, ct), Offset(x, cb), 1f)
                                    drawLine(Color.White.copy(alpha = 0.25f), Offset(cl, y), Offset(cr, y), 1f)
                                }
                            }
                            CropOverlayGrid.NONE -> Unit
                        }

                        // Crop border & corner brackets
                        drawRect(Color.White, Offset(cl, ct), Size(cw, ch), style = Stroke(2.5f))
                        val corners = listOf(
                            Offset(cl, ct),
                            Offset(cr, ct),
                            Offset(cl, cb),
                            Offset(cr, cb)
                        )
                        corners.forEach { corner ->
                            drawCircle(StudioCyan, radius = 10f, center = corner)
                            drawCircle(Color.White, radius = 10f, center = corner, style = Stroke(2.5f))
                        }
                    }
                }

                // Mask handles & live brush trail
                if (activeTab == DevelopTab.MASKING && activeMask != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (activeMask.type == MaskType.LINEAR_GRADIENT) {
                            val sx = imgLeft + activeMask.startX * drawW
                            val sy = imgTop + activeMask.startY * drawH
                            val ex = imgLeft + activeMask.endX * drawW
                            val ey = imgTop + activeMask.endY * drawH
                            drawLine(StudioCyan, Offset(sx, sy), Offset(ex, ey), strokeWidth = 2.5f)
                            drawCircle(StudioCyan, radius = 12f, center = Offset(sx, sy))
                            drawCircle(Color.White, radius = 10f, center = Offset(ex, ey))
                        } else if (activeMask.type == MaskType.RADIAL_GRADIENT) {
                            val cx = imgLeft + activeMask.centerX * drawW
                            val cy = imgTop + activeMask.centerY * drawH
                            val rx = activeMask.radiusX * drawW
                            val ry = activeMask.radiusY * drawH
                            drawOval(
                                color = StudioCyan,
                                topLeft = Offset(cx - rx, cy - ry),
                                size = Size(rx * 2f, ry * 2f),
                                style = Stroke(width = 2.5f)
                            )
                            drawCircle(StudioCyan, radius = 11f, center = Offset(cx, cy))
                            drawCircle(Color.White, radius = 9f, center = Offset(cx + rx, cy))
                        }

                        if (liveBrushX.size > 1) {
                            val path = Path()
                            liveBrushX.forEachIndexed { idx, nx ->
                                val px = imgLeft + nx * drawW
                                val py = imgTop + liveBrushY[idx] * drawH
                                if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                            }
                            drawPath(
                                path,
                                color = StudioCyan.copy(alpha = 0.7f),
                                style = Stroke(
                                    width = brushSizeNorm * min(drawW, drawH) * 2f,
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }
                }

                // HUD Status Chips (Original Hold / Eyedropper / Split)
                if (isHoldingOriginal || compareMode == CompareMode.HOLD_ORIGINAL) {
                    StudioCanvasBadge("ORIGINAL", modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
                } else if (compareMode == CompareMode.SPLIT_WIPE) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                    ) {
                        StudioCanvasBadge("BEFORE")
                        Spacer(modifier = Modifier.width(8.dp))
                        StudioCanvasBadge("AFTER")
                    }
                } else if (isEyedropperActive) {
                    StudioCanvasBadge(
                        "TAP A NEUTRAL GRAY AREA TO SET WB",
                        tint = StudioCyan,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioCanvasBadge(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = tint,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(StudioSurface.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
