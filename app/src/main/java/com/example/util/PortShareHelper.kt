package com.example.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.model.Port

object PortShareHelper {

    fun sharePort(context: Context, port: Port) {
        try {
            val title = port.realGameTitle.ifBlank { port.name }
            val shareText = buildString {
                append("🎮 *${title}* (Android Port)\n\n")

                if (port.name.isNotBlank() && port.name != title) {
                    append("🏷️ Port: ${port.name}\n")
                }

                if (port.repoUrl.isNotBlank()) {
                    val fullRepo = if (port.repoUrl.startsWith("http://") || port.repoUrl.startsWith("https://")) {
                        port.repoUrl
                    } else {
                        "https://github.com/${port.repoUrl.removePrefix("/")}"
                    }
                    append("🔗 Repositorio GitHub:\n$fullRepo\n\n")
                }

                if (port.apkDownloadUrl.isNotBlank()) {
                    append("📥 Descarga directa APK:\n${port.apkDownloadUrl}\n\n")
                }

                if (port.romUrl.isNotBlank()) {
                    append("💾 Archivos ROM / Datos:\n${port.romUrl}\n\n")
                }

                if (port.installedVersion.isNotBlank()) {
                    append("📌 Versión recomendada/instalada: ${port.installedVersion}\n")
                }

                if (port.packageName.isNotBlank()) {
                    append("📱 Package ID: ${port.packageName}\n")
                }

                if (port.notes.isNotBlank()) {
                    append("\n📝 Notas e instrucciones:\n${port.notes}\n")
                }

                append("\nCompartido desde DroidPort Hub")
            }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                this.type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Port Android: $title")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }

            val chooser = Intent.createChooser(sendIntent, "Compartir Port con:")
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
