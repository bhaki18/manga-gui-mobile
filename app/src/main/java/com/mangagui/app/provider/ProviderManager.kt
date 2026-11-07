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

    fun normalizeTitleForDedup(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return ""
        return rawTitle
            .replace(Regex("""\b(?:sub\s*ita|ita\s*doppiato|ita\s*dubbed|ita/sub\s*ita|sub/dub|ita|subita|sub|dub|raw|oav|movie|film|tv)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(?:english\s*sub|eng\s*sub|english|eng)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[()\[\]/\-_]"""), " ")
            .replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()
    }

    fun cleanDisplayTitle(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return ""
        return rawTitle
            .replace(Regex("""\s*[(\[]\s*(?:sub\s*ita|ita\s*doppiato|ita\s*dubbed|ita/sub\s*ita|sub/dub|ita|subita|sub|dub|english\s*sub|eng|raw)\s*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*(?:sub\s*ita|ita\s*doppiato|ita\s*dubbed|ita|sub|dub).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:SUB\s*ITA|ITA\s*DUBBED|ITA/SUB\s*ITA|SUBITA|ITA)\b""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun mergeAudio(a1: String?, a2: String?): String {
        val norm1 = a1?.lowercase()?.trim() ?: ""
        val norm2 = a2?.lowercase()?.trim() ?: ""
        val hasSub1 = norm1 == "both" || norm1 == "sub" || norm1 == "subita" || norm1.contains("sub")
        val hasSub2 = norm2 == "both" || norm2 == "sub" || norm2 == "subita" || norm2.contains("sub")
        val hasDub1 = norm1 == "both" || norm1 == "dub" || norm1 == "ita" || norm1.contains("dub") || norm1.contains("ita")
        val hasDub2 = norm2 == "both" || norm2 == "dub" || norm2 == "ita" || norm2.contains("dub") || norm2.contains("ita")

        val hasSub = hasSub1 || hasSub2
        val hasDub = hasDub1 || hasDub2

        return when {
            hasSub && hasDub -> "both"
            hasDub -> "dub"
            hasSub -> "sub"
            else -> norm1.ifEmpty { norm2 }
        }
    }

    fun deduplicateItems(items: List<MediaItem>): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val seenKeys = mutableMapOf<String, MediaItem>()

        for (item in items) {
            val normTitle = normalizeTitleForDedup(item.title)
            val key = if (normTitle.length >= 3) "${item.type}:$normTitle" else (item.url ?: item.id)
            val existing = seenKeys[key]
            if (existing == null) {
                val cleanedTitle = cleanDisplayTitle(item.title)
                val cleaned = if (cleanedTitle.length >= 2) item.copy(title = cleanedTitle) else item
                seenKeys[key] = cleaned
                result.add(cleaned)
            } else {
                val mergedAudio = mergeAudio(existing.audio, item.audio)
                val mergedImage = if (existing.image.isNullOrBlank() && !item.image.isNullOrBlank()) item.image else existing.image
                val mergedCount = maxOf(existing.count, item.count)
                val updated = existing.copy(
                    audio = mergedAudio,
                    image = mergedImage,
                    count = mergedCount
                )
                seenKeys[key] = updated
                val idx = result.indexOf(existing)
                if (idx >= 0) {
                    result[idx] = updated
                }
            }
        }
        return result
    }

    private fun interleaveResults(resultsBySource: List<List<MediaItem>>): List<MediaItem> {
        val interleaved = mutableListOf<MediaItem>()
        val seenKeys = mutableMapOf<String, MediaItem>()
        var hasMore = true
        var index = 0

        while (hasMore) {
            hasMore = false
            for (list in resultsBySource) {
                if (index < list.size) {
                    val item = list[index]
                    val normTitle = normalizeTitleForDedup(item.title)
                    val key = if (normTitle.length >= 3) "${item.type}:$normTitle" else (item.url ?: item.id)
                    val existing = seenKeys[key]
                    if (existing == null) {
                        val cleanedTitle = cleanDisplayTitle(item.title)
                        val cleaned = if (cleanedTitle.length >= 2) item.copy(title = cleanedTitle) else item
                        seenKeys[key] = cleaned
                        interleaved.add(cleaned)
                    } else {
                        val mergedAudio = mergeAudio(existing.audio, item.audio)
                        val mergedImage = if (existing.image.isNullOrBlank() && !item.image.isNullOrBlank()) item.image else existing.image
                        val mergedCount = maxOf(existing.count, item.count)
                        val updated = existing.copy(
                            audio = mergedAudio,
                            image = mergedImage,
                            count = mergedCount
                        )
                        seenKeys[key] = updated
                        val idx = interleaved.indexOf(existing)
                        if (idx >= 0) {
                            interleaved[idx] = updated
                        }
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
            return@withContext deduplicateItems(selected.getCatalog(lang, genre, audio).map { it.copy(type = "anime") })
        }

        val deferreds = animeProviders.map { prov ->
            async {
                runCatching { prov.getCatalog(lang, genre, audio) }.getOrDefault(emptyList())
            }
        }
        val allLists = deferreds.awaitAll()
        interleaveResults(allLists).map { it.copy(type = "anime") }
    }

    suspend fun searchAnime(query: String, source: String?, lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val selected = getAnimeProvider(source)
        if (selected != null && !source.isNullOrBlank() && !source.equals("All sources", ignoreCase = true)) {
            return@withContext deduplicateItems(selected.search(query, lang, genre, audio).map { it.copy(type = "anime") })
        }

        val deferreds = animeProviders.map { prov ->
            async {
                runCatching { prov.search(query, lang, genre, audio) }.getOrDefault(emptyList())
            }
        }
        val allLists = deferreds.awaitAll()
        interleaveResults(allLists).map { it.copy(type = "anime") }
    }

    suspend fun getMangaCatalog(source: String?, lang: String?, genre: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val selected = getMangaProvider(source)
        if (selected != null && !source.isNullOrBlank() && !source.equals("All sources", ignoreCase = true)) {
            return@withContext deduplicateItems(selected.getCatalog(lang, genre).map { it.copy(type = "manga") })
        }

        val deferreds = mangaProviders.map { prov ->
            async {
                runCatching { prov.getCatalog(lang, genre) }.getOrDefault(emptyList())
            }
        }
        val allLists = deferreds.awaitAll()
        interleaveResults(allLists).map { it.copy(type = "manga") }
    }

    suspend fun searchManga(query: String, source: String?, lang: String?, genre: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val selected = getMangaProvider(source)
        if (selected != null && !source.isNullOrBlank() && !source.equals("All sources", ignoreCase = true)) {
            return@withContext deduplicateItems(selected.search(query, lang, genre).map { it.copy(type = "manga") })
        }

        val deferreds = mangaProviders.map { prov ->
            async {
                runCatching { prov.search(query, lang, genre) }.getOrDefault(emptyList())
            }
        }
        val allLists = deferreds.awaitAll()
        interleaveResults(allLists).map { it.copy(type = "manga") }
    }
}

