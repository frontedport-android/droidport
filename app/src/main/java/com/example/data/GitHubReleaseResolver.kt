package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ResolvedReleaseInfo(
    val tagName: String,
    val apkUrl: String?,
    val releaseNotes: String? = null
)

object GitHubReleaseResolver {

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val apkUrlRegex = Pattern.compile(
        "https?://[^\\s\"'<>]+\\.apk(?:\\?[^\\s\"'<>]*)?",
        Pattern.CASE_INSENSITIVE
    )

    private val hrefApkRegex = Pattern.compile(
        "href=[\"'](/[^\"']+/releases/download/[^\"']+\\.apk)[\"']",
        Pattern.CASE_INSENSITIVE
    )

    private val hrefFullApkRegex = Pattern.compile(
        "href=[\"'](https://github\\.com/[^\"']+\\.apk)[\"']",
        Pattern.CASE_INSENSITIVE
    )

    suspend fun resolveLatestApk(owner: String, repo: String): ResolvedReleaseInfo = withContext(Dispatchers.IO) {
        val cleanOwner = owner.trim()
        val cleanRepo = repo.trim()

        if (cleanOwner.isBlank() || cleanRepo.isBlank()) {
            return@withContext ResolvedReleaseInfo("", null)
        }

        // 1. Try REST API Latest Release
        try {
            val latestRelease = GitHubApiService.api.getLatestRelease(cleanOwner, cleanRepo)
            val tag = latestRelease.tagName ?: ""

            // Check direct asset
            val apkAsset = latestRelease.getApkAsset()
            if (apkAsset?.browserDownloadUrl != null) {
                Log.d("GitHubReleaseResolver", "Found APK via REST API latest release asset: ${apkAsset.browserDownloadUrl}")
                return@withContext ResolvedReleaseInfo(tag, apkAsset.browserDownloadUrl, latestRelease.body)
            }

            // Check body of latest release for markdown/HTML APK link
            val bodyUrl = extractApkFromText(latestRelease.body)
            if (bodyUrl != null) {
                Log.d("GitHubReleaseResolver", "Found APK via REST API release body: $bodyUrl")
                return@withContext ResolvedReleaseInfo(tag, bodyUrl, latestRelease.body)
            }
        } catch (e: Exception) {
            Log.w("GitHubReleaseResolver", "REST API getLatestRelease failed: ${e.message}")
        }

        // 2. Try REST API All Releases list
        try {
            val releases = GitHubApiService.api.getAllReleases(cleanOwner, cleanRepo)
            for (rel in releases) {
                val tag = rel.tagName ?: ""
                val apkAsset = rel.getApkAsset()
                if (apkAsset?.browserDownloadUrl != null) {
                    Log.d("GitHubReleaseResolver", "Found APK in release $tag asset: ${apkAsset.browserDownloadUrl}")
                    return@withContext ResolvedReleaseInfo(tag, apkAsset.browserDownloadUrl, rel.body)
                }
                val bodyUrl = extractApkFromText(rel.body)
                if (bodyUrl != null) {
                    Log.d("GitHubReleaseResolver", "Found APK in release $tag body: $bodyUrl")
                    return@withContext ResolvedReleaseInfo(tag, bodyUrl, rel.body)
                }
            }
        } catch (e: Exception) {
            Log.w("GitHubReleaseResolver", "REST API getAllReleases failed: ${e.message}")
        }

        // 3. Try Web Scraping GitHub Releases Page (Bypasses API rate limits)
        val scraped = scrapeGitHubReleasesPage(cleanOwner, cleanRepo)
        if (scraped?.apkUrl != null) {
            Log.d("GitHubReleaseResolver", "Found APK via Web Scraping: ${scraped.apkUrl}")
            return@withContext scraped
        }

        // 4. Test standard direct download URL candidates
        val candidates = listOf(
            "https://github.com/$cleanOwner/$cleanRepo/releases/latest/download/$cleanRepo.apk",
            "https://github.com/$cleanOwner/$cleanRepo/releases/latest/download/app-release.apk",
            "https://github.com/$cleanOwner/$cleanRepo/releases/latest/download/$cleanRepo-android.apk"
        )
        for (candidate in candidates) {
            if (checkUrlExists(candidate)) {
                Log.d("GitHubReleaseResolver", "Found working candidate APK URL: $candidate")
                return@withContext ResolvedReleaseInfo("latest", candidate)
            }
        }

        Log.w("GitHubReleaseResolver", "Could not resolve direct APK URL for $cleanOwner/$cleanRepo")
        return@withContext ResolvedReleaseInfo("", null)
    }

    private fun extractApkFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val matcher = apkUrlRegex.matcher(text)
        if (matcher.find()) {
            return matcher.group()
        }
        return null
    }

    private fun extractTagFromUrl(url: String): String? {
        val regex = Regex("""/releases/(?:download|tag)/([^/"'\s?#]+)""")
        val match = regex.find(url)
        if (match != null) {
            val tag = match.groupValues[1]
            if (tag.isNotBlank() && !tag.equals("latest", ignoreCase = true)) return tag
        }
        return null
    }

    private fun scrapeGitHubReleasesPage(owner: String, repo: String): ResolvedReleaseInfo? {
        val urlsToTry = listOf(
            "https://github.com/$owner/$repo/releases/latest",
            "https://github.com/$owner/$repo/releases"
        )

        for (targetUrl in urlsToTry) {
            try {
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val finalUrl = response.request.url.toString()
                    var tag = extractTagFromUrl(finalUrl) ?: ""

                    // Match relative href: href="/owner/repo/releases/download/v1.0.0/app.apk"
                    val relMatcher = hrefApkRegex.matcher(html)
                    if (relMatcher.find()) {
                        val path = relMatcher.group(1)
                        val fullUrl = "https://github.com$path"
                        if (tag.isBlank()) {
                            tag = extractTagFromUrl(fullUrl) ?: ""
                        }
                        return ResolvedReleaseInfo(tag, fullUrl)
                    }

                    // Match full href
                    val fullMatcher = hrefFullApkRegex.matcher(html)
                    if (fullMatcher.find()) {
                        val fullUrl = fullMatcher.group(1)
                        if (tag.isBlank()) {
                            tag = extractTagFromUrl(fullUrl) ?: ""
                        }
                        return ResolvedReleaseInfo(tag, fullUrl)
                    }

                    // Any general .apk link in HTML
                    val genMatcher = apkUrlRegex.matcher(html)
                    if (genMatcher.find()) {
                        val fullUrl = genMatcher.group()
                        if (tag.isBlank()) {
                            tag = extractTagFromUrl(fullUrl) ?: ""
                        }
                        return ResolvedReleaseInfo(tag, fullUrl)
                    }
                }
            } catch (e: Exception) {
                Log.w("GitHubReleaseResolver", "Scraping failed for $targetUrl: ${e.message}")
            }
        }
        return null
    }

    private fun checkUrlExists(urlStr: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(urlStr)
                .head()
                .header("User-Agent", "Mozilla/5.0 DroidPort/1.0")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful || response.code == 302 || response.code == 301
        } catch (e: Exception) {
            false
        }
    }
}
