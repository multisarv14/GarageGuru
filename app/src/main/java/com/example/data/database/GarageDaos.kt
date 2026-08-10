package com.example.data.database

import androidx.room.*
import com.example.data.model.CashItemEntity
import com.example.data.model.CustomerProfileEntity
import com.example.data.model.InventoryItemEntity
import com.example.data.model.JobCardEntity
import com.example.data.model.ServiceAlertEntity
import com.example.data.model.ServiceMembershipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GarageDao {

    // --- Job Cards ---
    @Query("SELECT * FROM job_cards ORDER BY creationTime DESC")
    fun getAllJobCards(): Flow<List<JobCardEntity>>

    @Query("SELECT * FROM job_cards WHERE id = :id")
    suspend fun getJobCardById(id: Int): JobCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobCard(jobCard: JobCardEntity): Long

    @Query("DELETE FROM job_cards WHERE id = :id")
    suspend fun deleteJobCardById(id: Int)

    // --- Cashbook Ledger ---
    @Query("SELECT * FROM cash_ledger ORDER BY timestamp DESC")
    fun getAllCashItems(): Flow<List<CashItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashItem(cashItem: CashItemEntity): Long

    @Query("DELETE FROM cash_ledger WHERE id = :id")
    suspend fun deleteCashItemById(id: Int)

    // --- Inventory Stock ---
    @Query("SELECT * FROM inventory ORDER BY name ASC")
    fun getAllInventoryItems(): Flow<List<InventoryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItem(item: InventoryItemEntity): Long

    @Query("DELETE FROM inventory WHERE id = :id")
    suspend fun deleteInventoryItemById(id: Int)

    // --- Service Plan Memberships ---
    @Query("SELECT * FROM service_memberships ORDER BY customerName ASC")
    fun getAllMemberships(): Flow<List<ServiceMembershipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembership(membership: ServiceMembershipEntity): Long

    @Query("DELETE FROM service_memberships WHERE id = :id")
    suspend fun deleteMembershipById(id: Int)

    // --- Customer Profiles ---
    @Query("SELECT * FROM customer_profiles ORDER BY name ASC")
    fun getAllCustomerProfiles(): Flow<List<CustomerProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerProfile(profile: CustomerProfileEntity): Long

    @Query("DELETE FROM customer_profiles WHERE id = :id")
    suspend fun deleteCustomerProfileById(id: Int)

    // --- Service Interval Notifications & Alerts ---
    @Query("SELECT * FROM service_alerts ORDER BY dueTimestamp ASC")
    fun getAllServiceAlerts(): Flow<List<ServiceAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceAlert(alert: ServiceAlertEntity): Long

    @Query("DELETE FROM service_alerts WHERE id = :id")
    suspend fun deleteServiceAlertById(id: Int)

    @Query("UPDATE service_alerts SET status = :status WHERE id = :id")
    suspend fun updateServiceAlertStatus(id: Int, status: String)

    // --- Wipe (used when the signed-in workshop changes, so one garage never
    // --- inherits another's rows from a previous session on the same device) ---
    @Query("DELETE FROM job_cards")
    suspend fun clearJobCards()

    @Query("DELETE FROM cash_ledger")
    suspend fun clearCashItems()

    @Query("DELETE FROM inventory")
    suspend fun clearInventory()

    @Query("DELETE FROM service_memberships")
    suspend fun clearMemberships()

    @Query("DELETE FROM customer_profiles")
    suspend fun clearCustomerProfiles()

    @Query("DELETE FROM service_alerts")
    suspend fun clearServiceAlerts()

    @Query("DELETE FROM sqlite_sequence WHERE name IN ('job_cards','cash_ledger','inventory','service_memberships','customer_profiles','service_alerts')")
    suspend fun resetAutoIncrement()
}
