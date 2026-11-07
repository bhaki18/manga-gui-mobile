package com.mangagui.app.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaItem(
    val id: String,
    val title: String,
    val url: String? = null,
    val image: String? = null,
    val type: String = "anime", // "anime" or "manga"
    val source: String = "",
    val sourceName: String = "",
    val language: String = "it",
    val audio: String? = null, // "ita", "subita", "both", "sub", "dub"
    val genres: String? = null,
    val author: String? = null,
    val count: Int = 0,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
    val downloadSizeText: String? = null,
    val customSubDubText: String? = null,
    val watchedNumbers: List<Int> = emptyList(),
    val watchedSummary: String? = null,
    val lastItem: String? = null,
    val lastAccessedAt: String? = null
) {
    val subDubText: String
        get() {
            if (!customSubDubText.isNullOrBlank()) return customSubDubText
            val isManga = type.equals("manga", ignoreCase = true)
            if (isDownloaded) {
                val unit = if (isManga) "chapters" else "episodes"
                val size = if (!downloadSizeText.isNullOrBlank()) " ($downloadSizeText)" else ""
                return "$count $unit downloaded$size"
            }
            if (!watchedSummary.isNullOrBlank()) {
                val label = if (isManga) "Read" else "Watched"
                val unit = if (isManga) "ch." else "ep."
                return "$label: $watchedSummary ($count $unit)"
            }
            if (!lastItem.isNullOrBlank()) {
                val label = if (isManga) "Read" else "Watched"
                val unit = if (isManga) "ch." else "ep."
                return "$label: $lastItem ($count $unit)"
            }
            val langBadge = if (language.isNotBlank()) language.uppercase() else "IT"
            if (isManga) {
                return "Manga ($langBadge)"
            }
            return when (audio?.lowercase()) {
                "both" -> "Sub | Dub ($langBadge)"
                "ita", "dub" -> "Dubbed (DUB $langBadge)"
                "subita", "sub" -> "Subtitled (SUB $langBadge)"
                else -> "Sub | Dub ($langBadge)"
            }
        }

    val hasSub: Boolean
        get() {
            if (type.equals("manga", ignoreCase = true)) return false
            val a = audio?.lowercase()?.trim() ?: return true
            if (a.isEmpty()) return true
            if (a == "both" || a.contains("both") || a.contains("ita/sub") || a.contains("sub/dub")) return true
            return a == "sub" || a == "subita" || a.contains("sub")
        }

    val hasDub: Boolean
        get() {
            if (type.equals("manga", ignoreCase = true)) return false
            val a = audio?.lowercase()?.trim() ?: return false
            if (a.isEmpty()) return false
            if (a == "both" || a.contains("both") || a.contains("ita/sub") || a.contains("sub/dub")) return true
            if (a == "dub" || a == "ita" || a.contains("dub") || a.contains("doppiato")) return true
            return a.contains("ita") && !a.contains("sub")
        }

    val audioBadge: String
        get() = if (isDownloaded) "OFFLINE" else if (type.equals("manga", ignoreCase = true)) {
            if (language.isNotBlank()) "MANGA • ${language.uppercase()}" else "MANGA"
        } else when (audio?.lowercase()) {
            "ita", "dub" -> if (language.isNotBlank()) "DUB ${language.uppercase()}" else "DUB"
            "subita", "sub" -> if (language.isNotBlank()) "SUB ${language.uppercase()}" else "SUB"
            "both" -> if (language.isNotBlank()) "SUB | DUB (${language.uppercase()})" else "SUB | DUB"
            else -> if (language.isNotBlank()) "ANIME • ${language.uppercase()}" else "ANIME"
        }

    val displaySourceName: String
        get() = if (sourceName.isNotBlank()) sourceName else source

    val typeIcon: String
        get() = if (type.equals("anime", ignoreCase = true)) "🎬" else "📖"
}

@Serializable
data class Episode(
    val id: String,
    val title: String,
    val url: String,
    val number: Int = 1
)

@Serializable
data class Chapter(
    val id: String,
    val title: String,
    val url: String,
    val number: String = "1",
    val externalUrl: String? = null
)

@Serializable
data class StreamData(
    val streamUrl: String?,
    val options: Map<String, String?> = emptyMap(),
    val availableAudios: List<String> = emptyList(),
    val referer: String? = null
)

@Serializable
data class ChapterPages(
    val title: String? = null,
    val chapterTitle: String? = null,
    val pages: List<String> = emptyList(),
    val externalUrl: String? = null,
    val referer: String? = null
)

