package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.Port
import com.example.model.Review
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PortRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observePorts(): Flow<List<Port>> = flow {
        try {
            emitAll(
                db.collection("ports")
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(Port::class.java)
                    }
                    .catch { e ->
                        if (e is CancellationException && e.message?.contains("Error getting Query snapshot") != true) {
                            throw e
                        }
                        Log.w("PortRepository", "Snapshot note on ports: ${e.message}")
                        emit(emptyList())
                    }
            )
        } catch (e: CancellationException) {
            if (e.message?.contains("Error getting Query snapshot") == true) {
                emit(emptyList())
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.w("PortRepository", "Error in observePorts flow: ${e.message}")
            emit(emptyList())
        }
    }

    suspend fun addPort(port: Port) {
        val portsRef = db.collection("ports")
        val payload = port.toMap() + mapOf(
            "createdAt" to FieldValue.serverTimestamp()
        )
        portsRef.add(payload).await()
    }

    suspend fun updatePort(port: Port) {
        require(port.id.isNotBlank()) { "Port ID must not be blank for update" }
        val docRef = db.collection("ports").document(port.id)
        val payload = port.toMap()
        docRef.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    suspend fun deletePort(portId: String) {
        require(portId.isNotBlank()) { "Port ID must not be blank for deletion" }
        db.collection("ports").document(portId).delete().await()
    }

    fun observeReviews(portId: String): Flow<List<Review>> = flow {
        if (portId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        try {
            emitAll(
                db.collection("ports").document(portId).collection("reviews")
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(Review::class.java)
                    }
                    .catch { e ->
                        if (e is CancellationException && e.message?.contains("Error getting Query snapshot") != true) {
                            throw e
                        }
                        Log.w("PortRepository", "Snapshot note on reviews for $portId: ${e.message}")
                        emit(emptyList())
                    }
            )
        } catch (e: CancellationException) {
            if (e.message?.contains("Error getting Query snapshot") == true) {
                emit(emptyList())
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.w("PortRepository", "Error in observeReviews flow for $portId: ${e.message}")
            emit(emptyList())
        }
    }

    suspend fun addReview(portId: String, review: Review) {
        require(portId.isNotBlank()) { "Port ID must not be blank for adding review" }
        val reviewsRef = db.collection("ports").document(portId).collection("reviews")
        val payload = review.toMap() + mapOf(
            "createdAt" to FieldValue.serverTimestamp()
        )
        reviewsRef.add(payload).await()
    }
}
