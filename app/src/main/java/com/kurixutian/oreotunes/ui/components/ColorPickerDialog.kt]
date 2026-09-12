package com.kurixutian.oreotunes.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Exact 5 x 8 Palette Matrix from Screenshot 1000077864
val SUGGESTED_PALETTE_COLORS = listOf(
    // Row 1
    Color(0xFFFF3B30), Color(0xFFFF6961), Color(0xFFD32F2F), Color(0xFFFF2D55),
    Color(0xFFFF1493), Color(0xFFFF9500), Color(0xFFFF6D00), Color(0xFFFF8A65),
    // Row 2
    Color(0xFFFF7043), Color(0xFFFFB300), Color(0xFFFFD600), Color(0xFFFFEE58),
    Color(0xFFB2FF59), Color(0xFF76FF03), Color(0xFF4CAF50), Color(0xFF00E676),
    // Row 3
    Color(0xFF00C853), Color(0xFF26A69A), Color(0xFF00BFA5), Color(0xFF4DD0E1),
    Color(0xFF00BCD4), Color(0xFF00E5FF), Color(0xFF40C4FF), Color(0xFF0091EA),
    // Row 4
    Color(0xFF1E88E5), Color(0xFF2979FF), Color(0xFF448AFF), Color(0xFF536DFE),
    Color(0xFF7C4DFF), Color(0xFF9C27B0), Color(0xFF6A1B9A), Color(0xFFBA68C8),
    // Row 5
    Color(0xFFE040FB), Color(0xFFFF4081), Color(0xFFA1887F), Color(0xFF8D6E63),
    Color(0xFF9E9E9E), Color(0xFF616161), Color(0xFF424242), Color(0xFFFFFFFF)
)

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
    onResetDefault: () -> Unit
) {
    val initialHsv = remember {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Colors Grid, 1 = Custom Wheel

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1].coerceIn(0f, 1f)) }
    var value by remember { mutableFloatStateOf(initialHsv[2].coerceIn(0f, 1f)) }

    fun getCurrentColor(): Color {
        val rgb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
        return Color(rgb)
    }

    fun toHex(c: Color): String {
        val r = (c.red * 255).toInt()
        val g = (c.green * 255).toInt()
        val b = (c.blue * 255).toInt()
        return String.format("#%02X%02X%02X", r, g, b)
    }

    var hexInput by remember { mutableStateOf(toHex(getCurrentColor())) }

    fun selectPreset(preset: Color) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(preset.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        hexInput = toHex(preset)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Color",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(getCurrentColor())
                            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                    )
                }

                // Sub-tabs: Colors vs Custom (from Screenshot 1000077864)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Colors", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Custom", fontWeight = FontWeight.Bold) }
                    )
                }

                if (selectedTab == 0) {
                    // Exact 5 x 8 Palette Matrix
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SUGGESTED_PALETTE_COLORS) { color ->
                            val isSelected = toHex(getCurrentColor()) == toHex(color)
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(color)
                                    .border(
                                        width = if (color == Color.White) 1.dp else 0.dp,
                                        color = if (color == Color.White) Color.LightGray else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectPreset(color) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (color == Color.White || color == Color(0xFFFFEE58)) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Custom Circular Wheel + Inner Sat/Val Box
                    CircularWheelWithInnerBox(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        onHueChanged = {
                            hue = it
                            hexInput = toHex(getCurrentColor())
                        },
                        onSatValChanged = { s, v ->
                            saturation = s
                            value = v
                            hexInput = toHex(getCurrentColor())
                        }
                    )
                }

                // Hex Code Field
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        val filtered = raw.uppercase().filter { it.isDigit() || it in 'A'..'F' || it == '#' }
                        hexInput = filtered
                        val cleanHex = if (filtered.startsWith("#")) filtered else "#$filtered"
                        if (cleanHex.length == 7) {
                            try {
                                val parsed = AndroidColor.parseColor(cleanHex)
                                val hsv = FloatArray(3)
                                AndroidColor.colorToHSV(parsed, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            } catch (_: Exception) {}
                        }
                    },
                    label = { Text("Hex Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Dialog Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        onResetDefault()
                        onDismiss()
                    }) {
                        Text("Default")
                    }

                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(onClick = {
                            onColorSelected(getCurrentColor())
                            onDismiss()
                        }) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CircularWheelWithInnerBox(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueChanged: (Float) -> Unit,
    onSatValChanged: (Float, Float) -> Unit
) {
    val outerSize = 220.dp
    val boxSize = 100.dp

    Box(
        modifier = Modifier.size(outerSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val angleRad = atan2(offset.y - center.y, offset.x - center.x)
                            var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
                            if (angleDeg < 0f) angleDeg += 360f
                            onHueChanged(angleDeg)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val angleRad = atan2(change.position.y - center.y, change.position.x - center.x)
                            var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
                            if (angleDeg < 0f) angleDeg += 360f
                            onHueChanged(angleDeg)
                        }
                    )
                }
        ) {
            val strokeWidth = 22.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            for (i in 0 until 360) {
                val currentRgb = AndroidColor.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
                drawArc(
                    color = Color(currentRgb),
                    startAngle = i.toFloat(),
                    sweepAngle = 1.2f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            }

            val thumbAngleRad = Math.toRadians(hue.toDouble())
            val thumbCenter = Offset(
                center.x + (radius * cos(thumbAngleRad)).toFloat(),
                center.y + (radius * sin(thumbAngleRad)).toFloat()
            )
            drawCircle(color = Color.White, radius = 11.dp.toPx(), center = thumbCenter, style = Stroke(width = 3.dp.toPx()))
        }

        var innerSizePx by remember { mutableStateOf(IntSize.Zero) }
        val baseHueColor = remember(hue) {
            Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        }

        Box(
            modifier = Modifier
                .size(boxSize)
                .clip(RoundedCornerShape(8.dp))
                .onSizeChanged { innerSizePx = it }
                .background(baseHueColor)
                .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .pointerInput(hue) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (innerSizePx.width > 0 && innerSizePx.height > 0) {
                                val s = (offset.x / innerSizePx.width).coerceIn(0f, 1f)
                                val v = 1f - (offset.y / innerSizePx.height).coerceIn(0f, 1f)
                                onSatValChanged(s, v)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (innerSizePx.width > 0 && innerSizePx.height > 0) {
                                val s = (change.position.x / innerSizePx.width).coerceIn(0f, 1f)
                                val v = 1f - (change.position.y / innerSizePx.height).coerceIn(0f, 1f)
                                onSatValChanged(s, v)
                            }
                        }
                    )
                }
        ) {
            if (innerSizePx.width > 0 && innerSizePx.height > 0) {
                val pinSize = 16.dp.value
                val maxOffX = (innerSizePx.width - pinSize).coerceAtLeast(0f)
                val maxOffY = (innerSizePx.height - pinSize).coerceAtLeast(0f)

                val offX = (saturation * maxOffX).roundToInt()
                val offY = ((1f - value) * maxOffY).roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(offX, offY) }
                        .size(16.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }
    }
}

