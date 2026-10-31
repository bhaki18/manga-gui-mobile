package com.mangagui.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mangagui.app.model.StreamData
import com.mangagui.app.ui.theme.CrunchyrollOrange
import com.mangagui.app.ui.theme.DubGreen
import com.mangagui.app.ui.theme.SubBlue

enum class VideoResizeMode(val title: String, val mode: Int) {
    FIT("Adatta (Originale)", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Riempi Schermo (Zoom)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FILL("Espandi (Stretch)", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    title: String,
    streamData: StreamData,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // 1. Fullscreen Immersive Mode & Keep Screen On (No timeout)
    DisposableEffect(Unit) {
        val window = activity?.window
        val originalOrientation = activity?.requestedOrientation

        // Force landscape
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Prevent screen from turning off due to inactivity
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Hide status bar and navigation bar completely (Immersive Sticky)
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
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var selectedResizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }

    var currentUrl by remember { mutableStateOf(streamData.streamUrl ?: "") }
    var currentAudioTrack by remember {
        mutableStateOf(
            streamData.availableAudios.firstOrNull { streamData.options[it] == streamData.streamUrl }
                ?: if (title.contains("DUB", ignoreCase = true)) "dub" else "sub"
        )
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")

        if (!streamData.referer.isNullOrBlank()) {
            httpDataSourceFactory.setDefaultRequestProperties(mapOf("Referer" to streamData.referer))
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                val mediaItem = MediaItem.fromUri(streamData.streamUrl ?: "")
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isLoading = (playbackState == Player.STATE_BUFFERING)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        isLoading = false
                        errorMessage = "Errore di riproduzione: ${error.localizedMessage ?: "Impossibile caricare il video"}"
                    }
                })
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = true
                    keepScreenOn = true
                    resizeMode = selectedResizeMode.mode
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        showControls = (visibility == android.view.View.VISIBLE)
                    })
                    playerViewRef = this
                }
            },
            update = { view ->
                view.resizeMode = selectedResizeMode.mode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Control Bar Overlay (Back Button + Title + Quick Actions + Settings Cog)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Torna indietro"
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Quick Buttons on the right
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Audio Track Toggle Pill (if multiple audios available)
                    if (streamData.options.containsKey("sub") && streamData.options.containsKey("dub")) {
                        val isDub = currentAudioTrack.equals("dub", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDub) DubGreen.copy(alpha = 0.35f) else SubBlue.copy(alpha = 0.35f),
                            modifier = Modifier.clickable {
                                val nextAudio = if (isDub) "sub" else "dub"
                                val nextUrl = streamData.options[nextAudio]
                                if (!nextUrl.isNullOrBlank()) {
                                    val currentPos = exoPlayer.currentPosition
                                    val isPlaying = exoPlayer.playWhenReady
                                    exoPlayer.setMediaItem(MediaItem.fromUri(nextUrl))
                                    exoPlayer.prepare()
                                    exoPlayer.seekTo(currentPos)
                                    exoPlayer.playWhenReady = isPlaying
                                    currentAudioTrack = nextAudio
                                    currentUrl = nextUrl
                                }
                            }
                        ) {
                            Text(
                                text = if (isDub) "DUB ITA" else "SUB ITA",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDub) DubGreen else Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Skip Opening Button (+85s)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CrunchyrollOrange.copy(alpha = 0.25f),
                        modifier = Modifier.clickable {
                            val currentPos = exoPlayer.currentPosition
                            exoPlayer.seekTo(currentPos + 85_000L)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Salta Sigla (+85s)",
                                tint = CrunchyrollOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+85s Sigla",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrunchyrollOrange
                            )
                        }
                    }

                    // Settings Cog Button
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Impostazioni Riproduzione",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Loading spinner
        if (isLoading && errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center),
                color = CrunchyrollOrange,
                strokeWidth = 4.dp
            )
        }

        // Error Banner
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Errore",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Button(
                        onClick = {
                            errorMessage = null
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrunchyrollOrange,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Riprova")
                    }
                }
            }
        }
    }

    // Modal Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = CrunchyrollOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Impostazioni Riproduzione",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Audio Track Selector (if available)
                    if (streamData.options.containsKey("sub") && streamData.options.containsKey("dub")) {
                        Text(
                            text = "Traccia Audio:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isSub = currentAudioTrack.equals("sub", ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSub) SubBlue else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val targetUrl = streamData.options["sub"]
                                        if (!targetUrl.isNullOrBlank() && targetUrl != currentUrl) {
                                            val currentPos = exoPlayer.currentPosition
                                            val isPlaying = exoPlayer.playWhenReady
                                            exoPlayer.setMediaItem(MediaItem.fromUri(targetUrl))
                                            exoPlayer.prepare()
                                            exoPlayer.seekTo(currentPos)
                                            exoPlayer.playWhenReady = isPlaying
                                            currentAudioTrack = "sub"
                                            currentUrl = targetUrl
                                        }
                                    }
                            ) {
                                Text(
                                    text = "SUB ITA",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSub) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSub) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            val isDub = currentAudioTrack.equals("dub", ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDub) DubGreen else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val targetUrl = streamData.options["dub"]
                                        if (!targetUrl.isNullOrBlank() && targetUrl != currentUrl) {
                                            val currentPos = exoPlayer.currentPosition
                                            val isPlaying = exoPlayer.playWhenReady
                                            exoPlayer.setMediaItem(MediaItem.fromUri(targetUrl))
                                            exoPlayer.prepare()
                                            exoPlayer.seekTo(currentPos)
                                            exoPlayer.playWhenReady = isPlaying
                                            currentAudioTrack = "dub"
                                            currentUrl = targetUrl
                                        }
                                    }
                            ) {
                                Text(
                                    text = "DUB ITA",
                                    fontSize = 12.sp,
                                    fontWeight = if (isDub) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isDub) Color.Black else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    // 1. Playback Speed Selector
                    Text(
                        text = "Velocità di Riproduzione:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        speeds.forEach { speed ->
                            val isSelected = playbackSpeed == speed
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) CrunchyrollOrange else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        playbackSpeed = speed
                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                    }
                            ) {
                                Text(
                                    text = "${speed}x",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    // 2. Video Resize Mode (Fit / Zoom / Stretch)
                    Text(
                        text = "Formato Schermo:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VideoResizeMode.entries.forEach { mode ->
                            val isSelected = selectedResizeMode == mode
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) CrunchyrollOrange else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedResizeMode = mode
                                        playerViewRef?.resizeMode = mode.mode
                                    }
                            ) {
                                Text(
                                    text = when (mode) {
                                        VideoResizeMode.FIT -> "Originale"
                                        VideoResizeMode.ZOOM -> "Riempi"
                                        VideoResizeMode.FILL -> "Espandi"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    // 3. Skip Buttons
                    Text(
                        text = "Salto Rapido:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val pos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(pos)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("-10 sec", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val pos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(pos)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+10 sec", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                val pos = (exoPlayer.currentPosition + 85_000L).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(pos)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrunchyrollOrange,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("+85s Sigla", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Chiudi", color = CrunchyrollOrange, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
