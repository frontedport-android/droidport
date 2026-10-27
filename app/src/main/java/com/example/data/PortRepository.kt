package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.Port
import com.example.model.Review
import com.example.util.AdminConfig
import com.example.util.LocalPortBackup
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PortRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    /**
     * Observes ports visible to the user:
     * - Admins see all ports.
     * - Regular users see all admin ports (isAdminPort == true) plus their own private ports (userId == currentUserId).
     */
    fun observePorts(currentUserEmail: String?, currentUserId: String?): Flow<List<Port>> {
        val isAdmin = AdminConfig.isAdmin(currentUserEmail)

        return if (isAdmin) {
            flow {
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
                                Log.w("PortRepository", "Snapshot note on ports (admin): ${e.message}")
                                emit(emptyList())
                            }
                    )
                } catch (e: Exception) {
                    Log.w("PortRepository", "Error in admin observePorts flow: ${e.message}")
                    emit(emptyList())
                }
            }
        } else {
            // For regular users, combine admin ports and user's private ports
            val adminPortsFlow: Flow<List<Port>> = flow {
                try {
                    emitAll(
                        db.collection("ports")
                            .whereEqualTo("isAdminPort", true)
                            .snapshots()
                            .map { snapshot -> snapshot.toObjects(Port::class.java) }
                            .catch { e ->
                                Log.w("PortRepository", "Snapshot note on admin ports: ${e.message}")
                                emit(emptyList())
                            }
                    )
                } catch (e: Exception) {
                    emit(emptyList())
                }
            }

            val userPortsFlow: Flow<List<Port>> = if (!currentUserId.isNullOrBlank()) {
                flow {
                    try {
                        emitAll(
                            db.collection("ports")
                                .whereEqualTo("userId", currentUserId)
                                .snapshots()
                                .map { snapshot -> snapshot.toObjects(Port::class.java) }
                                .catch { e ->
                                    Log.w("PortRepository", "Snapshot note on user ports: ${e.message}")
                                    emit(emptyList())
                                }
                        )
                    } catch (e: Exception) {
                        emit(emptyList())
                    }
                }
            } else {
                flowOf(emptyList())
            }

            combine(adminPortsFlow, userPortsFlow) { adminList, userList ->
                (adminList + userList).distinctBy { it.id.ifBlank { it.repoUrl.ifBlank { it.name } } }
            }
        }
    }

    suspend fun addPort(port: Port, context: Context) {
        val portsRef = db.collection("ports")
        val payload = port.toMap() + mapOf(
            "createdAt" to FieldValue.serverTimestamp()
        )
        val docRef = portsRef.add(payload).await()
        val portWithId = port.copy(id = docRef.id)
        LocalPortBackup.saveLocalPort(context, portWithId)
    }

    suspend fun updatePort(port: Port, context: Context) {
        require(port.id.isNotBlank()) { "Port ID must not be blank for update" }
        val docRef = db.collection("ports").document(port.id)
        val payload = port.toMap()
        docRef.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
        LocalPortBackup.saveLocalPort(context, port)
    }

    suspend fun deletePort(portId: String, context: Context) {
        require(portId.isNotBlank()) { "Port ID must not be blank for deletion" }
        db.collection("ports").document(portId).delete().await()
        LocalPortBackup.removeLocalPort(context, portId)
    }

    /**
     * Automatically syncs ports created locally while offline/unauthenticated to Firestore.
     * Also claims any legacy ports in Firestore when an admin logs in.
     */
    suspend fun syncLocalAndLegacyPorts(user: FirebaseUser, context: Context): Int {
        val isAdmin = AdminConfig.isAdmin(user.email)
        var syncedCount = 0

        try {
            // 1. If admin, check for legacy ports in Firestore (empty userId or missing isAdminPort)
            if (isAdmin) {
                try {
                    val allDocs = db.collection("ports").get().await()
                    for (doc in allDocs.documents) {
                        val portUserId = doc.getString("userId") ?: ""
                        val isAdminPort = doc.getBoolean("isAdminPort") ?: false
                        if (portUserId.isBlank() || !isAdminPort) {
                            doc.reference.update(
                                mapOf(
                                    "isAdminPort" to true,
                                    "userId" to user.uid,
                                    "userEmail" to (user.email ?: "")
                                )
                            ).await()
                            syncedCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.w("PortRepository", "Legacy ports migration notice: ${e.message}")
                }
            }

            // 2. Sync ports from LocalPortBackup to Firestore if not already present
            val localPorts = LocalPortBackup.getAllLocalPorts(context)
            if (localPorts.isNotEmpty()) {
                val existingSnapshot = try {
                    if (isAdmin) {
                        db.collection("ports").get().await()
                    } else {
                        db.collection("ports").whereEqualTo("userId", user.uid).get().await()
                    }
                } catch (e: Exception) {
                    null
                }

                val existingRepos = existingSnapshot?.documents?.mapNotNull {
                    it.getString("repoUrl")?.lowercase()?.trim()
                }?.toSet() ?: emptySet()

                val existingNames = existingSnapshot?.documents?.mapNotNull {
                    it.getString("name")?.lowercase()?.trim()
                }?.toSet() ?: emptySet()

                for (localPort in localPorts) {
                    val repoKey = localPort.repoUrl.lowercase().trim()
                    val nameKey = localPort.name.lowercase().trim()

                    val isAlreadyUploaded = (repoKey.isNotBlank() && existingRepos.contains(repoKey)) ||
                            (nameKey.isNotBlank() && existingNames.contains(nameKey))

                    if (!isAlreadyUploaded) {
                        val toUpload = localPort.copy(
                            userId = user.uid,
                            userEmail = user.email ?: "",
                            isAdminPort = isAdmin
                        )
                        val docRef = db.collection("ports").add(toUpload.toMap() + mapOf(
                            "createdAt" to FieldValue.serverTimestamp()
                        )).await()
                        LocalPortBackup.saveLocalPort(context, toUpload.copy(id = docRef.id))
                        syncedCount++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PortRepository", "Error during local-to-cloud sync: ${e.message}", e)
        }

        return syncedCount
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
