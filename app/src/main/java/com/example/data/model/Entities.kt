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
    val totalAmount: Double = 0.0,
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
    val quantity: Int,
    val minStockAlert: Int,
    val unitPrice: Double
)

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
    val name: String,
    val phone: String,
    val email: String = "",
    val vehicleName: String = "",
    val vehiclePlate: String = "",
    val address: String = "",
    val notes: String = "",
    val creationTime: Long = System.currentTimeMillis()
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
