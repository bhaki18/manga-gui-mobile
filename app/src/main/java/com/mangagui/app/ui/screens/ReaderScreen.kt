package com.mangagui.app.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mangagui.app.model.ChapterPages
import com.mangagui.app.ui.theme.CrunchyrollOrange
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    title: String,
    chapterPages: ChapterPages,
    onBack: () -> Unit,
    onNextChapter: (() -> Unit)? = null,
    onPrevChapter: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val pages = chapterPages.pages
    val totalPages = pages.size

    var showControls by remember { mutableStateOf(false) }
    var isRtlDirection by remember { mutableStateOf(true) } // Manga default: Right to Left

    // Keep screen on while reading
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        onBack()
    }

    if (totalPages == 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Nessuna pagina disponibile per questo capitolo.",
                    color = Color.White,
                    fontSize = 14.sp
                )
                OutlinedButton(onClick = onBack) {
                    Text("Torna indietro")
                }
            }
        }
        return
    }

    // Pager for single page swipeable view
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { totalPages }
    )

    val currentPage = pagerState.currentPage + 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Single Page View with Horizontal Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2,
            reverseLayout = isRtlDirection
        ) { pageIndex ->
            val pageUrl = pages.getOrNull(pageIndex).orEmpty()

            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
                val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                scale = newScale
                if (newScale > 1f) {
                    offset += offsetChange
                } else {
                    offset = Offset.Zero
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                    offset = Offset.Zero
                                }
                            },
                            onTap = { tapOffset ->
                                val width = size.width
                                val leftBoundary = width * 0.25f
                                val rightBoundary = width * 0.75f

                                when {
                                    tapOffset.x < leftBoundary -> {
                                        // Left tap
                                        coroutineScope.launch {
                                            if (isRtlDirection) {
                                                if (pagerState.currentPage < totalPages - 1) {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            } else {
                                                if (pagerState.currentPage > 0) {
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            }
                                        }
                                    }
                                    tapOffset.x > rightBoundary -> {
                                        // Right tap
                                        coroutineScope.launch {
                                            if (isRtlDirection) {
                                                if (pagerState.currentPage > 0) {
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            } else {
                                                if (pagerState.currentPage < totalPages - 1) {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        // Center tap: toggle controls
                                        showControls = !showControls
                                    }
                                }
                            }
                        )
                    }
                    .transformable(state = transformableState),
                contentAlignment = Alignment.Center
            ) {
                val request = remember(pageUrl) {
                    ImageRequest.Builder(context)
                        .data(pageUrl)
                        .crossfade(true)
                        .apply {
                            if (!chapterPages.referer.isNullOrBlank()) {
                                setHeader("Referer", chapterPages.referer)
                            }
                        }
                        .build()
                }

                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = "Pagina $currentPage",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = CrunchyrollOrange,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    },
                    error = {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Impossibile caricare pagina $currentPage",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                )
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
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

                    // Reading Direction Toggle Button (RTL vs LTR)
                    FilledTonalButton(
                        onClick = { isRtlDirection = !isRtlDirection },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CrunchyrollOrange.copy(alpha = 0.2f),
                            contentColor = CrunchyrollOrange
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isRtlDirection) "Manga (RTL ◀)" else "Web (LTR ▶)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Bottom Controls Overlay with Page Slider & Jump Buttons
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Page Indicator & Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "$currentPage",
                            color = CrunchyrollOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(32.dp)
                        )

                        Slider(
                            value = (currentPage - 1).toFloat(),
                            onValueChange = { newVal ->
                                coroutineScope.launch {
                                    pagerState.scrollToPage(newVal.toInt())
                                }
                            },
                            valueRange = 0f..(totalPages - 1).coerceAtLeast(0).toFloat(),
                            steps = if (totalPages > 2) totalPages - 2 else 0,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = CrunchyrollOrange,
                                activeTrackColor = CrunchyrollOrange,
                                inactiveTrackColor = Color.DarkGray
                            )
                        )

                        Text(
                            text = "$totalPages",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(32.dp)
                        )
                    }

                    // Chapter Jump Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onPrevChapter != null) {
                            TextButton(
                                onClick = onPrevChapter,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cap. Prec.")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(40.dp))
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CrunchyrollOrange.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Pagina $currentPage di $totalPages",
                                color = CrunchyrollOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        if (onNextChapter != null) {
                            TextButton(
                                onClick = onNextChapter,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Text("Cap. Succ.")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(40.dp))
                        }
                    }
                }
            }
        }
    }
}
