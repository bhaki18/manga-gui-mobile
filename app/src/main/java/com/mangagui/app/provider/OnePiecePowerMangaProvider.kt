package com.mangagui.app.provider

import com.mangagui.app.model.Chapter
import com.mangagui.app.model.ChapterPages
import com.mangagui.app.model.Filters
import com.mangagui.app.model.MediaItem
import com.mangagui.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnePiecePowerMangaProvider : MangaProvider {
    override val id: String = "onepiecepower"
    override val name: String = "One Piece Power"
    override val baseUrl: String = "https://onepiecepower.com"
    override val languages: List<String> = listOf("it", "en")

    private var mangaListCache: List<MediaItem>? = null

    private val popularKeywords = listOf(
        "one piece", "berserk", "naruto", "bleach", "jujutsu kaisen",
        "chainsaw man", "my hero academia", "dragon ball", "demon slayer",
        "hunter x hunter", "attack on titan", "black clover", "fairy tail",
        "tokyo ghoul", "solo leveling", "blue lock", "dandadan"
    )

    private suspend fun getAllManga(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!mangaListCache.isNullOrEmpty()) return@withContext mangaListCache!!

        val mangaList = mutableListOf<MediaItem>()
        mangaList.add(
            MediaItem(
                id = "onepiece-it",
                title = "One Piece",
                url = "$baseUrl/manga8/onepiece/lista-capitoli",
                author = "Eiichiro Oda",
                genres = "Avventura, Shounen, Azione",
                source = id,
                sourceName = name,
                language = "it"
            )
        )
        mangaList.add(
            MediaItem(
                id = "onepiece-en",
                title = "One Piece (English Edition)",
                url = "$baseUrl/manga8/one-piece-eng/chapter-list",
                author = "Eiichiro Oda",
                genres = "Adventure, Shounen, Action",
                source = id,
                sourceName = name,
                language = "en"
            )
        )

        val seenUrls = mangaList.mapNotNull { it.url }.toMutableSet()

        try {
            val html = NetworkClient.fetchHtml("$baseUrl/manga8/lista-manga3", baseUrl)
            val doc = org.jsoup.Jsoup.parse(html, baseUrl)
            doc.select("a").forEach { el ->
                val href = el.attr("href")
                if (href.contains("lista-capitoli")) {
                    val title = el.text().trim()
                    val author = el.attr("data-author")
                    val genres = el.attr("data-genres")
                    if (title.isBlank()) return@forEach

                    val fullUrl = if (href.startsWith("http")) href else {
                        if (href.startsWith("/")) "$baseUrl$href" else "$baseUrl/manga8/$href"
                    }
                    if (seenUrls.add(fullUrl)) {
                        mangaList.add(
                            MediaItem(
                                id = fullUrl,
                                title = title,
                                url = fullUrl,
                                author = author,
                                genres = genres,
                                source = id,
                                sourceName = name,
                                language = "it"
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        mangaListCache = mangaList
        mangaList
    }

    override suspend fun getCatalog(lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val all = getAllManga()
        val genreKey = Filters.normalizeGenre(genre)
        val cleanLang = Filters.resolveLanguageCode(lang)

        val filtered = all.filter { m ->
            if (cleanLang != "all" && m.language != cleanLang) return@filter false
            if (genreKey != "all" && !Filters.matchesGenreText(m.genres, genre, m.title)) return@filter false
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
        val all = getAllManga()
        val genreKey = Filters.normalizeGenre(genre)
        val cleanLang = Filters.resolveLanguageCode(lang)
        val clean = query.replace(Regex("\\s*\\[[^\\]]*\\]\\s*"), " ").lowercase().trim()
        val cleanNorm = clean.replace(Regex("[^a-z0-9]"), "")

        all.filter { m ->
            if (cleanLang != "all" && m.language != cleanLang) return@filter false
            if (genreKey != "all" && !Filters.matchesGenreText(m.genres, genre, m.title)) return@filter false
            if (clean.isBlank()) return@filter true
            val titleLower = m.title.lowercase()
            if (titleLower.contains(clean)) return@filter true
            if (cleanNorm.length >= 3 && titleLower.replace(Regex("[^a-z0-9]"), "").contains(cleanNorm)) return@filter true
            false
        }
    }

    override suspend fun getChapters(mangaUrl: String, lang: String?): List<Chapter> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<Chapter>()
        try {
            val html = NetworkClient.fetchHtml(mangaUrl, baseUrl)
            val doc = org.jsoup.Jsoup.parse(html, mangaUrl)
            val seenUrls = mutableSetOf<String>()

            doc.select("a[href*=\"reader/\"]").forEach { el ->
                val href = el.attr("href")
                val title = el.text().replace(Regex("\\s+"), " ").trim()
                val fullUrl = if (href.startsWith("http")) href else {
                    if (href.startsWith("/")) "$baseUrl$href" else {
                        val cleanMangaUrl = mangaUrl.replace(Regex("/(lista-capitoli|chapter-list).*$"), "")
                        "$cleanMangaUrl/${href.replace(Regex("^\\./"), "")}"
                    }
                }
                val capId = Regex("""reader/([^/?#]+)""").find(href)?.groupValues?.get(1).orEmpty()
                if (capId.isNotBlank() && seenUrls.add(fullUrl)) {
                    chapters.add(
                        Chapter(
                            id = capId,
                            title = title.ifBlank { "Chapter $capId" },
                            number = capId.replace(Regex("^0+"), "").ifBlank { "0" },
                            url = fullUrl
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        chapters
    }

    override suspend fun getChapterPages(chapterUrl: String): ChapterPages = withContext(Dispatchers.IO) {
        try {
            val html = NetworkClient.fetchHtml(chapterUrl, baseUrl)
            val doc = org.jsoup.Jsoup.parse(html, chapterUrl)
            var scriptContent = ""
            doc.select("script").forEach { el ->
                val text = el.html()
                if (text.contains("getPageLink")) scriptContent = text
            }

            val cleanUrl = chapterUrl.substringBefore("?")
            val baseReaderUrl = cleanUrl.substringBefore("reader/")
            val rawCap = cleanUrl.substringAfter("reader/").substringBefore("/").substringBefore(".php")

            val opVolMatch = Regex("""const\s+vol\s*=\s*["']([^"']+)["']""").find(scriptContent)?.groupValues?.get(1)
            val stdVolMatch = Regex("""const\s+volText\s*=\s*["']([^"']+)["']""").find(scriptContent)?.groupValues?.get(1)

            val generator: (Int) -> String = when {
                opVolMatch != null -> {
                    var rawVol = opVolMatch
                    if (!rawVol.endsWith("/")) rawVol += "/"
                    val capWithSlash = if (rawCap.endsWith("/")) rawCap else "$rawCap/"
                    val capNum = if (capWithSlash.contains("-")) capWithSlash.replace(Regex("^0"), "") else {
                        val n = capWithSlash.trimEnd('/').toIntOrNull() ?: 1
                        if (n < 10) String.format("%03d/", n) else capWithSlash
                    }
                    { pageNum ->
                        val p = if (pageNum < 10) "0$pageNum" else "$pageNum"
                        "${baseReaderUrl}volume${rawVol}${capNum}${p}.jpg"
                    }
                }
                stdVolMatch != null -> {
                    val vol = stdVolMatch
                    val formattedCap = if (rawCap.contains("-")) rawCap.replace(Regex("^0"), "") else {
                        val num = rawCap.toDoubleOrNull() ?: 1.0
                        if (num < 100 && rawCap.startsWith("0")) rawCap.substring(1) else rawCap
                    }
                    { pageNum ->
                        val p = if (pageNum < 10) "0$pageNum" else "$pageNum"
                        "${baseReaderUrl}volume$vol/capitolo$formattedCap/$p.jpg"
                    }
                }
                else -> { pageNum ->
                    val p = if (pageNum < 10) "0$pageNum" else "$pageNum"
                    "${baseReaderUrl}volume$rawCap/capitolo$rawCap/$p.jpg"
                }
            }

            // Scopriamo fino a 60 pagine
            val pages = mutableListOf<String>()
            for (p in 1..70) {
                pages.add(generator(p))
            }

            val title = doc.title().substringBefore("|").trim().ifBlank { "Chapter $rawCap" }
            ChapterPages(title = title, chapterTitle = title, pages = pages, referer = "$baseUrl/")
        } catch (_: Exception) {
            ChapterPages(pages = emptyList())
        }
    }
}

