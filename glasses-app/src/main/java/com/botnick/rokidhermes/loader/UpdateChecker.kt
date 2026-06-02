package com.botnick.rokidhermes.loader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Checks this repo's GitHub releases for a newer version and, if found, the URL
 * of the installable `.apk` asset to download.
 */
data class ReleaseInfo(
    val tagName: String,
    val version: String,
    val downloadUrl: String?,
    val releaseName: String
)

object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/botnick/rokid-hermes/releases/latest"

    private val client = OkHttpClient()

    /** Fetches the latest release info. Returns null on network/API error or no release. */
    suspend fun checkLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                val tagName = json.getString("tag_name")
                val version = tagName.removePrefix("v")
                val releaseName = json.optString("name", tagName)

                // Find the installable APK asset.
                val assets = json.getJSONArray("assets")
                var downloadUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                ReleaseInfo(tagName, version, downloadUrl, releaseName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Compares two semver strings. True if [remoteVersion] is newer than [localVersion]. */
    fun isNewer(localVersion: String, remoteVersion: String): Boolean {
        val local = localVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val remote = remoteVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(local.size, remote.size)
        for (i in 0 until maxLen) {
            val l = local.getOrElse(i) { 0 }
            val r = remote.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
