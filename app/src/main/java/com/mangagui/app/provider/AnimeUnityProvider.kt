package com.mangagui.app.provider

import com.mangagui.app.model.Episode
import com.mangagui.app.model.Filters
import com.mangagui.app.model.MediaItem
import com.mangagui.app.model.StreamData
import com.mangagui.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class AnimeUnityProvider : AnimeProvider {
    override val id: String = "animeunity"
    override val name: String = "AnimeUnity"
    override val baseUrl: String = "https://www.animeunity.co"
    override val languages: List<String> = listOf("it")

    private var catalogCache: List<MediaItem>? = null

    private fun pushAnimeRecord(record: JSONObject, results: MutableList<MediaItem>, seenIds: MutableSet<String>) {
        val animeId = record.optString("id")
        if (animeId.isBlank() || !seenIds.add(animeId)) return

        val title = record.optString("title_eng").ifBlank { record.optString("title_it") }.ifBlank { record.optString("title") }.trim()
        if (title.isBlank()) return

        val slug = record.optString("slug").ifBlank {
            title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        }
        val fullUrl = "$baseUrl/anime/$animeId-$slug"
        val isIta = record.optInt("dub") == 1 || record.optInt("ita") == 1 || title.contains("(ita)", ignoreCase = true)

        val genresArray = record.optJSONArray("genres")
        val genres = if (genresArray != null) {
            val list = mutableListOf<String>()
            for (i in 0 until genresArray.length()) {
                val g = genresArray.optJSONObject(i)?.optString("name")
                if (!g.isNullOrBlank()) list.add(g)
            }
            list.joinToString(", ")
        } else ""

        val image = record.optString("image_url").ifBlank { record.optString("image") }
        val fullImg = if (image.startsWith("http")) image else if (image.isNotBlank()) "$baseUrl/$image" else null

        results.add(
            MediaItem(
                id = "$animeId-$slug",
                title = title,
                url = fullUrl,
                image = fullImg,
                type = "anime",
                source = id,
                sourceName = name,
                language = "it",
                audio = if (isIta) "ita" else "subita",
                genres = genres
            )
        )
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
        val seenIds = mutableSetOf<String>()

        try {
            if (hasFilters) {
                val filtered = fetchArchivePost(title = null, genreDef = genreDef, audioKey = audioKey)
                return@withContext filtered.filter { Filters.matchesAudioFilter(it.audio, audioKey, it.title) }
            }

            val topHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/top-anime?popular=true", baseUrl) }.getOrNull()
            val homeHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/", baseUrl) }.getOrNull()

            if (!topHtml.isNullOrBlank()) {
                val doc = org.jsoup.Jsoup.parse(topHtml, baseUrl)
                val rawTop = doc.selectFirst("top-anime")?.attr("animes")
                if (!rawTop.isNullOrBlank()) {
                    runCatching {
                        val parsed = JSONObject(rawTop).optJSONArray("data") ?: JSONArray(rawTop)
                        for (i in 0 until parsed.length()) {
                            parsed.optJSONObject(i)?.let { pushAnimeRecord(it, results, seenIds) }
                        }
                    }
                }
            }

            if (!homeHtml.isNullOrBlank()) {
                val doc = org.jsoup.Jsoup.parse(homeHtml, baseUrl)
                val rawHome = doc.selectFirst("layout-items")?.attr("items-json")
                if (!rawHome.isNullOrBlank()) {
                    runCatching {
                        val parsed = JSONArray(rawHome)
                        for (i in 0 until parsed.length()) {
                            parsed.optJSONObject(i)?.let { pushAnimeRecord(it, results, seenIds) }
                        }
                    }
                }
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
        val seenIds = mutableSetOf<String>()

        try {
            val genreKey = Filters.normalizeGenre(genre)
            val genreDef = Filters.GENRE_DEFINITIONS[genreKey]
            val audioKey = Filters.normalizeAudio(audio)
            val cleaned = query.replace(Regex("\\s*\\[[^\\]]*\\]\\s*"), " ").trim()

            val searchUrl = "$baseUrl/archivio?title=${URLEncoder.encode(cleaned, "UTF-8")}"
            val html = NetworkClient.fetchHtml(searchUrl, baseUrl)
            parseSearchHtml(html, results, seenIds)

            if (results.isEmpty() && cleaned.contains("(")) {
                val baseQuery = cleaned.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ").trim()
                if (baseQuery.length >= 2) {
                    val fallbackHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/archivio?title=${URLEncoder.encode(baseQuery, "UTF-8")}", baseUrl) }.getOrNull()
                    fallbackHtml?.let { parseSearchHtml(it, results, seenIds) }
                }
            }

            return@withContext results.filter {
                Filters.matchesAudioFilter(it.audio, audioKey, it.title) &&
                        (genreDef == null || Filters.matchesGenreText(it.genres, genre, it.title))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSearchHtml(html: String, results: MutableList<MediaItem>, seenIds: MutableSet<String>) {
        val doc = org.jsoup.Jsoup.parse(html, baseUrl)
        val rawRecords = doc.selectFirst("archivio")?.attr("records") ?: return
        runCatching {
            val recordsObj = runCatching { JSONObject(rawRecords) }.getOrNull()
            val list = recordsObj?.optJSONArray("data") ?: JSONArray(rawRecords)
            for (i in 0 until list.length()) {
                list.optJSONObject(i)?.let { pushAnimeRecord(it, results, seenIds) }
            }
        }
    }

    private suspend fun fetchArchivePost(title: String?, genreDef: com.mangagui.app.model.GenreDefinition?, audioKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MediaItem>()
        val seenIds = mutableSetOf<String>()

        try {
            val archiveHtml = NetworkClient.fetchHtml("$baseUrl/archivio", baseUrl)
            val csrfMatch = Regex("""name="csrf-token"\s+content="([^"]+)"""", RegexOption.IGNORE_CASE).find(archiveHtml)
                ?: Regex("""content="([^"]+)"\s+name="csrf-token"""", RegexOption.IGNORE_CASE).find(archiveHtml)
            val csrf = csrfMatch?.groupValues?.get(1).orEmpty()
            if (csrf.isBlank()) return@withContext emptyList()

            val payload = JSONObject().apply {
                put("title", title ?: false)
                put("type", false)
                put("year", false)
                put("order", "Popolarità")
                put("status", false)
                if (genreDef?.auGenreId != null && !genreDef.auGenreName.isNullOrBlank()) {
                    val gArr = JSONArray().put(JSONObject().apply {
                        put("id", genreDef.auGenreId)
                        put("name", genreDef.auGenreName)
                    })
                    put("genres", gArr)
                } else {
                    put("genres", false)
                }
                put("offset", 0)
                put("dubbed", audioKey == "dub" || audioKey == "ita")
                put("season", false)
            }

            val req = Request.Builder()
                .url("$baseUrl/archivio/get-animes")
                .header("User-Agent", NetworkClient.DEFAULT_USER_AGENT)
                .header("Content-Type", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-CSRF-TOKEN", csrf)
                .header("Referer", "$baseUrl/archivio")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            NetworkClient.client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                val dataObj = JSONObject(bodyStr)
                val recs = dataObj.optJSONArray("records") ?: return@withContext emptyList()
                for (i in 0 until recs.length()) {
                    recs.optJSONObject(i)?.let { pushAnimeRecord(it, results, seenIds) }
                }
            }
        } catch (_: Exception) {}

        results
    }

    override suspend fun getEpisodes(animeUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<Episode>()
        try {
            val html = NetworkClient.fetchHtml(animeUrl, baseUrl)
            val doc = org.jsoup.Jsoup.parse(html, animeUrl)
            val vp = doc.selectFirst("video-player") ?: return@withContext emptyList()

            val rawEpisodes = vp.attr("episodes")
            val totalCount = vp.attr("episodes_count").toIntOrNull() ?: 0
            val allEpisodes = mutableListOf<JSONObject>()

            if (rawEpisodes.isNotBlank()) {
                runCatching {
                    val arr = JSONArray(rawEpisodes)
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { allEpisodes.add(it) }
                    }
                }
            }

            val animeId = Regex("""/anime/(\d+)""", RegexOption.IGNORE_CASE).find(animeUrl)?.groupValues?.get(1)
            if (animeId != null && totalCount > allEpisodes.size) {
                var start = allEpisodes.size + 1
                while (start <= totalCount) {
                    val end = (start + 119).coerceAtMost(totalCount)
                    val chunkJson = runCatching {
                        NetworkClient.fetchHtml("$baseUrl/info_api/$animeId/1?start_range=$start&end_range=$end", animeUrl)
                    }.getOrNull()

                    if (!chunkJson.isNullOrBlank()) {
                        runCatching {
                            val chunkObj = JSONObject(chunkJson)
                            val chunkArr = chunkObj.optJSONArray("episodes")
                            if (chunkArr != null) {
                                for (i in 0 until chunkArr.length()) {
                                    chunkArr.optJSONObject(i)?.let { allEpisodes.add(it) }
                                }
                            }
                        }
                    }
                    start += 120
                }
            }

            val seenNums = mutableSetOf<String>()
            for (ep in allEpisodes) {
                val epId = ep.optString("id")
                if (epId.isBlank()) continue
                val epNum = ep.optString("number").ifBlank { (episodes.size + 1).toString() }.trim()
                if (seenNums.add(epNum)) {
                    val numInt = epNum.toDoubleOrNull()?.toInt() ?: episodes.size + 1
                    episodes.add(
                        Episode(
                            id = epNum,
                            title = "Episode $epNum",
                            url = "$baseUrl/embed-url/$epId",
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
            val embedEndpoint = if (episodeUrl.contains("/embed-url/")) episodeUrl else "$baseUrl/embed-url/${episodeUrl.substringAfterLast("/")}"
            val vixEmbedUrl = NetworkClient.fetchHtml(embedEndpoint, baseUrl).trim()
            if (!vixEmbedUrl.startsWith("http")) return@withContext StreamData(streamUrl = null)

            val vixHtml = NetworkClient.fetchHtml(vixEmbedUrl, "$baseUrl/")

            // 1. Prefer direct 1080p MP4 downloadUrl when provided by VixCloud
            val dlMatch = Regex("""window\.downloadUrl\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(vixHtml)
            if (dlMatch != null) {
                return@withContext StreamData(streamUrl = dlMatch.groupValues[1], referer = "https://vixcloud.cc/")
            }

            // 2. Fallback to VixCloud master HLS playlist (.m3u8)
            val urlMatch = Regex("""url:\s*['"](https?://[^'"]+/playlist/\d+[^'"]*)['"]""", RegexOption.IGNORE_CASE).find(vixHtml)
            val tokenMatch = Regex("""['"]token['"]\s*:\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(vixHtml)
            val expiresMatch = Regex("""['"]expires['"]\s*:\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(vixHtml)

            if (urlMatch != null && tokenMatch != null && expiresMatch != null) {
                val basePlaylist = urlMatch.groupValues[1]
                val sep = if (basePlaylist.contains("?")) "&" else "?"
                val streamUrl = "$basePlaylist${sep}token=${URLEncoder.encode(tokenMatch.groupValues[1], "UTF-8")}&expires=${URLEncoder.encode(expiresMatch.groupValues[1], "UTF-8")}&h=1"
                return@withContext StreamData(streamUrl = streamUrl, referer = "https://vixcloud.cc/")
            }

            StreamData(streamUrl = null)
        } catch (_: Exception) {
            StreamData(streamUrl = null)
        }
    }
}

