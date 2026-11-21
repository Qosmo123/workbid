package app.workbid.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

    private fun <T> listen(query: Query, map: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        val registration = query.addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            if (snap != null) trySend(snap.documents.map(map))
        }
        awaitClose { registration.remove() }
    }

    /** Listens to one document; sends null while it doesn't exist. */
    private fun <T> listenDoc(ref: DocumentReference, map: (DocumentSnapshot) -> T): Flow<T?> = callbackFlow {
        val registration = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            trySend(snap?.takeIf { it.exists() }?.let(map))
        }
        awaitClose { registration.remove() }
    }

    // Each query below matches a read rule in firestore.rules. People only download what they're part of,
    // so a launch costs a handful of reads instead of the whole database.

    /** Jobs anyone can bid on. */
    fun openJobs(): Flow<List<HomeJob>> =
        listen(db.collection("jobs").whereEqualTo("status", "open")) { it.toJob() }

    /** Jobs I posted (any status). */
    fun jobsPostedBy(uid: String): Flow<List<HomeJob>> =
        listen(db.collection("jobs").whereEqualTo("postedBy", uid)) { it.toJob() }

    /** Jobs I'm booked on as the contractor. */
    fun jobsBookedFor(uid: String): Flow<List<HomeJob>> =
        listen(db.collection("jobs").whereEqualTo("contractorId", uid)) { it.toJob() }

    /** One job, e.g. a job I bid on that went to someone else. */
    fun job(id: String): Flow<HomeJob?> = listenDoc(db.collection("jobs").document(id)) { it.toJob() }

    /** Bids I placed. */
    fun bidsBy(uid: String): Flow<List<Bid>> =
        listen(db.collection("bids").whereEqualTo("contractorId", uid)) { it.toBid() }

    /** Bids on jobs I posted. */
    fun bidsOnJobsOf(uid: String): Flow<List<Bid>> =
        listen(db.collection("bids").whereEqualTo("jobOwner", uid)) { it.toBid() }

    /** Work orders on jobs I'm part of. */
    fun workOrdersFor(uid: String): Flow<List<WorkOrder>> =
        listen(db.collection("workorders").whereArrayContains("parties", uid)) { it.toWorkOrder() }

    /** Receipts on jobs I'm part of. */
    fun receiptsFor(uid: String): Flow<List<Receipt>> =
        listen(db.collection("receipts").whereArrayContains("parties", uid)) { it.toReceipt() }

    /** A contractor's profile, or null if they haven't made one. Read once; profiles change rarely. */
    suspend fun profile(uid: String): Profile? =
        db.collection("profiles").document(uid).get().await().takeIf { it.exists() }?.toProfile()

    /** Messages on jobs I'm part of. */
    fun messagesFor(uid: String): Flow<List<Message>> =
        listen(db.collection("messages").whereArrayContains("parties", uid)) { it.toMessage() }

    /** True if admins/{uid} exists. The owner creates that document by hand in the Firebase console. */
    suspend fun isAdmin(uid: String): Boolean =
        db.collection("admins").document(uid).get().await().exists()

    /** All reports, for admins only. */
    fun reports(): Flow<List<Report>> = listen(db.collection("reports")) { it.toReport() }

    /** Every bid on one job, for admins removing a job. */
    suspend fun bidsOnJob(jobId: String): List<Bid> =
        db.collection("bids").whereEqualTo("jobId", jobId).get().await().documents.map { it.toBid() }

    /** People I've blocked. */
    fun blocked(uid: String): Flow<List<Blocked>> =
        listen(db.collection("users").document(uid).collection("blocked")) { it.toBlocked() }

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
