package com.mangagui.app.service

import android.content.Context
import com.mangagui.app.model.MediaItem
import com.mangagui.app.network.NetworkClient
import com.mangagui.app.provider.ProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DownloadService(private val context: Context) {
    private val baseDownloadDir: File by lazy {
        val ext = context.getExternalFilesDir(null) ?: context.filesDir
        File(ext, "Downloaded").also { it.mkdirs() }
    }

    private val animeDir: File get() = File(baseDownloadDir, "Anime").also { it.mkdirs() }
    private val mangaDir: File get() = File(baseDownloadDir, "Manga").also { it.mkdirs() }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim().ifBlank { "unnamed" }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1000) String.format("%.1f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }

    suspend fun downloadItem(item: MediaItem, number: Int, preferredAudio: String? = null, onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val isManga = item.type.equals("manga", ignoreCase = true)
        val safeTitle = sanitizeFilename(item.title)
        val targetDir = File(if (isManga) mangaDir else animeDir, safeTitle).also { it.mkdirs() }

        try {
            // Cache cover image locally for offline display
            if (!item.image.isNullOrBlank()) {
                val coverFile = File(targetDir, "cover.jpg")
                if (!coverFile.exists() || coverFile.length() == 0L) {
                    runCatching {
                        NetworkClient.downloadToFile(item.image, coverFile)
                    }
                }
            }

            if (isManga) {
                onProgress("Fetching manga chapters...")
                val prov = ProviderManager.getMangaProvider(item.source)
                    ?: ProviderManager.mangaProviders.firstOrNull { it.languages.contains(item.language) || it.languages.contains("all") }
                    ?: return@withContext false

                val chapters = prov.getChapters(item.url ?: item.id, item.language)
                val targetCh = chapters.find { it.number == number.toString() } ?: chapters.getOrNull(number - 1)
                if (targetCh == null) {
                    onProgress("Chapter $number not found")
                    return@withContext false
                }

                onProgress("Fetching pages for chapter ${targetCh.number}...")
                val chPages = prov.getChapterPages(targetCh.url)
                if (chPages.pages.isEmpty()) {
                    onProgress("No pages found for chapter")
                    return@withContext false
                }

                val chapterDir = File(targetDir, "Ch_$number").also { it.mkdirs() }
                chPages.pages.forEachIndexed { idx, pageUrl ->
                    val pageNum = idx + 1
                    val rawExt = pageUrl.substringBefore("?").substringBefore("#").substringAfterLast(".", "jpg").lowercase()
                    val ext = if (rawExt in listOf("jpg", "jpeg", "png", "webp", "gif", "avif")) rawExt else "jpg"
                    val pageFile = File(chapterDir, "page_${String.format("%03d", pageNum)}.$ext")
                    if (!pageFile.exists() || pageFile.length() == 0L) {
                        onProgress("Downloading page $pageNum / ${chPages.pages.size}...")
                        NetworkClient.downloadToFile(pageUrl, pageFile, chPages.referer ?: item.url)
                    }
                }
                onProgress("✔ Download completed!")
                return@withContext true
            } else {
                onProgress("Fetching stream for episode $number...")
                val prov = ProviderManager.getAnimeProvider(item.source) ?: ProviderManager.animeProviders.first()
                val episodes = prov.getEpisodes(item.url ?: item.id)
                val targetEp = episodes.find { it.number == number } ?: episodes.getOrNull(number - 1)
                if (targetEp == null) {
                    onProgress("Episode $number not found")
                    return@withContext false
                }

                val targetAudio = when {
                    !preferredAudio.isNullOrBlank() -> preferredAudio
                    item.audio?.lowercase() in listOf("dub", "ita") -> "dub"
                    else -> "subita"
                }
                val stream = prov.getEpisodeStream(targetEp.url, targetAudio)
                val streamUrl = stream.streamUrl
                if (streamUrl.isNullOrBlank()) {
                    onProgress("Unable to extract video stream")
                    return@withContext false
                }

                val epFile = File(targetDir, "Episode_$number.mp4")
                onProgress("Downloading video...")
                NetworkClient.downloadToFile(streamUrl, epFile, stream.referer) { current, total ->
                    val percent = if (total > 0) (current * 100 / total).toInt() else 0
                    val sizeText = formatSize(current)
                    onProgress("$percent% ($sizeText)")
                }
                onProgress("✔ Download completed!")
                return@withContext true
            }
        } catch (ex: Exception) {
            onProgress("Download error: ${ex.message}")
            false
        }
    }

    suspend fun getDownloadedItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()

        // 1. Anime
        if (animeDir.exists()) {
            animeDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val videoFiles = dir.walkTopDown().filter { f ->
                    f.isFile && (f.extension.equals("mp4", true) || f.extension.equals("mkv", true) || f.extension.equals("webm", true))
                }.toList()

                if (videoFiles.isNotEmpty()) {
                    val totalBytes = videoFiles.sumOf { it.length() }
                    val numbers = videoFiles.mapNotNull { f ->
                        Regex("""(?:ep(?:isode)?|_|\b)(\d+)""", RegexOption.IGNORE_CASE).find(f.name)?.groupValues?.get(1)?.toIntOrNull()
                    }.distinct().sorted()

                    val coverFile = File(dir, "cover.jpg")
                    val coverUri = if (coverFile.exists() && coverFile.length() > 0) android.net.Uri.fromFile(coverFile).toString() else null

                    list.add(
                        MediaItem(
                            id = "download:anime:${dir.name}",
                            title = dir.name.replace("_", " "),
                            type = "anime",
                            source = "downloaded",
                            sourceName = "Offline Storage",
                            image = coverUri,
                            isDownloaded = true,
                            localPath = dir.absolutePath,
                            count = if (numbers.isNotEmpty()) numbers.maxOrNull() ?: videoFiles.size else videoFiles.size,
                            watchedNumbers = if (numbers.isNotEmpty()) numbers else listOf(1),
                            downloadSizeText = formatSize(totalBytes),
                            customSubDubText = "${videoFiles.size} ${if (videoFiles.size == 1) "episode" else "episodes"} (${formatSize(totalBytes)})"
                        )
                    )
                }
            }
        }

        // 2. Manga
        if (mangaDir.exists()) {
            mangaDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val subDirs = dir.listFiles()?.filter { it.isDirectory }?.toList() ?: emptyList()
                val totalBytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

                val numbers = subDirs.mapNotNull { d ->
                    Regex("""(?:ch(?:apter)?|cap(?:itolo)?|_|\b)(\d+)""", RegexOption.IGNORE_CASE).find(d.name)?.groupValues?.get(1)?.toIntOrNull()
                }.distinct().sorted()

                val hasImages = dir.walkTopDown().any { f ->
                    f.isFile && (f.extension.equals("jpg", true) || f.extension.equals("png", true) || f.extension.equals("webp", true)) && !f.name.equals("cover.jpg", true)
                }

                if (numbers.isNotEmpty() || hasImages) {
                    val validNumbers = if (numbers.isNotEmpty()) numbers else listOf(1)
                    val count = validNumbers.maxOrNull() ?: 1

                    val coverFile = File(dir, "cover.jpg")
                    val coverUri = if (coverFile.exists() && coverFile.length() > 0) android.net.Uri.fromFile(coverFile).toString() else null

                    list.add(
                        MediaItem(
                            id = "download:manga:${dir.name}",
                            title = dir.name.replace("_", " "),
                            type = "manga",
                            source = "downloaded",
                            sourceName = "Offline Storage",
                            image = coverUri,
                            isDownloaded = true,
                            localPath = dir.absolutePath,
                            count = count,
                            watchedNumbers = validNumbers,
                            downloadSizeText = formatSize(totalBytes),
                            customSubDubText = "${validNumbers.size} ${if (validNumbers.size == 1) "chapter" else "chapters"} (${formatSize(totalBytes)})"
                        )
                    )
                }
            }
        }

        list
    }

    suspend fun deleteItem(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val path = item.localPath ?: return@withContext false
        val file = File(path)
        if (file.exists()) {
            file.deleteRecursively()
        } else false
    }
}

