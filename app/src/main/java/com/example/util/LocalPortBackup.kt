package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.Port
import org.json.JSONArray
import org.json.JSONObject

object LocalPortBackup {
    private const val PREFS_NAME = "droidport_local_backup_prefs"
    private const val KEY_LOCAL_PORTS = "local_ports_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun saveLocalPort(context: Context, port: Port) {
        try {
            val list = getAllLocalPorts(context).toMutableList()
            val existingIndex = list.indexOfFirst {
                (it.id.isNotBlank() && it.id == port.id) ||
                (it.repoUrl.isNotBlank() && it.repoUrl.equals(port.repoUrl, ignoreCase = true)) ||
                (it.name.isNotBlank() && it.name.equals(port.name, ignoreCase = true))
            }
            if (existingIndex >= 0) {
                list[existingIndex] = port
            } else {
                list.add(port)
            }
            saveAll(context, list)
        } catch (e: Exception) {
            Log.e("LocalPortBackup", "Failed to save local port: ${e.message}", e)
        }
    }

    @Synchronized
    fun removeLocalPort(context: Context, portId: String) {
        try {
            val list = getAllLocalPorts(context).filterNot { it.id == portId }
            saveAll(context, list)
        } catch (e: Exception) {
            Log.e("LocalPortBackup", "Failed to remove local port: ${e.message}", e)
        }
    }

    @Synchronized
    fun getAllLocalPorts(context: Context): List<Port> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_LOCAL_PORTS, null) ?: return emptyList()
        val result = mutableListOf<Port>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(
                    Port(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", ""),
                        realGameTitle = obj.optString("realGameTitle", ""),
                        repoUrl = obj.optString("repoUrl", ""),
                        packageName = obj.optString("packageName", ""),
                        installedVersion = obj.optString("installedVersion", ""),
                        iconUrl = obj.optString("iconUrl", ""),
                        notes = obj.optString("notes", ""),
                        latestVersion = obj.optString("latestVersion", ""),
                        hasUpdate = obj.optBoolean("hasUpdate", false),
                        apkDownloadUrl = obj.optString("apkDownloadUrl", ""),
                        romUrl = obj.optString("romUrl", ""),
                        userId = obj.optString("userId", ""),
                        userEmail = obj.optString("userEmail", ""),
                        isAdminPort = obj.optBoolean("isAdminPort", false)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("LocalPortBackup", "Error reading local ports backup: ${e.message}", e)
        }
        return result
    }

    private fun saveAll(context: Context, ports: List<Port>) {
        val jsonArray = JSONArray()
        for (port in ports) {
            val obj = JSONObject()
            obj.put("id", port.id)
            obj.put("name", port.name)
            obj.put("realGameTitle", port.realGameTitle)
            obj.put("repoUrl", port.repoUrl)
            obj.put("packageName", port.packageName)
            obj.put("installedVersion", port.installedVersion)
            obj.put("iconUrl", port.iconUrl)
            obj.put("notes", port.notes)
            obj.put("latestVersion", port.latestVersion)
            obj.put("hasUpdate", port.hasUpdate)
            obj.put("apkDownloadUrl", port.apkDownloadUrl)
            obj.put("romUrl", port.romUrl)
            obj.put("userId", port.userId)
            obj.put("userEmail", port.userEmail)
            obj.put("isAdminPort", port.isAdminPort)
            jsonArray.put(obj)
        }
        getPrefs(context).edit().putString(KEY_LOCAL_PORTS, jsonArray.toString()).apply()
    }
}
