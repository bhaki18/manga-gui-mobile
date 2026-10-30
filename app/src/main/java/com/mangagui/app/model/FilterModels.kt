package com.mangagui.app.model

data class GenreDefinition(
    val key: String,
    val awId: String,
    val auGenreId: Int?,
    val auGenreName: String?,
    val mwSlug: String?,
    val mpGenre: String?,
    val mdTag: String?,
    val keywords: List<String>
)

object Filters {
    val GENRES = listOf(
        "All genres",
        "Shounen",
        "Seinen",
        "Isekai",
        "Action (Azione)",
        "Adventure (Avventura)",
        "Fantasy",
        "Comedy (Commedia)",
        "Romance (Romantico)",
        "Drama (Drammatico)",
        "Sci-Fi (Fantascienza)",
        "Horror",
        "Mystery (Mistero)",
        "Supernatural (Soprannaturale)",
        "Slice of Life",
        "Sports (Sport)",
        "Psychological (Psicologico)",
        "Thriller",
        "Ecchi",
        "Mecha",
        "Martial Arts (Arti Marziali)",
        "School (Scolastico)",
        "Historical (Storico)",
        "Shoujo"
    )

    val QUICK_GENRES = listOf(
        "All genres",
        "Shounen",
        "Isekai",
        "Seinen",
        "Action (Azione)",
        "Fantasy",
        "Comedy (Commedia)",
        "Romance (Romantico)",
        "Horror",
        "Mystery (Mistero)",
        "Sports (Sport)",
        "Psychological (Psicologico)",
        "Slice of Life",
        "Sci-Fi (Fantascienza)"
    )

    val LANGUAGES = listOf(
        "All languages",
        "Italiano (IT)",
        "English (EN)",
        "Español (ES)",
        "Français (FR)",
        "Deutsch (DE)",
        "Português (PT-BR)",
        "Русский (RU)",
        "日本語 (JA)",
        "العربية (AR)",
        "Polski (PL)",
        "Türkçe (TR)",
        "Indonesian (ID)",
        "Tiếng Việt (VI)",
        "한국어 (KO)",
        "中文 (ZH)"
    )

    val GENRE_DEFINITIONS = mapOf(
        "action" to GenreDefinition("action", "1", 51, "Action", "azione", "Action", "391b0423-d847-456f-aff0-8b0cfc03066b", listOf("action", "azione", "combattimento")),
        "adventure" to GenreDefinition("adventure", "2", 21, "Adventure", "avventura", "Adventure", "87cc87cd-a395-47af-b27a-93258283bbc6", listOf("adventure", "avventura")),
        "shounen" to GenreDefinition("shounen", "32", 34, "Shounen", "shounen", "Shounen", null, listOf("shounen", "shonen")),
        "seinen" to GenreDefinition("seinen", "29", 49, "Seinen", "seinen", "Seinen", null, listOf("seinen", "maturo", "adulti")),
        "shoujo" to GenreDefinition("shoujo", "30", 18, "Shoujo", "shoujo", "Shoujo", null, listOf("shoujo", "shojo")),
        "isekai" to GenreDefinition("isekai", "49", 53, "Isekai", "fantasy", "Isekai", "ace04997-f6bd-436e-b261-779182193d3d", listOf("isekai", "reincarnation", "reincarnazione", "altro mondo", "another world")),
        "fantasy" to GenreDefinition("fantasy", "9", 9, "Fantasy", "fantasy", "Fantasy", "cdc58593-87dd-415e-bbc0-2ec27bf404cc", listOf("fantasy", "fantasia", "magia", "magic")),
        "comedy" to GenreDefinition("comedy", "4", 37, "Comedy", "commedia", "Comedy", "4d32cc48-9f00-4cca-9b5a-a839f0764984", listOf("comedy", "commedia", "umorismo", "parodia")),
        "romance" to GenreDefinition("romance", "46", 17, "Romance", "romantico", "Romance", "423e2eae-a7a2-4a8b-ac03-a8351462d71d", listOf("romance", "romantico", "sentimentale")),
        "drama" to GenreDefinition("drama", "7", 22, "Drama", "drammatico", "Drama", "b9af3a63-f058-46de-a9a0-e0c13906197a", listOf("drama", "drammatico", "tragico", "tragedy")),
        "scifi" to GenreDefinition("scifi", "28", 40, "Sci-fi", "sci-fi", "Sci-Fi", "256c8bd9-4904-4360-bf4f-508a76d67183", listOf("sci-fi", "scifi", "fantascienza", "spazio", "space", "cyberpunk")),
        "horror" to GenreDefinition("horror", "13", 3, "Horror", "horror", "Horror", "cdad7e68-1419-41dd-bdce-27753074a640", listOf("horror", "gore", "splatter")),
        "mystery" to GenreDefinition("mystery", "21", 24, "Mystery", "mistero", "Mystery", "ee968100-4191-4968-93d3-f82d72be7e46", listOf("mystery", "mistero", "giallo", "investigativo")),
        "supernatural" to GenreDefinition("supernatural", "37", 42, "Supernatural", "soprannaturale", "Supernatural", "eabc5b4c-6aff-42f3-b657-3e90cbd00b75", listOf("supernatural", "soprannaturale", "sovrannaturale", "demoni", "demons", "vampiri")),
        "sliceoflife" to GenreDefinition("sliceoflife", "34", 50, "Slice of Life", "slice-of-life", "Slice of Life", "e5301a23-ebd9-49dd-a0cb-2add944c7fe9", listOf("slice of life", "slice-of-life", "quotidiano")),
        "sports" to GenreDefinition("sports", "36", 27, "Sports", "sport", "Sports", "69964a64-2f90-4d33-beeb-f3ed2875eb4c", listOf("sport", "sports", "sportivo", "calcio", "basket")),
        "psychological" to GenreDefinition("psychological", "24", 47, "Psychological", "psicologico", "Psychological", "3b60b75c-a2d7-4860-ab56-05f391bb889c", listOf("psychological", "psicologico", "thriller")),
        "thriller" to GenreDefinition("thriller", "39", 48, "Thriller", "psicologico", "Thriller", "07251805-a27e-4d59-b488-f0bfbec15168", listOf("thriller", "suspense", "psicologico")),
        "ecchi" to GenreDefinition("ecchi", "8", 5, "Ecchi", "ecchi", "Ecchi", null, listOf("ecchi", "harem")),
        "mecha" to GenreDefinition("mecha", "18", 38, "Mecha", "mecha", "Mecha", "50880a9d-5440-4732-9afb-8f457127e836", listOf("mecha", "robot")),
        "martialarts" to GenreDefinition("martialarts", "3", 31, "Martial Arts", "arti-marziali", "Martial Arts", "799c202e-7daa-44eb-9cf7-8a3c0441531e", listOf("martial arts", "arti marziali", "samurai")),
        "school" to GenreDefinition("school", "27", 33, "School", "scolastico", "School", "caaa44eb-cd40-4177-b930-79d3ef2afe87", listOf("school", "scolastico", "school life")),
        "historical" to GenreDefinition("historical", "12", 30, "Historical", "storico", "Historical", "33771934-028e-4cb3-8744-691e866a923e", listOf("historical", "storico"))
    )

    fun normalizeGenre(raw: String?): String {
        if (raw.isNullOrBlank()) return "all"
        val s = raw.lowercase().trim()
        if (s == "all" || s.contains("all genres") || s.contains("tutti i generi") || s.contains("tutti")) return "all"
        if (GENRE_DEFINITIONS.containsKey(s)) return s
        if (s.contains("azion") || s.contains("action")) return "action"
        if (s.contains("avventur") || s.contains("adventur")) return "adventure"
        if (s.contains("shoun") || s.contains("shon")) return "shounen"
        if (s.contains("seinen")) return "seinen"
        if (s.contains("shoujo") || s.contains("shojo")) return "shoujo"
        if (s.contains("isekai") || s.contains("reincarn")) return "isekai"
        if (s.contains("fantasy") || s.contains("fantas")) return "fantasy"
        if (s.contains("commed") || s.contains("comed")) return "comedy"
        if (s.contains("romant") || s.contains("romanc") || s.contains("sentiment")) return "romance"
        if (s.contains("dramm") || s.contains("drama")) return "drama"
        if (s.contains("sci-fi") || s.contains("scifi") || s.contains("fantascienza")) return "scifi"
        if (s.contains("horror")) return "horror"
        if (s.contains("mister") || s.contains("myster")) return "mystery"
        if (s.contains("soprannat") || s.contains("sovrannat") || s.contains("supernat")) return "supernatural"
        if (s.contains("slice")) return "sliceoflife"
        if (s.contains("sport")) return "sports"
        if (s.contains("psicolog") || s.contains("psycholog")) return "psychological"
        if (s.contains("thriller")) return "thriller"
        if (s.contains("ecchi")) return "ecchi"
        if (s.contains("mecha")) return "mecha"
        if (s.contains("marzial") || s.contains("martial")) return "martialarts"
        if (s.contains("scolast") || s.contains("school")) return "school"
        if (s.contains("storic") || s.contains("histor")) return "historical"
        return "all"
    }

    fun normalizeAudio(raw: String?): String {
        if (raw.isNullOrBlank()) return "all"
        val s = raw.lowercase().trim()
        if (s == "all" || s.contains("all audio") || s.contains("tutti") || (s.contains("sub") && s.contains("dub"))) return "all"
        if (s == "dub" || s == "ita" || s.contains("dub") || s.contains("doppiat") || s.contains("doblado") || s.contains("doubl") || s.contains("synchron") || s.contains("solo ita")) return "dub"
        if (s == "sub" || s == "subita" || s.contains("sub") || s.contains("sottotitol") || s.contains("sous-titr") || s.contains("untertitel") || s.contains("legendado")) return "sub"
        return "all"
    }

    fun resolveLanguageCode(raw: String?): String {
        if (raw.isNullOrBlank()) return "all"
        val s = raw.lowercase().trim()
        if (s == "all" || s.contains("all languages") || s.contains("tutte le lingue")) return "all"
        val match = Regex("\\(([a-z\\-]+)\\)\\s*$").find(s)
        if (match != null) return match.groupValues[1].trim()
        if (s.contains("italiano") || s == "it") return "it"
        if (s.contains("english") || s == "en") return "en"
        if (s.contains("español") || s.contains("spanish") || s == "es") return "es"
        if (s.contains("français") || s.contains("french") || s == "fr") return "fr"
        if (s.contains("deutsch") || s.contains("german") || s == "de") return "de"
        if (s.contains("portugu") || s == "pt" || s == "pt-br") return "pt-br"
        if (s.contains("русский") || s == "ru") return "ru"
        if (s.contains("日本語") || s == "ja") return "ja"
        if (s.contains("العربية") || s == "ar") return "ar"
        if (s.contains("polski") || s == "pl") return "pl"
        if (s.contains("türkçe") || s == "tr") return "tr"
        if (s.contains("indonesian") || s == "id") return "id"
        if (s.contains("việt") || s == "vi") return "vi"
        if (s.contains("한국어") || s == "ko") return "ko"
        if (s.contains("中文") || s == "zh") return "zh"
        return s
    }

    data class AudioOptionData(
        val allOption: String,
        val subOption: String,
        val dubOption: String,
        val quickSubText: String,
        val quickDubText: String
    )

    fun getAudioOptionsForLanguage(selectedLanguage: String?): AudioOptionData {
        val code = resolveLanguageCode(selectedLanguage)
        if (code.isBlank() || code == "all") {
            return AudioOptionData(
                "All Audio (Sub & Dub)",
                "Sub (Subtitled / Sottotitolato)",
                "Dub (Dubbed / Doppiato)",
                "💬 Solo Sub",
                "🎙️ Solo Dub"
            )
        }
        val upper = code.uppercase()
        val (flag, subDesc, dubDesc) = when (code) {
            "it" -> Triple("🇮🇹", "Sottotitolato", "Doppiato")
            "en" -> Triple("🇬🇧", "Subtitled", "Dubbed")
            "es" -> Triple("🇪🇸", "Subtitulado", "Doblado")
            "fr" -> Triple("🇫🇷", "Sous-titré", "Doublé")
            "de" -> Triple("🇩🇪", "Untertitelt", "Synchronisiert")
            "pt-br" -> Triple("🇧🇷", "Legendado", "Dublado")
            "ru" -> Triple("🇷🇺", "Субтитры", "Дубляж")
            "ja" -> Triple("🇯🇵", "Subtitled", "Original / Dub")
            "ar" -> Triple("🇸🇦", "مترجم", "مدبلج")
            "pl" -> Triple("🇵🇱", "Napisy", "Dubbing")
            "tr" -> Triple("🇹🇷", "Altyazılı", "Dublaj")
            "id" -> Triple("🇮🇩", "Subtitle", "Dubbing")
            "vi" -> Triple("🇻🇳", "Vietsub", "Thuyết minh")
            "ko" -> Triple("🇰🇷", "자막", "더빙")
            "zh" -> Triple("🇨🇳", "字幕", "配音")
            else -> Triple("🌐", "Subtitled", "Dubbed")
        }
        return AudioOptionData(
            "All Audio (Sub & Dub - $upper)",
            "Sub $upper ($subDesc)",
            "Dub $upper ($dubDesc)",
            "$flag Solo Sub $upper",
            "$flag Solo Dub $upper"
        )
    }

    fun matchesGenreText(genresText: String?, rawGenre: String?, titleText: String = ""): Boolean {
        val key = normalizeGenre(rawGenre)
        if (key == "all") return true
        val def = GENRE_DEFINITIONS[key] ?: return true
        val haystack = "${genresText ?: ""} $titleText".lowercase()
        return def.keywords.any { haystack.contains(it.lowercase()) }
    }

    fun matchesAudioFilter(itemAudio: String?, rawAudio: String?, itemTitle: String = "", targetLang: String? = null, itemLang: String? = null): Boolean {
        if (!targetLang.isNullOrBlank() && targetLang != "all" && !itemLang.isNullOrBlank() && itemLang != "all") {
            val tl = targetLang.lowercase().trim()
            val il = itemLang.lowercase().trim()
            if (tl != il && !il.startsWith(tl) && !tl.startsWith(il)) return false
        }
        val target = normalizeAudio(rawAudio)
        if (target == "all") return true
        val a = (itemAudio ?: "").lowercase().trim()
        val t = itemTitle.lowercase()
        val isDubTitle = t.contains("(ita)") || t.contains("(dub)") || t.contains("dubbed") || t.contains("doppiato") ||
                t.contains("doblado") || t.contains("doublé") || (t.contains("[ita]") && !t.contains("sub")) ||
                (t.contains("[dub]") && !t.contains("sub"))

        return when (target) {
            "dub", "ita" -> a == "dub" || a == "ita" || a == "both" || isDubTitle
            "sub", "subita" -> {
                if (isDubTitle && (a == "ita" || a == "dub")) false
                else a == "sub" || a == "subita" || a == "both" || a.isEmpty()
            }
            else -> true
        }
    }
}

