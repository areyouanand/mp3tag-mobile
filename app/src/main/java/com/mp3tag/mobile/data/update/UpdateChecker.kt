package com.mp3tag.mobile.data.update

import com.mp3tag.mobile.data.prefs.UpdateChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionString: String,
    val buildNumber: Int?,
    val title: String,
    val isPrerelease: Boolean,
    val publishedDate: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
)

data class UpdateCheckResult(
    val channel: UpdateChannel,
    val latestRelease: ReleaseInfo?,
    val isUpdateAvailable: Boolean,
    val message: String,
)

object UpdateChecker {
    const val CURRENT_VERSION_NAME = "0.6.3"
    const val CURRENT_VERSION_CODE = 24

    const val GITHUB_REPO_URL = "https://github.com/areyouanand/mp3tag-mobile"
    const val GITHUB_RELEASES_URL = "https://github.com/areyouanand/mp3tag-mobile/releases"
    const val GITHUB_DEVELOPER_URL = "https://github.com/areyouanand"
    private const val API_RELEASES_URL = "https://api.github.com/repos/areyouanand/mp3tag-mobile/releases?per_page=30"

    private val BUILD_NUMBER_REGEX = Regex("""\(build\s+(\d+)\)""", RegexOption.IGNORE_CASE)

    suspend fun checkForUpdates(channel: UpdateChannel): Result<UpdateCheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(API_RELEASES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MP3Tag-Mobile/$CURRENT_VERSION_NAME")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    error("GitHub returned HTTP $code")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val releases = parseReleases(body)
                val matching = when (channel) {
                    UpdateChannel.RELEASE -> releases.filter { !it.isPrerelease }
                    UpdateChannel.BETA -> releases
                }

                val latest = matching.maxWithOrNull { a, b -> compareReleases(a, b) }
                if (latest == null) {
                    UpdateCheckResult(
                        channel = channel,
                        latestRelease = null,
                        isUpdateAvailable = false,
                        message = "No releases found on the ${channel.label} channel.",
                    )
                } else {
                    val semCmp = compareVersionStrings(latest.versionString, CURRENT_VERSION_NAME)
                    val buildNewer = latest.buildNumber != null && latest.buildNumber > CURRENT_VERSION_CODE
                    val isNewer = semCmp > 0 || (semCmp == 0 && buildNewer)

                    val statusMsg = when {
                        isNewer -> "New ${if (latest.isPrerelease) "beta" else "stable"} update available: ${latest.tagName}"
                        semCmp == 0 -> "You are up to date (${latest.tagName} is the latest on ${channel.label})."
                        else -> "Installed v$CURRENT_VERSION_NAME is newer than the latest ${channel.label} build (${latest.tagName})."
                    }

                    UpdateCheckResult(
                        channel = channel,
                        latestRelease = latest,
                        isUpdateAvailable = isNewer,
                        message = statusMsg,
                    )
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    internal fun parseReleases(jsonText: String): List<ReleaseInfo> {
        val arr = JSONArray(jsonText)
        val result = ArrayList<ReleaseInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft", false)) continue

            val tagName = obj.optString("tag_name", "").trim()
            if (tagName.isEmpty()) continue

            val name = obj.optString("name", tagName).ifBlank { tagName }
            val isPrerelease = obj.optBoolean("prerelease", tagName.contains("-beta", ignoreCase = true))
            val htmlUrl = obj.optString("html_url", GITHUB_RELEASES_URL)
            val publishedAt = obj.optString("published_at", "").take(10)
            val versionString = normalizeVersion(tagName)
            val buildNum = BUILD_NUMBER_REGEX.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()

            var apkUrl: String? = null
            var apkName: String? = null
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (a in 0 until assets.length()) {
                    val asset = assets.optJSONObject(a) ?: continue
                    val assetName = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                        apkName = assetName
                        apkUrl = downloadUrl
                        break
                    }
                }
            }

            result.add(
                ReleaseInfo(
                    tagName = tagName,
                    versionString = versionString,
                    buildNumber = buildNum,
                    title = name,
                    isPrerelease = isPrerelease,
                    publishedDate = publishedAt,
                    releasePageUrl = htmlUrl,
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkName,
                )
            )
        }
        return result
    }

    internal fun normalizeVersion(tag: String): String =
        tag.trim().removePrefix("v").removePrefix("V").substringBefore('-')

    internal fun compareReleases(a: ReleaseInfo, b: ReleaseInfo): Int {
        val vCmp = compareVersionStrings(a.versionString, b.versionString)
        if (vCmp != 0) return vCmp
        val bA = a.buildNumber ?: 0
        val bB = b.buildNumber ?: 0
        if (bA != bB) return bA.compareTo(bB)
        // Prefer stable over prerelease when version & build match
        if (a.isPrerelease != b.isPrerelease) {
            return if (a.isPrerelease) -1 else 1
        }
        return 0
    }

    internal fun compareVersionStrings(v1: String, v2: String): Int {
        val parts1 = v1.split('.').map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}
