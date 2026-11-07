package com.mangagui.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangagui.app.ui.theme.CrunchyrollOrange

@Composable
fun UpdateDialog(
    currentVersion: String,
    onlineVersion: String,
    changelog: String?,
    isDownloading: Boolean,
    downloadProgress: Float,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = "Update available",
                tint = CrunchyrollOrange,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "New Update Available!",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "A new version of Manga GUI is available:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Current version: v$currentVersion",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "New: v$onlineVersion",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrunchyrollOrange
                    )
                }

                if (!changelog.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "What's new:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = changelog,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = CrunchyrollOrange
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrunchyrollOrange,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Update Now", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

