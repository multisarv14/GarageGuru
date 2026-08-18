package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "job_cards")
data class JobCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** Workshop-visible bill number, e.g. `SG1001`. Issued once, on creation, and never reused. */
    val invoiceNumber: String = "",
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
    /** Motorcycle, Scooter, Electric Scooter… the master entry the rest of the vehicle hangs off. */
    val vehicleType: String = "",
    val vehicleMake: String = "",
    val vehicleModel: String = "",
    val vehicleVariant: String = "",
    val vehicleKm: String = "",
    val vehicleFuel: String = "",
    /** Used when a walk-in gives no name — a serial the front desk can call them by. */
    val customerSerial: String = "",
    /** Who is doing the work. Defaults to whoever raised the card. */
    val mechanicName: String = "",
    /** Epoch millis the customer was promised the vehicle back; 0 when none was given. */
    val expectedDelivery: Long = 0L,
    val customerRemarks: String = "",
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

/**
 * A catalogue line: a service the workshop offers, or a spare/lubricant it holds.
 *
 * [stockType] decides which of these fields mean anything. A Service is labour — it has a charge
 * and nothing else, so quantity, unit, purchase price and the low-stock threshold stay at zero
 * and are never asked for. A Spare or Lube is real stock and uses all of them.
 */
@Entity(tableName = "inventory")
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val stockType: String = "Spare", // Service, Spare, Lube
    /** TWO_WHEELER / FOUR_WHEELER / BOTH — decides which workshops are offered this item. */
    val vehicleCategory: String = "BOTH",
    /** Manufacturer or brand. Not used for a Service. */
    val companyName: String = "",
    /** Part number / catalogue code. Not used for a Service. */
    val partCode: String = "",
    val quantity: Int,
    /** Piece, Litre, Box… whichever unit the quantity is counted in. */
    val unit: String = "",
    /** What the workshop paid per unit. Not used for a Service. */
    val purchasePrice: Double = 0.0,
    val minStockAlert: Int,
    /** Selling price per unit — and, for a Service, simply the service charge. */
    val unitPrice: Double
) {
    val isService: Boolean get() = stockType.equals("Service", ignoreCase = true)

    /** What the workshop makes per unit. Meaningless for a Service, which has no cost price. */
    val profitPerUnit: Double get() = if (isService) 0.0 else unitPrice - purchasePrice

    /** Services are labour: they are never counted, never run out and never block a bill. */
    val tracksStock: Boolean get() = !isService
}

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

/**
 * A group of users, e.g. "Front Desk" or "Workshop Floor".
 *
 * Teams carry no permissions of their own — those come from the role. A team is how an owner
 * organises people, and deactivating one is how a whole shift is stood down without touching
 * anybody's history.
 */
@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = "",
    val isActive: Boolean = true,
    val creationTime: Long = System.currentTimeMillis()
)

/**
 * A named set of module permissions.
 *
 * [permissionsJson] is the encoded [com.example.data.permissions.PermissionSet]. It is stored as
 * one string rather than a child table because a role is always read and written whole, and
 * because the same string is copied onto the user's `admin` document for the security rules.
 */
@Entity(tableName = "roles")
data class RoleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = "",
    val permissionsJson: String = "",
    /** The owner's own role cannot be edited away, or a garage could lock itself out. */
    val isSystem: Boolean = false,
    val creationTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "team_members")
data class TeamMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    /** Role name, kept for display and for older rows; [roleId] is what actually grants access. */
    val role: String,
    /** The mobile number is the login id — there is no separate username. */
    val phone: String,
    val password: String = "",
    val isOwner: Boolean = false,
    val teamId: Int = 0,
    val roleId: Int = 0,
    /**
     * Users are never deleted, only deactivated, so job cards, invoices and ledger entries keep
     * pointing at a real person long after they have left.
     */
    val isActive: Boolean = true,
    /** Firebase Auth uid, once the user has a login of their own. */
    val authUid: String = "",
    val email: String = "",
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
