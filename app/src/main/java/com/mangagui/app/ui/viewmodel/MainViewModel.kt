package com.mangagui.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangagui.app.model.ChapterPages
import com.mangagui.app.model.Filters
import com.mangagui.app.model.MediaItem
import com.mangagui.app.model.StreamData
import com.mangagui.app.provider.ProviderManager
import com.mangagui.app.service.CoverService
import com.mangagui.app.service.DownloadService
import com.mangagui.app.service.HistoryService
import com.mangagui.app.service.SearchHistoryService
import com.mangagui.app.service.UpdateInfo
import com.mangagui.app.service.UpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val historyService = HistoryService(application)
    val searchHistoryService = SearchHistoryService(application)
    val downloadService = DownloadService(application)
    val coverService = CoverService(application)
    val updateService = UpdateService(application)

    private val _activeTab = MutableStateFlow("Anime")
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusText = MutableStateFlow("Ready")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _selectedSource = MutableStateFlow("All sources")
    val selectedSource: StateFlow<String> = _selectedSource.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("All languages")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _selectedGenre = MutableStateFlow("All genres")
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    private val _selectedAudioFilter = MutableStateFlow("All Audio (Sub & Dub)")
    val selectedAudioFilter: StateFlow<String> = _selectedAudioFilter.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _isSearchHistoryOpen = MutableStateFlow(false)
    val isSearchHistoryOpen: StateFlow<Boolean> = _isSearchHistoryOpen.asStateFlow()

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _selectedItem = MutableStateFlow<MediaItem?>(null)
    val selectedItem: StateFlow<MediaItem?> = _selectedItem.asStateFlow()

    private val _selectedNumber = MutableStateFlow(1)
    val selectedNumber: StateFlow<Int> = _selectedNumber.asStateFlow()

    private val _maxNumber = MutableStateFlow(1)
    val maxNumber: StateFlow<Int> = _maxNumber.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgressText = MutableStateFlow("")
    val downloadProgressText: StateFlow<String> = _downloadProgressText.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isUpdateDialogVisible = MutableStateFlow(false)
    val isUpdateDialogVisible: StateFlow<Boolean> = _isUpdateDialogVisible.asStateFlow()

    private val _isUpdatingApk = MutableStateFlow(false)
    val isUpdatingApk: StateFlow<Boolean> = _isUpdatingApk.asStateFlow()

    private val _updateProgress = MutableStateFlow(0)
    val updateProgress: StateFlow<Int> = _updateProgress.asStateFlow()

    init {
        loadSearchHistory()
        loadCatalog()
        checkForUpdates()
    }

    private fun loadSearchHistory() {
        viewModelScope.launch {
            _searchHistory.value = searchHistoryService.getSearchHistory()
        }
    }

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun openSearchHistory() {
        if (_searchHistory.value.isNotEmpty()) {
            _isSearchHistoryOpen.value = true
        }
    }

    fun closeSearchHistory() {
        _isSearchHistoryOpen.value = false
    }

    fun selectSearchHistory(q: String) {
        _searchQuery.value = q
        _isSearchHistoryOpen.value = false
        search()
    }

    fun removeSearchHistoryItem(q: String) {
        viewModelScope.launch {
            _searchHistory.value = searchHistoryService.removeSearchQuery(q)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            searchHistoryService.clearHistory()
            _searchHistory.value = emptyList()
            _isSearchHistoryOpen.value = false
        }
    }

    fun switchTab(tab: String) {
        if (_activeTab.value == tab) return
        _activeTab.value = tab
        _selectedItem.value = null
        _selectedSource.value = "All sources"
        _searchQuery.value = ""

        when (tab) {
            "Downloaded" -> loadDownloaded()
            "History" -> loadHistory()
            else -> loadCatalog()
        }
    }

    fun onSourceChanged(s: String) {
        _selectedSource.value = s
        if (_searchQuery.value.isNotBlank()) search() else loadCatalog()
    }

    fun onLanguageChanged(lang: String) {
        _selectedLanguage.value = lang
        val opts = Filters.getAudioOptionsForLanguage(lang)
        _selectedAudioFilter.value = opts.allOption
        if (_searchQuery.value.isNotBlank()) search() else loadCatalog()
    }

    fun onGenreChanged(genre: String) {
        _selectedGenre.value = genre
        if (_searchQuery.value.isNotBlank()) search() else loadCatalog()
    }

    fun onAudioFilterChanged(audio: String) {
        _selectedAudioFilter.value = audio
        if (_activeTab.value == "Anime") {
            if (_searchQuery.value.isNotBlank()) search() else loadCatalog()
        }
    }

    fun selectQuickGenre(genre: String) {
        _selectedGenre.value = genre
        if (_searchQuery.value.isNotBlank()) search() else loadCatalog()
    }

    fun selectQuickAudio(target: String) {
        val currentCode = Filters.normalizeAudio(_selectedAudioFilter.value)
        val targetCode = Filters.normalizeAudio(target)
        val opts = Filters.getAudioOptionsForLanguage(_selectedLanguage.value)

        _selectedAudioFilter.value = if (currentCode == targetCode && targetCode != "all") {
            opts.allOption
        } else {
            when (targetCode) {
                "sub" -> opts.subOption
                "dub" -> opts.dubOption
                else -> opts.allOption
            }
        }

        if (_activeTab.value == "Anime") {
            if (_searchQuery.value.isNotBlank()) search() else loadCatalog()
        }
    }

    fun resetFilters() {
        val opts = Filters.getAudioOptionsForLanguage(_selectedLanguage.value)
        _selectedGenre.value = "All genres"
        _selectedAudioFilter.value = opts.allOption
        _selectedSource.value = "All sources"
        _searchQuery.value = ""
        loadCatalog()
    }

    fun search() {
        closeSearchHistory()
        val q = _searchQuery.value.trim()
        if (q.isBlank()) {
            loadCatalog()
            return
        }

        viewModelScope.launch {
            _searchHistory.value = searchHistoryService.addSearchQuery(q)
            _isLoading.value = true
            _statusText.value = "Searching for \"$q\"..."
            _items.value = emptyList()

            try {
                val tab = _activeTab.value
                val isAnime = tab == "Anime"
                val src = _selectedSource.value.takeIf { it != "All sources" }
                val lang = _selectedLanguage.value
                val genre = _selectedGenre.value
                val audio = _selectedAudioFilter.value

                val results = if (isAnime) {
                    ProviderManager.searchAnime(q, src, lang, genre, audio).map { it.copy(type = "anime") }
                } else {
                    ProviderManager.searchManga(q, src, lang, genre).map { it.copy(type = "manga") }
                }

                _items.value = results
                _statusText.value = "Found ${results.size} titles for \"$q\""
                enrichCovers(results, if (isAnime) "anime" else "manga")
            } catch (ex: Exception) {
                _statusText.value = "Search error: ${ex.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCatalog() {
        viewModelScope.launch {
            val tab = _activeTab.value
            if (tab != "Anime" && tab != "Manga") return@launch

            _isLoading.value = true
            _statusText.value = "Loading $tab catalog (${_selectedLanguage.value})..."
            _items.value = emptyList()

            try {
                val isAnime = tab == "Anime"
                val src = _selectedSource.value.takeIf { it != "All sources" }
                val lang = _selectedLanguage.value
                val genre = _selectedGenre.value
                val audio = _selectedAudioFilter.value

                val results = if (isAnime) {
                    ProviderManager.getAnimeCatalog(src, lang, genre, audio).map { it.copy(type = "anime") }
                } else {
                    ProviderManager.getMangaCatalog(src, lang, genre).map { it.copy(type = "manga") }
                }

                _items.value = results
                _statusText.value = "$tab Catalog: ${results.size} titles ready"
                enrichCovers(results, if (isAnime) "anime" else "manga")
            } catch (ex: Exception) {
                _statusText.value = "Catalog error: ${ex.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun enrichCovers(items: List<MediaItem>, type: String) {
        viewModelScope.launch {
            val updated = items.toMutableList()
            var changed = false
            for (i in updated.indices) {
                val item = updated[i]
                if (item.image.isNullOrBlank()) {
                    val cover = coverService.getCover(item.title, type)
                    if (!cover.isNullOrBlank()) {
                        updated[i] = item.copy(image = cover)
                        changed = true
                    }
                }
            }
            if (changed) {
                _items.value = updated
            }
        }
    }

    fun loadDownloaded() {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = "Scanning downloaded titles..."
            _items.value = emptyList()
            try {
                val downloaded = downloadService.getDownloadedItems()
                _items.value = downloaded
                _statusText.value = "Downloaded: ${downloaded.size} titles ready offline"
            } catch (ex: Exception) {
                _statusText.value = "Downloads error: ${ex.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = "Loading history..."
            _items.value = emptyList()
            try {
                val history = historyService.getHistory()
                _items.value = history
                _statusText.value = "History: ${history.size} titles"
            } catch (ex: Exception) {
                _statusText.value = "History error: ${ex.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectItem(item: MediaItem) {
        _selectedItem.value = item
        if (item.isDownloaded) {
            val available = item.watchedNumbers.sorted()
            val initial = available.firstOrNull() ?: 1
            val maxNum = available.maxOrNull() ?: 1
            _maxNumber.value = maxNum
            _selectedNumber.value = initial
            return
        }

        val highest = item.watchedNumbers.maxOrNull() ?: 1
        val initialMax = maxOf(highest, if (item.count > 0) item.count else 1)
        _maxNumber.value = initialMax
        _selectedNumber.value = highest

        // Fetch detail count in background
        viewModelScope.launch {
            try {
                val isManga = item.type.equals("manga", ignoreCase = true)
                val total = if (isManga) {
                    val prov = ProviderManager.getMangaProvider(item.source)
                        ?: ProviderManager.mangaProviders.firstOrNull { it.languages.contains(item.language) || it.languages.contains("all") }
                    prov?.getChapters(item.url ?: item.id, item.language)?.size ?: 1
                } else {
                    val prov = ProviderManager.getAnimeProvider(item.source) ?: ProviderManager.animeProviders.first()
                    prov.getEpisodes(item.url ?: item.id).size
                }
                if (_selectedItem.value?.id == item.id && total > 0) {
                    _maxNumber.value = maxOf(total, _selectedNumber.value)
                }
            } catch (_: Exception) {}
        }
    }

    fun closeDetails() {
        _selectedItem.value = null
    }

    fun decrementNumber() {
        if (_selectedNumber.value > 1) {
            _selectedNumber.value -= 1
        }
    }

    fun incrementNumber() {
        if (_selectedNumber.value < _maxNumber.value) {
            _selectedNumber.value += 1
        }
    }

    fun setNumber(num: Int) {
        if (num >= 1) {
            if (num > _maxNumber.value) {
                _maxNumber.value = num
            }
            _selectedNumber.value = num
        }
    }

    fun unmarkWatched(item: MediaItem, number: Int) {
        viewModelScope.launch {
            val ok = historyService.unmarkWatched(item, number)
            if (ok) {
                val newNumbers = item.watchedNumbers.toMutableList().apply { remove(number) }
                val updatedItem = item.copy(
                    watchedNumbers = newNumbers,
                    count = newNumbers.size
                )
                if (_selectedItem.value?.id == item.id) {
                    _selectedItem.value = updatedItem
                }
                if (_activeTab.value == "History") {
                    loadHistory()
                }
                _statusText.value = "✔ Unmarked number $number from history"
            }
        }
    }

    fun deleteDownloadedItem(item: MediaItem) {
        viewModelScope.launch {
            val ok = downloadService.deleteItem(item)
            if (ok) {
                closeDetails()
                loadDownloaded()
                _statusText.value = "✔ Deleted ${item.title} from offline storage"
            }
        }
    }

    fun downloadCurrentItem(preferredAudio: String? = null) {
        val item = _selectedItem.value ?: return
        if (_isDownloading.value) return
        val num = _selectedNumber.value

        viewModelScope.launch {
            _isDownloading.value = true
            _statusText.value = "Starting download for ${item.title}..."
            _downloadProgressText.value = "Starting..."

            val activeFilterAudio = Filters.normalizeAudio(_selectedAudioFilter.value)
            val audioToUse = when {
                !preferredAudio.isNullOrBlank() -> preferredAudio
                activeFilterAudio in listOf("dub", "ita") -> "dub"
                activeFilterAudio in listOf("sub", "subita") -> "sub"
                else -> item.audio
            }

            val success = downloadService.downloadItem(item, num, audioToUse) { prog ->
                _downloadProgressText.value = prog
                _statusText.value = "Downloading ${item.title}: $prog"
            }

            _isDownloading.value = false
            _downloadProgressText.value = ""
            if (success) {
                _statusText.value = "✔ Download completed for ${item.title} ($num)"
                if (_activeTab.value == "Downloaded") loadDownloaded()
            } else {
                _statusText.value = "❌ Download failed"
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val info = updateService.checkUpdate()
            _updateInfo.value = info
            if (info.hasUpdate) {
                _isUpdateDialogVisible.value = true
            }
        }
    }

    fun dismissUpdateDialog() {
        _isUpdateDialogVisible.value = false
    }

    fun startUpdate() {
        val apkUrl = _updateInfo.value?.apkUrl ?: return
        viewModelScope.launch {
            _isUpdatingApk.value = true
            val success = updateService.downloadAndInstallApk(apkUrl) { percent ->
                _updateProgress.value = percent
            }
            _isUpdatingApk.value = false
            if (success) {
                _isUpdateDialogVisible.value = false
            }
        }
    }

    suspend fun getStreamForPlayback(item: MediaItem, number: Int, preferredAudio: String? = null): StreamData? = withContext(Dispatchers.IO) {
        try {
            // Check if downloaded offline file exists
            if (item.isDownloaded && !item.localPath.isNullOrBlank()) {
                val dir = java.io.File(item.localPath)
                if (dir.exists()) {
                    val videoFile = if (dir.isFile) {
                        dir
                    } else {
                        val videoFiles = dir.walkTopDown().filter { file ->
                            file.isFile && (
                                file.extension.equals("mp4", true) ||
                                file.extension.equals("mkv", true) ||
                                file.extension.equals("webm", true)
                            )
                        }.toList()

                        videoFiles.find { file ->
                            val epNum = Regex("""(?:ep(?:isode)?|_|\b)(${number})(?:\.|\b|_)""", RegexOption.IGNORE_CASE)
                                .find(file.name)?.groupValues?.get(1)?.toIntOrNull()
                            epNum == number
                        } ?: videoFiles.find { file ->
                            file.name.contains("$number")
                        } ?: videoFiles.firstOrNull()
                    }

                    if (videoFile != null && videoFile.exists()) {
                        historyService.markWatched(item, number)
                        return@withContext StreamData(
                            streamUrl = android.net.Uri.fromFile(videoFile).toString(),
                            referer = null
                        )
                    }
                }
            }

            val prov = ProviderManager.getAnimeProvider(item.source)
                ?: ProviderManager.animeProviders.firstOrNull { it.languages.contains(item.language) || it.languages.contains("all") }
                ?: ProviderManager.animeProviders.first()

            val episodes = prov.getEpisodes(item.url ?: item.id)
            val targetEp = episodes.find { it.number == number } ?: episodes.getOrNull(number - 1)
            if (targetEp != null) {
                val activeFilterAudio = Filters.normalizeAudio(_selectedAudioFilter.value)
                val targetAudio = when {
                    !preferredAudio.isNullOrBlank() -> preferredAudio
                    activeFilterAudio in listOf("dub", "ita") -> "dub"
                    activeFilterAudio in listOf("sub", "subita") -> "sub"
                    item.audio?.lowercase() in listOf("dub", "ita") -> "dub"
                    else -> "sub"
                }

                val stream = prov.getEpisodeStream(targetEp.url, targetAudio)
                // Mark as watched
                historyService.markWatched(item, number)
                return@withContext stream
            }
            null
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    suspend fun getChapterPagesForReading(item: MediaItem, number: Int): ChapterPages? = withContext(Dispatchers.IO) {
        try {
            // Check if downloaded offline chapter exists
            if (item.isDownloaded && !item.localPath.isNullOrBlank()) {
                val f = java.io.File(item.localPath)
                if (f.exists() && f.isDirectory) {
                    val subDirs = f.listFiles { d -> d.isDirectory } ?: emptyArray()
                    val chapterDir = subDirs.find { d ->
                        val num = Regex("""(?:ch(?:apter)?|cap(?:itolo)?|_|\b)(${number})(?:\b|_)""", RegexOption.IGNORE_CASE)
                            .find(d.name)?.groupValues?.get(1)?.toIntOrNull()
                        num == number
                    } ?: subDirs.find { d -> d.name.contains("$number") } ?: f

                    val imageFiles = chapterDir.walkTopDown().filter { file ->
                        file.isFile && (
                            file.extension.equals("jpg", true) ||
                            file.extension.equals("jpeg", true) ||
                            file.extension.equals("png", true) ||
                            file.extension.equals("webp", true)
                        ) && !file.name.equals("cover.jpg", true)
                    }.sortedBy { it.name }.toList()

                    if (imageFiles.isNotEmpty()) {
                        val uris = imageFiles.map { android.net.Uri.fromFile(it).toString() }
                        historyService.markWatched(item, number)
                        return@withContext ChapterPages(chapterTitle = "Capitolo $number", pages = uris)
                    }
                }
            }

            val prov = ProviderManager.getMangaProvider(item.source)
                ?: ProviderManager.mangaProviders.firstOrNull { it.languages.contains(item.language) || it.languages.contains("all") }
                ?: ProviderManager.mangaProviders.first()

            val chapters = prov.getChapters(item.url ?: item.id, item.language)
            val targetCh = chapters.find { it.number == number.toString() } ?: chapters.getOrNull(number - 1)
            if (targetCh != null) {
                val pages = prov.getChapterPages(targetCh.url)
                // Mark as read
                historyService.markWatched(item, number)
                return@withContext pages
            }
            null
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }
}
