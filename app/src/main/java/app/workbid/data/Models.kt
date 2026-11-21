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
    /** Sales tax rate copied from the accepted bid (0.05 = GST 5%). */
    val taxRate: Double = DEFAULT_TAX,
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
    /** Sales tax the contractor charges on top of [amount]; 0 if not registered. */
    val taxRate: Double = DEFAULT_TAX,
)

/** Someone I've blocked. Stored at users/{me}/blocked/{uid}, readable only by me. */
data class Blocked(val uid: String = "", val name: String = "", val createdAt: Long = 0)

/**
 * A contractor's public profile, at profiles/{uid}. Everything here is self-reported;
 * the app says so wherever it's shown.
 */
data class Profile(
    val uid: String = "",
    val company: String = "",
    val about: String = "",
    val trades: List<String> = emptyList(),
    val serviceArea: String = "",
    val years: Int = 0,
    val licence: String = "",
    val insured: Boolean = false,
    val updatedAt: Long = 0,
)

/** A message between a homeowner and the contractor booked on their job. */
data class Message(
    val id: String = "",
    val jobId: String = "",
    val by: String = "",
    val text: String = "",
    val createdAt: Long = 0,
)

/** GST 5%, for older bids and jobs saved before tax was chosen per bid. */
const val DEFAULT_TAX = 0.05

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
private fun Any?.tax(): Double = (this as? Number)?.toDouble() ?: DEFAULT_TAX

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
    taxRate = get("taxRate").tax(),
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
    taxRate = get("taxRate").tax(),
)

fun DocumentSnapshot.toProfile() = Profile(
    uid = id,
    company = get("company").str(),
    about = get("about").str(),
    trades = (get("trades") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
    serviceArea = get("serviceArea").str(),
    years = get("years").lng().toInt(),
    licence = get("licence").str(),
    insured = get("insured") as? Boolean ?: false,
    updatedAt = get("updatedAt").lng(),
)

fun DocumentSnapshot.toMessage() = Message(id, get("jobId").str(), get("by").str(), get("text").str(), get("createdAt").lng())

fun DocumentSnapshot.toBlocked() = Blocked(id, get("name").str(), get("createdAt").lng())

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
