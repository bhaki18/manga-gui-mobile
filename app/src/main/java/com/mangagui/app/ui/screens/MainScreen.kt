package com.mangagui.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangagui.app.model.ChapterPages
import com.mangagui.app.model.StreamData
import com.mangagui.app.ui.components.*
import com.mangagui.app.ui.theme.CrunchyrollOrange
import com.mangagui.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToPlayer: (String, StreamData) -> Unit,
    onNavigateToReader: (String, ChapterPages) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val activeTab by viewModel.activeTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val selectedAudioFilter by viewModel.selectedAudioFilter.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val items by viewModel.items.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()
    val selectedNumber by viewModel.selectedNumber.collectAsState()
    val maxNumber by viewModel.maxNumber.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgressText by viewModel.downloadProgressText.collectAsState()

    val updateInfo by viewModel.updateInfo.collectAsState()
    val isUpdateDialogVisible by viewModel.isUpdateDialogVisible.collectAsState()
    val isUpdatingApk by viewModel.isUpdatingApk.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()

    var isResolvingMedia by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // App Logo / Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = CrunchyrollOrange,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "M",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MANGA GUI",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = 1.sp
                        )
                    }

                    // Refresh Button
                    IconButton(
                        onClick = {
                            if (activeTab == "Downloaded") viewModel.loadDownloaded()
                            else if (activeTab == "History") viewModel.loadHistory()
                            else viewModel.loadCatalog()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Aggiorna",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Search Bar
                TopSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onSearch = { viewModel.search() },
                    searchHistory = searchHistory,
                    onRemoveHistoryItem = { viewModel.removeSearchHistoryItem(it) },
                    onClearHistory = { viewModel.clearSearchHistory() }
                )

                // Filters section for Anime and Manga
                if (activeTab == "Anime" || activeTab == "Manga") {
                    Spacer(modifier = Modifier.height(10.dp))
                    FilterSection(
                        selectedGenre = selectedGenre,
                        onGenreSelected = { viewModel.onGenreChanged(it) },
                        selectedAudioFilter = selectedAudioFilter,
                        onAudioSelected = { viewModel.selectQuickAudio(it) },
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = { viewModel.onLanguageChanged(it) },
                        isAnimeTab = activeTab == "Anime",
                        onResetFilters = { viewModel.resetFilters() }
                    )
                }

                // Status text bar
                if (statusText.isNotBlank()) {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }
        },
        bottomBar = {
            BottomNavBar(
                currentTab = activeTab,
                onTabSelected = { viewModel.switchTab(it) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && items.isEmpty()) {
                // Centered loading spinner
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = CrunchyrollOrange,
                        strokeWidth = 3.5.dp
                    )
                }
            } else if (items.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (activeTab == "Downloaded") Icons.Default.FolderOpen
                        else if (activeTab == "History") Icons.Default.History
                        else Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (activeTab) {
                            "Downloaded" -> "Nessun contenuto scaricato offline."
                            "History" -> "Nessun elemento nella cronologia di visione."
                            else -> "Nessun risultato trovato con i filtri correnti."
                        },
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            if (activeTab == "Downloaded") viewModel.loadDownloaded()
                            else if (activeTab == "History") viewModel.loadHistory()
                            else viewModel.resetFilters()
                        }
                    ) {
                        Text(if (activeTab == "Downloaded" || activeTab == "History") "Ricarica" else "Azzera Filtri")
                    }
                }
            } else {
                // Media Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.id + it.source + it.count }) { item ->
                        MediaCard(
                            item = item,
                            onClick = {
                                viewModel.selectItem(item)
                            }
                        )
                    }
                }
            }

            // Loading overlay for stream / chapter resolving
            if (isResolvingMedia) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = CrunchyrollOrange,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text("Caricamento in corso...", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Details Modal Bottom Sheet
    selectedItem?.let { item ->
        DetailsBottomSheet(
            item = item,
            selectedNumber = selectedNumber,
            maxNumber = maxNumber,
            onNumberChange = { viewModel.setNumber(it) },
            onUnmarkWatched = { num -> viewModel.unmarkWatched(item, num) },
            onPlayOrRead = {
                val isAnime = item.type.equals("anime", ignoreCase = true)
                isResolvingMedia = true
                coroutineScope.launch {
                    try {
                        if (isAnime) {
                            val stream = viewModel.getStreamForPlayback(item, selectedNumber)
                            isResolvingMedia = false
                            if (stream != null) {
                                onNavigateToPlayer(item.title + " - Ep. $selectedNumber", stream)
                            } else {
                                Toast.makeText(context, "Impossibile riprodurre l'episodio $selectedNumber", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val pages = viewModel.getChapterPagesForReading(item, selectedNumber)
                            isResolvingMedia = false
                            if (pages != null && pages.pages.isNotEmpty()) {
                                onNavigateToReader(item.title + " - Cap. $selectedNumber", pages)
                            } else {
                                Toast.makeText(context, "Impossibile caricare il capitolo $selectedNumber", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        isResolvingMedia = false
                        Toast.makeText(context, "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDownload = { viewModel.downloadCurrentItem() },
            onDeleteDownload = { viewModel.deleteDownloadedItem(item) },
            isDownloading = isDownloading,
            downloadProgressText = downloadProgressText,
            onDismiss = { viewModel.closeDetails() }
        )
    }

    // In-App Updater Dialog
    if (isUpdateDialogVisible && updateInfo != null) {
        val info = updateInfo!!
        UpdateDialog(
            currentVersion = info.installedVersion,
            onlineVersion = info.remoteVersion,
            changelog = info.notes,
            isDownloading = isUpdatingApk,
            downloadProgress = updateProgress / 100f,
            onConfirm = { viewModel.startUpdate() },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }
}

