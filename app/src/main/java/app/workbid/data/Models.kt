package app.workbid.data

import com.google.firebase.firestore.DocumentSnapshot

/** A job a homeowner has posted. Dates are stored as "yyyy-MM-dd" strings. */
data class HomeJob(
    val id: String = "",
    val ref: String = "",
    val title: String = "",
    val category: String = "",
    val area: String = "",
    val description: String = "",
    val budgetMin: Double = 0.0,
    val budgetMax: Double = 0.0,
    val timing: String = "",
    val preferDate: String = "",
    val status: String = "open",            // open | scheduled | in_progress | complete
    val postedBy: String = "",
    val postedByName: String = "",
    val createdAt: Long = 0,
    val acceptedBidId: String = "",
    val contractorId: String = "",
    val contractorName: String = "",
    val company: String = "",
    val price: Double = 0.0,
    val scheduledDate: String = "",
    val window: String = "",
    val completedAt: Long = 0,
)

data class Bid(
    val id: String = "",
    val jobId: String = "",
    val contractorId: String = "",
    val contractorName: String = "",
    val company: String = "",
    val amount: Double = 0.0,
    val days: Double = 1.0,
    val availableDate: String = "",
    val window: String = "",
    val message: String = "",
    val createdAt: Long = 0,
)

data class LineItem(val desc: String = "", val qty: Double = 0.0, val rate: Double = 0.0) {
    val total: Double get() = qty * rate
}

data class WorkOrder(
    val id: String = "",
    val jobId: String = "",
    val by: String = "",
    val number: Int = 1,
    val lines: List<LineItem> = emptyList(),
    val notes: String = "",
    val approved: Boolean = false,
    val createdAt: Long = 0,
) {
    val subtotal: Double get() = lines.sumOf { it.total }
}

data class Receipt(
    val id: String = "",
    val jobId: String = "",
    val by: String = "",
    val vendor: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val note: String = "",
    val createdAt: Long = 0,
)

// ---- Firestore mapping (manual, so a missing or oddly typed field never crashes the app) ----

private fun Any?.str(): String = this as? String ?: ""
private fun Any?.dbl(): Double = (this as? Number)?.toDouble() ?: 0.0
private fun Any?.lng(): Long = (this as? Number)?.toLong() ?: 0L

fun DocumentSnapshot.toJob() = HomeJob(
    id = id,
    ref = get("ref").str(),
    title = get("title").str(),
    category = get("category").str(),
    area = get("area").str(),
    description = get("description").str(),
    budgetMin = get("budgetMin").dbl(),
    budgetMax = get("budgetMax").dbl(),
    timing = get("timing").str(),
    preferDate = get("preferDate").str(),
    status = get("status").str().ifBlank { "open" },
    postedBy = get("postedBy").str(),
    postedByName = get("postedByName").str(),
    createdAt = get("createdAt").lng(),
    acceptedBidId = get("acceptedBidId").str(),
    contractorId = get("contractorId").str(),
    contractorName = get("contractorName").str(),
    company = get("company").str(),
    price = get("price").dbl(),
    scheduledDate = get("scheduledDate").str(),
    window = get("window").str(),
    completedAt = get("completedAt").lng(),
)

fun DocumentSnapshot.toBid() = Bid(
    id = id,
    jobId = get("jobId").str(),
    contractorId = get("contractorId").str(),
    contractorName = get("contractorName").str(),
    company = get("company").str(),
    amount = get("amount").dbl(),
    days = get("days").dbl().takeIf { it > 0 } ?: 1.0,
    availableDate = get("availableDate").str(),
    window = get("window").str(),
    message = get("message").str(),
    createdAt = get("createdAt").lng(),
)

fun DocumentSnapshot.toWorkOrder(): WorkOrder {
    val raw = get("lines") as? List<*> ?: emptyList<Any>()
    val lines = raw.mapNotNull { item ->
        val m = item as? Map<*, *> ?: return@mapNotNull null
        LineItem(m["desc"].str(), m["qty"].dbl(), m["rate"].dbl())
    }
    return WorkOrder(
        id = id,
        jobId = get("jobId").str(),
        by = get("by").str(),
        number = get("number").lng().toInt().coerceAtLeast(1),
        lines = lines,
        notes = get("notes").str(),
        approved = get("approved") as? Boolean ?: false,
        createdAt = get("createdAt").lng(),
    )
}

fun DocumentSnapshot.toReceipt() = Receipt(
    id = id,
    jobId = get("jobId").str(),
    by = get("by").str(),
    vendor = get("vendor").str(),
    amount = get("amount").dbl(),
    date = get("date").str(),
    note = get("note").str(),
    createdAt = get("createdAt").lng(),
)
