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

    suspend fun downloadItem(item: MediaItem, number: Int, onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val isManga = item.type.equals("manga", ignoreCase = true)
        val safeTitle = sanitizeFilename(item.title)
        val targetDir = File(if (isManga) mangaDir else animeDir, safeTitle).also { it.mkdirs() }

        try {
            if (isManga) {
                onProgress("Recupero capitoli manga...")
                val prov = ProviderManager.getMangaProvider(item.source)
                    ?: ProviderManager.mangaProviders.firstOrNull { it.languages.contains(item.language) || it.languages.contains("all") }
                    ?: return@withContext false

                val chapters = prov.getChapters(item.url ?: item.id, item.language)
                val targetCh = chapters.find { it.number == number.toString() } ?: chapters.getOrNull(number - 1)
                if (targetCh == null) {
                    onProgress("Capitolo $number non trovato")
                    return@withContext false
                }

                onProgress("Recupero pagine capitolo ${targetCh.number}...")
                val chPages = prov.getChapterPages(targetCh.url)
                if (chPages.pages.isEmpty()) {
                    onProgress("Nessuna pagina trovata per il capitolo")
                    return@withContext false
                }

                val chapterDir = File(targetDir, "Ch_$number").also { it.mkdirs() }
                chPages.pages.forEachIndexed { idx, pageUrl ->
                    val pageNum = idx + 1
                    val ext = pageUrl.substringBefore("?").substringAfterLast(".", "jpg")
                    val pageFile = File(chapterDir, "page_${String.format("%03d", pageNum)}.$ext")
                    if (!pageFile.exists() || pageFile.length() == 0L) {
                        onProgress("Download pagina $pageNum / ${chPages.pages.size}...")
                        NetworkClient.downloadToFile(pageUrl, pageFile, chPages.referer ?: item.url)
                    }
                }
                onProgress("✔ Download completato!")
                return@withContext true
            } else {
                onProgress("Recupero stream episodio $number...")
                val prov = ProviderManager.getAnimeProvider(item.source) ?: ProviderManager.animeProviders.first()
                val episodes = prov.getEpisodes(item.url ?: item.id)
                val targetEp = episodes.find { it.number == number } ?: episodes.getOrNull(number - 1)
                if (targetEp == null) {
                    onProgress("Episodio $number non trovato")
                    return@withContext false
                }

                val stream = prov.getEpisodeStream(targetEp.url, item.audio ?: "subita")
                val streamUrl = stream.streamUrl
                if (streamUrl.isNullOrBlank()) {
                    onProgress("Impossibile estrarre lo stream video")
                    return@withContext false
                }

                val epFile = File(targetDir, "Episode_$number.mp4")
                onProgress("Download video in corso...")
                NetworkClient.downloadToFile(streamUrl, epFile, stream.referer) { current, total ->
                    val percent = if (total > 0) (current * 100 / total).toInt() else 0
                    val sizeText = formatSize(current)
                    onProgress("$percent% ($sizeText)")
                }
                onProgress("✔ Download completato!")
                return@withContext true
            }
        } catch (ex: Exception) {
            onProgress("Errore download: ${ex.message}")
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
                        Regex("""(?:ep(?:isode)?|\b)(\d+)""", RegexOption.IGNORE_CASE).find(f.name)?.groupValues?.get(1)?.toIntOrNull()
                    }.distinct().sorted()

                    list.add(
                        MediaItem(
                            id = "download:anime:${dir.name}",
                            title = dir.name.replace("_", " "),
                            type = "anime",
                            source = "downloaded",
                            sourceName = "Offline Storage",
                            isDownloaded = true,
                            localPath = dir.absolutePath,
                            count = videoFiles.size,
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

                if (subDirs.isNotEmpty() || totalBytes > 0) {
                    val count = if (subDirs.isNotEmpty()) subDirs.size else 1
                    val numbers = subDirs.mapNotNull { d ->
                        Regex("""(?:ch(?:apter)?|cap(?:itolo)?|\b)(\d+)""", RegexOption.IGNORE_CASE).find(d.name)?.groupValues?.get(1)?.toIntOrNull()
                    }.distinct().sorted()

                    list.add(
                        MediaItem(
                            id = "download:manga:${dir.name}",
                            title = dir.name.replace("_", " "),
                            type = "manga",
                            source = "downloaded",
                            sourceName = "Offline Storage",
                            isDownloaded = true,
                            localPath = dir.absolutePath,
                            count = count,
                            watchedNumbers = if (numbers.isNotEmpty()) numbers else listOf(1),
                            downloadSizeText = formatSize(totalBytes),
                            customSubDubText = "$count ${if (count == 1) "chapter" else "chapters"} (${formatSize(totalBytes)})"
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

