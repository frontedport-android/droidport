package com.example.util

object VersionComparator {

    /**
     * Compares latest version string from GitHub with installed version string.
     * Returns true if latest version is strictly newer than installed version.
     */
    fun isUpdateAvailable(latestVersion: String, installedVersion: String): Boolean {
        val latest = cleanVersion(latestVersion)
        val installed = cleanVersion(installedVersion)

        if (latest.isBlank()) return false
        if (installed.isBlank()) return true
        if (latest.equals(installed, ignoreCase = true)) return false

        val latestNumbers = extractNumbers(latest)
        val installedNumbers = extractNumbers(installed)

        if (latestNumbers.isNotEmpty() && installedNumbers.isNotEmpty()) {
            val maxLen = maxOf(latestNumbers.size, installedNumbers.size)
            for (i in 0 until maxLen) {
                val lVal = latestNumbers.getOrElse(i) { 0 }
                val iVal = installedNumbers.getOrElse(i) { 0 }
                if (lVal > iVal) return true
                if (lVal < iVal) return false
            }
            return false
        }

        return !latest.equals(installed, ignoreCase = true)
    }

    private fun cleanVersion(version: String): String {
        return version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .removePrefix("release-")
            .removePrefix("Release-")
            .trim()
    }

    private fun extractNumbers(cleanVer: String): List<Int> {
        return cleanVer.split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toIntOrNull() }
    }
}
