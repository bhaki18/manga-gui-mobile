package com.mangagui.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mangagui.app.model.Filters
import com.mangagui.app.model.MediaItem
import com.mangagui.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsBottomSheet(
    item: MediaItem,
    selectedNumber: Int,
    maxNumber: Int,
    initialAudioFilter: String = "all",
    onNumberChange: (Int) -> Unit,
    onUnmarkWatched: (Int) -> Unit,
    onPlayOrRead: (String) -> Unit,
    onDownload: (String) -> Unit,
    onDeleteDownload: () -> Unit,
    isDownloading: Boolean,
    downloadProgressText: String,
    onDismiss: () -> Unit
) {
    val isAnime = item.type.equals("anime", ignoreCase = true)
    val isWatched = item.watchedNumbers.contains(selectedNumber)

    // Calculate initial audio selection based on item or active filter
    val filterNorm = Filters.normalizeAudio(initialAudioFilter)
    val defaultAudio = when {
        filterNorm in listOf("dub", "ita") -> "dub"
        filterNorm in listOf("sub", "subita") -> "sub"
        item.audio?.lowercase() in listOf("dub", "ita") -> "dub"
        else -> "sub"
    }

    var selectedAudio by remember(item.id, initialAudioFilter) { mutableStateOf(defaultAudio) }

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

                    if (item.subDubText.isNotBlank()) {
                        Text(
                            text = item.subDubText,
                            fontSize = 12.sp,
                            color = CrunchyrollOrange,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Audio Selector (SUB / DUB) for Anime (only when online)
            if (isAnime && !item.isDownloaded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Traccia Audio:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // SUB Button
                            val isSub = selectedAudio == "sub"
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSub) SubBlue else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedAudio = "sub" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSub) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = "SUB (Sottotitolato)",
                                        fontSize = 13.sp,
                                        fontWeight = if (isSub) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSub) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // DUB Button
                            val isDub = selectedAudio == "dub"
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDub) DubGreen else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedAudio = "dub" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isDub) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = "DUB (Doppiato)",
                                        fontSize = 13.sp,
                                        fontWeight = if (isDub) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isDub) Color.Black else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Number Stepper & Direct Typing Selector
            val downloadedList = remember(item.id, item.watchedNumbers) {
                if (item.isDownloaded) item.watchedNumbers.sorted() else emptyList()
            }
            val currentIndex = if (item.isDownloaded && downloadedList.isNotEmpty()) downloadedList.indexOf(selectedNumber) else -1
            val canGoPrev = if (item.isDownloaded && downloadedList.isNotEmpty()) currentIndex > 0 else selectedNumber > 1
            val canGoNext = if (item.isDownloaded && downloadedList.isNotEmpty()) currentIndex in 0 until (downloadedList.size - 1) else selectedNumber < maxNumber

            var numberInputText by remember(selectedNumber) { mutableStateOf(selectedNumber.toString()) }
            val focusManager = LocalFocusManager.current

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAnime) "Seleziona Episodio" else "Seleziona Capitolo",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = if (item.isDownloaded && downloadedList.isNotEmpty()) {
                                "${downloadedList.size} disponibili offline"
                            } else {
                                "Totale: $maxNumber"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = CrunchyrollOrange
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                focusManager.clearFocus()
                                if (item.isDownloaded && downloadedList.isNotEmpty()) {
                                    if (currentIndex > 0) {
                                        onNumberChange(downloadedList[currentIndex - 1])
                                    }
                                } else if (selectedNumber > 1) {
                                    onNumberChange(selectedNumber - 1)
                                }
                            },
                            enabled = canGoPrev,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Precedente")
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            val maxAvailable = if (item.isDownloaded && downloadedList.isNotEmpty()) {
                                downloadedList.maxOrNull() ?: 1
                            } else {
                                maxOf(1, maxNumber)
                            }

                            OutlinedTextField(
                                value = numberInputText,
                                onValueChange = { newText ->
                                    if (newText.isEmpty()) {
                                        numberInputText = ""
                                    } else if (newText.length <= 5 && newText.all { it.isDigit() }) {
                                        val num = newText.toIntOrNull()
                                        if (num != null) {
                                            val clamped = num.coerceIn(1, maxAvailable)
                                            val finalTarget = if (item.isDownloaded && downloadedList.isNotEmpty()) {
                                                downloadedList.minByOrNull { kotlin.math.abs(it - clamped) } ?: clamped
                                            } else {
                                                clamped
                                            }
                                            if (num != finalTarget) {
                                                numberInputText = finalTarget.toString()
                                            } else {
                                                numberInputText = newText
                                            }
                                            onNumberChange(finalTarget)
                                        }
                                    }
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    textAlign = TextAlign.Center,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CrunchyrollOrange
                                ),
                                placeholder = {
                                    Text(
                                        text = selectedNumber.toString(),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CrunchyrollOrange.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        val num = numberInputText.toIntOrNull()
                                        val target = num?.coerceIn(1, maxAvailable) ?: selectedNumber
                                        val finalTarget = if (item.isDownloaded && downloadedList.isNotEmpty()) {
                                            downloadedList.minByOrNull { kotlin.math.abs(it - target) } ?: target
                                        } else {
                                            target
                                        }
                                        numberInputText = finalTarget.toString()
                                        onNumberChange(finalTarget)
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CrunchyrollOrange,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused) {
                                            val num = numberInputText.toIntOrNull()
                                            val target = num?.coerceIn(1, maxAvailable) ?: selectedNumber
                                            val finalTarget = if (item.isDownloaded && downloadedList.isNotEmpty()) {
                                                downloadedList.minByOrNull { kotlin.math.abs(it - target) } ?: target
                                            } else {
                                                target
                                            }
                                            numberInputText = finalTarget.toString()
                                            onNumberChange(finalTarget)
                                        }
                                    }
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (item.isDownloaded && downloadedList.isNotEmpty()) {
                                    "Disponibili offline: ${downloadedList.first()} - ${downloadedList.last()}"
                                } else {
                                    "Range consentito: 1 - $maxAvailable"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                focusManager.clearFocus()
                                if (item.isDownloaded && downloadedList.isNotEmpty()) {
                                    if (currentIndex in 0 until (downloadedList.size - 1)) {
                                        onNumberChange(downloadedList[currentIndex + 1])
                                    }
                                } else if (selectedNumber < maxNumber) {
                                    onNumberChange(selectedNumber + 1)
                                }
                            },
                            enabled = canGoNext,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Successivo")
                        }
                    }

                    // Quick Jump Chips: Primo (1) and Ultimo ($maxNumber)
                    if (!item.isDownloaded && maxNumber > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        focusManager.clearFocus()
                                        onNumberChange(1)
                                    }
                            ) {
                                Text(
                                    text = if (isAnime) "Episodio 1" else "Capitolo 1",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CrunchyrollOrange.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        focusManager.clearFocus()
                                        onNumberChange(maxNumber)
                                    }
                            ) {
                                Text(
                                    text = if (isAnime) "Ultimo (Ep. $maxNumber)" else "Ultimo (Cap. $maxNumber)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CrunchyrollOrange,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
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
            val audioSuffix = if (isAnime) " (${selectedAudio.uppercase()})" else ""
            Button(
                onClick = { onPlayOrRead(selectedAudio) },
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
                    imageVector = if (isAnime) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAnime) "GUARDA EPISODIO $selectedNumber$audioSuffix" else "LEGGI CAPITOLO $selectedNumber",
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
                        onClick = { onPlayOrRead(selectedAudio) },
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
                    onClick = { onDownload(selectedAudio) },
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
                        text = if (isAnime) "Scarica Episodio $selectedNumber$audioSuffix" else "Scarica Capitolo $selectedNumber",
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
