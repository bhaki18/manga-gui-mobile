package com.mangagui.app.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mangagui.app.model.ChapterPages
import com.mangagui.app.ui.theme.CrunchyrollOrange
import com.mangagui.app.ui.theme.SurfaceBorder
import com.mangagui.app.ui.theme.SurfaceDark
import kotlinx.coroutines.launch

private enum class CrReaderLayout {
    SINGLE_PAGE,
    DOUBLE_PAGE,
    VERTICAL_STRIP
}

private enum class CrReaderBg(val label: String, val color: Color) {
    OLED_BLACK("Nero OLED", Color(0xFF000000)),
    CR_CHARCOAL("Crunchyroll", Color(0xFF141519)),
    DARK_SEPIA("Seppia", Color(0xFF1B1813))
}

private data class PageSpreadSlot(
    val firstPageIndex: Int,
    val secondPageIndex: Int? = null,
    val isEndOfChapterCard: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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

    // Crunchyroll Manga Reader states
    var showControls by remember { mutableStateOf(false) }
    var showThumbnails by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }

    var layoutMode by remember { mutableStateOf(CrReaderLayout.SINGLE_PAGE) }
    var isRtlDirection by remember { mutableStateOf(false) }
    var doublePageCoverOffset by remember { mutableStateOf(false) } // True = Page 1 alone (cover), then 2-3, 4-5...
    var readerBg by remember { mutableStateOf(CrReaderBg.OLED_BLACK) }

    // Keep screen on & full immersive mode
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
        when {
            showSettingsSheet -> showSettingsSheet = false
            showThumbnails -> showThumbnails = false
            else -> onBack()
        }
    }

    if (totalPages == 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141519)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = CrunchyrollOrange,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Nessuna pagina disponibile per questo capitolo.",
                    color = Color.White,
                    fontSize = 14.sp
                )
                OutlinedButton(
                    onClick = onBack,
                    border = BorderStroke(1.dp, CrunchyrollOrange),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrunchyrollOrange)
                ) {
                    Text("Torna indietro")
                }
            }
        }
        return
    }

    // Build spread slots for Paged Mode (1-Page or 2-Page) + final End-of-Chapter card
    val pagerSlots = remember(totalPages, layoutMode, doublePageCoverOffset) {
        val list = mutableListOf<PageSpreadSlot>()
        if (layoutMode == CrReaderLayout.DOUBLE_PAGE) {
            var idx = 0
            if (doublePageCoverOffset && totalPages > 0) {
                list.add(PageSpreadSlot(firstPageIndex = 0, secondPageIndex = null))
                idx = 1
            }
            while (idx < totalPages) {
                val second = if (idx + 1 < totalPages) idx + 1 else null
                list.add(PageSpreadSlot(firstPageIndex = idx, secondPageIndex = second))
                idx += 2
            }
        } else {
            for (i in 0 until totalPages) {
                list.add(PageSpreadSlot(firstPageIndex = i, secondPageIndex = null))
            }
        }
        // Append Crunchyroll End-of-Chapter Card slot at the end
        list.add(
            PageSpreadSlot(
                firstPageIndex = (totalPages - 1).coerceAtLeast(0),
                secondPageIndex = null,
                isEndOfChapterCard = true
            )
        )
        list
    }

    val contentSlotCount = (pagerSlots.size - 1).coerceAtLeast(1)

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pagerSlots.size }
    )

    val verticalListState = rememberLazyListState()
    val thumbnailListState = rememberLazyListState()

    // Current 0-based page index across any active layout mode
    val currentZeroBasedPage: Int by remember {
        derivedStateOf {
            if (layoutMode == CrReaderLayout.VERTICAL_STRIP) {
                verticalListState.firstVisibleItemIndex.coerceIn(0, totalPages - 1)
            } else {
                val slot = pagerSlots.getOrNull(pagerState.currentPage)
                slot?.firstPageIndex?.coerceIn(0, totalPages - 1) ?: 0
            }
        }
    }

    val currentSecondZeroBasedPage: Int? by remember {
        derivedStateOf {
            if (layoutMode == CrReaderLayout.DOUBLE_PAGE) {
                val slot = pagerSlots.getOrNull(pagerState.currentPage)
                if (slot?.isEndOfChapterCard == true) null else slot?.secondPageIndex
            } else {
                null
            }
        }
    }

    // Auto-scroll thumbnail strip to keep active page centered when thumbnails are visible
    LaunchedEffect(currentZeroBasedPage, showThumbnails, showControls) {
        if ((showThumbnails || isScrubbing) && currentZeroBasedPage in 0 until totalPages) {
            val target = (currentZeroBasedPage - 2).coerceAtLeast(0)
            thumbnailListState.animateScrollToItem(target)
        }
    }

    // Helper to switch layout mode while preserving the exact current page
    val switchLayoutMode: (CrReaderLayout) -> Unit = { targetMode ->
        if (layoutMode != targetMode) {
            val anchorPage = currentZeroBasedPage.coerceIn(0, totalPages - 1)
            layoutMode = targetMode
            coroutineScope.launch {
                when (targetMode) {
                    CrReaderLayout.VERTICAL_STRIP -> {
                        verticalListState.scrollToItem(anchorPage)
                    }
                    CrReaderLayout.SINGLE_PAGE -> {
                        pagerState.scrollToPage(anchorPage)
                    }
                    CrReaderLayout.DOUBLE_PAGE -> {
                        val slotIdx = if (doublePageCoverOffset) {
                            if (anchorPage == 0) 0 else ((anchorPage - 1) / 2) + 1
                        } else {
                            anchorPage / 2
                        }
                        pagerState.scrollToPage(slotIdx)
                    }
                }
            }
        }
    }

    // Helper to jump to a specific 0-based page index
    val jumpToPage: (Int) -> Unit = { pageIdx ->
        val safePage = pageIdx.coerceIn(0, totalPages - 1)
        coroutineScope.launch {
            if (layoutMode == CrReaderLayout.VERTICAL_STRIP) {
                verticalListState.animateScrollToItem(safePage)
            } else if (layoutMode == CrReaderLayout.DOUBLE_PAGE) {
                val slotIdx = if (doublePageCoverOffset) {
                    if (safePage == 0) 0 else ((safePage - 1) / 2) + 1
                } else {
                    safePage / 2
                }
                pagerState.scrollToPage(slotIdx.coerceIn(0, contentSlotCount - 1))
            } else {
                pagerState.scrollToPage(safePage)
            }
        }
    }

    val pageBadgeText = remember(currentZeroBasedPage, currentSecondZeroBasedPage, totalPages) {
        val p1 = currentZeroBasedPage + 1
        val p2 = currentSecondZeroBasedPage?.plus(1)
        if (p2 != null) "$p1-$p2 / $totalPages" else "$p1 / $totalPages"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerBg.color)
    ) {
        // =========================================================================
        // 1. READER CANVAS: VERTICAL STRIP OR HORIZONTAL PAGER (1-PAGE / 2-PAGE)
        // =========================================================================
        if (layoutMode == CrReaderLayout.VERTICAL_STRIP) {
            var vScale by remember { mutableFloatStateOf(1f) }
            var vOffsetX by remember { mutableFloatStateOf(0f) }

            val vTransformState = rememberTransformableState { zoomChange, panChange, _ ->
                val newScale = (vScale * zoomChange).coerceIn(1f, 3.5f)
                vScale = newScale
                vOffsetX = if (newScale > 1.01f) {
                    (vOffsetX + panChange.x).coerceIn(-1000f, 1000f)
                } else {
                    0f
                }
            }

            LazyColumn(
                state = verticalListState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (vScale > 1.01f) {
                                    vScale = 1f
                                    vOffsetX = 0f
                                } else {
                                    vScale = 2.2f
                                }
                            },
                            onTap = {
                                showControls = !showControls
                                if (!showControls) showThumbnails = false
                            }
                        )
                    }
                    .transformable(
                        state = vTransformState,
                        canPan = { vScale > 1.01f }
                    )
                    .graphicsLayer(
                        scaleX = vScale,
                        scaleY = vScale,
                        translationX = vOffsetX
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(pages, key = { idx, _ -> "vpage_$idx" }) { index, pageUrl ->
                    CrMangaPageImage(
                        pageUrl = pageUrl,
                        pageNumber = index + 1,
                        referer = chapterPages.referer,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }

                item(key = "end_of_chapter_vertical") {
                    CrEndOfChapterCard(
                        title = title,
                        chapterTitle = chapterPages.chapterTitle.orEmpty(),
                        onNextChapter = onNextChapter,
                        onPrevChapter = onPrevChapter,
                        onBackToChapter = { jumpToPage(totalPages - 1) },
                        onExitReader = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp)
                    )
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 3,
                reverseLayout = isRtlDirection
            ) { slotIndex ->
                val slot = pagerSlots.getOrNull(slotIndex) ?: return@HorizontalPager

                if (slot.isEndOfChapterCard) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                showControls = !showControls
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        CrEndOfChapterCard(
                            title = title,
                            chapterTitle = chapterPages.chapterTitle.orEmpty(),
                            onNextChapter = onNextChapter,
                            onPrevChapter = onPrevChapter,
                            onBackToChapter = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage((pagerSlots.size - 2).coerceAtLeast(0))
                                }
                            },
                            onExitReader = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp)
                        )
                    }
                    return@HorizontalPager
                }

                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                // Reset zoom when leaving page or switching layout
                LaunchedEffect(pagerState.currentPage, layoutMode, doublePageCoverOffset) {
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
                        .pointerInput(isRtlDirection, pagerSlots.size) {
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
                                    val leftZone = width * 0.24f
                                    val rightZone = width * 0.76f

                                    when {
                                        tapOffset.x < leftZone -> {
                                            coroutineScope.launch {
                                                if (isRtlDirection) {
                                                    if (pagerState.currentPage < pagerSlots.size - 1) {
                                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                    }
                                                } else {
                                                    if (pagerState.currentPage > 0) {
                                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                    }
                                                }
                                            }
                                        }
                                        tapOffset.x > rightZone -> {
                                            coroutineScope.launch {
                                                if (isRtlDirection) {
                                                    if (pagerState.currentPage > 0) {
                                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                    }
                                                } else {
                                                    if (pagerState.currentPage < pagerSlots.size - 1) {
                                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                    }
                                                }
                                            }
                                        }
                                        else -> {
                                            showControls = !showControls
                                            if (!showControls) showThumbnails = false
                                        }
                                    }
                                }
                            )
                        }
                        // Unblocks 1-finger swipe for HorizontalPager when not zoomed in
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
                        val firstIdx = slot.firstPageIndex
                        val secondIdx = slot.secondPageIndex
                        val firstUrl = pages.getOrNull(firstIdx).orEmpty()
                        val secondUrl = secondIdx?.let { pages.getOrNull(it) }

                        if (secondUrl != null && secondIdx != null) {
                            // Crunchyroll True Spread: pages meet flush at the center spine!
                            val leftIdx = if (isRtlDirection) secondIdx else firstIdx
                            val rightIdx = if (isRtlDirection) firstIdx else secondIdx
                            val leftUrl = if (isRtlDirection) secondUrl else firstUrl
                            val rightUrl = if (isRtlDirection) firstUrl else secondUrl

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CrMangaPageImage(
                                    pageUrl = leftUrl,
                                    pageNumber = leftIdx + 1,
                                    referer = chapterPages.referer,
                                    contentScale = ContentScale.Fit,
                                    contentAlignment = Alignment.CenterEnd, // Flush against center spine
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                                CrMangaPageImage(
                                    pageUrl = rightUrl,
                                    pageNumber = rightIdx + 1,
                                    referer = chapterPages.referer,
                                    contentScale = ContentScale.Fit,
                                    contentAlignment = Alignment.CenterStart, // Flush against center spine
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        } else {
                            CrMangaPageImage(
                                pageUrl = firstUrl,
                                pageNumber = firstIdx + 1,
                                referer = chapterPages.referer,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 2. MINIMAL FLOATING PAGE PILL (WHEN OVERLAY CONTROLS ARE HIDDEN)
        // =========================================================================
        AnimatedVisibility(
            visible = !showControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 14.dp, bottom = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF141519).copy(alpha = 0.72f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Text(
                    text = pageBadgeText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }

        // =========================================================================
        // 3. CRUNCHYROLL TOP BAR OVERLAY
        // =========================================================================
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(180)),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF141519).copy(alpha = 0.95f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Back button + Crunchyroll 2-line Series/Chapter header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Indietro",
                                    tint = Color.White
                                )
                            }

                            Column(
                                modifier = Modifier.padding(start = 2.dp, end = 8.dp)
                            ) {
                                Text(
                                    text = chapterPages.chapterTitle.orEmpty().ifBlank { "MANGA READER" }.uppercase(),
                                    color = CrunchyrollOrange,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Right: Quick 1P/2P Pill + Thumbnail Drawer Icon + Settings Gear Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 1-Tap Quick Toggle for Single / Double Page View
                            val isDouble = layoutMode == CrReaderLayout.DOUBLE_PAGE
                            Surface(
                                onClick = {
                                    if (layoutMode == CrReaderLayout.DOUBLE_PAGE) {
                                        switchLayoutMode(CrReaderLayout.SINGLE_PAGE)
                                    } else {
                                        switchLayoutMode(CrReaderLayout.DOUBLE_PAGE)
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDouble) CrunchyrollOrange else Color(0xFF23252B),
                                border = BorderStroke(
                                    1.dp,
                                    if (isDouble) CrunchyrollOrange else Color.White.copy(alpha = 0.18f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDouble) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.CropPortrait,
                                        contentDescription = "Cambia vista pagina",
                                        tint = if (isDouble) Color.Black else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = when (layoutMode) {
                                            CrReaderLayout.DOUBLE_PAGE -> "2P"
                                            CrReaderLayout.VERTICAL_STRIP -> "VERT"
                                            CrReaderLayout.SINGLE_PAGE -> "1P"
                                        },
                                        color = if (isDouble) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Thumbnail Strip Toggle Button
                            IconButton(
                                onClick = { showThumbnails = !showThumbnails }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Anteprime pagine",
                                    tint = if (showThumbnails) CrunchyrollOrange else Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            // Crunchyroll Reader Settings Gear Button
                            IconButton(
                                onClick = { showSettingsSheet = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Impostazioni Reader",
                                    tint = if (showSettingsSheet) CrunchyrollOrange else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.08f)
                )
            }
        }

        // =========================================================================
        // 4. CRUNCHYROLL BOTTOM SCRUBBER & VISUAL THUMBNAIL STRIP
        // =========================================================================
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(180)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Visual Page Thumbnail Strip (Crunchyroll Drawer)
                AnimatedVisibility(
                    visible = showThumbnails || isScrubbing,
                    enter = fadeIn(tween(150)) + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF141519).copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        LazyRow(
                            state = thumbnailListState,
                            reverseLayout = isRtlDirection && layoutMode != CrReaderLayout.VERTICAL_STRIP,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(pages, key = { idx, _ -> "thumb_$idx" }) { idx, url ->
                                val isSelected = idx == currentZeroBasedPage || idx == currentSecondZeroBasedPage
                                CrThumbnailItem(
                                    pageUrl = url,
                                    pageNumber = idx + 1,
                                    referer = chapterPages.referer,
                                    isSelected = isSelected,
                                    onClick = { jumpToPage(idx) }
                                )
                            }
                        }
                    }
                }

                // Bottom Crunchyroll Scrubber Bar
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.08f)
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF141519).copy(alpha = 0.96f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Top mini-status row inside bottom bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CrunchyrollOrange.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = when (layoutMode) {
                                            CrReaderLayout.SINGLE_PAGE -> "PAGINA SINGOLA"
                                            CrReaderLayout.DOUBLE_PAGE -> if (doublePageCoverOffset) "2 PAGINE (SPREAD)" else "2 PAGINE"
                                            CrReaderLayout.VERTICAL_STRIP -> "WEBTOON VERTICALE"
                                        },
                                        color = CrunchyrollOrange,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (layoutMode != CrReaderLayout.VERTICAL_STRIP) {
                                    Text(
                                        text = if (isRtlDirection) "• Lettura RTL ◀" else "• Lettura LTR ▶",
                                        color = Color(0xFFA0A0A0),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable {
                                            isRtlDirection = !isRtlDirection
                                        }
                                    )
                                }
                            }

                            Text(
                                text = "PAGINA $pageBadgeText",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Main Scrubber Row: [Prev Chapter] [Slider] [Next Chapter]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { onPrevChapter?.invoke() },
                                enabled = onPrevChapter != null,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Capitolo Precedente",
                                    tint = if (onPrevChapter != null) Color.White else Color.White.copy(alpha = 0.25f)
                                )
                            }

                            val sliderInverted = isRtlDirection && layoutMode != CrReaderLayout.VERTICAL_STRIP

                            Slider(
                                value = currentZeroBasedPage.toFloat(),
                                onValueChange = { newVal ->
                                    isScrubbing = true
                                    jumpToPage(newVal.toInt())
                                },
                                onValueChangeFinished = {
                                    isScrubbing = false
                                },
                                valueRange = 0f..(totalPages - 1).coerceAtLeast(0).toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer(scaleX = if (sliderInverted) -1f else 1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = CrunchyrollOrange,
                                    activeTrackColor = CrunchyrollOrange,
                                    inactiveTrackColor = Color(0xFF333540)
                                )
                            )

                            IconButton(
                                onClick = { onNextChapter?.invoke() },
                                enabled = onNextChapter != null,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Capitolo Successivo",
                                    tint = if (onNextChapter != null) Color.White else Color.White.copy(alpha = 0.25f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 5. CRUNCHYROLL READER SETTINGS MODAL BOTTOM SHEET
        // =========================================================================
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = Color(0xFF141519),
                contentColor = Color.White,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = CrunchyrollOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Impostazioni Reader",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        TextButton(onClick = { showSettingsSheet = false }) {
                            Text(
                                text = "Chiudi",
                                color = CrunchyrollOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 1. Layout Mode Selector (1 Pagina / 2 Pagine / Verticale)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "MODALITÀ DI LETTURA",
                            color = Color(0xFFA0A0A0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CrSettingOptionCard(
                                label = "1 Pagina",
                                subLabel = "Swipe",
                                icon = Icons.Default.CropPortrait,
                                selected = layoutMode == CrReaderLayout.SINGLE_PAGE,
                                onClick = { switchLayoutMode(CrReaderLayout.SINGLE_PAGE) },
                                modifier = Modifier.weight(1f)
                            )
                            CrSettingOptionCard(
                                label = "2 Pagine",
                                subLabel = "Spread",
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                selected = layoutMode == CrReaderLayout.DOUBLE_PAGE,
                                onClick = { switchLayoutMode(CrReaderLayout.DOUBLE_PAGE) },
                                modifier = Modifier.weight(1f)
                            )
                            CrSettingOptionCard(
                                label = "Verticale",
                                subLabel = "Webtoon",
                                icon = Icons.Default.SwapVert,
                                selected = layoutMode == CrReaderLayout.VERTICAL_STRIP,
                                onClick = { switchLayoutMode(CrReaderLayout.VERTICAL_STRIP) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 2. Double-Page Cover Offset Switch (Crunchyroll Spread Alignment)
                    AnimatedVisibility(visible = layoutMode == CrReaderLayout.DOUBLE_PAGE) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E2028),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val anchor = currentZeroBasedPage
                                        doublePageCoverOffset = !doublePageCoverOffset
                                        coroutineScope.launch {
                                            val newSlot = if (doublePageCoverOffset) {
                                                if (anchor == 0) 0 else ((anchor - 1) / 2) + 1
                                            } else {
                                                anchor / 2
                                            }
                                            pagerState.scrollToPage(newSlot)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Prima pagina singola (Copertina)",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Allinea le tavole doppie accoppiando le pagine 2-3, 4-5...",
                                        color = Color(0xFFA0A0A0),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = doublePageCoverOffset,
                                    onCheckedChange = { checked ->
                                        val anchor = currentZeroBasedPage
                                        doublePageCoverOffset = checked
                                        coroutineScope.launch {
                                            val newSlot = if (checked) {
                                                if (anchor == 0) 0 else ((anchor - 1) / 2) + 1
                                            } else {
                                                anchor / 2
                                            }
                                            pagerState.scrollToPage(newSlot)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = CrunchyrollOrange
                                    )
                                )
                            }
                        }
                    }

                    // 3. Reading Direction (LTR vs RTL)
                    if (layoutMode != CrReaderLayout.VERTICAL_STRIP) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "DIREZIONE DISFOGLIAMENTO",
                                color = Color(0xFFA0A0A0),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CrSettingOptionCard(
                                    label = "Sinistra ▶ Destra",
                                    subLabel = "Standard (LTR)",
                                    icon = Icons.AutoMirrored.Filled.NavigateNext,
                                    selected = !isRtlDirection,
                                    onClick = { isRtlDirection = false },
                                    modifier = Modifier.weight(1f)
                                )
                                CrSettingOptionCard(
                                    label = "Destra ◀ Sinistra",
                                    subLabel = "Manga (RTL)",
                                    icon = Icons.AutoMirrored.Filled.NavigateBefore,
                                    selected = isRtlDirection,
                                    onClick = { isRtlDirection = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // 4. Background Theme
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "SFONDO READER",
                            color = Color(0xFFA0A0A0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CrReaderBg.entries.forEach { bgOption ->
                                val isSel = readerBg == bgOption
                                Surface(
                                    onClick = { readerBg = bgOption },
                                    shape = RoundedCornerShape(10.dp),
                                    color = bgOption.color,
                                    border = BorderStroke(
                                        width = if (isSel) 2.dp else 1.dp,
                                        color = if (isSel) CrunchyrollOrange else Color.White.copy(alpha = 0.16f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSel) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(CrunchyrollOrange)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = bgOption.label,
                                            color = if (isSel) CrunchyrollOrange else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
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
private fun CrSettingOptionCard(
    label: String,
    subLabel: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) CrunchyrollOrange.copy(alpha = 0.16f) else Color(0xFF1E2028),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) CrunchyrollOrange else Color.White.copy(alpha = 0.08f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) CrunchyrollOrange else Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = if (selected) CrunchyrollOrange else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subLabel,
                color = Color(0xFFA0A0A0),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CrThumbnailItem(
    pageUrl: String,
    pageNumber: Int,
    referer: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val request = remember(pageUrl, referer) {
        ImageRequest.Builder(context)
            .data(pageUrl)
            .size(180, 260)
            .crossfade(true)
            .apply {
                if (!referer.isNullOrBlank()) {
                    setHeader("Referer", referer)
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .width(56.dp)
            .height(82.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF23252B))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) CrunchyrollOrange else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = "Miniatura $pageNumber",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    if (isSelected) CrunchyrollOrange else Color.Black.copy(alpha = 0.75f)
                )
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$pageNumber",
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun CrEndOfChapterCard(
    title: String,
    chapterTitle: String,
    onNextChapter: (() -> Unit)?,
    onPrevChapter: (() -> Unit)?,
    onBackToChapter: () -> Unit,
    onExitReader: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF18191E),
        border = BorderStroke(1.dp, SurfaceBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(CrunchyrollOrange.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CrunchyrollOrange,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = "CAPITOLO COMPLETATO",
                color = CrunchyrollOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )

            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (chapterTitle.isNotBlank()) {
                Text(
                    text = chapterTitle,
                    color = Color(0xFFA0A0A0),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (onNextChapter != null) {
                Button(
                    onClick = onNextChapter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrunchyrollOrange,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "PROSSIMO CAPITOLO",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = null
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onPrevChapter != null) {
                    OutlinedButton(
                        onClick = onPrevChapter,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cap. Prec.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedButton(
                    onClick = onBackToChapter,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Torna alle pagine", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            TextButton(onClick = onExitReader) {
                Text(
                    text = "Torna al catalogo",
                    color = Color(0xFFA0A0A0),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CrMangaPageImage(
    pageUrl: String,
    pageNumber: Int,
    referer: String?,
    contentScale: ContentScale,
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
        contentScale = contentScale,
        alignment = contentAlignment,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = CrunchyrollOrange,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "$pageNumber",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = "Errore caricamento pagina $pageNumber",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    )
}
