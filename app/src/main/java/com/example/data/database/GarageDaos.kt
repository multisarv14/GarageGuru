package com.example.data.database

import androidx.room.*
import com.example.data.model.CashItemEntity
import com.example.data.model.CustomerProfileEntity
import com.example.data.model.InventoryItemEntity
import com.example.data.model.JobCardEntity
import com.example.data.model.ServiceAlertEntity
import com.example.data.model.ServiceMembershipEntity
import com.example.data.model.RoleEntity
import com.example.data.model.StockIssueEntity
import com.example.data.model.TeamEntity
import com.example.data.model.TeamMemberEntity
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

    // --- Stock Issues (withdrawals from the stock room) ---
    @Query("SELECT * FROM stock_issues ORDER BY timestamp DESC")
    fun getAllStockIssues(): Flow<List<StockIssueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockIssue(issue: StockIssueEntity): Long

    @Query("DELETE FROM stock_issues WHERE id = :id")
    suspend fun deleteStockIssueById(id: Int)

    @Query("DELETE FROM stock_issues")
    suspend fun clearStockIssues()

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

    // --- Team Members ---
    @Query("SELECT * FROM team_members ORDER BY isOwner DESC, name ASC")
    fun getAllTeamMembers(): Flow<List<TeamMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeamMember(member: TeamMemberEntity): Long

    @Query("DELETE FROM team_members WHERE id = :id")
    suspend fun deleteTeamMemberById(id: Int)

    @Query("DELETE FROM team_members")
    suspend fun clearTeamMembers()

    // --- Teams ---
    @Query("SELECT * FROM teams ORDER BY name ASC")
    fun getAllTeams(): Flow<List<TeamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeam(team: TeamEntity): Long

    @Query("DELETE FROM teams")
    suspend fun clearTeams()

    // --- Roles ---
    @Query("SELECT * FROM roles ORDER BY name ASC")
    fun getAllRoles(): Flow<List<RoleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRole(role: RoleEntity): Long

    @Query("DELETE FROM roles")
    suspend fun clearRoles()

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

    @Query("DELETE FROM sqlite_sequence WHERE name IN ('job_cards','cash_ledger','inventory','service_memberships','customer_profiles','service_alerts','stock_issues','teams','roles')")
    suspend fun resetAutoIncrement()
}
