package com.kurixutian.oreotunes.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kurixutian.oreotunes.data.update.UpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    currentVersion: String,
    onRemindLater: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onRemindLater,
        icon = {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = "Update available"
            )
        },
        title = {
            Text("Update available")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "OreoTunes ${updateInfo.versionName} is available."
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "You currently have version $currentVersion."
                )

                if (updateInfo.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "What's new",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = updateInfo.releaseNotes
                    )
                }
            }
        },
        confirmButton = {
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
        },
        dismissButton = {
            OutlinedButton(
                onClick = onRemindLater
            ) {
                Text("Remind me later")
            }
        }
    )
}
