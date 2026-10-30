package com.mangagui.app.provider

import com.mangagui.app.model.Chapter
import com.mangagui.app.model.ChapterPages
import com.mangagui.app.model.Filters
import com.mangagui.app.model.MediaItem
import com.mangagui.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class MangaWorldProvider : MangaProvider {
    override val id: String = "mangaworld"
    override val name: String = "MangaWorld"
    override val baseUrl: String = "https://www.mangaworld.ac"
    override val languages: List<String> = listOf("it")

    private var catalogCache: List<MediaItem>? = null

    private fun parseEntriesFromHtml(html: String, results: MutableList<MediaItem>, seenUrls: MutableSet<String>) {
        val doc = org.jsoup.Jsoup.parse(html, baseUrl)
        doc.select(".comics-grid .entry").forEach { el ->
            val a = el.selectFirst("a.manga-title, .name a, a.thumb")
            val titleEl = el.selectFirst("a.manga-title, .manga-title")
            val href = a?.attr("href") ?: titleEl?.attr("href")
            val title = (titleEl?.text() ?: a?.attr("title") ?: "").trim()
            if (href.isNullOrBlank() || title.isBlank()) return@forEach

            val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
            val pathKey = fullUrl.replace(Regex("^https?://[^/]+", RegexOption.IGNORE_CASE), "")
            if (!seenUrls.add(pathKey)) return@forEach

            val author = el.selectFirst(".author a, .author")?.text()?.replace(Regex("^Autore:\\s*", RegexOption.IGNORE_CASE), "")?.trim().orEmpty()
            val genres = el.select(".genres a").map { it.text().trim() }.filter { it.isNotBlank() }.joinToString(", ")
            val img = el.selectFirst("img")?.attr("src")
            val fullImg = if (img?.startsWith("http") == true) img else if (!img.isNullOrBlank()) "$baseUrl$img" else null

            results.add(
                MediaItem(
                    id = pathKey,
                    title = title,
                    url = fullUrl,
                    image = fullImg,
                    author = author,
                    genres = genres,
                    language = "it",
                    source = id,
                    sourceName = name,
                    type = "manga"
                )
            )
        }
    }

    override suspend fun getCatalog(lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanLang = Filters.resolveLanguageCode(lang)
        if (cleanLang != "all" && cleanLang != "it") return@withContext emptyList()

        val genreKey = Filters.normalizeGenre(genre)
        val genreDef = Filters.GENRE_DEFINITIONS[genreKey]

        if (genreKey == "all" && !catalogCache.isNullOrEmpty()) {
            return@withContext catalogCache!!
        }

        val results = mutableListOf<MediaItem>()
        val seenUrls = mutableSetOf<String>()

        try {
            if (genreDef?.mwSlug != null) {
                val slug = URLEncoder.encode(genreDef.mwSlug, "UTF-8")
                val p1 = runCatching { NetworkClient.fetchHtml("$baseUrl/archive?sort=most_read&genre=$slug", baseUrl) }.getOrNull()
                val p2 = runCatching { NetworkClient.fetchHtml("$baseUrl/archive?sort=newest&genre=$slug", baseUrl) }.getOrNull()
                p1?.let { parseEntriesFromHtml(it, results, seenUrls) }
                p2?.let { parseEntriesFromHtml(it, results, seenUrls) }
                return@withContext results
            }

            val popularHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/archive?sort=most_read", baseUrl) }.getOrNull()
            val homeHtml = runCatching { NetworkClient.fetchHtml("$baseUrl/", baseUrl) }.getOrNull()
            popularHtml?.let { parseEntriesFromHtml(it, results, seenUrls) }
            homeHtml?.let { parseEntriesFromHtml(it, results, seenUrls) }

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
            val genreParam = if (genreDef?.mwSlug != null) "&genre=${URLEncoder.encode(genreDef.mwSlug, "UTF-8")}" else ""
            val searchUrl = "$baseUrl/archive?keyword=${URLEncoder.encode(query.trim(), "UTF-8")}$genreParam"
            val html = NetworkClient.fetchHtml(searchUrl, baseUrl)
            parseEntriesFromHtml(html, results, seenUrls)

            return@withContext if (genreDef != null) {
                results.filter { Filters.matchesGenreText(it.genres, genre, it.title) }
            } else results
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getChapters(mangaUrl: String, lang: String?): List<Chapter> = withContext(Dispatchers.IO) {
        val chapters = mutableListOf<Chapter>()
        try {
            val html = NetworkClient.fetchHtml(mangaUrl, baseUrl)
            val doc = org.jsoup.Jsoup.parse(html, mangaUrl)
            val seenUrls = mutableSetOf<String>()

            doc.select(".chapters-wrapper .chapter a.chap").forEach { el ->
                val href = el.attr("href")
                if (href.isBlank()) return@forEach
                val cleanHref = href.substringBefore("?")
                if (!seenUrls.add(cleanHref)) return@forEach

                val spanText = el.selectFirst("span")?.text()?.trim()
                val rawTitle = spanText ?: el.attr("title").ifBlank { el.text() }.trim()
                val numMatch = Regex("""(?:capitolo|cap\.?|chapter|ch\.?)\s*0*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(rawTitle)
                    ?: Regex("""0*(\d+(?:\.\d+)?)""").find(rawTitle)
                val chapNum = numMatch?.groupValues?.get(1) ?: cleanHref.substringAfterLast("/")

                chapters.add(
                    Chapter(
                        id = chapNum,
                        number = chapNum,
                        title = "[IT] ${spanText ?: "Capitolo $chapNum"}",
                        url = if (cleanHref.startsWith("http")) cleanHref else "$baseUrl$cleanHref"
                    )
                )
            }
        } catch (_: Exception) {}
        chapters.reversed()
    }

    override suspend fun getChapterPages(chapterUrl: String): ChapterPages = withContext(Dispatchers.IO) {
        try {
            val baseChapUrl = chapterUrl.substringBefore("?").trimEnd('/')
            val listUrl = if (Regex("""/read/[^/]+/\d+$""", RegexOption.IGNORE_CASE).containsMatchIn(baseChapUrl)) {
                "$baseChapUrl?style=list"
            } else {
                "$baseChapUrl/1?style=list"
            }

            val html = NetworkClient.fetchHtml(listUrl, baseUrl)
            val doc = org.jsoup.Jsoup.parse(html, listUrl)
            val pages = mutableListOf<String>()
            val seen = mutableSetOf<String>()

            doc.select("#page img").forEach { el ->
                val src = el.attr("src").ifBlank { el.attr("data-src") }
                if (src.isNotBlank() && seen.add(src)) {
                    pages.add(src)
                }
            }

            if (pages.isEmpty()) {
                val matches = Regex("""https?://cdn\.mangaworld\.[a-z]+/chapters/[^\s"'<>]+""", RegexOption.IGNORE_CASE).findAll(html)
                for (m in matches) {
                    val u = m.value
                    if (seen.add(u)) pages.add(u)
                }
            }

            val title = doc.title().replace(Regex("""\s*-\s*MangaWorld.*$""", RegexOption.IGNORE_CASE), "").trim()
            ChapterPages(title = title, chapterTitle = title, pages = pages, referer = "https://www.mangaworld.ac/")
        } catch (_: Exception) {
            ChapterPages(pages = emptyList())
        }
    }
}

