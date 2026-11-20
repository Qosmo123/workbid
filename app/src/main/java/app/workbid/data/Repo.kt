package app.workbid.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper over Cloud Firestore.
 *
 * Errors are never swallowed: listeners close the flow with the error, and writes throw,
 * so the caller can tell the user what went wrong. Firestore's own offline cache queues
 * writes made without a connection and syncs them later.
 */
object Repo {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun <T> listen(collection: String, map: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        val registration = db.collection(collection).addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            if (snap != null) trySend(snap.documents.map(map))
        }
        awaitClose { registration.remove() }
    }

    fun jobs(): Flow<List<HomeJob>> = listen("jobs") { it.toJob() }
    fun bids(): Flow<List<Bid>> = listen("bids") { it.toBid() }
    fun workOrders(): Flow<List<WorkOrder>> = listen("workorders") { it.toWorkOrder() }
    fun receipts(): Flow<List<Receipt>> = listen("receipts") { it.toReceipt() }

    /** Adds a document with a generated ID and returns that ID. */
    suspend fun add(collection: String, data: Map<String, Any>): String =
        db.collection(collection).add(data).await().id

    suspend fun set(collection: String, id: String, data: Map<String, Any>) {
        db.collection(collection).document(id).set(data).await()
    }

    suspend fun update(collection: String, id: String, data: Map<String, Any>) {
        db.collection(collection).document(id).update(data).await()
    }

    suspend fun delete(collection: String, id: String) {
        db.collection(collection).document(id).delete().await()
    }

    suspend fun get(collection: String, id: String): DocumentSnapshot =
        db.collection(collection).document(id).get().await()

    /** Runs several writes as one all-or-nothing batch. */
    suspend fun batch(block: (batch: WriteBatch, db: FirebaseFirestore) -> Unit) {
        val b = db.batch()
        block(b, db)
        b.commit().await()
    }
}
