package com.mangagui.app.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class SearchHistoryService(private val context: Context) {
    private val searchFile: File by lazy {
        File(context.filesDir, "search-history.json")
    }

    private val maxItems = 5

    suspend fun getSearchHistory(): List<String> = withContext(Dispatchers.IO) {
        if (!searchFile.exists()) return@withContext emptyList()
        try {
            val text = searchFile.readText()
            val arr = JSONArray(text)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)?.trim()
                if (!s.isNullOrBlank()) list.add(s)
            }
            list.distinctBy { it.lowercase() }.take(maxItems)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addSearchQuery(query: String): List<String> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext getSearchHistory()
        val list = getSearchHistory().toMutableList()
        list.removeAll { it.equals(clean, ignoreCase = true) }
        list.add(0, clean)
        val trimmed = list.take(maxItems)
        try {
            val arr = JSONArray(trimmed)
            searchFile.writeText(arr.toString())
        } catch (_: Exception) {}
        trimmed
    }

    suspend fun removeSearchQuery(query: String): List<String> = withContext(Dispatchers.IO) {
        val list = getSearchHistory().toMutableList()
        list.removeAll { it.equals(query.trim(), ignoreCase = true) }
        try {
            val arr = JSONArray(list)
            searchFile.writeText(arr.toString())
        } catch (_: Exception) {}
        list
    }

    suspend fun clearHistory(): Boolean = withContext(Dispatchers.IO) {
        try {
            searchFile.writeText("[]")
            true
        } catch (_: Exception) {
            false
        }
    }
}

