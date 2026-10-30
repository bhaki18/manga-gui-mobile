package com.mangagui.app.service

import android.content.Context
import com.mangagui.app.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryService(private val context: Context) {
    private val historyFile: File by lazy {
        File(context.filesDir, "history.json")
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    suspend fun getHistory(): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        if (!historyFile.exists()) return@withContext emptyList()
        try {
            val text = historyFile.readText()
            val root = JSONObject(text)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                val isAnime = key.startsWith("anime:") || obj.optString("mediaType") == "anime"
                val title = obj.optString("title", key)
                val url = obj.optString("url")
                val source = obj.optString("source")
                val language = obj.optString("language", "it")
                val lastDate = if (isAnime) obj.optString("lastWatchedAt") else obj.optString("lastReadAt")

                val numbers = mutableListOf<Int>()
                val arrName = if (isAnime) "watchedEpisodes" else "readChapters"
                val arr = obj.optJSONArray(arrName)
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val elem = arr.optJSONObject(i)
                        val idStr = elem?.optString("id")
                        val num = idStr?.filter { it.isDigit() }?.toIntOrNull()
                        if (num != null) numbers.add(num)
                    }
                }

                val distinctNums = numbers.distinct().sorted()
                val prefix = if (isAnime) "Ep." else "Ch."
                val watchedSummary = if (distinctNums.isNotEmpty()) formatRanges(distinctNums, prefix) else null
                val lastItem = if (distinctNums.isNotEmpty()) "$prefix ${distinctNums.last()}" else null

                list.add(
                    MediaItem(
                        id = key,
                        title = title,
                        url = url,
                        type = if (isAnime) "anime" else "manga",
                        source = source,
                        language = language,
                        count = distinctNums.size,
                        watchedNumbers = distinctNums,
                        watchedSummary = watchedSummary,
                        lastItem = lastItem,
                        lastAccessedAt = lastDate
                    )
                )
            }
        } catch (_: Exception) {}

        list.sortedByDescending { it.lastAccessedAt.orEmpty() }
    }

    suspend fun markWatched(item: MediaItem, number: Int) = withContext(Dispatchers.IO) {
        try {
            val root = if (historyFile.exists()) JSONObject(historyFile.readText()) else JSONObject()
            val isAnime = item.type.equals("anime", ignoreCase = true)
            val prefix = if (isAnime) "anime:" else "manga:"
            val safeKey = prefix + (item.url ?: item.id).lowercase()

            val entry = root.optJSONObject(safeKey) ?: JSONObject().apply {
                put("title", item.title)
                put("url", item.url ?: item.id)
                put("source", item.source)
                put("language", item.language)
                put("mediaType", item.type)
            }

            val now = dateFormat.format(Date())
            if (isAnime) entry.put("lastWatchedAt", now) else entry.put("lastReadAt", now)

            val arrName = if (isAnime) "watchedEpisodes" else "readChapters"
            val arr = entry.optJSONArray(arrName) ?: JSONArray().also { entry.put(arrName, it) }

            var alreadyExists = false
            for (i in 0 until arr.length()) {
                val elem = arr.optJSONObject(i)
                if (elem?.optString("id") == number.toString()) {
                    alreadyExists = true
                    break
                }
            }

            if (!alreadyExists) {
                arr.put(JSONObject().apply {
                    put("id", number.toString())
                    put("title", (if (isAnime) "Episode " else "Chapter ") + number)
                })
            }

            root.put(safeKey, entry)
            historyFile.writeText(root.toString())
        } catch (_: Exception) {}
    }

    suspend fun unmarkWatched(item: MediaItem, number: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!historyFile.exists()) return@withContext false
            val root = JSONObject(historyFile.readText())
            val isAnime = item.type.equals("anime", ignoreCase = true)
            val prefix = if (isAnime) "anime:" else "manga:"
            val safeKey = prefix + (item.url ?: item.id).lowercase()

            val entry = root.optJSONObject(safeKey) ?: return@withContext false
            val arrName = if (isAnime) "watchedEpisodes" else "readChapters"
            val arr = entry.optJSONArray(arrName) ?: return@withContext false

            val newArr = JSONArray()
            for (i in 0 until arr.length()) {
                val elem = arr.optJSONObject(i)
                if (elem?.optString("id") != number.toString()) {
                    newArr.put(elem)
                }
            }
            entry.put(arrName, newArr)
            root.put(safeKey, entry)
            historyFile.writeText(root.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun removeItem(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!historyFile.exists()) return@withContext false
            val root = JSONObject(historyFile.readText())
            val isAnime = item.type.equals("anime", ignoreCase = true)
            val prefix = if (isAnime) "anime:" else "manga:"
            val safeKey = prefix + (item.url ?: item.id).lowercase()

            root.remove(safeKey)
            historyFile.writeText(root.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearAll(): Boolean = withContext(Dispatchers.IO) {
        try {
            historyFile.writeText("{}")
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun formatRanges(numbers: List<Int>, prefix: String): String {
        if (numbers.isEmpty()) return ""
        val sorted = numbers.distinct().sorted()
        val ranges = mutableListOf<String>()
        var start = sorted[0]
        var prev = sorted[0]

        for (i in 1 until sorted.size) {
            val cur = sorted[i]
            if (cur == prev + 1) {
                prev = cur
            } else {
                ranges.add(if (start == prev) "$start" else "$start-$prev")
                start = cur
                prev = cur
            }
        }
        ranges.add(if (start == prev) "$start" else "$start-$prev")
        return "$prefix " + ranges.joinToString(", ")
    }
}

