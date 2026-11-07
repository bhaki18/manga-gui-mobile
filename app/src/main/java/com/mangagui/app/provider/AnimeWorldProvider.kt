package com.mangagui.app.provider

import com.mangagui.app.model.Episode
import com.mangagui.app.model.Filters
import com.mangagui.app.model.MediaItem
import com.mangagui.app.model.StreamData
import com.mangagui.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

class AnimeWorldProvider : AnimeProvider {
    override val id: String = "animeworld"
    override val name: String = "AnimeWorld"
    override val baseUrl: String = "https://www.animeworld.ac"
    override val languages: List<String> = listOf("it")

    private var catalogCache: List<MediaItem>? = null

    override suspend fun getCatalog(lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanLang = Filters.resolveLanguageCode(lang)
        if (cleanLang != "all" && cleanLang != "it") return@withContext emptyList()

        val genreKey = Filters.normalizeGenre(genre)
        val genreDef = Filters.GENRE_DEFINITIONS[genreKey]
        val audioKey = Filters.normalizeAudio(audio)
        val hasFilters = genreKey != "all" || audioKey != "all"

        if (!hasFilters && !catalogCache.isNullOrEmpty()) {
            return@withContext catalogCache!!
        }

        val results = mutableListOf<MediaItem>()
        val seenUrls = mutableSetOf<String>()

        try {
            if (hasFilters) {
                val genreParam = if (genreDef != null) "&genre=${genreDef.awId}" else ""
                val dubParam = if (audioKey == "dub" || audioKey == "ita") "&dub=1" else if (audioKey == "sub" || audioKey == "subita") "&dub=0" else ""
                val popHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/filter?sort=6$genreParam$dubParam", baseUrl) }.getOrNull()
                val latestHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/filter?sort=0$genreParam$dubParam", baseUrl) }.getOrNull()
                popHtml?.let { parseItemsFromHtml(it, results, seenUrls) }
                latestHtml?.let { parseItemsFromHtml(it, results, seenUrls) }
                return@withContext results.filter { Filters.matchesAudioFilter(it.audio, audioKey, it.title, cleanLang, it.language) }
            }

            val popularHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/filter?sort=6", baseUrl) }.getOrNull()
            val homeHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/", baseUrl) }.getOrNull()
            popularHtml?.let { parseItemsFromHtml(it, results, seenUrls) }
            homeHtml?.let { parseItemsFromHtml(it, results, seenUrls) }

            if (results.isEmpty()) {
                val azHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/az-list", baseUrl) }.getOrNull()
                azHtml?.let { parseItemsFromHtml(it, results, seenUrls) }
            }

            if (results.isNotEmpty()) {
                catalogCache = results
            }
        } catch (_: Exception) {}

        results
    }

    override suspend fun search(query: String, lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanLang = Filters.resolveLanguageCode(lang)
        if (cleanLang != "all" && cleanLang != "it") return@withContext emptyList()
        if (query.isBlank()) return@withContext getCatalog(lang, genre, audio)

        val results = mutableListOf<MediaItem>()
        val seenUrls = mutableSetOf<String>()

        try {
            val genreKey = Filters.normalizeGenre(genre)
            val genreDef = Filters.GENRE_DEFINITIONS[genreKey]
            val audioKey = Filters.normalizeAudio(audio)
            val genreParam = if (genreDef != null) "&genre=${genreDef.awId}" else ""
            val dubParam = if (audioKey == "dub" || audioKey == "ita") "&dub=1" else if (audioKey == "sub" || audioKey == "subita") "&dub=0" else ""

            val cleaned = query.replace(Regex("\\s*\\[[^\\]]*\\]\\s*"), " ").trim()
            val cleanQuery = URLEncoder.encode(cleaned, "UTF-8")
            val searchUrl = "$baseUrl/filter?keyword=$cleanQuery$genreParam$dubParam"
            val html = NetworkClient.fetchHtml(searchUrl, baseUrl)
            parseItemsFromHtml(html, results, seenUrls)

            if (results.isEmpty() && cleaned.contains("(")) {
                val baseQuery = cleaned.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ").trim()
                if (baseQuery.length >= 2) {
                    val fallbackHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/filter?keyword=${URLEncoder.encode(baseQuery, "UTF-8")}$genreParam$dubParam", baseUrl) }.getOrNull()
                    fallbackHtml?.let { parseItemsFromHtml(it, results, seenUrls) }
                }
            }
        } catch (_: Exception) {}

        results.filter { Filters.matchesAudioFilter(it.audio, audio, it.title, cleanLang, it.language) }
    }

    private fun parseItemsFromHtml(html: String, results: MutableList<MediaItem>, seenUrls: MutableSet<String>) {
        val doc = org.jsoup.Jsoup.parse(html, baseUrl)
        doc.select(".film-list .item").forEach { el ->
            val a = el.selectFirst("a.name")
            val href = a?.attr("href")
            val title = a?.text()?.trim()
            val img = el.selectFirst("img")?.attr("src")

            if (!href.isNullOrBlank() && !title.isNullOrBlank()) {
                val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                if (seenUrls.add(fullUrl)) {
                    val isIta = title.contains("(ita)", ignoreCase = true) || el.select(".status .dub").isNotEmpty()
                    val fullImg = if (img?.startsWith("http") == true) img else if (!img.isNullOrBlank()) "$baseUrl$img" else null
                    results.add(
                        MediaItem(
                            id = href,
                            title = title,
                            url = fullUrl,
                            image = fullImg,
                            type = "anime",
                            source = id,
                            sourceName = name,
                            language = "it",
                            audio = if (isIta) "ita" else "subita"
                        )
                    )
                }
            }
        }
    }

    override suspend fun getEpisodes(animeUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<Episode>()
        try {
            val doc = NetworkClient.fetchDocument(animeUrl, baseUrl)
            val seenNums = mutableSetOf<String>()
            val activeServer = doc.selectFirst(".server.active") ?: doc.selectFirst(".server")

            val container = activeServer ?: doc
            container.select(".episode a").forEach { el ->
                val href = el.attr("href")
                val epNum = el.attr("data-episode-num").ifBlank { el.attr("data-num") }.ifBlank { el.text() }.trim()
                if (href.isNotBlank() && epNum.isNotBlank() && seenNums.add(epNum)) {
                    val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                    val numInt = epNum.toIntOrNull() ?: episodes.size + 1
                    episodes.add(
                        Episode(
                            id = epNum,
                            title = "Episode $epNum",
                            url = fullUrl,
                            number = numInt
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        episodes
    }

    override suspend fun getEpisodeStream(episodeUrl: String, preferredAudio: String?): StreamData = withContext(Dispatchers.IO) {
        try {
            val html = NetworkClient.fetchHtml(episodeUrl, baseUrl)
            val csrfMatch = Regex("""content="([^"]+)"[^>]+name="csrf-token"""", RegexOption.IGNORE_CASE).find(html)
                ?: Regex("""name="csrf-token"[^>]+content="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
            val csrf = csrfMatch?.groupValues?.get(1).orEmpty()

            val idMatch = Regex("""data-id="([a-zA-Z0-9_-]+)"[^>]+class="[^"]*player-box""", RegexOption.IGNORE_CASE).find(html)
                ?: Regex("""class="[^"]*player-box[^"]*"[^>]+data-id="([a-zA-Z0-9_-]+)"""", RegexOption.IGNORE_CASE).find(html)
                ?: Regex("""data-episode-id="([a-zA-Z0-9_-]+)"""", RegexOption.IGNORE_CASE).find(html)
            val dataId = idMatch?.groupValues?.get(1) ?: episodeUrl.substringAfterLast("/").substringBefore("?").substringBefore("#")

            val fallbackMp4 = Regex("""(?:href|src)="(https?://[^"]+\.mp4[^"]*)"""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)

            if (dataId.isNotBlank() && csrf.isNotBlank()) {
                val apiUrl = "$baseUrl/api/episode/info?id=$dataId"
                val jsonText = runCatching {
                    NetworkClient.fetchHtml(
                        apiUrl,
                        referer = episodeUrl,
                        headers = mapOf(
                            "csrf-token" to csrf,
                            "x-requested-with" to "XMLHttpRequest"
                        )
                    )
                }.getOrNull()

                if (!jsonText.isNullOrBlank()) {
                    val obj = JSONObject(jsonText)
                    val streamUrl = obj.optString("grabber").ifBlank { obj.optString("target") }.ifBlank { fallbackMp4 }
                    return@withContext StreamData(streamUrl = streamUrl, referer = "$baseUrl/")
                }
            }

            return@withContext StreamData(streamUrl = fallbackMp4, referer = "$baseUrl/")
        } catch (_: Exception) {
            StreamData(streamUrl = null, referer = "$baseUrl/")
        }
    }
}

