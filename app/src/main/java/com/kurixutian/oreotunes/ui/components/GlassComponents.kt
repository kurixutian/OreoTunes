package com.kurixutian.oreotunes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    size: Dp = 38.dp,
    iconSize: Dp = 18.dp,
    isPrimary: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    tint: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.6f
    
    val containerColor = if (isPrimary) {
        primaryColor
    } else if (isLight) {
        Color.Black.copy(alpha = 0.07f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

    val iconTint = if (tint != null) {
        tint
    } else if (isPrimary) {
        Color.Black
    } else if (isLight) {
        Color(0xFF141724)
    } else {
        Color.White
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}
