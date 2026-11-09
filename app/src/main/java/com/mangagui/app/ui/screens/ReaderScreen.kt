package com.mangagui.app.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
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

@OptIn(ExperimentalFoundationApi::class)
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

    var showControls by remember { mutableStateOf(true) }
    var isRtlDirection by remember { mutableStateOf(false) } // Default LTR for natural mobile swipe; toggleable to RTL
    var isDoublePageMode by remember { mutableStateOf(false) } // Optional 2-page view, toggleable anytime

    val slotCount = remember(totalPages, isDoublePageMode) {
        if (totalPages == 0) 0
        else if (isDoublePageMode) (totalPages + 1) / 2
        else totalPages
    }

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
                    text = "No pages available for this chapter.",
                    color = Color.White,
                    fontSize = 14.sp
                )
                OutlinedButton(onClick = onBack) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    // Pager supporting both 1-page and optional 2-page swipeable views
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { slotCount }
    )

    val currentSlot = pagerState.currentPage.coerceIn(0, (slotCount - 1).coerceAtLeast(0))
    val currentFirstPage = if (isDoublePageMode) (currentSlot * 2 + 1).coerceAtMost(totalPages) else (currentSlot + 1)
    val currentSecondPage = if (isDoublePageMode) (currentSlot * 2 + 2).takeIf { it <= totalPages } else null

    val pageIndicatorLabel = if (isDoublePageMode && currentSecondPage != null) {
        "Pagine $currentFirstPage-$currentSecondPage di $totalPages"
    } else {
        "Pagina $currentFirstPage di $totalPages"
    }

    val toggleDoublePageMode: () -> Unit = {
        val currentPageIndexZeroBased = if (isDoublePageMode) {
            (pagerState.currentPage * 2).coerceIn(0, totalPages - 1)
        } else {
            pagerState.currentPage.coerceIn(0, totalPages - 1)
        }
        val nextDoubleMode = !isDoublePageMode
        isDoublePageMode = nextDoubleMode
        coroutineScope.launch {
            val targetSlot = if (nextDoubleMode) {
                currentPageIndexZeroBased / 2
            } else {
                currentPageIndexZeroBased
            }
            pagerState.scrollToPage(targetSlot)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Swipeable Horizontal Pager (1-Page or 2-Page mode)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2,
            reverseLayout = isRtlDirection
        ) { slotIndex ->
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            // Reset zoom when swiping to another page
            LaunchedEffect(pagerState.currentPage, isDoublePageMode) {
                if (pagerState.currentPage != slotIndex && scale != 1f) {
                    scale = 1f
                    offset = Offset.Zero
                }
            }

            val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
                val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                scale = newScale
                if (newScale > 1.01f) {
                    offset += offsetChange
                } else {
                    offset = Offset.Zero
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isRtlDirection, slotCount) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.01f) {
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
                                        // Left edge tap
                                        coroutineScope.launch {
                                            if (isRtlDirection) {
                                                if (pagerState.currentPage < slotCount - 1) {
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
                                        // Right edge tap
                                        coroutineScope.launch {
                                            if (isRtlDirection) {
                                                if (pagerState.currentPage > 0) {
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            } else {
                                                if (pagerState.currentPage < slotCount - 1) {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        // Center tap: toggle controls overlay
                                        showControls = !showControls
                                    }
                                }
                            }
                        )
                    }
                    // Allow 1-finger horizontal swipe on HorizontalPager when not zoomed in!
                    .transformable(
                        state = transformableState,
                        canPan = { scale > 1.01f }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isDoublePageMode) {
                        val pageUrl = pages.getOrNull(slotIndex).orEmpty()
                        MangaPageImage(
                            pageUrl = pageUrl,
                            pageNumber = slotIndex + 1,
                            referer = chapterPages.referer,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val firstIdx = slotIndex * 2
                        val secondIdx = slotIndex * 2 + 1
                        val firstUrl = pages.getOrNull(firstIdx).orEmpty()
                        val secondUrl = pages.getOrNull(secondIdx)

                        if (secondUrl != null) {
                            // Two-Page Side-by-Side View (respecting LTR or RTL reading order)
                            val leftIdx = if (isRtlDirection) secondIdx else firstIdx
                            val rightIdx = if (isRtlDirection) firstIdx else secondIdx
                            val leftUrl = if (isRtlDirection) secondUrl else firstUrl
                            val rightUrl = if (isRtlDirection) firstUrl else secondUrl

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                MangaPageImage(
                                    pageUrl = leftUrl,
                                    pageNumber = leftIdx + 1,
                                    referer = chapterPages.referer,
                                    contentAlignment = Alignment.CenterEnd,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(end = 1.dp)
                                )
                                MangaPageImage(
                                    pageUrl = rightUrl,
                                    pageNumber = rightIdx + 1,
                                    referer = chapterPages.referer,
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(start = 1.dp)
                                )
                            }
                        } else {
                            // Odd last page in 2-page mode: display centered
                            MangaPageImage(
                                pageUrl = firstUrl,
                                pageNumber = firstIdx + 1,
                                referer = chapterPages.referer,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
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
                color = Color.Black.copy(alpha = 0.88f),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
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
                                contentDescription = "Chiudi Reader",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Optional 1-Page / 2-Page View Toggle Button
                        FilledTonalButton(
                            onClick = toggleDoublePageMode,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isDoublePageMode) CrunchyrollOrange else CrunchyrollOrange.copy(alpha = 0.2f),
                                contentColor = if (isDoublePageMode) Color.Black else CrunchyrollOrange
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isDoublePageMode) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.CropPortrait,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDoublePageMode) "2 Pagine" else "1 Pagina",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Reading Direction Toggle Button (LTR vs RTL)
                        FilledTonalButton(
                            onClick = { isRtlDirection = !isRtlDirection },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isRtlDirection) "RTL ◀" else "LTR ▶",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls Overlay with Page Slider, Quick Page Turn & Chapter Jump
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.88f),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Page Indicator & Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isDoublePageMode && currentSecondPage != null) "$currentFirstPage-$currentSecondPage" else "$currentFirstPage",
                            color = CrunchyrollOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 34.dp)
                        )

                        Slider(
                            value = currentSlot.toFloat(),
                            onValueChange = { newVal ->
                                coroutineScope.launch {
                                    pagerState.scrollToPage(newVal.toInt().coerceIn(0, (slotCount - 1).coerceAtLeast(0)))
                                }
                            },
                            valueRange = 0f..(slotCount - 1).coerceAtLeast(0).toFloat(),
                            steps = if (slotCount > 2) slotCount - 2 else 0,
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 28.dp)
                        )
                    }

                    // Quick Page & Chapter Navigation Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (onPrevChapter != null) {
                                TextButton(
                                    onClick = onPrevChapter,
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Cap. Prec.", fontSize = 12.sp)
                                }
                            }

                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (pagerState.currentPage > 0) {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                },
                                enabled = currentSlot > 0
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                                    contentDescription = "Pagina Precedente",
                                    tint = if (currentSlot > 0) CrunchyrollOrange else Color.DarkGray
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CrunchyrollOrange.copy(alpha = 0.2f),
                            onClick = toggleDoublePageMode
                        ) {
                            Text(
                                text = pageIndicatorLabel,
                                color = CrunchyrollOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (pagerState.currentPage < slotCount - 1) {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                },
                                enabled = currentSlot < slotCount - 1
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                    contentDescription = "Pagina Successiva",
                                    tint = if (currentSlot < slotCount - 1) CrunchyrollOrange else Color.DarkGray
                                )
                            }

                            if (onNextChapter != null) {
                                TextButton(
                                    onClick = onNextChapter,
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Cap. Succ.", fontSize = 12.sp)
                                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaPageImage(
    pageUrl: String,
    pageNumber: Int,
    referer: String?,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center
) {
    val context = LocalContext.current
    val request = remember(pageUrl, referer) {
        ImageRequest.Builder(context)
            .data(pageUrl)
            .crossfade(true)
            .apply {
                if (!referer.isNullOrBlank()) {
                    setHeader("Referer", referer)
                }
            }
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = "Pagina $pageNumber",
        contentScale = ContentScale.Fit,
        alignment = contentAlignment,
        modifier = modifier,
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
                    text = "Impossibile caricare pagina $pageNumber",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    )
}
