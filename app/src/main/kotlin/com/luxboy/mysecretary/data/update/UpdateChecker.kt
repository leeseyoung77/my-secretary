package com.luxboy.mysecretary.data.update

import android.content.Context
import com.luxboy.mysecretary.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Returns [UpdateInfo] if the latest GitHub release is newer than the installed version,
     * else null. Network/parse errors are swallowed and return null (silent fallback).
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val owner = BuildConfig.GITHUB_OWNER
            val repo = BuildConfig.GITHUB_REPO
            val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MySecretary-Android")
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            if (conn.responseCode !in 200..299) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val release = json.decodeFromString<GithubRelease>(body)
            if (release.draft || release.prerelease) return@runCatching null

            val latest = release.tagName.removePrefix("v").trim()
            val current = currentVersionName() ?: return@runCatching null
            if (!isNewer(latest, current)) return@runCatching null

            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@runCatching null

            UpdateInfo(
                latestVersion = latest,
                currentVersion = current,
                notes = release.body?.trim()?.takeIf { it.isNotEmpty() },
                downloadUrl = apk.browserDownloadUrl,
                sizeBytes = apk.size,
                releasePageUrl = release.htmlUrl,
            )
        }.getOrNull()
    }

    private fun currentVersionName(): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    companion object {
        /** Compares dotted version strings; returns true when [a] > [b]. */
        fun isNewer(a: String, b: String): Boolean {
            val aParts = a.toParts()
            val bParts = b.toParts()
            val n = maxOf(aParts.size, bParts.size)
            for (i in 0 until n) {
                val av = aParts.getOrElse(i) { 0 }
                val bv = bParts.getOrElse(i) { 0 }
                if (av != bv) return av > bv
            }
            return false
        }

        private fun String.toParts(): List<Int> = split('.', '-').mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
    }
}
