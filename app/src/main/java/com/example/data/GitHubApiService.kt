package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class GitHubAsset(
    @Json(name = "name") val name: String?,
    @Json(name = "browser_download_url") val browserDownloadUrl: String?,
    @Json(name = "size") val size: Long? = 0L,
    @Json(name = "content_type") val contentType: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "html_url") val htmlUrl: String?,
    @Json(name = "body") val body: String? = null,
    @Json(name = "assets") val assets: List<GitHubAsset>? = emptyList()
) {
    fun getApkAsset(): GitHubAsset? {
        return assets?.firstOrNull { asset ->
            val assetName = asset.name?.lowercase() ?: ""
            val url = asset.browserDownloadUrl?.lowercase() ?: ""
            assetName.endsWith(".apk") || url.contains(".apk") || asset.contentType == "application/vnd.android.package-archive"
        }
    }
}

@JsonClass(generateAdapter = true)
data class GitHubOwner(
    @Json(name = "avatar_url") val avatarUrl: String?
)

@JsonClass(generateAdapter = true)
data class GitHubRepoInfo(
    @Json(name = "name") val name: String?,
    @Json(name = "description") val description: String?,
    @Json(name = "owner") val owner: GitHubOwner?
)

interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getAllReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GitHubRelease>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepoInfo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepoInfo
}

object GitHubApiService {
    private val okHttpClient = okhttp3.OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "DroidPortHub/1.0 (Android)")
                .header("Accept", "application/vnd.github+json")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val api: GitHubApi = retrofit.create(GitHubApi::class.java)
}
