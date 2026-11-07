package com.mangagui.app.service

import android.content.Context
import com.mangagui.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class CoverService(private val context: Context) {
    private val coverCache = ConcurrentHashMap<String, String>()
    private val cacheFile: File by lazy { File(context.filesDir, "covers-cache.json") }
    private val coverMutex = Mutex()

    init {
        loadCache()
    }

    private fun loadCache() {
        if (cacheFile.exists()) {
            runCatching {
                val json = JSONObject(cacheFile.readText())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    coverCache[k] = json.optString(k)
                }
            }
        }
    }

    private fun saveCache() {
        runCatching {
            val json = JSONObject()
            coverCache.forEach { (k, v) -> json.put(k, v) }
            cacheFile.writeText(json.toString())
        }
    }

    suspend fun getCover(title: String, type: String): String? = coverMutex.withLock {
        withContext(Dispatchers.IO) {
            val cleanTitle = title.replace(Regex("""\s*\[[^\]]+\]"""), "")
                .replace(Regex("""\s*\([^\)]+\)$"""), "")
                .trim()
                .lowercase()

            if (cleanTitle.isBlank()) return@withContext null
            val cacheKey = "$type:$cleanTitle"
            coverCache[cacheKey]?.let { return@withContext it }

            // Polite delay between sequential requests to respect rate limits
            delay(250)

            // Query Jikan (MyAnimeList open API)
            try {
                val endpoint = if (type.equals("manga", ignoreCase = true)) "manga" else "anime"
                val query = URLEncoder.encode(cleanTitle, "UTF-8")
                val url = "https://api.jikan.moe/v4/$endpoint?q=$query&limit=1&sfw=true"
                val jsonText = NetworkClient.fetchHtml(url)
                val root = JSONObject(jsonText)
                val dataArr = root.optJSONArray("data")
                if (dataArr != null && dataArr.length() > 0) {
                    val first = dataArr.optJSONObject(0)
                    val images = first?.optJSONObject("images")
                    val jpg = images?.optJSONObject("jpg")
                    val imgUrl = jpg?.optString("large_image_url")?.ifBlank { jpg.optString("image_url") }
                    if (!imgUrl.isNullOrBlank()) {
                        coverCache[cacheKey] = imgUrl
                        saveCache()
                        return@withContext imgUrl
                    }
                }
            } catch (_: Exception) {}

            null
        }
    }
}

