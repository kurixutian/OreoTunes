package com.kurixutian.oreotunes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurixutian.oreotunes.domain.model.FolderInfo
import com.kurixutian.oreotunes.ui.components.GlassIconButton
import com.kurixutian.oreotunes.ui.theme.Manrope

@Composable
fun FolderPickerScreen(
    detectedFolders: List<FolderInfo>,
    selectedFolders: Set<String>,
    onToggleFolder: (String) -> Unit,
    onClearAll: () -> Unit,
    getChildFolders: (parentPath: String?) -> List<FolderInfo>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPath by remember { mutableStateOf<String?>(null) }
    val breadcrumbScrollState = rememberScrollState()

    BackHandler(enabled = currentPath != null) {
        if (currentPath != null) {
            val parent = currentPath!!.substringBeforeLast('/', "")
            currentPath = if (parent.length > "/storage/emulated/0".length) parent else null
        }
    }

    val currentItems = remember(currentPath, detectedFolders, selectedFolders) {
        getChildFolders(currentPath)
    }

    // Selected folders pinned to the top, then alphabetically sorted
    val sortedItems = remember(currentItems, selectedFolders) {
        currentItems.sortedWith(
            compareByDescending<FolderInfo> { it.path in selectedFolders }
                .thenBy { it.name.lowercase() }
        )
    }

    val breadcrumbs = remember(currentPath) {
        if (currentPath == null) listOf(Pair("Storage", null))
        else {
            val list = mutableListOf(Pair("Storage", null as String?))
            val base = "/storage/emulated/0"
            if (currentPath!!.startsWith(base)) {
                val relative = currentPath!!.removePrefix(base).trim('/')
                if (relative.isNotEmpty()) {
                    var acc = base
                    relative.split('/').forEach { segment ->
                        acc = "$acc/$segment"
                        list.add(Pair(segment, acc))
                    }
                }
            } else {
                list.add(Pair(currentPath!!.substringAfterLast('/'), currentPath))
            }
            list
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = {
                        if (currentPath != null) {
                            val parent = currentPath!!.substringBeforeLast('/', "")
                            currentPath = if (parent.length > "/storage/emulated/0".length) parent else null
                        } else {
                            onBack()
                        }
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Music Folders",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontFamily = Manrope),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (selectedFolders.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text(
                        text = "Scan All",
                        fontFamily = Manrope,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Breadcrumb Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(breadcrumbScrollState)
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            breadcrumbs.forEachIndexed { index, crumb ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp).padding(horizontal = 2.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (index == breadcrumbs.size - 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .clickable { currentPath = crumb.second }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (index == 0) {
                                Icon(
                                    imageVector = Icons.Rounded.Home,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = crumb.first,
                                fontFamily = Manrope,
                                fontSize = 12.sp,
                                fontWeight = if (index == breadcrumbs.size - 1) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "Tap a folder to open it. Check the box to scan all music files inside:",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Manrope),
            color = Color.White.copy(alpha = 0.60f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 140.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (sortedItems.isEmpty()) {
                item {
                    Text(
                        text = "No subfolders found.",
                        fontFamily = Manrope,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                itemsIndexed(sortedItems, key = { index, item -> "${item.path}_$index" }) { _, folder ->
                    val isChecked = folder.path in selectedFolders
                    val isInherited = selectedFolders.any {
                        it != folder.path && folder.path.startsWith("$it/", ignoreCase = true)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                else if (isInherited) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else Color(0xFF161A29).copy(alpha = 0.55f)
                            )
                            .clickable { currentPath = folder.path }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = if (isChecked || isInherited) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.80f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontFamily = Manrope),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isChecked) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• Selected",
                                        fontFamily = Manrope,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${folder.songCount} songs inside",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontFamily = Manrope),
                                color = Color.White.copy(alpha = 0.50f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleFolder(folder.path) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = Color.White.copy(alpha = 0.35f),
                                checkmarkColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}
