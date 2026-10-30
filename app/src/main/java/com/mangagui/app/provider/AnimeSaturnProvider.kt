package com.mangagui.app.provider

import android.util.Base64
import com.mangagui.app.model.Episode
import com.mangagui.app.model.Filters
import com.mangagui.app.model.MediaItem
import com.mangagui.app.model.StreamData
import com.mangagui.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

class AnimeSaturnProvider : AnimeProvider {
    override val id: String = "animesaturn"
    override val name: String = "AnimeSaturn"
    override val baseUrl: String = "https://www.animesaturn.net"
    override val languages: List<String> = listOf("it")

    private var catalogCache: List<MediaItem>? = null

    private fun decodeSaturnStream(b64: String?, key: String?): String {
        if (b64.isNullOrBlank()) return ""
        return try {
            val decodedBytes = Base64.decode(b64, Base64.DEFAULT)
            val k = if (!key.isNullOrBlank()) key else "as"
            val kBytes = k.toByteArray(Charsets.ISO_8859_1)
            val out = CharArray(decodedBytes.size)
            for (i in decodedBytes.indices) {
                val b = decodedBytes[i].toInt() and 0xFF
                val kb = kBytes[i % kBytes.size].toInt() and 0xFF
                out[i] = (b xor kb).toChar()
            }
            String(out)
        } catch (_: Exception) {
            ""
        }
    }

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
                val catParam = if (genreDef != null) "&categories%5B%5D=${genreDef.awId}" else ""
                val dubParam = if (audioKey == "dub" || audioKey == "ita") "&dub=1" else if (audioKey == "sub" || audioKey == "subita") "&dub=0" else ""
                val mostViewed = runCatching { NetworkClient.fetchHtml("$baseUrl/filter?sort=most_viewed$catParam$dubParam", baseUrl) }.getOrNull()
                val recent = runCatching { NetworkClient.fetchHtml("$baseUrl/filter?sort=recent$catParam$dubParam", baseUrl) }.getOrNull()
                mostViewed?.let { parseItemsFromHtml(it, results, seenUrls) }
                recent?.let { parseItemsFromHtml(it, results, seenUrls) }
                return@withContext results.filter { Filters.matchesAudioFilter(it.audio, audioKey, it.title, cleanLang, it.language) }
            }

            val topHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/toplist", baseUrl) }.getOrNull()
            val ongoingHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/ongoing", baseUrl) }.getOrNull()
            topHtml?.let { parseItemsFromHtml(it, results, seenUrls) }
            ongoingHtml?.let { parseItemsFromHtml(it, results, seenUrls) }

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
            val catParam = if (genreDef != null) "&categories%5B%5D=${genreDef.awId}" else ""
            val dubParam = if (audioKey == "dub" || audioKey == "ita") "&dub=1" else if (audioKey == "sub" || audioKey == "subita") "&dub=0" else ""

            val cleaned = query.replace(Regex("\\s*\\[[^\\]]*\\]\\s*"), " ").trim()
            val searchUrl = "$baseUrl/filter?key=${URLEncoder.encode(cleaned, "UTF-8")}$catParam$dubParam"
            val html = NetworkClient.fetchHtml(searchUrl, baseUrl)
            parseItemsFromHtml(html, results, seenUrls)

            if (results.isEmpty() && cleaned.contains("(")) {
                val baseQuery = cleaned.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ").trim()
                if (baseQuery.length >= 2) {
                    val fallbackHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/filter?key=${URLEncoder.encode(baseQuery, "UTF-8")}$catParam$dubParam", baseUrl) }.getOrNull()
                    fallbackHtml?.let { parseItemsFromHtml(it, results, seenUrls) }
                }
            }
        } catch (_: Exception) {}

        results.filter { Filters.matchesAudioFilter(it.audio, audio, it.title, cleanLang, it.language) }
    }

    private fun parseItemsFromHtml(html: String, results: MutableList<MediaItem>, seenUrls: MutableSet<String>) {
        val doc: Document = Jsoup.parse(html, baseUrl)
        doc.select("a[href^=\"/anime/\"]").forEach { el ->
            val href = el.attr("href")
            if (href.contains("/ep-")) return@forEach
            val img = el.selectFirst("img") ?: return@forEach

            val title = (img.attr("alt").ifBlank { el.attr("title") }.ifBlank { el.selectFirst("h3, h4, .title")?.text().orEmpty() }).trim()
            if (title.isBlank() || title.equals("dettagli", ignoreCase = true)) return@forEach

            val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
            if (seenUrls.add(fullUrl)) {
                val cardText = el.text().uppercase()
                val isIta = title.contains("(ita)", ignoreCase = true) || href.contains("-ita-", ignoreCase = true) || cardText.contains("DUB")
                val imgSrc = img.attr("src")
                val fullImg = if (imgSrc.startsWith("http")) imgSrc else if (imgSrc.isNotBlank()) "$baseUrl$imgSrc" else null

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

    override suspend fun getEpisodes(animeUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<Episode>()
        try {
            val doc = NetworkClient.fetchDocument(animeUrl, baseUrl)
            val seenNums = mutableSetOf<String>()

            doc.select("a[href*=\"/ep-\"]").forEach { el ->
                val href = el.attr("href")
                if (href.isBlank()) return@forEach
                val text = el.text().replace(Regex("\\s+"), " ").trim()
                if (text.startsWith("guarda ", ignoreCase = true)) return@forEach

                val epMatch = Regex("""/ep-(\d+(?:[.-]\d+)?)""", RegexOption.IGNORE_CASE).find(href)
                val epNum = epMatch?.groupValues?.get(1)?.replace("-", ".") ?: text
                if (epNum.isBlank() || !seenNums.add(epNum)) return@forEach

                val watchPath = href.replaceFirst(Regex("^/episode/", RegexOption.IGNORE_CASE), "/anime/")
                val fullUrl = if (watchPath.startsWith("http")) watchPath else "$baseUrl$watchPath"
                val numInt = epNum.toDoubleOrNull()?.toInt() ?: episodes.size + 1

                episodes.add(
                    Episode(
                        id = epNum,
                        title = "Episodio $epNum",
                        url = fullUrl,
                        number = numInt
                    )
                )
            }
        } catch (_: Exception) {}
        episodes
    }

    override suspend fun getEpisodeStream(episodeUrl: String, preferredAudio: String?): StreamData = withContext(Dispatchers.IO) {
        try {
            val watchUrl = episodeUrl.replace(Regex("/episode/", RegexOption.IGNORE_CASE), "/anime/")
            val html = NetworkClient.fetchHtml(watchUrl, baseUrl)
            val doc: Document = Jsoup.parse(html, watchUrl)

            val iframeSrc = doc.select("iframe")
                .map { it.attr("src").ifBlank { it.attr("data-src") } }
                .firstOrNull { it.contains("saturncdn.net") }

            if (!iframeSrc.isNullOrBlank()) {
                val fullIframeUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
                val uri = fullIframeUrl.toHttpUrlOrNull()
                val token = uri?.queryParameter("token").orEmpty()
                val playlistUrl = "${uri?.scheme}://${uri?.host}${uri?.encodedPath}/playlist?${uri?.query}"

                val jsonText = NetworkClient.fetchHtml(playlistUrl, fullIframeUrl)
                val data = JSONObject(jsonText)
                val d = data.optString("d")
                val streamUrl = decodeSaturnStream(d, token)
                if (streamUrl.isNotBlank() && !streamUrl.startsWith("youtube/")) {
                    return@withContext StreamData(streamUrl = streamUrl, referer = "https://play.saturncdn.net/")
                }
            }

            val directMatch = Regex("""https?://[^\s"'<>]+(?:\.mp4|\.m3u8)[^\s"'<>]*""", RegexOption.IGNORE_CASE).find(html)?.value
            return@withContext StreamData(streamUrl = directMatch, referer = baseUrl)
        } catch (_: Exception) {
            StreamData(streamUrl = null, referer = baseUrl)
        }
    }
}

