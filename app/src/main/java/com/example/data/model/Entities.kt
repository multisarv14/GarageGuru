package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "job_cards")
data class JobCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName: String,
    val customerPhone: String,
    val vehicleName: String, // e.g: Maruti Swift
    val vehiclePlate: String, // e.g: MH12-AB-3456
    val symptoms: String,
    val diagnosis: String = "",
    val status: String = "OPEN", // OPEN, COMPLETED, CLOSED
    val tasks: String = "", // Comma separated task list or JSON
    val materials: String = "", // Comma separated materials list or JSON (format: "Part Name (Qty) - $Price")
    val serviceCharges: Double = 0.0,
    val partsCharges: Double = 0.0,
    val taxes: Double = 0.0,
    /** Flat rupee amount knocked off the bill; shown as its own deduction line on the invoice. */
    val discount: Double = 0.0,
    val totalAmount: Double = 0.0,
    // Vehicle particulars kept as their own columns rather than only inside [vehicleName], so the
    // invoice and the printed PDF can lay them out — and the owner can correct them — field by field.
    val vehicleMake: String = "",
    val vehicleModel: String = "",
    val vehicleVariant: String = "",
    val vehicleKm: String = "",
    val vehicleFuel: String = "",
    val creationTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "cash_ledger")
data class CashItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // INCOME or EXPENSE
    val amount: Double,
    val category: String, // Service, Parts, Rent, Salary, Bill, Other
    val paymentMode: String, // Cash, Card, UPI, Bank Transfer
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "inventory")
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val stockType: String = "Spare", // Service, Spare, Lube
    val quantity: Int,
    val minStockAlert: Int,
    val unitPrice: Double
)

/**
 * One withdrawal from the stock room.
 *
 * Every issue has already been subtracted from [InventoryItemEntity.quantity]; this table is the
 * audit trail that explains *why* the count dropped — which part, how many, who took it and for
 * which job — so a shortfall at stock-take can be traced instead of guessed at.
 */
@Entity(tableName = "stock_issues")
data class StockIssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val inventoryItemId: Int,
    val itemName: String,
    val quantity: Int,
    val unitPrice: Double = 0.0,
    val issuedTo: String = "",
    /** Job card this stock went to, or 0 for a counter sale / internal use. */
    val jobCardId: Int = 0,
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalValue: Double get() = quantity * unitPrice
}

@Entity(tableName = "service_memberships")
data class ServiceMembershipEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName: String,
    val customerPhone: String,
    val membershipType: String, // Basic Wash, Gold Tier, Unlimited Premium
    val monthlyWashLimit: Int = 4,
    val washesUsed: Int = 0,
    val renewalDate: String, // YYYY-MM-DD
    val pendingBalance: Double = 0.0
)

@Entity(tableName = "customer_profiles")
data class CustomerProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** Human-readable client code (CL-0001) minted when the profile is first saved. */
    val clientId: String = "",
    val name: String,
    val phone: String,
    val email: String = "",
    val vehicleName: String = "",
    val vehiclePlate: String = "",
    val address: String = "",
    val notes: String = "",
    val creationTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "team_members")
data class TeamMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val role: String, // Owner, Manager, Service Advisor, Technician
    val phone: String,
    val username: String = "",
    val password: String = "",
    val isOwner: Boolean = false
)

@Entity(tableName = "service_alerts")
data class ServiceAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerName: String,
    val customerPhone: String,
    val vehiclePlate: String,
    val serviceType: String, // e.g., Oil Change, Brake Service, General Checkup
    val intervalKm: Int = 5000,
    val dueDaysLeft: Int = 0,
    val dueDateString: String,
    val dueTimestamp: Long = System.currentTimeMillis(),
    val status: String = "DUE_SOON", // UPCOMING, DUE_SOON, OVERDUE, COMPLETED
    val reminderNotes: String = ""
)
