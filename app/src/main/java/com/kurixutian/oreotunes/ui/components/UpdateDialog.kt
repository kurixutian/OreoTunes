package com.kurixutian.oreotunes.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun UpdateDialog(
    updateInfo: UpdateInfo,
    currentVersion: String,
    onRemindLater: () -> Unit
) {
    val context = LocalContext.current
    val isLight = MaterialTheme.colorScheme.background.red > 0.6f
    val textColor = if (isLight) Color(0xFF121520) else Color.White
    val subtleColor = textColor.copy(alpha = 0.72f)

    LiquidGlassModal(onDismissRequest = onRemindLater) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .liquidGlassSurface(isLight = isLight)
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = "Update available",
                    tint = textColor
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Update available",
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "OreoTunes ${updateInfo.versionName} is available.",
                    color = textColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "You currently have version $currentVersion.",
                    color = subtleColor
                )

                if (updateInfo.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "What's new",
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = updateInfo.releaseNotes,
                        color = subtleColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onRemindLater) {
                    Text("Later")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(updateInfo.releaseUrl)
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("Update")
                }
            }
        }
    }
}
