package com.mangagui.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object NetworkClient {
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                val existing = cookieStore.getOrPut(host) { mutableListOf() }
                existing.removeAll { old -> cookies.any { it.name == old.name } }
                existing.addAll(cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .build()

    val downloadClient: OkHttpClient = client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 0 disables timeout during long streaming downloads
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun fetchHtml(url: String, referer: String? = null, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")

        if (!referer.isNullOrBlank()) {
            reqBuilder.header("Referer", referer)
        }
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code} for $url")
            resp.body?.string().orEmpty()
        }
    }

    suspend fun fetchDocument(url: String, referer: String? = null): Document = withContext(Dispatchers.IO) {
        val html = fetchHtml(url, referer)
        Jsoup.parse(html, url)
    }

    suspend fun checkUrlExists(url: String, referer: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", DEFAULT_USER_AGENT)
            if (!referer.isNullOrBlank()) {
                reqBuilder.header("Referer", referer)
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun downloadToFile(
        url: String,
        destinationFile: File,
        referer: String? = null,
        onProgress: ((Long, Long) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept", "*/*")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("Connection", "keep-alive")

        if (!referer.isNullOrBlank()) {
            reqBuilder.header("Referer", referer)
        }

        val parent = destinationFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        val tempFile = File(parent, destinationFile.name + ".tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        try {
            downloadClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Download HTTP ${resp.code}: ${resp.message}")
                val body = resp.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        var lastProgressTime = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastProgressTime > 150 || (totalBytes > 0 && downloadedBytes >= totalBytes)) {
                                lastProgressTime = now
                                onProgress?.invoke(downloadedBytes, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }
            }

            // Successfully downloaded, atomically replace destination
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
        } catch (ex: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw ex
        }
    }
}

