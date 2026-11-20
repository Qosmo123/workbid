package app.workbid.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Wrapper over Cloud Firestore with automatic local fallback when API key / Firebase is unconfigured. */
object Repo {
    private val db: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance()
        } catch (_: Exception) {
            null
        }

    // Local in-memory state fallback
    private val localJobs = MutableStateFlow<List<HomeJob>>(initialDemoJobs())
    private val localBids = MutableStateFlow<List<Bid>>(initialDemoBids())
    private val localWorkOrders = MutableStateFlow<List<WorkOrder>>(emptyList())
    private val localReceipts = MutableStateFlow<List<Receipt>>(emptyList())

    private fun <T> listen(
        collection: String,
        localFlow: MutableStateFlow<List<T>>,
        map: (DocumentSnapshot) -> T
    ): Flow<List<T>> = callbackFlow {
        val firestore = db
        if (firestore == null) {
            val job = MainScope().launch {
                localFlow.collect { trySend(it) }
            }
            awaitClose { job.cancel() }
            return@callbackFlow
        }

        var registration: ListenerRegistration? = null
        try {
            registration = firestore.collection(collection).addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(localFlow.value)
                    return@addSnapshotListener
                }
                trySend(snap.documents.map(map))
            }
        } catch (_: Exception) {
            trySend(localFlow.value)
        }

        val job = MainScope().launch {
            localFlow.collect { trySend(it) }
        }

        awaitClose {
            registration?.remove()
            job.cancel()
        }
    }

    fun jobs(): Flow<List<HomeJob>> = listen("jobs", localJobs) { it.toJob() }
    fun bids(): Flow<List<Bid>> = listen("bids", localBids) { it.toBid() }
    fun workOrders(): Flow<List<WorkOrder>> = listen("workorders", localWorkOrders) { it.toWorkOrder() }
    fun receipts(): Flow<List<Receipt>> = listen("receipts", localReceipts) { it.toReceipt() }

    suspend fun add(collection: String, data: Map<String, Any>) {
        val id = "local_" + System.currentTimeMillis() + "_" + (100..999).random()
        try {
            db?.collection(collection)?.add(data)?.await()
        } catch (_: Exception) { }
        addToLocal(collection, id, data)
    }

    suspend fun set(collection: String, id: String, data: Map<String, Any>) {
        try {
            db?.collection(collection)?.document(id)?.set(data)?.await()
        } catch (_: Exception) { }
        updateLocal(collection, id, data)
    }

    suspend fun update(collection: String, id: String, data: Map<String, Any>) {
        try {
            db?.collection(collection)?.document(id)?.update(data)?.await()
        } catch (_: Exception) { }
        updateLocal(collection, id, data)
    }

    suspend fun delete(collection: String, id: String) {
        try {
            db?.collection(collection)?.document(id)?.delete()?.await()
        } catch (_: Exception) { }
        deleteFromLocal(collection, id)
    }

    private fun addToLocal(collection: String, id: String, data: Map<String, Any>) {
        when (collection) {
            "jobs" -> localJobs.value = listOf(mapToJob(id, data)) + localJobs.value
            "bids" -> localBids.value = localBids.value + mapToBid(id, data)
            "workorders" -> localWorkOrders.value = localWorkOrders.value + mapToWorkOrder(id, data)
            "receipts" -> localReceipts.value = localReceipts.value + mapToReceipt(id, data)
        }
    }

    private fun updateLocal(collection: String, id: String, data: Map<String, Any>) {
        when (collection) {
            "jobs" -> localJobs.value = localJobs.value.map { if (it.id == id) applyDataToJob(it, data) else it }
            "bids" -> localBids.value = localBids.value.map { if (it.id == id) applyDataToBid(it, data) else it }
            "workorders" -> localWorkOrders.value = localWorkOrders.value.map { if (it.id == id) applyDataToWorkOrder(it, data) else it }
            "receipts" -> localReceipts.value = localReceipts.value.map { if (it.id == id) applyDataToReceipt(it, data) else it }
        }
    }

    private fun deleteFromLocal(collection: String, id: String) {
        when (collection) {
            "jobs" -> localJobs.value = localJobs.value.filter { it.id != id }
            "bids" -> localBids.value = localBids.value.filter { it.id != id }
            "workorders" -> localWorkOrders.value = localWorkOrders.value.filter { it.id != id }
            "receipts" -> localReceipts.value = localReceipts.value.filter { it.id != id }
        }
    }

    private fun mapToJob(id: String, map: Map<String, Any>): HomeJob = HomeJob(
        id = id,
        ref = map["ref"] as? String ?: "WB-00001",
        title = map["title"] as? String ?: "",
        category = map["category"] as? String ?: "",
        area = map["area"] as? String ?: "",
        description = map["description"] as? String ?: "",
        budgetMin = (map["budgetMin"] as? Number)?.toDouble() ?: 0.0,
        budgetMax = (map["budgetMax"] as? Number)?.toDouble() ?: 0.0,
        timing = map["timing"] as? String ?: "",
        preferDate = map["preferDate"] as? String ?: "",
        status = map["status"] as? String ?: "open",
        postedBy = map["postedBy"] as? String ?: "",
        postedByName = map["postedByName"] as? String ?: "",
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        acceptedBidId = map["acceptedBidId"] as? String ?: "",
        contractorId = map["contractorId"] as? String ?: "",
        contractorName = map["contractorName"] as? String ?: "",
        company = map["company"] as? String ?: "",
        price = (map["price"] as? Number)?.toDouble() ?: 0.0,
        scheduledDate = map["scheduledDate"] as? String ?: "",
        window = map["window"] as? String ?: "",
        completedAt = (map["completedAt"] as? Number)?.toLong() ?: 0L,
    )

    private fun applyDataToJob(job: HomeJob, map: Map<String, Any>): HomeJob = job.copy(
        status = map["status"] as? String ?: job.status,
        acceptedBidId = map["acceptedBidId"] as? String ?: job.acceptedBidId,
        contractorId = map["contractorId"] as? String ?: job.contractorId,
        contractorName = map["contractorName"] as? String ?: job.contractorName,
        company = map["company"] as? String ?: job.company,
        price = (map["price"] as? Number)?.toDouble() ?: job.price,
        scheduledDate = map["scheduledDate"] as? String ?: job.scheduledDate,
        window = map["window"] as? String ?: job.window,
        completedAt = (map["completedAt"] as? Number)?.toLong() ?: job.completedAt,
    )

    private fun mapToBid(id: String, map: Map<String, Any>): Bid = Bid(
        id = id,
        jobId = map["jobId"] as? String ?: "",
        contractorId = map["contractorId"] as? String ?: "",
        contractorName = map["contractorName"] as? String ?: "",
        company = map["company"] as? String ?: "",
        amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
        days = (map["days"] as? Number)?.toDouble() ?: 1.0,
        availableDate = map["availableDate"] as? String ?: "",
        window = map["window"] as? String ?: "",
        message = map["message"] as? String ?: "",
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
    )

    private fun applyDataToBid(bid: Bid, map: Map<String, Any>): Bid = bid.copy(
        company = map["company"] as? String ?: bid.company,
        amount = (map["amount"] as? Number)?.toDouble() ?: bid.amount,
        days = (map["days"] as? Number)?.toDouble() ?: bid.days,
        availableDate = map["availableDate"] as? String ?: bid.availableDate,
        window = map["window"] as? String ?: bid.window,
        message = map["message"] as? String ?: bid.message,
    )

    private fun mapToWorkOrder(id: String, map: Map<String, Any>): WorkOrder {
        val raw = map["lines"] as? List<*> ?: emptyList<Any>()
        val lines = raw.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            LineItem(
                desc = m["desc"] as? String ?: "",
                qty = (m["qty"] as? Number)?.toDouble() ?: 0.0,
                rate = (m["rate"] as? Number)?.toDouble() ?: 0.0,
            )
        }
        return WorkOrder(
            id = id,
            jobId = map["jobId"] as? String ?: "",
            by = map["by"] as? String ?: "",
            number = (map["number"] as? Number)?.toInt() ?: 1,
            lines = lines,
            notes = map["notes"] as? String ?: "",
            approved = map["approved"] as? Boolean ?: false,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        )
    }

    private fun applyDataToWorkOrder(wo: WorkOrder, map: Map<String, Any>): WorkOrder = wo.copy(
        approved = map["approved"] as? Boolean ?: wo.approved,
    )

    private fun mapToReceipt(id: String, map: Map<String, Any>): Receipt = Receipt(
        id = id,
        jobId = map["jobId"] as? String ?: "",
        by = map["by"] as? String ?: "",
        vendor = map["vendor"] as? String ?: "",
        amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
        date = map["date"] as? String ?: "",
        note = map["note"] as? String ?: "",
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
    )

    private fun applyDataToReceipt(rec: Receipt, map: Map<String, Any>): Receipt = rec.copy(
        vendor = map["vendor"] as? String ?: rec.vendor,
        amount = (map["amount"] as? Number)?.toDouble() ?: rec.amount,
        date = map["date"] as? String ?: rec.date,
        note = map["note"] as? String ?: rec.note,
    )

    private fun initialDemoJobs(): List<HomeJob> = listOf(
        HomeJob(
            id = "demo_job_1",
            ref = "WB-10203",
            title = "Bathroom Vanity & Tile Installation",
            category = "Plumbing",
            area = "Downtown",
            description = "Need a new 36-inch vanity installed along with floor tile replacement.",
            budgetMin = 800.0,
            budgetMax = 1500.0,
            timing = "Next week",
            preferDate = "2025-04-15",
            status = "open",
            postedBy = "demo_homeowner",
            postedByName = "Alex Morgan",
            createdAt = System.currentTimeMillis() - 86400000,
        )
    )

    private fun initialDemoBids(): List<Bid> = listOf(
        Bid(
            id = "demo_bid_1",
            jobId = "demo_job_1",
            contractorId = "demo_contractor",
            contractorName = "Sam Rivers",
            company = "Apex Plumbing & Tile",
            amount = 1150.0,
            days = 2.0,
            availableDate = "2025-04-15",
            window = "8:00 AM - 12:00 PM",
            message = "We can supply top-tier materials and finish within 2 days.",
            createdAt = System.currentTimeMillis() - 43200000,
        )
    )
}
