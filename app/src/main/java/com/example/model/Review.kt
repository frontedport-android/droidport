package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class Review(
    @DocumentId val id: String = "",
    val userName: String = "",
    val rating: Float = 5f, // 1.0 to 5.0
    val comment: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "userName" to userName,
            "rating" to rating,
            "comment" to comment
        )
    }
}
