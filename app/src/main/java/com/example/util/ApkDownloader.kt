package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.Port
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object ApkDownloader {

    private fun getApkDirectory(context: Context): File {
        val baseDir = context.getExternalFilesDir("apks") ?: File(context.filesDir, "apks")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    fun getDownloadedApkFile(context: Context, port: Port): File? {
        val downloadsDir = getApkDirectory(context)
        val cleanName = port.name.ifBlank { "game_port" }.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val file = File(downloadsDir, "${cleanName}.apk")
        if (file.exists() && file.length() > 1024L) {
            return file
        }
        // Fallback check in cache dir for backward compatibility
        val cacheFile = File(File(context.cacheDir, "apks"), "${cleanName}.apk")
        return if (cacheFile.exists() && cacheFile.length() > 1024L) cacheFile else null
    }

    fun isApkDownloaded(context: Context, port: Port): Boolean {
        return getDownloadedApkFile(context, port) != null
    }

    fun deleteDownloadedApk(context: Context, port: Port) {
        getDownloadedApkFile(context, port)?.delete()
    }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadApk(
        context: Context,
        port: Port,
        url: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: (apkFile: File, detectedPackageName: String?) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 DroidPort/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    onError("Download failed with HTTP ${response.code}")
                }
                return@withContext
            }

            val body = response.body
            if (body == null) {
                withContext(Dispatchers.Main) {
                    onError("Empty response body from server")
                }
                return@withContext
            }

            val totalBytes = body.contentLength()
            val downloadsDir = getApkDirectory(context)
            val cleanName = port.name.ifBlank { "game_port" }.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val destinationFile = File(downloadsDir, "${cleanName}.apk")
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(destinationFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0f
                withContext(Dispatchers.Main) {
                    onProgress(progress, totalRead, totalBytes)
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // Parse APK package info directly from the downloaded APK binary
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(destinationFile.absolutePath, 0)
            val detectedPackageName = packageInfo?.packageName

            withContext(Dispatchers.Main) {
                onComplete(destinationFile, detectedPackageName)
            }
        } catch (e: Exception) {
            Log.e("ApkDownloader", "Download error", e)
            withContext(Dispatchers.Main) {
                onError("Download error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() < 1024L) {
                Log.e("ApkDownloader", "Invalid or non-existent APK file")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e("ApkDownloader", "Failed to start install intent", e)
        }
    }
}
