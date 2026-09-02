package com.kurixutian.oreotunes.ui.screens

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kurixutian.oreotunes.data.preferences.AppThemeMode
import com.kurixutian.oreotunes.data.preferences.DarkThemeStyle
import com.kurixutian.oreotunes.data.preferences.LightThemeStyle
import com.kurixutian.oreotunes.data.repository.ArtworkPalette
import com.kurixutian.oreotunes.data.repository.GeminiMoodEngine
import com.kurixutian.oreotunes.data.update.UpdateInfo
import com.kurixutian.oreotunes.ui.components.AboutSupportSection
import com.kurixutian.oreotunes.ui.components.GlassIconButton
import com.kurixutian.oreotunes.ui.theme.Manrope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    crossfadeEnabled: Boolean,
    crossfadeDuration: Int,
    heroRefreshHours: Int = 3,
    volumeNormalizationEnabled: Boolean = true,
    hiFiBypassEnabled: Boolean = false,
    isExternalDacConnected: Boolean = false,
    isBluetoothConnected: Boolean = false,
    connectedDeviceName: String = "",
    selectedFoldersCount: Int = 0,
    palette: ArtworkPalette? = null,
    appThemeMode: AppThemeMode = AppThemeMode.DEFAULT,
    darkThemeStyle: DarkThemeStyle = DarkThemeStyle.AMOLED_DYNAMIC,
    lightThemeStyle: LightThemeStyle = LightThemeStyle.PURE_WHITE_DYNAMIC,
    customAccentColor: Color = Color(0xFF64D2FF),
    artworkScalePercent: Int = 100,
    artworkCornerRadiusDp: Int = 28,
    currentVersion: String = "Unknown",
    updateInfo: UpdateInfo? = null,
    isCheckingForUpdate: Boolean = false,
    onCheckForUpdates: () -> Unit = {},
    supportUrl: String = "https://buymeacoffee.com/kurixutian",
    onToggleCrossfade: (Boolean) -> Unit,
    onCrossfadeDurationChange: (Int) -> Unit,
    onHeroRefreshHoursChange: (Int) -> Unit = {},
    onToggleVolumeNormalization: (Boolean) -> Unit = {},
    onToggleHiFiBypass: (Boolean) -> Unit = {},
    onPromptDacPermission: () -> Unit = {},
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
    onDarkThemeStyleChange: (DarkThemeStyle) -> Unit = {},
    onLightThemeStyleChange: (LightThemeStyle) -> Unit = {},
    onCustomAccentColorChange: (Color) -> Unit = {},
    onArtworkScaleChange: (Int) -> Unit = {},
    onArtworkCornerRadiusChange: (Int) -> Unit = {},
    onManageFolders: () -> Unit = {},
    onScanLibrary: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val moodEngine = remember { GeminiMoodEngine(context) }
    var geminiApiKey by remember { mutableStateOf(moodEngine.getApiKey()) }
    var isKeySaved by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }

    var showCustomHexDialog by remember { mutableStateOf(false) }
    var hexInputText by remember { mutableStateOf("#") }

    val isLight = appThemeMode == AppThemeMode.LIGHT
    val dialogBg = if (isLight) Color(0xFFFFFFFF) else Color(0xFF141724).copy(alpha = 0.95f)
    val cardBg = if (isLight) Color(0xFFF1F3F9) else Color.White.copy(alpha = 0.07f)
    val textColor = if (isLight) Color(0xFF121520) else Color.White
    val subtleColor = textColor.copy(alpha = 0.65f)

    val dynamicPrimary = if (isLight) (palette?.lightAccent ?: Color(0xFF181A24)) else (palette?.accent ?: Color(0xFF64D2FF))

    val activeAccent = when (appThemeMode) {
        AppThemeMode.DEFAULT -> dynamicPrimary
        AppThemeMode.DARK -> when (darkThemeStyle) {
            DarkThemeStyle.AMOLED_DYNAMIC -> dynamicPrimary
            DarkThemeStyle.AMOLED_CUSTOM_ACCENT -> customAccentColor
        }
        AppThemeMode.LIGHT -> when (lightThemeStyle) {
            LightThemeStyle.PURE_WHITE_DYNAMIC -> dynamicPrimary
            LightThemeStyle.PURE_WHITE_CUSTOM_ACCENT -> customAccentColor
        }
    }

    val animatedPrimary by animateColorAsState(targetValue = activeAccent, animationSpec = tween(400), label = "settingsPrimary")

    val infiniteTransition = rememberInfiniteTransition(label = "scanRotate")
    val scanRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing)
        ),
        label = "scanRotateAngle"
    )

    val themeColorRows = listOf(
        listOf(Color(0xFFE4E4EC), Color(0xFFC8C8D2), Color(0xFF9696A4), Color(0xFF646472), Color(0xFF383842), Color(0xFF14141A)),
        listOf(Color(0xFFBBE5FD), Color(0xFF7CD4FD), Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0369A1), Color(0xFF075985)),
        listOf(Color(0xFFFEF08A), Color(0xFFFDE047), Color(0xFFEAB308), Color(0xFFCA8A04), Color(0xFFA16207), Color(0xFF713F12)),
        listOf(Color(0xFFFECDD3), Color(0xFFFDA4AF), Color(0xFFFB7185), Color(0xFFE11D48), Color(0xFFBE123C), Color(0xFF881337)),
        listOf(Color(0xFFBBF7D0), Color(0xFF86EFAC), Color(0xFF4ADE80), Color(0xFF16A34A), Color(0xFF15803D), Color(0xFF14532D))
    )

    val vibrantMyColors = listOf(
        Color(0xFF64748B), Color(0xFF475569), Color(0xFF3B82F6), Color(0xFF06B6D4), Color(0xFF8B5CF6), Color(0xFFD946EF),
        Color(0xFFF43F5E), Color(0xFF6366F1), Color(0xFF22C55E), Color(0xFF10B981), Color(0xFFEAB308), Color(0xFFF97316)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                window.setDimAmount(0.55f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes.blurBehindRadius = 48
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(dialogBg)
                .border(
                    width = if (isLight) 1.dp else 0.dp,
                    color = Color.Black.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .padding(22.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontFamily = Manrope),
                                color = textColor,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Themes, Artwork, Hi-Fi DAC & Playback",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFamily = Manrope),
                                color = subtleColor
                            )
                        }

                        GlassIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Close",
                            size = 38.dp,
                            iconSize = 18.dp,
                            onClick = onDismiss
                        )
                    }
                }

                // 1. THEME SELECTION & COLOR PICKER SECTION
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "THEME & ACCENTS",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = subtleColor,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardBg)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppThemeMode.values().forEach { mode ->
                                val isSelected = mode == appThemeMode
                                val tabTextBg = if (isSelected) animatedPrimary else if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f)
                                val tabContentColor = if (isSelected) {
                                    if (isLight && animatedPrimary.red < 0.5f) Color.White else Color.Black
                                } else textColor

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(tabTextBg)
                                        .clickable { onAppThemeModeChange(mode) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.name.replaceFirstChar { it.uppercase() },
                                        fontFamily = Manrope,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = tabContentColor,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        // Sub-options for DARK MODE
                        if (appThemeMode == AppThemeMode.DARK) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Dark / AMOLED Style:", fontFamily = Manrope, fontSize = 12.sp, color = subtleColor)
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DarkThemeStyle.values().forEach { style ->
                                    val isStyleSelected = style == darkThemeStyle
                                    val label = when (style) {
                                        DarkThemeStyle.AMOLED_DYNAMIC -> "AMOLED with Dynamic Artwork Color"
                                        DarkThemeStyle.AMOLED_CUSTOM_ACCENT -> "AMOLED with Custom Accent Color"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isStyleSelected) animatedPrimary.copy(alpha = 0.18f) else Color.Transparent)
                                            .clickable { onDarkThemeStyleChange(style) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isStyleSelected,
                                            onClick = { onDarkThemeStyleChange(style) },
                                            colors = RadioButtonDefaults.colors(selectedColor = animatedPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(label, fontFamily = Manrope, fontSize = 12.5.sp, color = textColor)
                                    }
                                }
                            }
                        }

                        // Sub-options for LIGHT MODE
                        if (appThemeMode == AppThemeMode.LIGHT) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Light Mode Style:", fontFamily = Manrope, fontSize = 12.sp, color = subtleColor)
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LightThemeStyle.values().forEach { style ->
                                    val isStyleSelected = style == lightThemeStyle
                                    val label = when (style) {
                                        LightThemeStyle.PURE_WHITE_DYNAMIC -> "Clean White with Dynamic Color"
                                        LightThemeStyle.PURE_WHITE_CUSTOM_ACCENT -> "Clean White with Custom Accent"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isStyleSelected) animatedPrimary.copy(alpha = 0.12f) else Color.Transparent)
                                            .clickable { onLightThemeStyleChange(style) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isStyleSelected,
                                            onClick = { onLightThemeStyleChange(style) },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = animatedPrimary,
                                                unselectedColor = Color(0xFF6B7280)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(label, fontFamily = Manrope, fontSize = 12.5.sp, color = textColor, fontWeight = if (isStyleSelected) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        // Color Picker Box
                        val showColorPicker = (appThemeMode == AppThemeMode.DARK && darkThemeStyle == DarkThemeStyle.AMOLED_CUSTOM_ACCENT) ||
                                (appThemeMode == AppThemeMode.LIGHT && lightThemeStyle == LightThemeStyle.PURE_WHITE_CUSTOM_ACCENT)

                        if (showColorPicker) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isLight) Color(0xFFE6E9F2) else Color(0xFF0F111A))
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Theme Color", fontFamily = Manrope, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                        Text(
                                            text = "#${Integer.toHexString(customAccentColor.toArgb()).uppercase().takeLast(6)}",
                                            fontFamily = Manrope,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = animatedPrimary,
                                            modifier = Modifier.clickable {
                                                hexInputText = "#${Integer.toHexString(customAccentColor.toArgb()).uppercase().takeLast(6)}"
                                                showCustomHexDialog = true
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        themeColorRows.forEach { rowColors ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                rowColors.forEach { color ->
                                                    val isSelected = color == customAccentColor
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(20.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(color)
                                                            .border(
                                                                width = if (isSelected) 2.dp else 0.dp,
                                                                color = if (isLight) Color.Black else Color.White,
                                                                shape = RoundedCornerShape(4.dp)
                                                            )
                                                            .clickable { onCustomAccentColorChange(color) }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Preset Swatches", fontFamily = Manrope, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = subtleColor)
                                        Text(
                                            text = "+ Custom Hex",
                                            fontFamily = Manrope,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = animatedPrimary,
                                            modifier = Modifier.clickable {
                                                hexInputText = "#"
                                                showCustomHexDialog = true
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(vibrantMyColors) { color ->
                                            val isSelected = color == customAccentColor
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(
                                                        width = if (isSelected) 2.dp else 0.dp,
                                                        color = if (isLight) Color.Black else Color.White,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { onCustomAccentColorChange(color) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. NOW PLAYING ARTWORK DISPLAY PREVIEW
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "NOW PLAYING ARTWORK DISPLAY",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = subtleColor,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardBg)
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isLight) Color(0xFFE6E9F2) else Color(0xFF0F121C)),
                            contentAlignment = Alignment.Center
                        ) {
                            val previewScaleFraction = (artworkScalePercent / 100f).coerceIn(0.65f, 1f)
                            val previewShape = RoundedCornerShape((artworkCornerRadiusDp * (120f / 350f)).dp)

                            Box(
                                modifier = Modifier
                                    .size(110.dp * previewScaleFraction)
                                    .clip(previewShape)
                                    .background(animatedPrimary.copy(alpha = if (isLight) 0.20f else 0.35f))
                                    .border(1.dp, if (isLight) animatedPrimary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.25f), previewShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = if (isLight) animatedPrimary else Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Artwork Scale", fontFamily = Manrope, fontSize = 13.sp, color = textColor, fontWeight = FontWeight.SemiBold)
                            Text("${artworkScalePercent}%", fontFamily = Manrope, fontSize = 12.sp, color = animatedPrimary, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = artworkScalePercent.toFloat(),
                            onValueChange = { onArtworkScaleChange(it.toInt()) },
                            valueRange = 65f..100f,
                            steps = 6,
                            colors = SliderDefaults.colors(
                                thumbColor = animatedPrimary,
                                activeTrackColor = animatedPrimary,
                                inactiveTrackColor = if (isLight) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.10f)
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Corner Roundness", fontFamily = Manrope, fontSize = 13.sp, color = textColor, fontWeight = FontWeight.SemiBold)
                            Text("${artworkCornerRadiusDp} dp", fontFamily = Manrope, fontSize = 12.sp, color = animatedPrimary, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = artworkCornerRadiusDp.toFloat(),
                            onValueChange = { onArtworkCornerRadiusChange(it.toInt()) },
                            valueRange = 0f..36f,
                            steps = 35,
                            colors = SliderDefaults.colors(
                                thumbColor = animatedPrimary,
                                activeTrackColor = animatedPrimary,
                                inactiveTrackColor = if (isLight) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.10f)
                            )
                        )
                    }
                }

                // 3. HI-FI BIT-PERFECT & DAC SECTION
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "HI-FI & BIT-PERFECT AUDIO",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = subtleColor,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardBg)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(animatedPrimary.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Headphones,
                                        contentDescription = null,
                                        tint = animatedPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Hi-Fi Processing Bypass",
                                        fontFamily = Manrope,
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        text = "Bit-perfect unscaled audio for DAC & BT",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Manrope),
                                        color = subtleColor
                                    )
                                }
                            }

                            Switch(
                                checked = hiFiBypassEnabled,
                                onCheckedChange = onToggleHiFiBypass,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = animatedPrimary,
                                    uncheckedThumbColor = if (isLight) Color.Gray else Color.White.copy(alpha = 0.6f),
                                    uncheckedTrackColor = if (isLight) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)
                                )
                            )
                        }

                        if (isExternalDacConnected || isBluetoothConnected) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(animatedPrimary.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isExternalDacConnected) Icons.Rounded.Usb else Icons.Rounded.Bluetooth,
                                        contentDescription = null,
                                        tint = animatedPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (connectedDeviceName.isNotBlank()) connectedDeviceName else if (isExternalDacConnected) "External USB DAC Active" else "Bluetooth Hi-Fi Active",
                                        fontFamily = Manrope,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                                if (isExternalDacConnected) {
                                    TextButton(onClick = onPromptDacPermission) {
                                        Text("Re-query", fontFamily = Manrope, fontSize = 11.sp, color = animatedPrimary)
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. GEMINI AI INTEGRATION
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "GEMINI AI INTEGRATION",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = subtleColor,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardBg)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Gemini API Key",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            fontSize = 14.5.sp
                        )
                        Text(
                            text = "Powers AI mood & trip curation algorithm",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Manrope),
                            color = subtleColor
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = geminiApiKey,
                            onValueChange = {
                                geminiApiKey = it
                                isKeySaved = false
                            },
                            placeholder = { Text("Paste your Gemini API key...", color = subtleColor.copy(alpha = 0.5f), fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = animatedPrimary,
                                unfocusedBorderColor = if (isLight) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                moodEngine.saveApiKey(geminiApiKey)
                                isKeySaved = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = animatedPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(if (isKeySaved) "Saved ✓" else "Save Key", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // 5. LIBRARY SOURCE
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "LIBRARY SOURCE",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = subtleColor,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardBg)
                            .clickable {
                                onDismiss()
                                onManageFolders()
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(animatedPrimary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = animatedPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Music Folders",
                                    fontFamily = Manrope,
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = if (selectedFoldersCount > 0) "$selectedFoldersCount folders active" else "Scanning all storage",
                                    fontFamily = Manrope,
                                    color = subtleColor,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = subtleColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardBg)
                            .clickable {
                                if (!isScanning) {
                                    isScanning = true
                                    onScanLibrary()
                                    coroutineScope.launch {
                                        delay(1200)
                                        isScanning = false
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(animatedPrimary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = null,
                                    tint = animatedPrimary,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .rotate(if (isScanning) scanRotation else 0f)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Scan Library",
                                    fontFamily = Manrope,
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = if (isScanning) "Searching for new files..." else "Sync downloaded tracks without restarting",
                                    fontFamily = Manrope,
                                    color = subtleColor,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(animatedPrimary.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isScanning) "Syncing" else "Sync",
                                fontFamily = Manrope,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = animatedPrimary
                            )
                        }
                    }
                }

                // 6. AUDIO PROCESSING & GAIN
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AUDIO PROCESSING & GAIN",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = subtleColor,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardBg)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(animatedPrimary.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Equalizer,
                                        contentDescription = null,
                                        tint = animatedPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Volume Normalization",
                                        fontFamily = Manrope,
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        text = "Consistent -14 LUFS, -1 dBTP ceiling",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Manrope),
                                        color = subtleColor
                                    )
                                }
                            }

                            Switch(
                                checked = volumeNormalizationEnabled,
                                onCheckedChange = onToggleVolumeNormalization,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = animatedPrimary,
                                    uncheckedThumbColor = if (isLight) Color.Gray else Color.White.copy(alpha = 0.6f),
                                    uncheckedTrackColor = if (isLight) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.06f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(animatedPrimary.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = animatedPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Crossfade",
                                        fontFamily = Manrope,
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        text = "Seamless track transitions",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Manrope),
                                        color = subtleColor
                                    )
                                }
                            }

                            Switch(
                                checked = crossfadeEnabled,
                                onCheckedChange = onToggleCrossfade,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = animatedPrimary,
                                    uncheckedThumbColor = if (isLight) Color.Gray else Color.White.copy(alpha = 0.6f),
                                    uncheckedTrackColor = if (isLight) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)
                                )
                            )
                        }

                        AnimatedVisibility(
                            visible = crossfadeEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                HorizontalDivider(color = if (isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.06f))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Auto-Ending Duration",
                                        fontFamily = Manrope,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor,
                                        fontSize = 13.sp
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(animatedPrimary.copy(alpha = 0.20f))
                                            .padding(horizontal = 10.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${crossfadeDuration}s",
                                            fontFamily = Manrope,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = animatedPrimary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Slider(
                                    value = crossfadeDuration.toFloat(),
                                    onValueChange = { onCrossfadeDurationChange(it.toInt()) },
                                    valueRange = 1f..15f,
                                    steps = 13,
                                    colors = SliderDefaults.colors(
                                        thumbColor = animatedPrimary,
                                        activeTrackColor = animatedPrimary,
                                        inactiveTrackColor = if (isLight) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.10f)
                                    )
                                )
                            }
                        }
                    }
                }

                // 7. DISCOVER FEED HERO REFRESH
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "DISCOVER FEED",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFamily = Manrope),
                        fontWeight = FontWeight.Bold,
                        color = subtleColor,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardBg)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(animatedPrimary.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = animatedPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Hero Refresh",
                                        fontFamily = Manrope,
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        text = "Shuffle carousel every",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Manrope),
                                        color = subtleColor
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(animatedPrimary.copy(alpha = 0.18f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${heroRefreshHours}h",
                                    fontFamily = Manrope,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = animatedPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = heroRefreshHours.toFloat(),
                            onValueChange = { onHeroRefreshHoursChange(it.toInt()) },
                            valueRange = 1f..6f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = animatedPrimary,
                                activeTrackColor = animatedPrimary,
                                inactiveTrackColor = if (isLight) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.10f)
                            )
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    AboutSupportSection(
    currentVersion = currentVersion,
    updateInfo = updateInfo,
    isCheckingForUpdate = isCheckingForUpdate,
    supportUrl = supportUrl,
    textColor = textColor,
    subtleColor = subtleColor,
    cardBg = cardBg,
    primaryColor = animatedPrimary,
    onCheckForUpdates = onCheckForUpdates
)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showCustomHexDialog) {
        Dialog(onDismissRequest = { showCustomHexDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isLight) Color.White else Color(0xFF181B26))
                    .padding(20.dp)
            ) {
                Column {
                    Text("Custom Hex Color", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = hexInputText,
                        onValueChange = { hexInputText = it },
                        placeholder = { Text("#38BDF8", color = subtleColor.copy(alpha = 0.4f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = animatedPrimary,
                            unfocusedBorderColor = if (isLight) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCustomHexDialog = false }) {
                            Text("Cancel", color = subtleColor, fontFamily = Manrope)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val clean = hexInputText.trim().removePrefix("#")
                                    val colorLong = clean.toLong(16) or 0xFF000000
                                    onCustomAccentColorChange(Color(colorLong))
                                    showCustomHexDialog = false
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = animatedPrimary, contentColor = Color.White)
                        ) {
                            Text("Apply", fontFamily = Manrope, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
