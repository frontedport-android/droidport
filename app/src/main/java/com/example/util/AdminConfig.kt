package com.example.util

object AdminConfig {
    val ADMIN_EMAILS = setOf(
        "frontedport@gmail.com",
        "jdnmcuba2016@gmail.com",
        "jdnm2021@gmail.com"
    )

    fun isAdmin(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val clean = email.trim().lowercase()
        return ADMIN_EMAILS.any { it.equals(clean, ignoreCase = true) }
    }
}
