package com.mangagui.app.provider

import com.mangagui.app.model.Chapter
import com.mangagui.app.model.ChapterPages
import com.mangagui.app.model.Filters
import com.mangagui.app.model.MediaItem
import com.mangagui.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

class MangaDexProvider : MangaProvider {
    override val id: String = "mangadex"
    override val name: String = "MangaDex"
    override val baseUrl: String = "https://api.mangadex.org"
    override val languages: List<String> = listOf("all")

    private fun buildLangParam(paramName: String, lang: String?): String {
        val clean = Filters.resolveLanguageCode(lang)
        if (clean == "all") return ""
        return when (clean) {
            "es", "es-la" -> "&$paramName[]=es&$paramName[]=es-la"
            "pt", "pt-br" -> "&$paramName[]=pt-br&$paramName[]=pt"
            "zh", "zh-hk" -> "&$paramName[]=zh&$paramName[]=zh-hk"
            else -> "&$paramName[]=${URLEncoder.encode(clean, "UTF-8")}"
        }
    }

    private fun extractMangaItem(item: JSONObject, lang: String?): MediaItem {
        val id = item.optString("id")
        val attrs = item.optJSONObject("attributes") ?: JSONObject()
        val titleObj = attrs.optJSONObject("title") ?: JSONObject()
        val altTitles = attrs.optJSONArray("altTitles")

        val findAlt: (String) -> String? = { code ->
            var found: String? = null
            if (altTitles != null) {
                for (i in 0 until altTitles.length()) {
                    val obj = altTitles.optJSONObject(i)
                    val s = obj?.optString(code)
                    if (!s.isNullOrBlank()) {
                        found = s.trim()
                        break
                    }
                }
            }
            found
        }

        val cleanLang = Filters.resolveLanguageCode(lang)
        val langTitle = if (cleanLang != "all") titleObj.optString(cleanLang).ifBlank { findAlt(cleanLang).orEmpty() } else ""
        val enTitle = titleObj.optString("en").ifBlank { findAlt("en").orEmpty() }
        val itTitle = titleObj.optString("it").ifBlank { findAlt("it").orEmpty() }
        val romajiTitle = titleObj.optString("ja-ro").ifBlank { findAlt("ja-ro").orEmpty() }

        val title = langTitle.ifBlank { enTitle }.ifBlank { itTitle }.ifBlank { romajiTitle }.ifBlank { "Manga $id" }

        val tagsArray = attrs.optJSONArray("tags")
        val tagsList = mutableListOf<String>()
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length().coerceAtMost(5)) {
                val tObj = tagsArray.optJSONObject(i)
                val n = tObj?.optJSONObject("attributes")?.optJSONObject("name")?.optString("en")
                if (!n.isNullOrBlank()) tagsList.add(n)
            }
        }
        val demo = attrs.optString("publicationDemographic")
        if (demo.isNotBlank() && demo != "none") {
            tagsList.add(0, demo.replaceFirstChar { it.uppercase() })
        }

        val relationships = item.optJSONArray("relationships")
        var author = ""
        var coverFileName: String? = null
        if (relationships != null) {
            for (i in 0 until relationships.length()) {
                val r = relationships.optJSONObject(i)
                if (r?.optString("type") == "author") {
                    author = r.optJSONObject("attributes")?.optString("name").orEmpty()
                } else if (r?.optString("type") == "cover_art") {
                    coverFileName = r.optJSONObject("attributes")?.optString("fileName")
                }
            }
        }

        val coverUrl = if (!coverFileName.isNullOrBlank()) {
            "https://uploads.mangadex.org/covers/$id/$coverFileName.256.jpg"
        } else null

        val resolvedLang = if (cleanLang != "all") cleanLang else "en"

        return MediaItem(
            id = id,
            title = title,
            url = "https://mangadex.org/title/$id",
            image = coverUrl,
            author = author,
            genres = tagsList.joinToString(", "),
            source = this.id,
            sourceName = this.name,
            language = resolvedLang,
            type = "manga"
        )
    }

    override suspend fun getCatalog(lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanLang = Filters.resolveLanguageCode(lang)
        val langParam = buildLangParam("availableTranslatedLanguage", lang)
        val genreDef = Filters.GENRE_DEFINITIONS[Filters.normalizeGenre(genre)]
        val genreParam = when {
            genreDef?.mdDemographic != null -> "&publicationDemographic[]=${genreDef.mdDemographic}"
            genreDef?.mdTag != null -> "&includedTags[]=${genreDef.mdTag}"
            else -> ""
        }

        val url = "$baseUrl/manga?limit=25&hasAvailableChapters=true&order[followedCount]=desc$langParam$genreParam&includes[]=cover_art&includes[]=author"
        try {
            val jsonText = NetworkClient.fetchHtml(url)
            val doc = JSONObject(jsonText)
            val data = doc.optJSONArray("data") ?: return@withContext emptyList()
            val list = mutableListOf<MediaItem>()
            for (i in 0 until data.length()) {
                data.optJSONObject(i)?.let { list.add(extractMangaItem(it, lang)) }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun search(query: String, lang: String?, genre: String?, audio: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext getCatalog(lang, genre, audio)
        val cleanLang = Filters.resolveLanguageCode(lang)
        val langParam = buildLangParam("availableTranslatedLanguage", lang)
        val genreDef = Filters.GENRE_DEFINITIONS[Filters.normalizeGenre(genre)]
        val genreParam = when {
            genreDef?.mdDemographic != null -> "&publicationDemographic[]=${genreDef.mdDemographic}"
            genreDef?.mdTag != null -> "&includedTags[]=${genreDef.mdTag}"
            else -> ""
        }

        val url = "$baseUrl/manga?title=${URLEncoder.encode(query.trim(), "UTF-8")}&limit=25&hasAvailableChapters=true$langParam$genreParam&includes[]=cover_art&includes[]=author&order[relevance]=desc"
        try {
            val jsonText = NetworkClient.fetchHtml(url)
            val doc = JSONObject(jsonText)
            val data = doc.optJSONArray("data") ?: return@withContext emptyList()
            val list = mutableListOf<MediaItem>()
            for (i in 0 until data.length()) {
                data.optJSONObject(i)?.let { list.add(extractMangaItem(it, lang)) }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getChapters(mangaUrl: String, lang: String?): List<Chapter> = withContext(Dispatchers.IO) {
        val mangaId = mangaUrl.substringAfterLast("/").substringBefore("?")
        val cleanLang = Filters.resolveLanguageCode(lang)
        val langParam = buildLangParam("translatedLanguage", lang)
        val url = "$baseUrl/manga/$mangaId/feed?limit=500&order[chapter]=asc&order[volume]=asc$langParam"

        try {
            val jsonText = NetworkClient.fetchHtml(url)
            val doc = JSONObject(jsonText)
            val data = doc.optJSONArray("data") ?: return@withContext emptyList()
            val list = mutableListOf<Chapter>()
            val seenNums = mutableSetOf<String>()

            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val attrs = item.optJSONObject("attributes") ?: continue
                val externalUrl = attrs.optString("externalUrl").ifBlank { null }
                val chNum = attrs.optString("chapter").ifBlank { (list.size + 1).toString() }
                val chTitle = attrs.optString("title")

                val displayTitle = if (chTitle.isNotBlank()) "Ch. $chNum - $chTitle" else "Chapter $chNum"
                if (seenNums.add(chNum)) {
                    list.add(
                        Chapter(
                            id = id,
                            title = displayTitle,
                            number = chNum,
                            url = "https://mangadex.org/chapter/$id",
                            externalUrl = externalUrl
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getChapterPages(chapterUrl: String): ChapterPages = withContext(Dispatchers.IO) {
        val chapterId = chapterUrl.substringAfterLast("/").substringBefore("?")
        try {
            val url = "$baseUrl/at-home/server/$chapterId"
            val jsonText = NetworkClient.fetchHtml(url)
            val doc = JSONObject(jsonText)
            val baseUrlHome = doc.optString("baseUrl")
            val chapterObj = doc.optJSONObject("chapter") ?: return@withContext ChapterPages(pages = emptyList())
            val hash = chapterObj.optString("hash")
            val dataArray = chapterObj.optJSONArray("data") ?: return@withContext ChapterPages(pages = emptyList())

            val pages = mutableListOf<String>()
            for (i in 0 until dataArray.length()) {
                val f = dataArray.optString(i)
                if (f.isNotBlank()) {
                    pages.add("$baseUrlHome/data/$hash/$f")
                }
            }

            ChapterPages(title = "Chapter $chapterId", chapterTitle = "Chapter $chapterId", pages = pages, referer = "https://mangadex.org/")
        } catch (_: Exception) {
            ChapterPages(pages = emptyList())
        }
    }
}

