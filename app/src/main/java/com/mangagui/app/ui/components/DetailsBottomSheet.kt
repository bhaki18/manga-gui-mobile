package com.mangagui.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mangagui.app.model.MediaItem
import com.mangagui.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsBottomSheet(
    item: MediaItem,
    selectedNumber: Int,
    maxNumber: Int,
    onNumberChange: (Int) -> Unit,
    onUnmarkWatched: (Int) -> Unit,
    onPlayOrRead: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    isDownloading: Boolean,
    downloadProgressText: String,
    onDismiss: () -> Unit
) {
    val isAnime = item.type.equals("anime", ignoreCase = true)
    val isWatched = item.watchedNumbers.contains(selectedNumber)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Poster, Title, Meta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.image)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(105.dp)
                        .aspectRatio(0.70f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CrunchyrollOrange.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = item.displaySourceName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrunchyrollOrange,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        val audio = item.audioBadge
                        if (audio.isNotBlank()) {
                            val isDub = audio.contains("DUB", ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isDub) DubGreen.copy(alpha = 0.25f) else SubBlue.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = audio,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDub) DubGreen else SubBlue,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (!item.genres.isNullOrBlank()) {
                        Text(
                            text = item.genres,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!item.subDubText.isBlank()) {
                        Text(
                            text = item.subDubText,
                            fontSize = 12.sp,
                            color = CrunchyrollOrange,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Number Stepper Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = if (isAnime) "Seleziona Episodio" else "Seleziona Capitolo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decrement Button
                        FilledTonalIconButton(
                            onClick = {
                                if (selectedNumber > 1) {
                                    onNumberChange(selectedNumber - 1)
                                }
                            },
                            enabled = selectedNumber > 1,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Precedente")
                        }

                        // Current Number Display
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isAnime) "Episodio $selectedNumber" else "Capitolo $selectedNumber",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrunchyrollOrange
                            )
                            Text(
                                text = "di $maxNumber disponibili",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Increment Button
                        FilledTonalIconButton(
                            onClick = {
                                if (selectedNumber < maxNumber) {
                                    onNumberChange(selectedNumber + 1)
                                }
                            },
                            enabled = selectedNumber < maxNumber,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Successivo")
                        }
                    }

                    if (isWatched) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUnmarkWatched(selectedNumber) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = DubGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAnime) "Visto (Tocca per rimuovere)" else "Letto (Tocca per rimuovere)",
                                fontSize = 13.sp,
                                color = DubGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Action Button (Play / Read)
            Button(
                onClick = onPlayOrRead,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrunchyrollOrange,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = if (isAnime) Icons.Default.PlayArrow else Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAnime) "GUARDA EPISODIO $selectedNumber" else "LEGGI CAPITOLO $selectedNumber",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Download Status / Action Button
            if (isDownloading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = CrunchyrollOrange
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (downloadProgressText.isNotBlank()) downloadProgressText else "Scaricamento in corso...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (item.isDownloaded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPlayOrRead,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DubGreen
                        )
                    ) {
                        Icon(Icons.Default.DownloadDone, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scaricato Offline", fontWeight = FontWeight.SemiBold)
                    }

                    FilledTonalIconButton(
                        onClick = onDeleteDownload,
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Elimina download")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAnime) "Scarica Episodio $selectedNumber" else "Scarica Capitolo $selectedNumber",
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
