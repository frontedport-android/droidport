package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.model.Port
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

object RomStorageManager {

    private const val PREFS_NAME = "rom_storage_prefs"
    private const val KEY_ROM_DIR_URI = "base_rom_directory_uri"

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun getSavedRomDirectoryUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_ROM_DIR_URI, null) ?: return null
        return try {
            val uri = Uri.parse(uriStr)
            // Verify persistable permission is still valid
            val persistedPermissions = context.contentResolver.persistedUriPermissions
            val hasPerm = persistedPermissions.any { it.uri == uri && it.isWritePermission }
            if (hasPerm) uri else uri // Return uri even if re-checking fails, SAF will validate
        } catch (e: Exception) {
            null
        }
    }

    fun saveRomDirectoryUri(context: Context, uri: Uri) {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            Log.w("RomStorageManager", "Could not take persistable URI permission: ${e.message}")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ROM_DIR_URI, uri.toString()).apply()
    }

    suspend fun downloadRom(
        context: Context,
        baseUri: Uri,
        port: Port,
        url: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: (folderName: String, fileName: String) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val rawGameTitle = port.realGameTitle.ifBlank { port.name }
            val safeFolderName = rawGameTitle.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "Game_ROM" }

            val baseDir = DocumentFile.fromTreeUri(context, baseUri)
            if (baseDir == null || !baseDir.canWrite()) {
                withContext(Dispatchers.Main) {
                    onError("No se tiene permiso de escritura en la carpeta seleccionada.")
                }
                return@withContext
            }

            // Create or find subfolder with game title
            var gameSubDir = baseDir.findFile(safeFolderName)
            if (gameSubDir == null || !gameSubDir.isDirectory) {
                gameSubDir = baseDir.createDirectory(safeFolderName)
            }
            if (gameSubDir == null) {
                withContext(Dispatchers.Main) {
                    onError("No se pudo crear la carpeta '$safeFolderName'")
                }
                return@withContext
            }

            // Determine file name and MIME type from URL
            val cleanUrl = url.trim()
            val rawFileName = cleanUrl.substringAfterLast("/").substringBefore("?").ifBlank { "${safeFolderName}_rom.zip" }
            val finalFileName = if (rawFileName.contains(".")) rawFileName else "${rawFileName}.zip"

            val mimeType = when {
                finalFileName.endsWith(".zip", ignoreCase = true) -> "application/zip"
                finalFileName.endsWith(".7z", ignoreCase = true) -> "application/x-7z-compressed"
                finalFileName.endsWith(".rar", ignoreCase = true) -> "application/x-rar-compressed"
                finalFileName.endsWith(".iso", ignoreCase = true) -> "application/x-iso9660-image"
                else -> "application/octet-stream"
            }

            var destFileDoc = gameSubDir.findFile(finalFileName)
            if (destFileDoc == null) {
                destFileDoc = gameSubDir.createFile(mimeType, finalFileName)
            }
            if (destFileDoc == null) {
                withContext(Dispatchers.Main) {
                    onError("No se pudo crear el archivo ROM en la carpeta.")
                }
                return@withContext
            }

            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 DroidPort/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    onError("Error de descarga HTTP ${response.code}")
                }
                return@withContext
            }

            val body = response.body
            if (body == null) {
                withContext(Dispatchers.Main) {
                    onError("Respuesta vacía del servidor al descargar la ROM")
                }
                return@withContext
            }

            val totalBytes = body.contentLength()
            val inputStream: InputStream = body.byteStream()
            val outputStream: OutputStream? = context.contentResolver.openOutputStream(destFileDoc.uri)

            if (outputStream == null) {
                withContext(Dispatchers.Main) {
                    onError("No se pudo abrir flujo de escritura para la ROM")
                }
                return@withContext
            }

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

            withContext(Dispatchers.Main) {
                onComplete(safeFolderName, finalFileName)
            }
        } catch (e: Exception) {
            Log.e("RomStorageManager", "Error downloading ROM", e)
            withContext(Dispatchers.Main) {
                onError("Error descargando ROM: ${e.localizedMessage ?: "Error desconocido"}")
            }
        }
    }
}
