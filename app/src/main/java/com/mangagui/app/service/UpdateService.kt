package com.mangagui.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mangagui.app.BuildConfig
import com.mangagui.app.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class UpdateInfo(
    val hasUpdate: Boolean,
    val installedVersion: String,
    val remoteVersion: String,
    val notes: String = "",
    val apkUrl: String? = null
)

class UpdateService(private val context: Context) {
    private val versionUrls = listOf(
        "https://raw.githubusercontent.com/bhaki18/manga-cli/gh-pages/version.json",
        "https://raw.githubusercontent.com/bhaki18/manga-gui/main/version.json",
        "https://bhaki18.github.io/manga-cli/version.json"
    )

    private fun parseVersionTuple(raw: String?): IntArray {
        if (raw.isNullOrBlank()) return intArrayOf(0, 0, 0, 0)
        val clean = raw.trim().trimStart('v', 'V')
        val dashParts = clean.split("-")
        val mainPart = dashParts[0]
        val relPart = if (dashParts.size > 1) dashParts[1] else "1"

        val dotParts = mainPart.split(".")
        val major = dotParts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val minor = dotParts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val patch = dotParts.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val rel = relPart.filter { it.isDigit() }.toIntOrNull() ?: 1

        return intArrayOf(major, minor, patch, rel)
    }

    fun isRemoteNewer(localVer: String?, remoteVer: String?): Boolean {
        val local = parseVersionTuple(localVer)
        val remote = parseVersionTuple(remoteVer)

        for (i in 0 until maxOf(local.size, remote.size)) {
            val l = local.getOrElse(i) { 0 }
            val r = remote.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    suspend fun checkUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        val installed = BuildConfig.VERSION_NAME
        var bestRemote: String? = null
        var notes = ""
        var apkUrl: String? = null

        val ts = System.currentTimeMillis()
        for (url in versionUrls) {
            try {
                val jsonText = NetworkClient.fetchHtml("$url?t=$ts")
                val root = JSONObject(jsonText)
                val ver = root.optString("version").ifBlank {
                    val pv = root.optString("pkgver")
                    val pr = root.optString("pkgrel", "1")
                    if (pv.isNotBlank()) "$pv-$pr" else ""
                }
                if (ver.isNotBlank()) {
                    if (bestRemote == null || isRemoteNewer(bestRemote, ver)) {
                        bestRemote = ver
                        notes = root.optString("notes")
                        apkUrl = root.optString("androidApkUrl").ifBlank {
                            "https://raw.githubusercontent.com/bhaki18/manga-cli/gh-pages/android/manga-gui-$ver.apk"
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (bestRemote != null && isRemoteNewer(installed, bestRemote)) {
            UpdateInfo(
                hasUpdate = true,
                installedVersion = installed,
                remoteVersion = bestRemote,
                notes = notes,
                apkUrl = apkUrl
            )
        } else {
            UpdateInfo(
                hasUpdate = false,
                installedVersion = installed,
                remoteVersion = bestRemote ?: installed,
                notes = notes,
                apkUrl = apkUrl
            )
        }
    }

    suspend fun downloadAndInstallApk(apkUrl: String, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val destination = File(context.getExternalFilesDir(null) ?: context.filesDir, "update.apk")
        try {
            NetworkClient.downloadToFile(apkUrl, destination) { current, total ->
                val p = if (total > 0) (current * 100 / total).toInt() else 0
                onProgress(p)
            }

            // Launch package installer
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

