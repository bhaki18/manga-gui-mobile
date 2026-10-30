package com.mangagui.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mangagui.app.model.ChapterPages
import com.mangagui.app.model.StreamData
import com.mangagui.app.ui.screens.MainScreen
import com.mangagui.app.ui.screens.PlayerScreen
import com.mangagui.app.ui.screens.ReaderScreen
import com.mangagui.app.ui.theme.MangaGuiTheme
import com.mangagui.app.ui.viewmodel.MainViewModel

sealed interface Screen {
    data object Main : Screen
    data class Player(val title: String, val streamData: StreamData) : Screen
    data class Reader(val title: String, val pages: ChapterPages) : Screen
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MangaGuiTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            is Screen.Main -> {
                                MainScreen(
                                    viewModel = viewModel,
                                    onNavigateToPlayer = { title, streamData ->
                                        currentScreen = Screen.Player(title, streamData)
                                    },
                                    onNavigateToReader = { title, pages ->
                                        currentScreen = Screen.Reader(title, pages)
                                    }
                                )
                            }
                            is Screen.Player -> {
                                PlayerScreen(
                                    title = screen.title,
                                    streamData = screen.streamData,
                                    onBack = { currentScreen = Screen.Main }
                                )
                            }
                            is Screen.Reader -> {
                                ReaderScreen(
                                    title = screen.title,
                                    chapterPages = screen.pages,
                                    onBack = { currentScreen = Screen.Main }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

