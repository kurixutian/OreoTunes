package com.kurixutian.oreotunes.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kurixutian.oreotunes.data.update.UpdateInfo

@Composable
fun AboutSupportSection(
    currentVersion: String,
    updateInfo: UpdateInfo?,
    isCheckingForUpdate: Boolean,
    supportUrl: String = "",
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    subtleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    cardBg: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onCheckForUpdates: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "ABOUT & SUPPORT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = subtleColor,
            letterSpacing = androidx.compose.ui.unit.TextUnit(
                1f,
                androidx.compose.ui.unit.TextUnitType.Sp
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "OreoTunes",
                        color = textColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Version $currentVersion",
                        color = subtleColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = subtleColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (updateInfo != null) {
                Text(
                    text = "Version ${updateInfo.versionName} is available",
                    color = primaryColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Update,
                        text = "Update",
                        tint = primaryColor,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(updateInfo.releaseUrl)
                                    )
                                )
                            }
                        }
                    )

                    GlassActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.SystemUpdate,
                        text = "Check again",
                        tint = textColor,
                        onClick = onCheckForUpdates
                    )
                }
            } else {
                GlassActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.SystemUpdate,
                    text = if (isCheckingForUpdate) {
                        "Checking for updates…"
                    } else {
                        "Check for updates"
                    },
                    tint = if (isCheckingForUpdate) {
                        subtleColor
                    } else {
                        textColor
                    },
                    enabled = !isCheckingForUpdate,
                    onClick = onCheckForUpdates
                )
            }

            if (supportUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                GlassActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Coffee,
                    text = "Buy Me a Coffee",
                    tint = textColor,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(supportUrl)
                                )
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun GlassActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val backgroundColor = if (enabled) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color.White.copy(alpha = 0.025f)
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(15.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(
                    alpha = if (enabled) 0.08f else 0.04f
                ),
                shape = RoundedCornerShape(15.dp)
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint
            )

            Spacer(modifier = Modifier.padding(horizontal = 4.dp))

            Text(
                text = text,
                color = tint,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}