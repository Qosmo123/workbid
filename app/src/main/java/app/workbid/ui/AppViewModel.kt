package app.workbid.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.workbid.data.Bid
import app.workbid.data.HomeJob
import app.workbid.data.LineItem
import app.workbid.data.Receipt
import app.workbid.data.Repo
import app.workbid.data.WorkOrder
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = FirebaseAuth.getInstance()
    private val prefs = app.getSharedPreferences("workbid", Context.MODE_PRIVATE)

    /** Only ever the signed-in Firebase user's ID. */
    var uid by mutableStateOf(auth.currentUser?.uid)
        private set
    var myName by mutableStateOf(auth.currentUser?.displayName.orEmpty())
        private set
    @set:JvmName("setRoleInternal")
    var role by mutableStateOf(prefs.getString("role", "home") ?: "home")
        private set
    var busy by mutableStateOf(false)
        private set

    var jobs by mutableStateOf(listOf<HomeJob>())
        private set
    var bids by mutableStateOf(listOf<Bid>())
        private set
    var workOrders by mutableStateOf(listOf<WorkOrder>())
        private set
    var receipts by mutableStateOf(listOf<Receipt>())
        private set

    /** One-off message shown in a snackbar, then cleared by the UI. */
    var message by mutableStateOf<String?>(null)

    private var listeners: List<Job> = emptyList()

    init {
        // Earlier versions stored a fake offline identity here. Remove it so it can never be used.
        prefs.edit().remove("local_uid").remove("local_name").remove("local_email").apply()
        if (uid != null) {
            startListening()
            viewModelScope.launch { runCatching { ensureProfile(null) } }
        }
    }

    val isHomeowner: Boolean get() = role == "home"

    fun setRole(r: String) {
        role = r
        prefs.edit().putString("role", r).apply()
    }

    fun lastCompany(): String = prefs.getString("company", "") ?: ""

    // ---------- lookups ----------
    fun job(id: String) = jobs.find { it.id == id }
    fun bidsFor(jobId: String) = bids.filter { it.jobId == jobId }.sortedBy { it.amount }
    fun myBid(jobId: String) = bids.find { it.jobId == jobId && it.contractorId == uid }
    fun workOrdersFor(jobId: String) = workOrders.filter { it.jobId == jobId }.sortedBy { it.createdAt }
    fun receiptsFor(jobId: String) = receipts.filter { it.jobId == jobId }.sortedBy { it.date }

    // ---------- auth ----------
    fun signIn(email: String, password: String) = authAction {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Sign-in failed. Try again.")
        uid = user.uid
        myName = user.displayName.orEmpty()
        ensureProfile(email.trim())
        startListening()
    }

    fun signUp(name: String, email: String, password: String) = authAction {
        val cleanName = name.trim()
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Account was not created. Try again.")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()).await()
        uid = user.uid
        myName = cleanName
        Repo.set("users", user.uid, mapOf("name" to cleanName, "createdAt" to System.currentTimeMillis()))
        startListening()
    }

    /**
     * Makes sure users/{uid} exists and uses its name as the display name.
     * The Firestore rules check that names on jobs and bids match this document.
     */
    private suspend fun ensureProfile(email: String?) {
        val me = uid ?: return
        val doc = Repo.get("users", me)
        val stored = doc.getString("name").orEmpty()
        if (stored.isNotBlank()) {
            myName = stored
        } else {
            val name = myName.ifBlank {
                (email ?: auth.currentUser?.email.orEmpty()).substringBefore("@")
                    .replaceFirstChar { it.uppercase() }.ifBlank { "Workbid user" }
            }.take(60)
            Repo.set("users", me, mapOf("name" to name, "createdAt" to System.currentTimeMillis()))
            myName = name
        }
    }

    private fun authAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: Exception) {
                // A half-finished sign-in must not leave the app looking signed in.
                if (auth.currentUser == null) uid = null
                message = authError(e)
            } finally {
                busy = false
            }
        }
    }

    private fun authError(e: Exception): String = when (e) {
        is FirebaseAuthInvalidUserException -> "No account with that email. Check it or create an account."
        is FirebaseAuthWeakPasswordException -> "Pick a stronger password (at least 6 characters)."
        is FirebaseAuthInvalidCredentialsException -> "Email or password is wrong."
        is FirebaseAuthUserCollisionException -> "An account with that email already exists. Sign in instead."
        is FirebaseNetworkException -> "No connection. Check your internet and try again."
        is FirebaseTooManyRequestsException -> "Too many attempts. Wait a few minutes and try again."
        else -> e.localizedMessage ?: "Sign-in failed. Try again."
    }

    fun signOut() {
        listeners.forEach { it.cancel() }
        listeners = emptyList()
        auth.signOut()
        uid = null
        myName = ""
        jobs = emptyList(); bids = emptyList(); workOrders = emptyList(); receipts = emptyList()
    }

    private fun <T> collect(flow: Flow<List<T>>, assign: (List<T>) -> Unit): Job = viewModelScope.launch {
        flow.catch { message = "Live updates stopped: ${it.localizedMessage ?: "connection lost"}" }
            .collect { assign(it) }
    }

    private fun startListening() {
        listeners.forEach { it.cancel() }
        listeners = listOf(
            collect(Repo.jobs()) { jobs = it },
            collect(Repo.bids()) { bids = it },
            collect(Repo.workOrders()) { workOrders = it },
            collect(Repo.receipts()) { receipts = it },
        )
    }

    // ---------- actions ----------
    /** Runs a write and reports the real outcome: [ok] only if it succeeded. */
    private fun act(ok: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            message = try {
                block()
                ok
            } catch (e: Exception) {
                writeError(e)
            }
        }
    }

    private fun writeError(e: Exception): String {
        if (e is FirebaseFirestoreException) return when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "That change wasn't allowed. The job may have changed. Go back and try again."
            FirebaseFirestoreException.Code.NOT_FOUND -> "That item no longer exists."
            FirebaseFirestoreException.Code.UNAVAILABLE -> "No connection. Check your internet and try again."
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Your sign-in expired. Sign out and back in."
            else -> "Couldn't save: ${e.localizedMessage ?: e.code.name}"
        }
        return "Couldn't save: ${e.localizedMessage ?: "unknown error"}"
    }

    fun postJob(
        title: String, category: String, area: String, description: String,
        budgetMin: Double, budgetMax: Double, timing: String, preferDate: String,
    ) {
        val me = uid ?: return
        val now = System.currentTimeMillis()
        act("Job posted") {
            Repo.add(
                "jobs", mapOf(
                    "ref" to "WB-" + (now % 100000).toString().padStart(5, '0'),
                    "title" to title.trim(),
                    "category" to category,
                    "area" to area.trim(),
                    "description" to description.trim(),
                    "budgetMin" to budgetMin,
                    "budgetMax" to budgetMax,
                    "timing" to timing,
                    "preferDate" to preferDate,
                    "status" to "open",
                    "postedBy" to me,
                    "postedByName" to myName,
                    "createdAt" to now,
                    "acceptedBidId" to "",
                    "contractorId" to "",
                )
            )
        }
    }

    fun placeBid(
        job: HomeJob, company: String, amount: Double, days: Double,
        availableDate: String, window: String, note: String,
    ) {
        val me = uid ?: return
        prefs.edit().putString("company", company.trim()).apply()
        // Fields a contractor may change on their own bid while the job is open.
        val editable = mapOf(
            "company" to company.trim(),
            "amount" to amount,
            "days" to days,
            "availableDate" to availableDate,
            "window" to window,
            "message" to note.trim(),
            "createdAt" to System.currentTimeMillis(),
        )
        val existing = myBid(job.id)
        act(if (existing != null) "Bid updated" else "Bid sent") {
            if (existing != null) {
                Repo.update("bids", existing.id, editable)
            } else {
                // One bid per contractor per job: the ID is fixed, and the rules require it.
                Repo.set(
                    "bids", "${job.id}_$me",
                    editable + mapOf("jobId" to job.id, "contractorId" to me, "contractorName" to myName),
                )
            }
        }
    }

    fun withdrawBid(bid: Bid) = act("Bid withdrawn") { Repo.delete("bids", bid.id) }

    fun acceptBid(job: HomeJob, bid: Bid) = act("Booked ${bid.company} for ${fdate(bid.availableDate)}") {
        Repo.update(
            "jobs", job.id, mapOf(
                "status" to "scheduled",
                "acceptedBidId" to bid.id,
                "contractorId" to bid.contractorId,
                "contractorName" to bid.contractorName,
                "company" to bid.company,
                "price" to bid.amount,
                "scheduledDate" to bid.availableDate,
                "window" to bid.window,
            )
        )
    }

    fun updateSchedule(job: HomeJob, date: String, window: String) = act("Booking updated") {
        Repo.update("jobs", job.id, mapOf("scheduledDate" to date, "window" to window))
    }

    fun setStatus(job: HomeJob, status: String) =
        act(if (status == "complete") "Job marked complete" else "Work started") {
            val data = if (status == "complete")
                mapOf("status" to status, "completedAt" to System.currentTimeMillis())
            else mapOf("status" to status)
            Repo.update("jobs", job.id, data)
        }

    fun addWorkOrder(job: HomeJob, lines: List<LineItem>, notes: String) {
        val me = uid ?: return
        act("Work order sent") {
            Repo.add(
                "workorders", mapOf(
                    "jobId" to job.id,
                    "by" to me,
                    "number" to workOrdersFor(job.id).size + 1,
                    "lines" to lines.map { mapOf("desc" to it.desc, "qty" to it.qty, "rate" to it.rate) },
                    "notes" to notes.trim(),
                    "approved" to false,
                    "createdAt" to System.currentTimeMillis(),
                )
            )
        }
    }

    fun approveWorkOrder(wo: WorkOrder) = act("Work order approved") {
        Repo.update("workorders", wo.id, mapOf("approved" to true, "approvedAt" to System.currentTimeMillis()))
    }

    fun addReceipt(job: HomeJob, vendor: String, amount: Double, date: String, note: String) {
        val me = uid ?: return
        act("Receipt added") {
            Repo.add(
                "receipts", mapOf(
                    "jobId" to job.id,
                    "by" to me,
                    "vendor" to vendor.trim(),
                    "amount" to Math.round(amount * 100) / 100.0,
                    "date" to date,
                    "note" to note.trim(),
                    "createdAt" to System.currentTimeMillis(),
                )
            )
        }
    }

    /** Deletes an open job and its bids together, so a failure can't leave stray bids behind. */
    fun deleteJob(job: HomeJob) {
        val jobBids = bidsFor(job.id)
        act("Job deleted") {
            Repo.batch { b, db ->
                jobBids.forEach { b.delete(db.collection("bids").document(it.id)) }
                b.delete(db.collection("jobs").document(job.id))
            }
        }
    }
}
