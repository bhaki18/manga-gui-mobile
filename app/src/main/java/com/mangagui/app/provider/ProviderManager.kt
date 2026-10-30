package com.mangagui.app.provider

import com.mangagui.app.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object ProviderManager {
    val animeProviders = listOf<AnimeProvider>(
        AnimeWorldProvider(),
        AnimeSaturnProvider(),
        AnimeUnityProvider(),
        OnePiecePowerAnimeProvider()
    )

    val mangaProviders = listOf<MangaProvider>(
        OnePiecePowerMangaProvider(),
        MangaWorldProvider(),
        MangaDexProvider(),
        MangapillProvider()
    )

    fun getAnimeProvider(id: String?): AnimeProvider? {
        if (id.isNullOrBlank()) return animeProviders.firstOrNull()
        val clean = id.lowercase().trim()
        return animeProviders.firstOrNull { it.id.equals(clean, ignoreCase = true) }
    }

    fun getMangaProvider(id: String?): MangaProvider? {
        if (id.isNullOrBlank()) return mangaProviders.firstOrNull()
        val clean = id.lowercase().trim()
        return mangaProviders.firstOrNull { it.id.equals(clean, ignoreCase = true) }
    }

    private fun interleaveResults(resultsBySource: List<List<MediaItem>>): List<MediaItem> {
        val interleaved = mutableListOf<MediaItem>()
        val seenUrls = mutableSetOf<String>()
        var hasMore = true
        var index = 0

        while (hasMore) {
            hasMore = false
            for (list in resultsBySource) {
                if (index < list.size) {
                    val item = list[index]
                    val key = item.url ?: item.id
                    if (seenUrls.add(key)) {
                        interleaved.add(item)
                    }
                    hasMore = true
                }
            }
            index++
        }
        return interleaved
    }

    suspend fun getAnimeCatalog(source: String?, lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val selected = getAnimeProvider(source)
        if (selected != null && !source.isNullOrBlank() && !source.equals("All sources", ignoreCase = true)) {
            return@withContext selected.getCatalog(lang, genre, audio)
        }

        val deferreds = animeProviders.map { prov ->
            async {
                runCatching { prov.getCatalog(lang, genre, audio) }.getOrDefault(emptyList())
            }
        }
        val allLists = deferreds.awaitAll()
        interleaveResults(allLists)
    }

    suspend fun searchAnime(query: String, source: String?, lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val selected = getAnimeProvider(source)
        if (selected != null && !source.isNullOrBlank() && !source.equals("All sources", ignoreCase = true)) {
            return@withContext selected.search(query, lang, genre, audio)
        }

        val deferreds = animeProviders.map { prov ->
            async {
                runCatching { prov.search(query, lang, genre, audio) }.getOrDefault(emptyList())
            }
        }
        val allLists = deferreds.awaitAll()
        interleaveResults(allLists)
    }

    suspend fun getMangaCatalog(source: String?, lang: String?, genre: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val selected = getMangaProvider(source)
        if (selected != null && !source.isNullOrBlank() && !source.equals("All sources", ignoreCase = true)) {
            return@withContext selected.getCatalog(lang, genre)
        }

        val deferreds = mangaProviders.map { prov ->
            async {
                runCatching { prov.getCatalog(lang, genre) }.getOrDefault(emptyList())
            }
        }
        val allLists = deferreds.awaitAll()
        interleaveResults(allLists)
    }

    suspend fun searchManga(query: String, source: String?, lang: String?, genre: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val selected = getMangaProvider(source)
        if (selected != null && !source.isNullOrBlank() && !source.equals("All sources", ignoreCase = true)) {
            return@withContext selected.search(query, lang, genre)
        }

        val deferreds = mangaProviders.map { prov ->
            async {
                runCatching { prov.search(query, lang, genre) }.getOrDefault(emptyList())
            }
        }
        val allLists = deferreds.awaitAll()
        interleaveResults(allLists)
    }
}

