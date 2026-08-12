package com.example.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiClient
import com.example.data.database.GarageDatabase
import com.example.data.model.CashItemEntity
import com.example.data.model.CustomerProfileEntity
import com.example.data.model.InventoryItemEntity
import com.example.data.model.JobCardEntity
import com.example.data.model.ServiceAlertEntity
import com.example.data.model.ServiceMembershipEntity
import com.example.data.model.TeamMemberEntity
import com.example.data.repository.GarageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class GarageViewModel(private val repository: GarageRepository) : ViewModel() {

    private val TAG = "GarageViewModel"

    // --- State Streams ---
    val jobCards = repository.allJobCards.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cashItems = repository.allCashItems.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val inventoryItems = repository.allInventoryItems.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val memberships = repository.allMemberships.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val customerProfiles = repository.allCustomerProfiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val serviceAlerts = repository.allServiceAlerts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val teamMembers = repository.allTeamMembers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Network & Security UI Signals ---
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    // Real installation id rather than a made-up token string. Set once the session is known.
    private val _fcmToken = MutableStateFlow("")
    val fcmToken: StateFlow<String> = _fcmToken.asStateFlow()

    private val _aiResult = MutableStateFlow("")
    val aiResult: StateFlow<String> = _aiResult.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow("")
    val lastSyncMessage: StateFlow<String> = _lastSyncMessage.asStateFlow()

    /**
     * No demo/seed data is inserted anywhere: a brand new workshop starts empty and only ever
     * shows what its owner actually enters, or what [syncWithCloud] restores from Firestore.
     */

    // --- Monitor Connectivity ---

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Tracks the *default* network rather than individual ones.
     *
     * The previous version flipped to offline inside `onLost` for any network that dropped, so
     * turning mobile data off while on Wi-Fi — or any Wi-Fi/data handover — left the banner stuck
     * on even though the phone was online. Every callback now re-reads the active network instead
     * of trusting a single per-network event.
     */
    fun startNetworkMonitoring(context: Context) {
        if (networkCallback != null) return // already watching

        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = cm

        fun refresh() {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            _isOffline.value = caps == null ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        refresh()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onUnavailable() = refresh()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
        }
        networkCallback = callback

        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            Log.e(TAG, "Could not register network callback", e)
            networkCallback = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkCallback?.let { cb ->
            runCatching { connectivityManager?.unregisterNetworkCallback(cb) }
        }
        networkCallback = null
    }

    // --- Job Card Actions ---
    fun addJobCard(job: JobCardEntity) {
        viewModelScope.launch {
            val assignedId = repository.insertJobCard(job)
            val finalJob = if (job.id == 0) job.copy(id = assignedId.toInt()) else job
            com.example.data.api.FirebaseSyncHelper.saveJobCard(finalJob)
        }
    }

    fun updateJobCardStatus(job: JobCardEntity, newStatus: String) {
        viewModelScope.launch {
            val updatedJob = job.copy(status = newStatus)
            repository.insertJobCard(updatedJob)
            com.example.data.api.FirebaseSyncHelper.saveJobCard(updatedJob)
        }
    }

    fun deleteJobCard(id: Int) {
        viewModelScope.launch {
            repository.deleteJobCardById(id)
            com.example.data.api.FirebaseSyncHelper.deleteJobCard(id)
        }
    }

    // --- Cash Ledger Actions ---
    fun addCashItem(item: CashItemEntity) {
        viewModelScope.launch {
            val assignedId = repository.insertCashItem(item)
            val finalItem = if (item.id == 0) item.copy(id = assignedId.toInt()) else item
            com.example.data.api.FirebaseSyncHelper.saveCashItem(finalItem)
        }
    }

    fun deleteCashItem(id: Int) {
        viewModelScope.launch {
            repository.deleteCashItemById(id)
            com.example.data.api.FirebaseSyncHelper.deleteCashItem(id)
        }
    }

    // --- Inventory Actions ---
    fun addInventoryItem(item: InventoryItemEntity) {
        viewModelScope.launch {
            val assignedId = repository.insertInventoryItem(item)
            val finalItem = if (item.id == 0) item.copy(id = assignedId.toInt()) else item
            com.example.data.api.FirebaseSyncHelper.saveInventoryItem(finalItem)
        }
    }

    fun deleteInventory(id: Int) {
        viewModelScope.launch {
            repository.deleteInventoryItemById(id)
            com.example.data.api.FirebaseSyncHelper.deleteInventoryItem(id)
        }
    }

    // --- Membership Actions ---
    fun addMembership(membership: ServiceMembershipEntity) {
        viewModelScope.launch {
            val assignedId = repository.insertMembership(membership)
            val finalMembership = if (membership.id == 0) membership.copy(id = assignedId.toInt()) else membership
            com.example.data.api.FirebaseSyncHelper.saveMembership(finalMembership)
        }
    }

    fun recordMembershipWash(membership: ServiceMembershipEntity) {
        if (membership.washesUsed < membership.monthlyWashLimit) {
            viewModelScope.launch {
                val updatedMembership = membership.copy(washesUsed = membership.washesUsed + 1)
                repository.insertMembership(updatedMembership)
                com.example.data.api.FirebaseSyncHelper.saveMembership(updatedMembership)
            }
        }
    }

    fun deleteMembership(id: Int) {
        viewModelScope.launch {
            repository.deleteMembershipById(id)
            com.example.data.api.FirebaseSyncHelper.deleteMembership(id)
        }
    }

    // --- Customer Profile Actions ---
    fun addCustomerProfile(customer: CustomerProfileEntity) {
        viewModelScope.launch {
            val assignedId = repository.insertCustomerProfile(customer)
            val finalCustomer = if (customer.id == 0) customer.copy(id = assignedId.toInt()) else customer
            com.example.data.api.FirebaseSyncHelper.saveCustomerProfile(finalCustomer)
        }
    }

    fun deleteCustomerProfile(id: Int) {
        viewModelScope.launch {
            repository.deleteCustomerProfileById(id)
            com.example.data.api.FirebaseSyncHelper.deleteCustomerProfile(id)
        }
    }

    // --- Service Alert Actions ---
    fun addServiceAlert(alert: ServiceAlertEntity) {
        viewModelScope.launch {
            val assignedId = repository.insertServiceAlert(alert)
            val finalAlert = if (alert.id == 0) alert.copy(id = assignedId.toInt()) else alert
            com.example.data.api.FirebaseSyncHelper.saveServiceAlert(finalAlert)
        }
    }

    fun deleteServiceAlert(id: Int) {
        viewModelScope.launch {
            repository.deleteServiceAlertById(id)
            com.example.data.api.FirebaseSyncHelper.deleteServiceAlert(id)
        }
    }

    fun updateServiceAlertStatus(id: Int, newStatus: String) {
        viewModelScope.launch {
            repository.updateServiceAlertStatus(id, newStatus)
            serviceAlerts.value.find { it.id == id }?.let {
                com.example.data.api.FirebaseSyncHelper.saveServiceAlert(it.copy(status = newStatus))
            }
        }
    }

    // --- Team Member Actions ---
    fun addTeamMember(member: TeamMemberEntity) {
        viewModelScope.launch {
            val assignedId = repository.insertTeamMember(member)
            val saved = if (member.id == 0) member.copy(id = assignedId.toInt()) else member
            com.example.data.api.FirebaseSyncHelper.saveTeamMember(saved)
        }
    }

    fun deleteTeamMember(id: Int) {
        viewModelScope.launch {
            repository.deleteTeamMemberById(id)
            com.example.data.api.FirebaseSyncHelper.deleteTeamMember(id)
        }
    }

    /**
     * Keeps exactly one owner row in step with the signed-in account.
     *
     * Must run *after* the cloud pull, otherwise it would create a second owner row while the
     * real one is still on its way down from Firestore. Any duplicates that already slipped
     * through are collapsed here.
     */
    private suspend fun reconcileOwnerTeamMember(name: String, phone: String) {
        if (name.isBlank()) return
        val owners = repository.allTeamMembers.first().filter { it.isOwner }

        // Collapse accidental duplicates down to the oldest row.
        owners.drop(1).forEach { extra ->
            repository.deleteTeamMemberById(extra.id)
            com.example.data.api.FirebaseSyncHelper.deleteTeamMember(extra.id)
        }

        val existing = owners.firstOrNull()
        if (existing == null) {
            addTeamMember(TeamMemberEntity(name = name, role = "Owner", phone = phone, isOwner = true))
        } else if (existing.name != name || existing.phone != phone) {
            addTeamMember(existing.copy(name = name, phone = phone))
        }
    }

    // --- Cloud Sync ---

    /**
     * Pulls this workshop's Firestore data into Room after sign in. When the cloud is empty
     * (a brand new garage) the locally seeded rows are uploaded instead, so both sides agree.
     */
    fun syncWithCloud(onDone: (pulled: Int, pushed: Int) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val session = com.example.data.api.GarageSession
                _fcmToken.value = if (session.isReady) "gg:${session.garageId}:${session.uid}" else ""
                // Room may still hold the previous account's rows from an earlier session on
                // this device. Drop them before syncing, or they would be shown as — and
                // uploaded into — the new workshop.
                if (session.localDataGarageId != session.garageId) {
                    repository.clearAllLocalData()
                    session.localDataGarageId = session.garageId
                    Log.d(TAG, "Local data belonged to another garage, wiped before sync")
                }

                val cloud = com.example.data.api.FirebaseSyncHelper.pullAll()
                if (!cloud.isEmpty) {
                    repository.applyCloudSnapshot(cloud)
                    Log.d(TAG, "Pulled ${cloud.total} records from Firestore")
                    _lastSyncMessage.value = "Restored ${cloud.total} records from cloud"
                    onDone(cloud.total, 0)
                } else {
                    // Nothing in the cloud: either a brand new workshop (nothing to do) or
                    // local edits made while offline that still need to go up.
                    val local = repository.currentSnapshot()
                    if (local.isEmpty) {
                        Log.d(TAG, "New workshop — starting with an empty book")
                        _lastSyncMessage.value = ""
                    } else {
                        com.example.data.api.FirebaseSyncHelper.pushAll(local)
                        Log.d(TAG, "Cloud empty, pushed ${local.total} local records")
                        _lastSyncMessage.value = "Uploaded ${local.total} records to cloud"
                    }
                    onDone(0, local.total)
                }
                reconcileOwnerTeamMember(session.ownerName, session.ownerPhone)
            } catch (e: Exception) {
                Log.e(TAG, "Cloud sync failed", e)
                _lastSyncMessage.value = "Sync failed: ${e.message}"
                onDone(0, 0)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /** Drops every local row. Called on sign out so the next account starts clean. */
    fun clearLocalData() {
        viewModelScope.launch {
            repository.clearAllLocalData()
            com.example.data.api.GarageSession.localDataGarageId = ""
        }
    }

    /** Re-uploads everything currently in Room. Used right after a fresh sign up. */
    fun pushLocalToCloud() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                com.example.data.api.FirebaseSyncHelper.pushAll(repository.currentSnapshot())
            } catch (e: Exception) {
                Log.e(TAG, "Initial push failed", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // --- Diagnostics with Gemini ---
    fun runAiDiagnostics(symptoms: String) {
        if (symptoms.isBlank()) {
            _aiResult.value = "Please describe the symptoms first."
            return
        }
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResult.value = "Analyzing vehicle symptoms with Garage Guru AI..."
            val result = GeminiClient.troubleshootSymptoms(symptoms)
            _aiResult.value = result
            _isAiLoading.value = false
        }
    }

    fun clearDiagnostics() {
        _aiResult.value = ""
    }

    // --- Simulate Camera Capture Return Base64 Image string ---
    fun simulateCameraCapture(onResult: (String) -> Unit) {
        // High quality mock Base64 representation of vehicle diagnostics image
        val simulatedBase64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg=="
        onResult(simulatedBase64)
    }

    // --- Downloader/Filesystem Management: PDF Saving ---
    fun saveInvoicePdf(job: JobCardEntity, context: Context): File? {
        val file = com.example.util.InvoicePdfGenerator.generatePdfFile(context, job)
        if (file != null) {
            Toast.makeText(context, "PDF Invoice saved: ${file.name}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Failed to generate PDF Invoice", Toast.LENGTH_SHORT).show()
        }
        return file
    }

    // --- Backup & Migration JSON exports/imports ---
    fun exportBackup(context: Context): String {
        return try {
            val backup = JSONObject()

            // Job cards array
            val jobsArray = JSONArray()
            jobCards.value.forEach {
                val obj = JSONObject()
                    .put("id", it.id)
                    .put("customerName", it.customerName)
                    .put("customerPhone", it.customerPhone)
                    .put("vehicleName", it.vehicleName)
                    .put("vehiclePlate", it.vehiclePlate)
                    .put("symptoms", it.symptoms)
                    .put("diagnosis", it.diagnosis)
                    .put("status", it.status)
                    .put("tasks", it.tasks)
                    .put("materials", it.materials)
                    .put("serviceCharges", it.serviceCharges)
                    .put("partsCharges", it.partsCharges)
                    .put("taxes", it.taxes)
                    .put("totalAmount", it.totalAmount)
                    .put("creationTime", it.creationTime)
                jobsArray.put(obj)
            }
            backup.put("jobCards", jobsArray)

            // Cash Ledger
            val cashArray = JSONArray()
            cashItems.value.forEach {
                val obj = JSONObject()
                    .put("id", it.id)
                    .put("type", it.type)
                    .put("amount", it.amount)
                    .put("category", it.category)
                    .put("paymentMode", it.paymentMode)
                    .put("description", it.description)
                    .put("timestamp", it.timestamp)
                cashArray.put(obj)
            }
            backup.put("cashItems", cashArray)

            // Inventory
            val invArray = JSONArray()
            inventoryItems.value.forEach {
                val obj = JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("quantity", it.quantity)
                    .put("minStockAlert", it.minStockAlert)
                    .put("unitPrice", it.unitPrice)
                invArray.put(obj)
            }
            backup.put("inventoryItems", invArray)

            // Memberships
            val memArray = JSONArray()
            memberships.value.forEach {
                val obj = JSONObject()
                    .put("id", it.id)
                    .put("customerName", it.customerName)
                    .put("customerPhone", it.customerPhone)
                    .put("membershipType", it.membershipType)
                    .put("monthlyWashLimit", it.monthlyWashLimit)
                    .put("washesUsed", it.washesUsed)
                    .put("renewalDate", it.renewalDate)
                    .put("pendingBalance", it.pendingBalance)
                memArray.put(obj)
            }
            backup.put("memberships", memArray)

            val jsonString = backup.toString(4)
            Log.d(TAG, "Exported successfully details: $jsonString")
            jsonString
        } catch (e: Exception) {
            Log.e(TAG, "Export Backup JSON Error", e)
            ""
        }
    }

    fun importBackup(jsonString: String, context: Context): Boolean {
        return try {
            val backup = JSONObject(jsonString)
            
            viewModelScope.launch {
                // Import Jobs
                if (backup.has("jobCards")) {
                    val arr = backup.getJSONArray("jobCards")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.insertJobCard(
                            JobCardEntity(
                                customerName = obj.optString("customerName", ""),
                                customerPhone = obj.optString("customerPhone", ""),
                                vehicleName = obj.optString("vehicleName", ""),
                                vehiclePlate = obj.optString("vehiclePlate", ""),
                                symptoms = obj.optString("symptoms", ""),
                                diagnosis = obj.optString("diagnosis", ""),
                                status = obj.optString("status", "OPEN"),
                                tasks = obj.optString("tasks", ""),
                                materials = obj.optString("materials", ""),
                                serviceCharges = obj.optDouble("serviceCharges", 0.0),
                                partsCharges = obj.optDouble("partsCharges", 0.0),
                                taxes = obj.optDouble("taxes", 0.0),
                                totalAmount = obj.optDouble("totalAmount", 0.0),
                                creationTime = obj.optLong("creationTime", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // Import Cash
                if (backup.has("cashItems")) {
                    val arr = backup.getJSONArray("cashItems")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.insertCashItem(
                            CashItemEntity(
                                type = obj.optString("type", "INCOME"),
                                amount = obj.optDouble("amount", 0.0),
                                category = obj.optString("category", "Service"),
                                paymentMode = obj.optString("paymentMode", "Cash"),
                                description = obj.optString("description", ""),
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }

                // Import Inventory
                if (backup.has("inventoryItems")) {
                    val arr = backup.getJSONArray("inventoryItems")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.insertInventoryItem(
                            InventoryItemEntity(
                                name = obj.optString("name", ""),
                                quantity = obj.optInt("quantity", 0),
                                minStockAlert = obj.optInt("minStockAlert", 5),
                                unitPrice = obj.optDouble("unitPrice", 0.0)
                            )
                        )
                    }
                }

                // Import Memberships
                if (backup.has("memberships")) {
                    val arr = backup.getJSONArray("memberships")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.insertMembership(
                            ServiceMembershipEntity(
                                customerName = obj.optString("customerName", ""),
                                customerPhone = obj.optString("customerPhone", ""),
                                membershipType = obj.optString("membershipType", ""),
                                monthlyWashLimit = obj.optInt("monthlyWashLimit", 4),
                                washesUsed = obj.optInt("washesUsed", 0),
                                renewalDate = obj.optString("renewalDate", "2026-07-01"),
                                pendingBalance = obj.optDouble("pendingBalance", 0.0)
                            )
                        )
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Import Backup JSON Error", e)
            false
        }
    }
}

class GarageViewModelFactory(private val repository: GarageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GarageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GarageViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
