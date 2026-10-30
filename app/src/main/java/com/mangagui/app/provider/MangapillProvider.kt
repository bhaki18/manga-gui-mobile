package com.mangagui.app.provider

import com.mangagui.app.model.Chapter
import com.mangagui.app.model.ChapterPages
import com.mangagui.app.model.Filters
import com.mangagui.app.model.MediaItem
import com.mangagui.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class MangapillProvider : MangaProvider {
    override val id: String = "mangapill"
    override val name: String = "Mangapill"
    override val baseUrl: String = "https://mangapill.com"
    override val languages: List<String> = listOf("en", "all")

    private var catalogCache: List<MediaItem>? = null

    private fun parseMangaCards(doc: org.jsoup.nodes.Document, results: MutableList<MediaItem>, seenUrls: MutableSet<String>) {
        doc.select("a[href^=\"/manga/\"]").forEach { el ->
            val href = el.attr("href")
            if (href.isBlank() || !href.startsWith("/manga/")) return@forEach
            val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
            if (!seenUrls.add(fullUrl)) return@forEach

            var title = el.selectFirst(".font-black, .font-bold")?.text()?.trim().orEmpty()
            if (title.isBlank()) {
                val raw = el.text().trim()
                if (raw.isNotBlank() && !raw.startsWith("#")) {
                    title = raw.substringBefore("\n").trim()
                }
            }
            if (title.isBlank() || title.startsWith("#")) return@forEach

            val card = el.parent()
            val altTitle = el.selectFirst(".text-xs.text-secondary")?.text()?.trim().orEmpty()
                .ifBlank { card?.selectFirst(".text-xs.text-secondary")?.text()?.trim().orEmpty() }

            val badgeTexts = card?.select(".flex.flex-wrap div")?.map { it.text().trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val genres = if (badgeTexts.isNotEmpty()) badgeTexts.joinToString(", ") else altTitle

            val img = el.selectFirst("img")?.attr("data-src")?.ifBlank { el.selectFirst("img")?.attr("src") }

            results.add(
                MediaItem(
                    id = href,
                    title = title,
                    url = fullUrl,
                    image = img,
                    genres = genres,
                    source = id,
                    sourceName = name,
                    language = "en",
                    type = "manga"
                )
            )
        }
    }

    override suspend fun getCatalog(lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanLang = Filters.resolveLanguageCode(lang)
        if (cleanLang != "all" && cleanLang != "en") return@withContext emptyList()

        val genreKey = Filters.normalizeGenre(genre)
        val genreDef = Filters.GENRE_DEFINITIONS[genreKey]

        if (genreKey == "all" && !catalogCache.isNullOrEmpty()) {
            return@withContext catalogCache!!
        }

        val results = mutableListOf<MediaItem>()
        val seenUrls = mutableSetOf<String>()

        try {
            if (genreDef?.mpGenre != null) {
                val html = runCatching {
                    NetworkClient.fetchHtml("$baseUrl/search?q=&genre=${URLEncoder.encode(genreDef.mpGenre, "UTF-8")}", baseUrl)
                }.getOrNull()
                if (!html.isNullOrBlank()) {
                    parseMangaCards(org.jsoup.Jsoup.parse(html, baseUrl), results, seenUrls)
                }
                return@withContext results
            }

            val homeHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/", baseUrl) }.getOrNull()
            val newHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/mangas/new", baseUrl) }.getOrNull()

            homeHtml?.let { parseMangaCards(org.jsoup.Jsoup.parse(it, baseUrl), results, seenUrls) }
            newHtml?.let { parseMangaCards(org.jsoup.Jsoup.parse(it, baseUrl), results, seenUrls) }

            if (results.isNotEmpty()) {
                catalogCache = results
            }
        } catch (_: Exception) {}

        results
    }

    override suspend fun search(query: String, lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanLang = Filters.resolveLanguageCode(lang)
        if (cleanLang != "all" && cleanLang != "en") return@withContext emptyList()
        if (query.isBlank()) return@withContext getCatalog(lang, genre, audio)

        val results = mutableListOf<MediaItem>()
        val seenUrls = mutableSetOf<String>()

        try {
            val genreKey = Filters.normalizeGenre(genre)
            val genreDef = Filters.GENRE_DEFINITIONS[genreKey]
            val genreParam = if (genreDef?.mpGenre != null) "&genre=${URLEncoder.encode(genreDef.mpGenre, "UTF-8")}" else ""
            val cleanQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val html = NetworkClient.fetchHtml("$baseUrl/search?q=$cleanQuery$genreParam", baseUrl)
            parseMangaCards(org.jsoup.Jsoup.parse(html, baseUrl), results, seenUrls)

            results.sortWith { a, b ->
                val aNovel = if (a.title.contains("novel", ignoreCase = true)) 1 else 0
                val bNovel = if (b.title.contains("novel", ignoreCase = true)) 1 else 0
                aNovel.compareTo(bNovel)
            }
        } catch (_: Exception) {}

        results
    }

    override suspend fun getChapters(mangaUrl: String, lang: String?): List<Chapter> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<Chapter>()
        try {
            val html = NetworkClient.fetchHtml(mangaUrl, baseUrl)
            val doc = org.jsoup.Jsoup.parse(html, mangaUrl)

            doc.select("a[href*=\"/chapters/\"]").forEach { el ->
                val href = el.attr("href")
                val title = el.text().trim()
                if (href.isNotBlank()) {
                    val numMatch = Regex("""(?:chapter|ch\.?)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(title)
                        ?: Regex("""(\d+(?:\.\d+)?)""").find(title)
                    val chapNum = numMatch?.groupValues?.get(1) ?: href.substringAfter("/chapters/")
                    chapters.add(
                        Chapter(
                            id = chapNum,
                            number = chapNum,
                            title = "[EN] $title",
                            url = if (href.startsWith("http")) href else "$baseUrl$href"
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        chapters.reversed()
    }

    override suspend fun getChapterPages(chapterUrl: String): ChapterPages = withContext(Dispatchers.IO) {
        try {
            val html = NetworkClient.fetchHtml(chapterUrl, baseUrl)
            val doc = org.jsoup.Jsoup.parse(html, chapterUrl)
            val pages = mutableListOf<String>()

            doc.select("img.js-page").forEach { el ->
                val src = el.attr("data-src").ifBlank { el.attr("src") }
                if (src.isNotBlank()) pages.add(src)
            }

            val title = doc.title().replace(Regex(""" - Mangapill$"""), "").trim()
            ChapterPages(title = title, chapterTitle = title, pages = pages, referer = "https://mangapill.com/")
        } catch (_: Exception) {
            ChapterPages(pages = emptyList())
        }
    }
}

