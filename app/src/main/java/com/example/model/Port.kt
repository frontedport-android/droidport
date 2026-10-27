package com.example.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.Timestamp

data class Port(
    @DocumentId val id: String = "",
    val name: String = "",
    val realGameTitle: String = "",    // Real game title e.g. "The Legend of Zelda: Link's Awakening"
    val repoUrl: String = "",          // e.g. "user/game-port-android"
    val packageName: String = "",      // e.g. "com.dev.game"
    val installedVersion: String = "", // e.g. "v1.0.0"
    val iconUrl: String = "",          // URL for game icon/banner
    val notes: String = "",            // Installation or save data notes
    val latestVersion: String = "",    // Fetched from GitHub API
    val hasUpdate: Boolean = false,    // Computed
    val apkDownloadUrl: String = "",   // Direct APK download link from GitHub release
    val romUrl: String = "",           // Direct URL for game ROM / data files
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "realGameTitle" to realGameTitle,
            "repoUrl" to repoUrl,
            "packageName" to packageName,
            "installedVersion" to installedVersion,
            "iconUrl" to iconUrl,
            "notes" to notes,
            "latestVersion" to latestVersion,
            "hasUpdate" to hasUpdate,
            "apkDownloadUrl" to apkDownloadUrl,
            "romUrl" to romUrl,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
    }
}
