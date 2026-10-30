package com.mangagui.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangagui.app.model.Filters
import com.mangagui.app.ui.theme.CrunchyrollOrange

@Composable
fun FilterSection(
    selectedGenre: String,
    onGenreSelected: (String) -> Unit,
    selectedAudioFilter: String,
    onAudioSelected: (String) -> Unit,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    isAnimeTab: Boolean,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAllGenresDialog by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }

    val audioOptions = Filters.getAudioOptionsForLanguage(selectedLanguage)
    val currentAudioNorm = Filters.normalizeAudio(selectedAudioFilter)
    val isSubSelected = currentAudioNorm == "sub"
    val isDubSelected = currentAudioNorm == "dub"

    val hasActiveFilters = Filters.normalizeGenre(selectedGenre) != "all" ||
            currentAudioNorm != "all" ||
            Filters.resolveLanguageCode(selectedLanguage) != "all"

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Genre selector chip
            val isGenreActive = Filters.normalizeGenre(selectedGenre) != "all"
            FilterChip(
                selected = isGenreActive,
                onClick = { showAllGenresDialog = true },
                label = {
                    Text(if (isGenreActive) selectedGenre else "Genere")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Generi",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CrunchyrollOrange,
                    selectedLabelColor = Color.Black
                ),
                shape = RoundedCornerShape(20.dp)
            )

            // Audio Filter Chips for Anime
            if (isAnimeTab) {
                // SUB chip
                FilterChip(
                    selected = isSubSelected,
                    onClick = {
                        onAudioSelected("sub")
                    },
                    label = {
                        Text(audioOptions.subOption)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CrunchyrollOrange,
                        selectedLabelColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp)
                )

                // DUB chip
                FilterChip(
                    selected = isDubSelected,
                    onClick = {
                        onAudioSelected("dub")
                    },
                    label = {
                        Text(audioOptions.dubOption)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CrunchyrollOrange,
                        selectedLabelColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }

            // Language Selector Chip
            Box {
                val isLangActive = Filters.resolveLanguageCode(selectedLanguage) != "all"
                FilterChip(
                    selected = isLangActive,
                    onClick = { showLanguageMenu = true },
                    label = {
                        Text(if (isLangActive) selectedLanguage else "Lingua")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Lingua",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CrunchyrollOrange,
                        selectedLabelColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp)
                )

                DropdownMenu(
                    expanded = showLanguageMenu,
                    onDismissRequest = { showLanguageMenu = false }
                ) {
                    Filters.LANGUAGES.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                onLanguageSelected(lang)
                                showLanguageMenu = false
                            }
                        )
                    }
                }
            }

            // Quick popular genre chips
            Filters.QUICK_GENRES.filter { it != "All genres" }.forEach { genre ->
                val isSelected = selectedGenre.equals(genre, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            onGenreSelected("All genres")
                        } else {
                            onGenreSelected(genre)
                        }
                    },
                    label = { Text(genre) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CrunchyrollOrange,
                        selectedLabelColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }

            // Reset Button
            if (hasActiveFilters) {
                IconButton(
                    onClick = onResetFilters,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Azzera Filtri",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Modal dialog to view all genres
    if (showAllGenresDialog) {
        AlertDialog(
            onDismissRequest = { showAllGenresDialog = false },
            title = {
                Text(
                    text = "Tutti i Generi",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    Filters.GENRES.chunked(2).forEach { rowGenres ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowGenres.forEach { genre ->
                                val isSelected = selectedGenre.equals(genre, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) CrunchyrollOrange else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onGenreSelected(genre)
                                            showAllGenresDialog = false
                                        }
                                ) {
                                    Text(
                                        text = genre,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                        maxLines = 1
                                    )
                                }
                            }
                            if (rowGenres.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllGenresDialog = false }) {
                    Text("Chiudi", color = CrunchyrollOrange)
                }
            }
        )
    }
}
