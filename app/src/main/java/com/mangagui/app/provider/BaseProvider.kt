package com.mangagui.app.provider

import com.mangagui.app.model.Chapter
import com.mangagui.app.model.ChapterPages
import com.mangagui.app.model.Episode
import com.mangagui.app.model.MediaItem
import com.mangagui.app.model.StreamData

interface BaseProvider {
    val id: String
    val name: String
    val baseUrl: String
    val type: String // "anime" or "manga"
    val languages: List<String>

    suspend fun getCatalog(lang: String? = null, genre: String? = null, audio: String? = null): List<MediaItem>
    suspend fun search(query: String, lang: String? = null, genre: String? = null, audio: String? = null): List<MediaItem>
}

interface AnimeProvider : BaseProvider {
    override val type: String get() = "anime"
    suspend fun getEpisodes(animeUrl: String): List<Episode>
    suspend fun getEpisodeStream(episodeUrl: String, preferredAudio: String? = "subita"): StreamData
}

interface MangaProvider : BaseProvider {
    override val type: String get() = "manga"
    suspend fun getChapters(mangaUrl: String, lang: String? = null): List<Chapter>
    suspend fun getChapterPages(chapterUrl: String): ChapterPages
}

