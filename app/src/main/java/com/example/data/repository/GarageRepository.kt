package com.example.data.repository

import com.example.data.api.FirebaseSyncHelper
import com.example.data.database.GarageDao
import com.example.data.model.CashItemEntity
import com.example.data.model.CustomerProfileEntity
import com.example.data.model.InventoryItemEntity
import com.example.data.model.JobCardEntity
import com.example.data.model.ServiceAlertEntity
import com.example.data.model.ServiceMembershipEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class GarageRepository(private val dao: GarageDao) {

    // --- Flows ---
    val allJobCards: Flow<List<JobCardEntity>> = dao.getAllJobCards()
    val allCashItems: Flow<List<CashItemEntity>> = dao.getAllCashItems()
    val allInventoryItems: Flow<List<InventoryItemEntity>> = dao.getAllInventoryItems()
    val allMemberships: Flow<List<ServiceMembershipEntity>> = dao.getAllMemberships()
    val allCustomerProfiles: Flow<List<CustomerProfileEntity>> = dao.getAllCustomerProfiles()
    val allServiceAlerts: Flow<List<ServiceAlertEntity>> = dao.getAllServiceAlerts()

    // --- Suspend Functions ---
    suspend fun getJobCardById(id: Int): JobCardEntity? = dao.getJobCardById(id)
    suspend fun insertJobCard(job: JobCardEntity): Long = dao.insertJobCard(job)
    suspend fun deleteJobCardById(id: Int) = dao.deleteJobCardById(id)

    suspend fun insertCashItem(item: CashItemEntity): Long = dao.insertCashItem(item)
    suspend fun deleteCashItemById(id: Int) = dao.deleteCashItemById(id)

    suspend fun insertInventoryItem(item: InventoryItemEntity): Long = dao.insertInventoryItem(item)
    suspend fun deleteInventoryItemById(id: Int) = dao.deleteInventoryItemById(id)

    suspend fun insertMembership(membership: ServiceMembershipEntity): Long = dao.insertMembership(membership)
    suspend fun deleteMembershipById(id: Int) = dao.deleteMembershipById(id)

    suspend fun insertCustomerProfile(profile: CustomerProfileEntity): Long = dao.insertCustomerProfile(profile)
    suspend fun deleteCustomerProfileById(id: Int) = dao.deleteCustomerProfileById(id)

    suspend fun insertServiceAlert(alert: ServiceAlertEntity): Long = dao.insertServiceAlert(alert)
    suspend fun deleteServiceAlertById(id: Int) = dao.deleteServiceAlertById(id)
    suspend fun updateServiceAlertStatus(id: Int, status: String) = dao.updateServiceAlertStatus(id, status)

    // --- Cloud <-> local bridging ---

    /** Writes everything Firestore returned into Room. Ids match, so REPLACE keeps rows in step. */
    suspend fun applyCloudSnapshot(snapshot: FirebaseSyncHelper.CloudSnapshot) {
        snapshot.jobCards.forEach { dao.insertJobCard(it) }
        snapshot.cashItems.forEach { dao.insertCashItem(it) }
        snapshot.inventoryItems.forEach { dao.insertInventoryItem(it) }
        snapshot.memberships.forEach { dao.insertMembership(it) }
        snapshot.customers.forEach { dao.insertCustomerProfile(it) }
        snapshot.serviceAlerts.forEach { dao.insertServiceAlert(it) }
    }

    /** The whole local database, shaped for an upload. */
    suspend fun currentSnapshot(): FirebaseSyncHelper.CloudSnapshot = FirebaseSyncHelper.CloudSnapshot(
        jobCards = allJobCards.first(),
        cashItems = allCashItems.first(),
        inventoryItems = allInventoryItems.first(),
        memberships = allMemberships.first(),
        customers = allCustomerProfiles.first(),
        serviceAlerts = allServiceAlerts.first()
    )

    /**
     * Wipes every local table. Called when the signed-in workshop changes so a fresh sign up
     * starts genuinely empty instead of inheriting the previous account's rows from Room.
     */
    suspend fun clearAllLocalData() {
        dao.clearJobCards()
        dao.clearCashItems()
        dao.clearInventory()
        dao.clearMemberships()
        dao.clearCustomerProfiles()
        dao.clearServiceAlerts()
        dao.resetAutoIncrement()
    }
}
