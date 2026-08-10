package com.example.data.api

import android.util.Log
import com.example.data.model.CashItemEntity
import com.example.data.model.CustomerProfileEntity
import com.example.data.model.InventoryItemEntity
import com.example.data.model.JobCardEntity
import com.example.data.model.ServiceAlertEntity
import com.example.data.model.ServiceMembershipEntity
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Mirrors the Room tables into Cloud Firestore and back.
 *
 * Documents live in root collections (jobCards, cashItems, inventoryItems, memberships,
 * customers, serviceAlerts) and are keyed `{garageId}_{roomId}` so two workshops can never
 * collide on Room's autoincrement ids. Every document also carries a `garageId` field, which
 * is what the security rules enforce and what [pullAll] filters on.
 */
object FirebaseSyncHelper {
    private const val TAG = "FirebaseSyncHelper"

    const val JOB_CARDS = "jobCards"
    const val CASH_ITEMS = "cashItems"
    const val INVENTORY_ITEMS = "inventoryItems"
    const val MEMBERSHIPS = "memberships"
    const val CUSTOMERS = "customers"
    const val SERVICE_ALERTS = "serviceAlerts"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private fun docId(id: Int): String = "${GarageSession.garageId}_$id"

    private fun canSync(): Boolean {
        if (!GarageSession.isReady) {
            Log.w(TAG, "Skipping sync: no active garage session yet")
            return false
        }
        return true
    }

    private fun write(collection: String, id: Int, data: HashMap<String, Any?>) {
        if (!canSync()) return
        data["garageId"] = GarageSession.garageId
        data["updatedAt"] = FieldValue.serverTimestamp()
        try {
            db.collection(collection)
                .document(docId(id))
                .set(data, SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "$collection/${docId(id)} saved") }
                .addOnFailureListener { e -> Log.e(TAG, "Error saving $collection/${docId(id)}", e) }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving $collection/$id", e)
        }
    }

    private fun remove(collection: String, id: Int) {
        if (!canSync()) return
        try {
            db.collection(collection)
                .document(docId(id))
                .delete()
                .addOnSuccessListener { Log.d(TAG, "$collection/${docId(id)} deleted") }
                .addOnFailureListener { e -> Log.e(TAG, "Error deleting $collection/${docId(id)}", e) }
        } catch (e: Exception) {
            Log.e(TAG, "Exception deleting $collection/$id", e)
        }
    }

    // --- Job Cards ---
    fun saveJobCard(job: JobCardEntity) = write(JOB_CARDS, job.id, hashMapOf(
        "id" to job.id,
        "customerName" to job.customerName,
        "customerPhone" to job.customerPhone,
        "vehicleName" to job.vehicleName,
        "vehiclePlate" to job.vehiclePlate,
        "symptoms" to job.symptoms,
        "diagnosis" to job.diagnosis,
        "status" to job.status,
        "tasks" to job.tasks,
        "materials" to job.materials,
        "serviceCharges" to job.serviceCharges,
        "partsCharges" to job.partsCharges,
        "taxes" to job.taxes,
        "totalAmount" to job.totalAmount,
        "creationTime" to job.creationTime
    ))

    fun deleteJobCard(id: Int) = remove(JOB_CARDS, id)

    // --- Cash Ledger ---
    fun saveCashItem(item: CashItemEntity) = write(CASH_ITEMS, item.id, hashMapOf(
        "id" to item.id,
        "type" to item.type,
        "amount" to item.amount,
        "category" to item.category,
        "paymentMode" to item.paymentMode,
        "description" to item.description,
        "timestamp" to item.timestamp
    ))

    fun deleteCashItem(id: Int) = remove(CASH_ITEMS, id)

    // --- Inventory ---
    fun saveInventoryItem(item: InventoryItemEntity) = write(INVENTORY_ITEMS, item.id, hashMapOf(
        "id" to item.id,
        "name" to item.name,
        "quantity" to item.quantity,
        "minStockAlert" to item.minStockAlert,
        "unitPrice" to item.unitPrice
    ))

    fun deleteInventoryItem(id: Int) = remove(INVENTORY_ITEMS, id)

    // --- Memberships ---
    fun saveMembership(membership: ServiceMembershipEntity) = write(MEMBERSHIPS, membership.id, hashMapOf(
        "id" to membership.id,
        "customerName" to membership.customerName,
        "customerPhone" to membership.customerPhone,
        "membershipType" to membership.membershipType,
        "monthlyWashLimit" to membership.monthlyWashLimit,
        "washesUsed" to membership.washesUsed,
        "renewalDate" to membership.renewalDate,
        "pendingBalance" to membership.pendingBalance
    ))

    fun deleteMembership(id: Int) = remove(MEMBERSHIPS, id)

    // --- Customer Profiles ---
    fun saveCustomerProfile(customer: CustomerProfileEntity) = write(CUSTOMERS, customer.id, hashMapOf(
        "id" to customer.id,
        "name" to customer.name,
        "phone" to customer.phone,
        "email" to customer.email,
        "vehicleName" to customer.vehicleName,
        "vehiclePlate" to customer.vehiclePlate,
        "address" to customer.address,
        "notes" to customer.notes,
        "creationTime" to customer.creationTime
    ))

    fun deleteCustomerProfile(id: Int) = remove(CUSTOMERS, id)

    // --- Service Alerts ---
    fun saveServiceAlert(alert: ServiceAlertEntity) = write(SERVICE_ALERTS, alert.id, hashMapOf(
        "id" to alert.id,
        "customerName" to alert.customerName,
        "customerPhone" to alert.customerPhone,
        "vehiclePlate" to alert.vehiclePlate,
        "serviceType" to alert.serviceType,
        "intervalKm" to alert.intervalKm,
        "dueDaysLeft" to alert.dueDaysLeft,
        "dueDateString" to alert.dueDateString,
        "dueTimestamp" to alert.dueTimestamp,
        "status" to alert.status,
        "reminderNotes" to alert.reminderNotes
    ))

    fun deleteServiceAlert(id: Int) = remove(SERVICE_ALERTS, id)

    // ------------------------------------------------------------------
    // Cloud -> local
    // ------------------------------------------------------------------

    /** Everything one workshop owns, as Room entities ready to be inserted. */
    data class CloudSnapshot(
        val jobCards: List<JobCardEntity> = emptyList(),
        val cashItems: List<CashItemEntity> = emptyList(),
        val inventoryItems: List<InventoryItemEntity> = emptyList(),
        val memberships: List<ServiceMembershipEntity> = emptyList(),
        val customers: List<CustomerProfileEntity> = emptyList(),
        val serviceAlerts: List<ServiceAlertEntity> = emptyList()
    ) {
        val isEmpty: Boolean
            get() = jobCards.isEmpty() && cashItems.isEmpty() && inventoryItems.isEmpty() &&
                memberships.isEmpty() && customers.isEmpty() && serviceAlerts.isEmpty()

        val total: Int
            get() = jobCards.size + cashItems.size + inventoryItems.size +
                memberships.size + customers.size + serviceAlerts.size
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }

    private suspend fun fetch(collection: String): List<Map<String, Any?>> = try {
        val snap: QuerySnapshot = db.collection(collection)
            .whereEqualTo("garageId", GarageSession.garageId)
            .get()
            .awaitResult()
        snap.documents.mapNotNull { it.data }
    } catch (e: Exception) {
        Log.e(TAG, "Failed pulling $collection", e)
        emptyList()
    }

    private fun Map<String, Any?>.int(key: String, fallback: Int = 0): Int =
        (this[key] as? Number)?.toInt() ?: fallback

    private fun Map<String, Any?>.long(key: String, fallback: Long = 0L): Long =
        (this[key] as? Number)?.toLong() ?: fallback

    private fun Map<String, Any?>.dbl(key: String, fallback: Double = 0.0): Double =
        (this[key] as? Number)?.toDouble() ?: fallback

    private fun Map<String, Any?>.str(key: String, fallback: String = ""): String =
        (this[key] as? String) ?: fallback

    /** Reads every collection this workshop owns. Returns an empty snapshot if not signed in. */
    suspend fun pullAll(): CloudSnapshot {
        if (!canSync()) return CloudSnapshot()

        val jobs = fetch(JOB_CARDS).map {
            JobCardEntity(
                id = it.int("id"),
                customerName = it.str("customerName"),
                customerPhone = it.str("customerPhone"),
                vehicleName = it.str("vehicleName"),
                vehiclePlate = it.str("vehiclePlate"),
                symptoms = it.str("symptoms"),
                diagnosis = it.str("diagnosis"),
                status = it.str("status", "OPEN"),
                tasks = it.str("tasks"),
                materials = it.str("materials"),
                serviceCharges = it.dbl("serviceCharges"),
                partsCharges = it.dbl("partsCharges"),
                taxes = it.dbl("taxes"),
                totalAmount = it.dbl("totalAmount"),
                creationTime = it.long("creationTime", System.currentTimeMillis())
            )
        }.filter { it.id > 0 }

        val cash = fetch(CASH_ITEMS).map {
            CashItemEntity(
                id = it.int("id"),
                type = it.str("type", "INCOME"),
                amount = it.dbl("amount"),
                category = it.str("category", "Other"),
                paymentMode = it.str("paymentMode", "Cash"),
                description = it.str("description"),
                timestamp = it.long("timestamp", System.currentTimeMillis())
            )
        }.filter { it.id > 0 }

        val inventory = fetch(INVENTORY_ITEMS).map {
            InventoryItemEntity(
                id = it.int("id"),
                name = it.str("name"),
                quantity = it.int("quantity"),
                minStockAlert = it.int("minStockAlert", 3),
                unitPrice = it.dbl("unitPrice")
            )
        }.filter { it.id > 0 && it.name.isNotBlank() }

        val memberships = fetch(MEMBERSHIPS).map {
            ServiceMembershipEntity(
                id = it.int("id"),
                customerName = it.str("customerName"),
                customerPhone = it.str("customerPhone"),
                membershipType = it.str("membershipType"),
                monthlyWashLimit = it.int("monthlyWashLimit", 4),
                washesUsed = it.int("washesUsed"),
                renewalDate = it.str("renewalDate"),
                pendingBalance = it.dbl("pendingBalance")
            )
        }.filter { it.id > 0 }

        val customers = fetch(CUSTOMERS).map {
            CustomerProfileEntity(
                id = it.int("id"),
                name = it.str("name"),
                phone = it.str("phone"),
                email = it.str("email"),
                vehicleName = it.str("vehicleName"),
                vehiclePlate = it.str("vehiclePlate"),
                address = it.str("address"),
                notes = it.str("notes"),
                creationTime = it.long("creationTime", System.currentTimeMillis())
            )
        }.filter { it.id > 0 }

        val alerts = fetch(SERVICE_ALERTS).map {
            ServiceAlertEntity(
                id = it.int("id"),
                customerName = it.str("customerName"),
                customerPhone = it.str("customerPhone"),
                vehiclePlate = it.str("vehiclePlate"),
                serviceType = it.str("serviceType"),
                intervalKm = it.int("intervalKm", 5000),
                dueDaysLeft = it.int("dueDaysLeft"),
                dueDateString = it.str("dueDateString"),
                dueTimestamp = it.long("dueTimestamp", System.currentTimeMillis()),
                status = it.str("status", "DUE_SOON"),
                reminderNotes = it.str("reminderNotes")
            )
        }.filter { it.id > 0 }

        return CloudSnapshot(jobs, cash, inventory, memberships, customers, alerts)
    }

    /** Pushes the whole local database up — used right after a fresh sign up. */
    fun pushAll(snapshot: CloudSnapshot) {
        if (!canSync()) return
        snapshot.jobCards.forEach { saveJobCard(it) }
        snapshot.cashItems.forEach { saveCashItem(it) }
        snapshot.inventoryItems.forEach { saveInventoryItem(it) }
        snapshot.memberships.forEach { saveMembership(it) }
        snapshot.customers.forEach { saveCustomerProfile(it) }
        snapshot.serviceAlerts.forEach { saveServiceAlert(it) }
        Log.d(TAG, "Pushed ${snapshot.total} local records to garage ${GarageSession.garageId}")
    }
}
