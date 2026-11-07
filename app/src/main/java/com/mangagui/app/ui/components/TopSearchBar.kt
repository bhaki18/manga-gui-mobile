package com.mangagui.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangagui.app.ui.theme.CrunchyrollOrange

@Composable
fun TopSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    searchHistory: List<String>,
    onRemoveHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                },
            placeholder = {
                Text(
                    text = "Search anime or manga...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 15.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isFocused) CrunchyrollOrange else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        onQueryChange("")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Cancella",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CrunchyrollOrange,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = CrunchyrollOrange
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (query.isNotBlank()) {
                        onSearch(query)
                        focusManager.clearFocus()
                    }
                }
            )
        )

        // Dropdown for the last 5 searches when the search bar is focused
        AnimatedVisibility(
            visible = isFocused && searchHistory.isNotEmpty()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ricerche Recenti",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Cancella tutto",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = CrunchyrollOrange,
                            modifier = Modifier.clickable {
                                onClearHistory()
                            }
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    searchHistory.take(5).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onQueryChange(item)
                                    onSearch(item)
                                    focusManager.clearFocus()
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = item,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onRemoveHistoryItem(item) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Rimuovi",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

