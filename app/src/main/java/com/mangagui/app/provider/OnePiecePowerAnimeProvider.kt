package com.mangagui.app.provider

import com.mangagui.app.model.Episode
import com.mangagui.app.model.Filters
import com.mangagui.app.model.MediaItem
import com.mangagui.app.model.StreamData
import com.mangagui.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class OnePiecePowerAnimeProvider : AnimeProvider {
    override val id: String = "onepiecepower-anime"
    override val name: String = "One Piece Power Anime"
    override val baseUrl: String = "https://onepiecepower.com"
    override val languages: List<String> = listOf("it", "en")

    private var animeListCache: List<MediaItem>? = null

    private val popularKeywords = listOf(
        "one piece", "naruto", "bleach", "dragon ball", "attack on titan",
        "shingeki no kyojin", "jujutsu kaisen", "demon slayer", "kimetsu no yaiba",
        "chainsaw man", "solo leveling", "my hero academia", "hunter x hunter",
        "death note", "fullmetal alchemist", "black clover", "tokyo ghoul",
        "sword art online", "code geass", "steins", "evangelion", "cowboy bebop",
        "frieren", "dandadan", "blue lock", "vinland saga", "spy x family",
        "one punch man", "mob psycho", "dr. stone", "fire force", "fairy tail"
    )

    private suspend fun getAllAnime(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!animeListCache.isNullOrEmpty()) return@withContext animeListCache!!

        val list = mutableListOf<MediaItem>()
        list.add(
            MediaItem(
                id = "onepiece-subita",
                title = "One Piece (Sub ITA)",
                url = "$baseUrl/anime18/onepiece/subita2/lista-episodi",
                language = "it",
                audio = "subita",
                source = id,
                sourceName = name
            )
        )
        list.add(
            MediaItem(
                id = "onepiece-ita",
                title = "One Piece (ITA Doppiato)",
                url = "$baseUrl/anime18/onepiece/ita3/lista-episodi",
                language = "it",
                audio = "ita",
                source = id,
                sourceName = name
            )
        )
        list.add(
            MediaItem(
                id = "onepiece-eng",
                title = "One Piece (English Sub)",
                url = "$baseUrl/anime18/one-piece-eng/episode-list",
                language = "en",
                audio = "sub",
                source = id,
                sourceName = name
            )
        )
        list.add(
            MediaItem(
                id = "onepiece-movie",
                title = "One Piece Movie / Film",
                url = "$baseUrl/anime18/one-piece-movie/episodi/lista-movie",
                language = "it",
                audio = "both",
                source = id,
                sourceName = name
            )
        )
        list.add(
            MediaItem(
                id = "onepiece-special",
                title = "One Piece Special",
                url = "$baseUrl/anime18/onepiece/special/lista-special",
                language = "it",
                audio = "both",
                source = id,
                sourceName = name
            )
        )

        val seenUrls = list.mapNotNull { it.url }.toMutableSet()

        try {
            val html = NetworkClient.fetchHtml("$baseUrl/anime18/lista-anime2", baseUrl)
            val doc = org.jsoup.Jsoup.parse(html, baseUrl)
            doc.select("a").forEach { el ->
                val href = el.attr("href")
                val text = el.text().trim()
                if (href.isBlank() || text.isBlank()) return@forEach
                if (href.contains("lista-anime") || href.contains("login") || href.contains("vip") ||
                    href.contains("guide") || href.contains("amazon") || href.contains("telegram") ||
                    href.contains("play.google") || href.startsWith("mailto:") || href.startsWith("javascript:")) return@forEach

                if (href.contains("episodi") || href.contains("lista") || href.contains("movie") || href.contains("special")) {
                    if (text.contains("one piece", ignoreCase = true)) return@forEach

                    val fullUrl = if (href.startsWith("http")) href else "$baseUrl/anime18/${href.replace(Regex("^(\\.\\./)+"), "")}"
                    if (seenUrls.add(fullUrl)) {
                        val isIta = text.contains("ITA", ignoreCase = true)
                        val isSub = text.contains("SUB", ignoreCase = true)
                        val audio = when {
                            isSub && !text.contains("ITA/") -> "subita"
                            isIta && !isSub -> "ita"
                            else -> "both"
                        }
                        list.add(
                            MediaItem(
                                id = fullUrl,
                                title = text,
                                url = fullUrl,
                                language = "it",
                                audio = audio,
                                source = id,
                                sourceName = name
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        animeListCache = list
        list
    }

    override suspend fun getCatalog(lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val all = getAllAnime()
        val genreKey = Filters.normalizeGenre(genre)
        val audioKey = Filters.normalizeAudio(audio)
        val cleanLang = Filters.resolveLanguageCode(lang)

        val filtered = all.filter { a ->
            if (cleanLang != "all" && a.language != cleanLang) return@filter false
            if (!Filters.matchesAudioFilter(a.audio, audioKey, a.title, cleanLang, a.language)) return@filter false
            if (genreKey != "all" && !Filters.matchesGenreText(a.genres ?: "Azione, Avventura, Shounen", genre, a.title)) return@filter false
            true
        }

        val popular = mutableListOf<MediaItem>()
        val rest = mutableListOf<MediaItem>()
        for (item in filtered) {
            val t = item.title.lowercase()
            if (popularKeywords.any { t.contains(it) }) popular.add(item) else rest.add(item)
        }
        popular + rest
    }

    override suspend fun search(query: String, lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext getCatalog(lang, genre, audio)
        val catalog = getAllAnime()
        val genreKey = Filters.normalizeGenre(genre)
        val audioKey = Filters.normalizeAudio(audio)
        val cleanLang = Filters.resolveLanguageCode(lang)
        val clean = query.replace(Regex("\\s*\\[[^\\]]*\\]\\s*"), " ").lowercase().trim()
        val cleanNorm = clean.replace(Regex("[^a-z0-9]"), "")

        catalog.filter { a ->
            if (cleanLang != "all" && a.language != cleanLang) return@filter false
            if (!Filters.matchesAudioFilter(a.audio, audioKey, a.title, cleanLang, a.language)) return@filter false
            if (genreKey != "all" && !Filters.matchesGenreText(a.genres ?: "Azione, Avventura, Shounen", genre, a.title)) return@filter false
            if (clean.isBlank()) return@filter true
            val titleLower = a.title.lowercase()
            if (titleLower.contains(clean)) return@filter true
            if (cleanNorm.length >= 3 && titleLower.replace(Regex("[^a-z0-9]"), "").contains(cleanNorm)) return@filter true
            false
        }
    }

    override suspend fun getEpisodes(animeUrl: String): List<Episode> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<Episode>()
        try {
            val html = NetworkClient.fetchHtml(animeUrl, baseUrl)
            val doc = org.jsoup.Jsoup.parse(html, animeUrl)

            val dirUrl = animeUrl.substringBeforeLast("/")
            doc.select("table tr").forEach { row ->
                val link = row.selectFirst("a[href*=\"pagine/\"]")
                if (link != null) {
                    val rawHref = link.attr("href")
                    val fullUrl = if (rawHref.startsWith("http")) rawHref else "$dirUrl/${rawHref.replace(Regex("^\\./"), "")}"
                    var title = row.text().replace(Regex("\\s+"), " ").trim()
                    val epMatch = Regex("""pagine/(?:[^/]+/)?(\d+)""").find(fullUrl)
                    val epNum = epMatch?.groupValues?.get(1).orEmpty()
                    if (title.isBlank() && epNum.isNotBlank()) {
                        title = "Episodio ${epNum.toIntOrNull() ?: epNum}"
                    }
                    val numInt = epNum.toIntOrNull() ?: episodes.size + 1
                    episodes.add(
                        Episode(
                            id = epNum.ifBlank { fullUrl },
                            title = title,
                            url = fullUrl,
                            number = numInt
                        )
                    )
                }
            }

            if (episodes.isEmpty()) {
                doc.select("a[href*=\"pagine/\"]").forEach { link ->
                    val rawHref = link.attr("href")
                    val fullUrl = if (rawHref.startsWith("http")) rawHref else "$dirUrl/${rawHref.replace(Regex("^\\./"), "")}"
                    var title = link.text().trim().ifBlank { link.parent()?.text()?.replace(Regex("\\s+"), " ")?.trim().orEmpty() }
                    val epMatch = Regex("""pagine/(?:[^/]+/)?(\d+)""").find(fullUrl)
                    val epNum = epMatch?.groupValues?.get(1).orEmpty()
                    if (title.isBlank() && epNum.isNotBlank()) {
                        title = "Episodio ${epNum.toIntOrNull() ?: epNum}"
                    }
                    val numInt = epNum.toIntOrNull() ?: episodes.size + 1
                    episodes.add(
                        Episode(
                            id = epNum.ifBlank { fullUrl },
                            title = title,
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
            var direct: String? = null
            var subita: String? = null
            var ita: String? = null

            // 1. ld+json
            val doc = org.jsoup.Jsoup.parse(html, episodeUrl)
            doc.select("script[type=\"application/ld+json\"]").forEach { el ->
                runCatching {
                    val data = JSONObject(el.html())
                    val u = data.optString("contentUrl").ifBlank { data.optString("embedUrl") }
                    if (u.contains(".mp4")) {
                        direct = u
                        if (u.contains("/subita/")) subita = u
                        if (u.contains("/ita/")) ita = u
                    }
                }
            }

            // 2. scripts
            doc.select("script").forEach { el ->
                val s = el.html()
                val concatMatch = Regex("""['"](https?://(?:serverfile|stream)[^'"]+)['"]\s*\+\s*type\s*\+\s*['"]([^'"]+)['"]""").find(s)
                if (concatMatch != null) {
                    ita = concatMatch.groupValues[1] + "ita" + concatMatch.groupValues[2]
                    subita = concatMatch.groupValues[1] + "subita" + concatMatch.groupValues[2]
                }
                val vMatch = Regex("""videoUrl\s*:\s*["']([^"']+\.mp4[^"']*)["']""").find(s)
                if (vMatch != null) {
                    val u = vMatch.groupValues[1]
                    direct = u
                    if (u.contains("/subita/")) subita = u
                    if (u.contains("/ita/")) ita = u
                }
                if (direct == null && ita == null && subita == null) {
                    val sfc = Regex("""["'](https?://serverfile\.club/[^"']+\.mp4)["']""").find(s)?.groupValues?.get(1)
                    if (sfc != null) {
                        direct = sfc
                        if (sfc.contains("/subita/")) subita = sfc
                        if (sfc.contains("/ita/")) ita = sfc
                    }
                }
            }

            if (direct == null && ita == null && subita == null) {
                val vid = doc.selectFirst("video source")?.attr("src") ?: doc.selectFirst("video")?.attr("src")
                if (!vid.isNullOrBlank()) {
                    direct = vid
                    if (vid.contains("/subita/")) subita = vid
                    if (vid.contains("/ita/")) ita = vid
                }
            }

            val streamUrl = when (preferredAudio?.lowercase()) {
                "ita", "dub" -> ita ?: subita ?: direct
                else -> subita ?: ita ?: direct
            }

            StreamData(streamUrl = streamUrl, referer = "$baseUrl/")
        } catch (_: Exception) {
            StreamData(streamUrl = null, referer = "$baseUrl/")
        }
    }
}

