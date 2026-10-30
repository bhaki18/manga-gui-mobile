package com.mangagui.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mangagui.app.model.ChapterPages
import com.mangagui.app.ui.theme.CrunchyrollOrange

@Composable
fun ReaderScreen(
    title: String,
    chapterPages: ChapterPages,
    onBack: () -> Unit,
    onNextChapter: (() -> Unit)? = null,
    onPrevChapter: (() -> Unit)? = null
) {
    var showControls by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val currentVisibleIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                showControls = !showControls
            }
    ) {
        // Continuous Vertical Pages
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(chapterPages.pages) { index, pageUrl ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val request = ImageRequest.Builder(context)
                        .data(pageUrl)
                        .crossfade(true)
                        .apply {
                            if (!chapterPages.referer.isNullOrBlank()) {
                                setHeader("Referer", chapterPages.referer)
                            }
                        }
                        .build()

                    AsyncImage(
                        model = request,
                        contentDescription = "Pagina ${index + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            }
        }

        // Top Navigation Bar Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.85f),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Chiudi Lettore",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // Bottom Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.85f),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Chapter
                    if (onPrevChapter != null) {
                        TextButton(
                            onClick = onPrevChapter,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.NavigateBefore, contentDescription = null)
                            Text("Cap. Prec.")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(40.dp))
                    }

                    // Page Indicator
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CrunchyrollOrange.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "$currentVisibleIndex / ${chapterPages.pages.size}",
                            color = CrunchyrollOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    // Next Chapter
                    if (onNextChapter != null) {
                        TextButton(
                            onClick = onNextChapter,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Text("Cap. Succ.")
                            Icon(Icons.Default.NavigateNext, contentDescription = null)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(40.dp))
                    }
                }
            }
        }
    }
}

