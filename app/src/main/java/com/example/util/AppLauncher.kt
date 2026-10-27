package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable? = null
)

object AppLauncher {

    fun launchApp(context: Context, packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            try {
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to launch $packageName", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "App $packageName is not installed on this device", Toast.LENGTH_LONG).show()
        }
    }

    fun openAppSettings(context: Context, packageName: String) {
        if (packageName.isBlank()) {
            Toast.makeText(context, "No application package linked to this port", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open settings for $packageName", Toast.LENGTH_SHORT).show()
        }
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            false
        }
    }

    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (e: Exception) {
            emptyList()
        }

        if (resolveInfos.isNotEmpty()) {
            return resolveInfos.mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val appInfo = activityInfo.applicationInfo ?: return@mapNotNull null
                // Filter out system apps - only include user installed apps
                val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystemApp) return@mapNotNull null

                val packageName = activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString().ifBlank { packageName }
                val icon = try { resolveInfo.loadIcon(pm) } catch (e: Exception) { null }
                InstalledAppInfo(packageName = packageName, appName = label, icon = icon)
            }.distinctBy { it.packageName }
             .sortedBy { it.appName.lowercase() }
        }

        val packages = pm.getInstalledPackages(0)
        return packages.mapNotNull { pkg ->
            if (pm.getLaunchIntentForPackage(pkg.packageName) == null) return@mapNotNull null
            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystemApp) return@mapNotNull null

            val label = appInfo.loadLabel(pm).toString()
            val icon = try { appInfo.loadIcon(pm) } catch (e: Exception) { null }
            InstalledAppInfo(packageName = pkg.packageName, appName = label, icon = icon)
        }.distinctBy { it.packageName }
         .sortedBy { it.appName.lowercase() }
    }
}
