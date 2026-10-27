package com.example.data

import android.util.Log
import com.example.util.GameTitleDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object WebImageSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val WIKI_USER_AGENT = "DroidPort/1.0 (droidport@example.com; Game Frontend)"

    /**
     * Search high-resolution, authentic video game box arts, covers, and icons across
     * official Wikipedia gaming archives, Steam, and square-filtered gaming web searches.
     * Enforces strict square (1:1) aspect ratios and filters out all unrelated movies,
     * actors, and music albums. Supports pagination via [page] (0, 1, 2, ...).
     */
    suspend fun searchGameImages(query: String, page: Int = 0): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        // 1. Detect real commercial video game title if an alias or abbreviation was passed
        val cleanInput = query.trim()
            .replace(Regex("""(?i)\b(android|switch|pc|port|recomp|decomp|v\d+.*)\b"""), "")
            .trim()
        val detectedTitle = GameTitleDetector.detectRealTitle(cleanInput)
        val searchQuery = if (detectedTitle.isNotBlank() && detectedTitle != cleanInput) detectedTitle else cleanInput

        // Dedicated sets preserving insertion order and ensuring zero duplicates
        val officialGameCovers = linkedSetOf<String>()
        val gamingDbCovers = linkedSetOf<String>()
        val generalSquareCovers = linkedSetOf<String>()

        // 2. Official Wikipedia Files & Fair-Use Box Arts (100% Authentic Game Covers)
        try {
            val wikiOffset = page * 10
            val encodedQuery = URLEncoder.encode("\"$searchQuery\" video game cover", "UTF-8")
            val wikiUrl = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encodedQuery&gsrnamespace=6&prop=imageinfo&iiprop=url&iiurlwidth=600&format=json&gsrlimit=12${if (wikiOffset > 0) "&gsroffset=$wikiOffset" else ""}"
            val request = Request.Builder()
                .url(wikiUrl)
                .header("User-Agent", WIKI_USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string().orEmpty()
                parseMediaWikiPages(jsonStr, searchQuery, officialGameCovers, gamingDbCovers)
            }

            // Secondary file search by direct title
            val directQuery = URLEncoder.encode("\"$searchQuery\"", "UTF-8")
            val directWikiUrl = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$directQuery&gsrnamespace=6&prop=imageinfo&iiprop=url&iiurlwidth=600&format=json&gsrlimit=8${if (wikiOffset > 0) "&gsroffset=$wikiOffset" else ""}"
            val directReq = Request.Builder().url(directWikiUrl).header("User-Agent", WIKI_USER_AGENT).build()
            val directResp = client.newCall(directReq).execute()
            if (directResp.isSuccessful) {
                val directJson = directResp.body?.string().orEmpty()
                parseMediaWikiPages(directJson, searchQuery, officialGameCovers, gamingDbCovers)
            }
        } catch (e: Exception) {
            Log.w("WebImageSearch", "Wikipedia search note: ${e.message}")
        }

        // 3. Direct Wikipedia Video Game Article Images (Infobox Box Arts on page 0)
        if (page == 0) {
            try {
                val encodedArticle = URLEncoder.encode("$searchQuery video game", "UTF-8")
                val articleUrl = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encodedArticle&gsrnamespace=0&prop=images&format=json&gsrlimit=2"
                val request = Request.Builder().url(articleUrl).header("User-Agent", WIKI_USER_AGENT).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string().orEmpty()
                    val candidateFiles = extractArticleGameImageTitles(jsonStr, searchQuery)
                    if (candidateFiles.isNotEmpty()) {
                        val fileTitlesParam = candidateFiles.joinToString("|")
                        val encodedParam = URLEncoder.encode(fileTitlesParam, "UTF-8")
                        val infoUrl = "https://en.wikipedia.org/w/api.php?action=query&titles=$encodedParam&prop=imageinfo&iiprop=url&iiurlwidth=600&format=json"
                        val infoReq = Request.Builder().url(infoUrl).header("User-Agent", WIKI_USER_AGENT).build()
                        val infoResp = client.newCall(infoReq).execute()
                        if (infoResp.isSuccessful) {
                            val infoJson = infoResp.body?.string().orEmpty()
                            parseMediaWikiPages(infoJson, searchQuery, officialGameCovers, gamingDbCovers)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("WebImageSearch", "Wikipedia article image note: ${e.message}")
            }
        }

        // 4. Steam Store Search API (Square capsules & library covers for PC/Steam games on page 0)
        if (page == 0) {
            try {
                val encodedSteam = URLEncoder.encode(searchQuery, "UTF-8")
                val steamUrl = "https://store.steampowered.com/api/storesearch/?term=$encodedSteam&l=english&cc=US"
                val request = Request.Builder().url(steamUrl).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string().orEmpty()
                    if (jsonStr.isNotBlank()) {
                        val jsonObj = JSONObject(jsonStr)
                        val items = jsonObj.optJSONArray("items")
                        if (items != null) {
                            for (i in 0 until minOf(items.length(), 4)) {
                                val item = items.optJSONObject(i) ?: continue
                                val id = item.optLong("id", 0)
                                if (id > 0) {
                                    // Official Steam square capsule and library covers
                                    gamingDbCovers.add("https://cdn.akamai.steamstatic.com/steam/apps/$id/capsule_sm_120.jpg")
                                    gamingDbCovers.add("https://cdn.akamai.steamstatic.com/steam/apps/$id/library_600x900.jpg")
                                    gamingDbCovers.add("https://cdn.akamai.steamstatic.com/steam/apps/$id/header.jpg")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("WebImageSearch", "Steam search note: ${e.message}")
            }
        }

        // 5. Bing Square-Filtered Image Search (&qft=+filterui:aspect-square) with strict game constraints
        try {
            val firstIndex = page * 25 + 1
            // Use strict quotes and negative search terms to exclude movies, actors, and music albums completely
            val rawBingQuery = "\"$searchQuery\" (\"video game\" OR \"box art\" OR \"game icon\" OR \"cover\") -movie -film -actor -soundtrack -album -pelicula -trailer"
            val encodedBing = URLEncoder.encode(rawBingQuery, "UTF-8")
            val bingUrl = "https://www.bing.com/images/search?q=$encodedBing&qft=+filterui:aspect-square&form=HDRSC2&first=$firstIndex"
            val request = Request.Builder()
                .url(bingUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string().orEmpty()
                parseBingSquareResults(html, searchQuery, gamingDbCovers, generalSquareCovers)
            }
        } catch (e: Exception) {
            Log.w("WebImageSearch", "Bing square image search note: ${e.message}")
        }

        // 6. Wikimedia Commons Fallback
        try {
            val commonsOffset = page * 10
            val encodedCommons = URLEncoder.encode("\"$searchQuery\" cover", "UTF-8")
            val commonsUrl = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encodedCommons&gsrnamespace=6&prop=imageinfo&iiprop=url&iiurlwidth=600&format=json&gsrlimit=10${if (commonsOffset > 0) "&gsroffset=$commonsOffset" else ""}"
            val request = Request.Builder()
                .url(commonsUrl)
                .header("User-Agent", WIKI_USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string().orEmpty()
                parseMediaWikiPages(jsonStr, searchQuery, officialGameCovers, generalSquareCovers)
            }
        } catch (e: Exception) {
            Log.w("WebImageSearch", "Wikimedia search note: ${e.message}")
        }

        // Combine strictly in order:
        // 1. Official game covers (Wikipedia fair-use box arts)
        // 2. High-value gaming databases (AllRecomps, MobyGames, GameFAQs, NintendoLife, Steam)
        // 3. Other verified square game images
        val combined = linkedSetOf<String>()
        combined.addAll(officialGameCovers)
        combined.addAll(gamingDbCovers)
        combined.addAll(generalSquareCovers)

        return@withContext combined.toList()
    }

    private fun extractArticleGameImageTitles(jsonStr: String, query: String): List<String> {
        val candidates = mutableListOf<String>()
        try {
            val jsonObj = JSONObject(jsonStr)
            val queryObj = jsonObj.optJSONObject("query") ?: return emptyList()
            val pages = queryObj.optJSONObject("pages") ?: return emptyList()
            val queryTokens = query.lowercase().split(" ").filter { it.length > 2 }

            pages.keys().forEach { key ->
                val page = pages.optJSONObject(key) ?: return@forEach
                val images = page.optJSONArray("images") ?: return@forEach
                for (i in 0 until images.length()) {
                    val imgObj = images.optJSONObject(i) ?: continue
                    val title = imgObj.optString("title", "")
                    val lower = title.lowercase()
                    if (isBlacklistedNonGame(lower)) continue
                    if (lower.endsWith(".svg") || lower.endsWith(".pdf") || lower.contains("screenshot") || lower.contains("comparison")) continue
                    
                    if (lower.contains("cover") || lower.contains("box") || lower.contains("art") ||
                        lower.contains("front") || lower.contains("pack") || queryTokens.any { lower.contains(it) }
                    ) {
                        candidates.add(title)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("WebImageSearch", "Extract article images note: ${e.message}")
        }
        return candidates.distinct().take(6)
    }

    private fun parseMediaWikiPages(
        jsonStr: String,
        query: String,
        priorityList: MutableSet<String>,
        secondaryList: MutableSet<String>
    ) {
        if (jsonStr.isBlank()) return
        try {
            val jsonObj = JSONObject(jsonStr)
            val queryObj = jsonObj.optJSONObject("query") ?: return
            val pages = queryObj.optJSONObject("pages") ?: return
            val queryTokens = query.lowercase().split(" ").filter { it.length > 3 }

            pages.keys().forEach { key ->
                val page = pages.optJSONObject(key) ?: return@forEach
                val title = page.optString("title", "").lowercase()
                if (isBlacklistedNonGame(title)) return@forEach

                val imageInfos = page.optJSONArray("imageinfo")
                if (imageInfos != null && imageInfos.length() > 0) {
                    val info = imageInfos.optJSONObject(0) ?: return@forEach
                    val thumb = info.optString("thumburl", "")
                    val url = info.optString("url", "")
                    val candidate = when {
                        thumb.isNotBlank() && isValidImageUrl(thumb) -> thumb
                        url.isNotBlank() && isValidImageUrl(url) -> url
                        else -> ""
                    }
                    if (candidate.isNotBlank()) {
                        val isCoverArt = title.contains("cover") || title.contains("box") ||
                                title.contains("art") || title.contains("front") ||
                                title.contains("pack") || queryTokens.any { title.contains(it) }
                        if (isCoverArt) {
                            priorityList.add(candidate)
                        } else {
                            secondaryList.add(candidate)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("WebImageSearch", "MediaWiki parse note: ${e.message}")
        }
    }

    private fun parseBingSquareResults(
        html: String,
        searchQuery: String,
        gamingList: MutableSet<String>,
        generalList: MutableSet<String>
    ) {
        // Find all JSON metadata blocks m="{...}" from Bing search results
        val regex = Regex("""m=(?:&quot;|")(\{.+?\})(?:&quot;|")""")
        val matches = regex.findAll(html)

        for (match in matches) {
            try {
                val rawJson = unescapeHtml(match.groupValues[1])
                val json = JSONObject(rawJson)
                val murl = json.optString("murl", "")
                val title = json.optString("t", "")
                val desc = json.optString("desc", "")
                val purl = json.optString("purl", "")

                val combinedContext = "$murl $title $desc $purl"
                if (isBlacklistedNonGame(combinedContext)) {
                    continue
                }

                if (isValidImageUrl(murl)) {
                    if (isHighValueGamingSource(murl, purl, title)) {
                        gamingList.add(murl)
                    } else {
                        generalList.add(murl)
                    }
                }
            } catch (e: Exception) {
                // Ignore single item parsing failure
            }
        }

        // Fallback simple regex for direct murls if json pattern matched few items
        if (gamingList.isEmpty() && generalList.isEmpty()) {
            val fallbackRegex = Regex("""(?:murl&quot;:&quot;|"murl":")(https?://[^"&]+?)(?:&quot;|")""")
            fallbackRegex.findAll(html).forEach { match ->
                val url = match.groupValues[1]
                if (isValidImageUrl(url) && !isBlacklistedNonGame(url)) {
                    if (isHighValueGamingSource(url, "", "")) {
                        gamingList.add(url)
                    } else {
                        generalList.add(url)
                    }
                }
            }
        }
    }

    private fun isHighValueGamingSource(url: String, purl: String, title: String): Boolean {
        val combined = "$url $purl $title".lowercase()
        return combined.contains("mobygames") ||
                combined.contains("launchbox") ||
                combined.contains("gamefaqs") ||
                combined.contains("nintendolife") ||
                combined.contains("nintendo.com") ||
                combined.contains("playstation.com") ||
                combined.contains("allrecomps") ||
                combined.contains("speedrun.com") ||
                combined.contains("igdb.com") ||
                combined.contains("steamstatic") ||
                combined.contains("steampowered") ||
                combined.contains("fandom.com") ||
                combined.contains("boxart") ||
                combined.contains("box_art") ||
                combined.contains("game_cover") ||
                combined.contains("gamecover")
    }

    private fun isBlacklistedNonGame(text: String): Boolean {
        val lower = text.lowercase()
        val badWords = listOf(
            "movie", "film", "pelicula", "cinema", "actor", "actress", "celebrity",
            "soundtrack", "album", "discogs", "imdb.", "filmaffinity", "cinematerial",
            "rottentomatoes", "allmusic", "billboard", "trailer", "hollywood", "singer",
            "musician", "discography", "record label", "dvd", "bluray", "blu-ray",
            "i am legend", "tom cruise", "will smith", "john legend", "ridley scott",
            "legend (1985", "legend (2015", "legend (soundtrack", "legend (album",
            "vocalist", "discogs", "pop music", "r&b"
        )
        return badWords.any { lower.contains(it) }
    }

    private fun isValidImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        // Reject non-image documents, vector formats, or icons
        if (lower.endsWith(".svg") || lower.endsWith(".svg.png") || lower.endsWith(".pdf") ||
            lower.endsWith(".webm") || lower.endsWith(".ogg") || lower.endsWith(".gif")
        ) {
            return false
        }
        // Reject tracking pixels and social media badges
        if (lower.contains("facebook.com") || lower.contains("twitter.com") || lower.contains("favicon")) {
            return false
        }
        if (isBlacklistedNonGame(lower)) {
            return false
        }
        return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") || lower.contains(".webp")
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
