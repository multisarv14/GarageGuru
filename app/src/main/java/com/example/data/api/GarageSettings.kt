package com.example.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * Everything the owner configures about *their* workshop rather than about a single job: postal
 * address, GST position, invoice numbering and the two stock rules.
 *
 * [GarageSession] already holds the signed-in identity; this holds the preferences that identity
 * carries. It is a separate object because these values are edited from the setup wizard and the
 * profile screen, and read from the invoice, the PDF and the stock screens — a StateFlow keeps all
 * of those in step without threading a parameter through the whole composable tree.
 *
 * Values are mirrored into SharedPreferences (so a cold start bills correctly before the network
 * comes back) and into `garages/{garageId}` (so a reinstall or a second device inherits them).
 */
object GarageSettings {

    private const val TAG = "GarageSettings"
    private const val PREFS = "garage_guru_settings"

    /** Global counter document behind the Client ID serial. */
    private const val COUNTERS = "counters"
    private const val CLIENT_ID_COUNTER = "clientId"

    /** The serial in a Client ID starts here and only ever climbs. */
    const val FIRST_CLIENT_SERIAL = 101L

    /**
     * Every workshop registered through this app is also registered with the app operator, who
     * is reachable here. Stored on the garage/admin documents at sign up.
     */
    const val APP_ADMIN_EMAIL = "ceomultisarv@gmail.com"

    /**
     * What the workshop actually services. Chosen first in the setup wizard, because it decides
     * which vehicle types, services, spares and lubricants the rest of the app offers at all —
     * a two-wheeler garage should never be shown a list of car variants.
     */
    enum class ServicingType(val storedvalue: String, val label: String) {
        TWO_WHEELER("TWO_WHEELER", "2 Wheeler"),
        FOUR_WHEELER("FOUR_WHEELER", "4 Wheeler"),
        BOTH("BOTH", "Both 2 Wheeler & 4 Wheeler");

        val servicesTwoWheeler: Boolean get() = this == TWO_WHEELER || this == BOTH
        val servicesFourWheeler: Boolean get() = this == FOUR_WHEELER || this == BOTH

        companion object {
            fun from(raw: String?): ServicingType =
                entries.firstOrNull { it.storedvalueMatches(raw) } ?: BOTH
        }

        private fun storedvalueMatches(raw: String?) = storedvalue.equals(raw, ignoreCase = true)
    }

    /** One snapshot of the whole configuration, so composables collect a single flow. */
    data class Snapshot(
        val clientId: String = "",
        val servicingType: ServicingType = ServicingType.BOTH,
        val servicingTypeChosen: Boolean = false,
        val address: String = "",
        val gstin: String = "",
        /** "GST Applicable — Yes / No". When No, nothing tax-shaped reaches an invoice. */
        val gstApplicable: Boolean = true,
        val cgstPercent: Double = 9.0,
        val sgstPercent: Double = 9.0,
        val invoicePrefix: String = "",
        val invoiceStartNumber: Int = 1001,
        /** Next invoice serial to hand out; 0 until the first one is allocated. */
        val invoiceNextNumber: Int = 0,
        val stockValidationEnabled: Boolean = true,
        val showVehicleOnInvoice: Boolean = true,
        val setupCompleted: Boolean = false
    ) {
        /** Total GST rate. CGST and SGST are stored apart but always charged together. */
        val gstRatePercent: Double get() = cgstPercent + sgstPercent

        /** CGST for a subtotal, honouring the applicable switch. */
        fun cgstOn(subtotal: Double): Double =
            if (gstApplicable) subtotal * (cgstPercent / 100.0) else 0.0

        /** SGST for a subtotal, honouring the applicable switch. */
        fun sgstOn(subtotal: Double): Double =
            if (gstApplicable) subtotal * (sgstPercent / 100.0) else 0.0

        /** Total GST for a subtotal — CGST + SGST, which is what lands on the bill. */
        fun taxOn(subtotal: Double): Double = cgstOn(subtotal) + sgstOn(subtotal)

        /**
         * What the owner still has to fill in before the workshop is properly configured.
         *
         * Drives the checklist on the account screen, so "pending" is decided in one place rather
         * than by each screen forming its own opinion.
         */
        val pendingSetup: List<String>
            get() = buildList {
                if (!servicingTypeChosen) add("Garage servicing type")
                if (address.isBlank()) add("Garage address")
                if (gstApplicable) {
                    if (gstin.isBlank()) add("GSTIN")
                    else if (!isValidGstin(gstin)) add("Valid GSTIN")
                    if (gstRatePercent <= 0.0) add("CGST / SGST rates")
                }
                if (invoicePrefix.isBlank()) add("Invoice prefix code")
                if (invoiceStartNumber <= 0) add("Invoice start number")
            }

        val isFullyConfigured: Boolean get() = pendingSetup.isEmpty()
    }

    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    /** Snapshot readable from non-composable code (PDF generation, callbacks). */
    val current: Snapshot get() = _state.value

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _state.value = Snapshot(
            clientId = p.getString("clientId", "").orEmpty(),
            servicingType = ServicingType.from(p.getString("servicingType", null)),
            servicingTypeChosen = p.getBoolean("servicingTypeChosen", false),
            address = p.getString("address", "").orEmpty(),
            gstin = p.getString("gstin", "").orEmpty(),
            gstApplicable = p.getBoolean("gstApplicable", true),
            cgstPercent = p.getFloat("cgstPercent", 9f).toDouble(),
            sgstPercent = p.getFloat("sgstPercent", 9f).toDouble(),
            invoicePrefix = p.getString("invoicePrefix", "").orEmpty(),
            invoiceStartNumber = p.getInt("invoiceStartNumber", 1001),
            invoiceNextNumber = p.getInt("invoiceNextNumber", 0),
            stockValidationEnabled = p.getBoolean("stockValidationEnabled", true),
            showVehicleOnInvoice = p.getBoolean("showVehicleOnInvoice", true),
            setupCompleted = p.getBoolean("setupCompleted", false)
        )
    }

    /**
     * Applies a partial change. Unnamed arguments keep their current value, so a screen that only
     * flips the GST switch cannot accidentally blank out the address.
     */
    fun update(
        clientId: String = _state.value.clientId,
        servicingType: ServicingType = _state.value.servicingType,
        servicingTypeChosen: Boolean = _state.value.servicingTypeChosen,
        address: String = _state.value.address,
        gstin: String = _state.value.gstin,
        gstApplicable: Boolean = _state.value.gstApplicable,
        cgstPercent: Double = _state.value.cgstPercent,
        sgstPercent: Double = _state.value.sgstPercent,
        invoicePrefix: String = _state.value.invoicePrefix,
        invoiceStartNumber: Int = _state.value.invoiceStartNumber,
        invoiceNextNumber: Int = _state.value.invoiceNextNumber,
        stockValidationEnabled: Boolean = _state.value.stockValidationEnabled,
        showVehicleOnInvoice: Boolean = _state.value.showVehicleOnInvoice,
        setupCompleted: Boolean = _state.value.setupCompleted,
        syncToCloud: Boolean = true
    ) {
        val next = Snapshot(
            // A Client ID is issued once and then never moves, so an id already held is never
            // overwritten by a later save — a printed invoice must stay traceable.
            clientId = _state.value.clientId.ifBlank { clientId },
            servicingType = servicingType,
            servicingTypeChosen = servicingTypeChosen,
            address = address,
            gstin = gstin,
            gstApplicable = gstApplicable,
            cgstPercent = cgstPercent,
            sgstPercent = sgstPercent,
            invoicePrefix = invoicePrefix,
            invoiceStartNumber = invoiceStartNumber,
            invoiceNextNumber = invoiceNextNumber,
            stockValidationEnabled = stockValidationEnabled,
            showVehicleOnInvoice = showVehicleOnInvoice,
            setupCompleted = setupCompleted
        )
        _state.value = next

        prefs?.edit()
            ?.putString("clientId", next.clientId)
            ?.putString("servicingType", next.servicingType.storedvalue)
            ?.putBoolean("servicingTypeChosen", next.servicingTypeChosen)
            ?.putString("address", next.address)
            ?.putString("gstin", next.gstin)
            ?.putBoolean("gstApplicable", next.gstApplicable)
            ?.putFloat("cgstPercent", next.cgstPercent.toFloat())
            ?.putFloat("sgstPercent", next.sgstPercent.toFloat())
            ?.putString("invoicePrefix", next.invoicePrefix)
            ?.putInt("invoiceStartNumber", next.invoiceStartNumber)
            ?.putInt("invoiceNextNumber", next.invoiceNextNumber)
            ?.putBoolean("stockValidationEnabled", next.stockValidationEnabled)
            ?.putBoolean("showVehicleOnInvoice", next.showVehicleOnInvoice)
            ?.putBoolean("setupCompleted", next.setupCompleted)
            ?.apply()

        if (syncToCloud) pushToCloud(next)
    }

    private fun pushToCloud(snapshot: Snapshot) {
        if (!GarageSession.isReady) return
        try {
            db.collection("garages").document(GarageSession.garageId).set(
                hashMapOf(
                    "clientId" to snapshot.clientId,
                    "servicingType" to snapshot.servicingType.storedvalue,
                    "servicingTypeChosen" to snapshot.servicingTypeChosen,
                    "address" to snapshot.address,
                    "gstNumber" to snapshot.gstin,
                    "gstApplicable" to snapshot.gstApplicable,
                    "cgstPercent" to snapshot.cgstPercent,
                    "sgstPercent" to snapshot.sgstPercent,
                    "taxRatePercent" to snapshot.gstRatePercent,
                    "invoicePrefix" to snapshot.invoicePrefix,
                    "invoiceStartNumber" to snapshot.invoiceStartNumber,
                    "invoiceNextNumber" to snapshot.invoiceNextNumber,
                    "stockValidationEnabled" to snapshot.stockValidationEnabled,
                    "showVehicleOnInvoice" to snapshot.showVehicleOnInvoice,
                    "setupCompleted" to snapshot.setupCompleted,
                    "appAdminEmail" to APP_ADMIN_EMAIL,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not push garage settings", e)
        }
    }

    /** Applies whatever the `garages/{id}` document holds, without echoing it straight back up. */
    fun applyFromCloud(
        clientId: String?,
        servicingType: String?,
        servicingTypeChosen: Boolean?,
        address: String?,
        gstin: String?,
        gstApplicable: Boolean?,
        cgstPercent: Double?,
        sgstPercent: Double?,
        invoicePrefix: String?,
        invoiceStartNumber: Int?,
        invoiceNextNumber: Int?,
        stockValidationEnabled: Boolean?,
        showVehicleOnInvoice: Boolean?,
        setupCompleted: Boolean?
    ) {
        val now = _state.value
        update(
            clientId = clientId?.takeIf { it.isNotBlank() } ?: now.clientId,
            servicingType = servicingType?.let { ServicingType.from(it) } ?: now.servicingType,
            servicingTypeChosen = servicingTypeChosen ?: now.servicingTypeChosen,
            address = address ?: now.address,
            gstin = gstin ?: now.gstin,
            gstApplicable = gstApplicable ?: now.gstApplicable,
            cgstPercent = cgstPercent ?: now.cgstPercent,
            sgstPercent = sgstPercent ?: now.sgstPercent,
            invoicePrefix = invoicePrefix ?: now.invoicePrefix,
            invoiceStartNumber = invoiceStartNumber ?: now.invoiceStartNumber,
            // The higher of the two wins, so a stale device can never re-issue an invoice number
            // another device has already used.
            invoiceNextNumber = maxOf(invoiceNextNumber ?: 0, now.invoiceNextNumber),
            stockValidationEnabled = stockValidationEnabled ?: now.stockValidationEnabled,
            showVehicleOnInvoice = showVehicleOnInvoice ?: now.showVehicleOnInvoice,
            setupCompleted = setupCompleted ?: now.setupCompleted,
            syncToCloud = false
        )
    }

    fun clear() {
        _state.value = Snapshot()
        prefs?.edit()?.clear()?.apply()
    }

    // ------------------------------------------------------------------
    // Client ID
    // ------------------------------------------------------------------

    /**
     * The garage code inside a Client ID: the initials of the workshop name.
     *
     * "Shree Garage" gives `SG`. A single-word name falls back to its first two letters, and a
     * name with no letters at all (or none yet, before the profile has loaded) gives `GG`, so the
     * id is always well formed.
     */
    fun garageCodeFor(garageName: String): String {
        val words = garageName.trim()
            .split(Regex("\\s+"))
            .map { it.filter(Char::isLetter) }
            .filter { it.isNotEmpty() }

        val code = when {
            words.isEmpty() -> "GG"
            words.size == 1 -> words[0].take(2)
            else -> words.take(2).joinToString("") { it.take(1) }
        }
        return code.uppercase().padEnd(2, 'X')
    }

    /**
     * Formats a Client ID: `GG` + garage code + `-` + YY + MM + serial.
     *
     * "Shree Garage" registering in August 2026 as the first workshop gives `GGSG-2608101`.
     */
    fun formatClientId(garageName: String, serial: Long, atMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = atMillis }
        val yy = String.format("%02d", cal.get(Calendar.YEAR) % 100)
        val mm = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        return "GG${garageCodeFor(garageName)}-$yy$mm$serial"
    }

    /**
     * Issues this workshop's Client ID once and then leaves it alone.
     *
     * The serial comes from a Firestore transaction on `counters/clientId`, so two workshops
     * registering at the same moment cannot be handed the same number — a locally computed serial
     * could not promise that. Order of preference:
     *
     *  1. an id already held on this device — nothing to do;
     *  2. an id already on the garage document — adopt it, which is what makes a reinstall or a
     *     second device keep the id the customer has seen on paper;
     *  3. only then mint a new one.
     *
     * If the network is down the id simply is not issued yet and the caller is told so; retrying
     * on the next launch is safe, and is far better than inventing a serial that may collide.
     */
    fun ensureClientId(garageName: String, onDone: (clientId: String) -> Unit = {}) {
        val existing = _state.value.clientId
        if (existing.isNotBlank()) {
            onDone(existing)
            return
        }
        if (!GarageSession.isReady) {
            onDone("")
            return
        }

        val garageRef = db.collection("garages").document(GarageSession.garageId)
        garageRef.get()
            .addOnSuccessListener { snap ->
                val onDoc = snap.getString("clientId").orEmpty()
                if (onDoc.isNotBlank()) {
                    update(clientId = onDoc, syncToCloud = false)
                    onDone(onDoc)
                    return@addOnSuccessListener
                }
                mintClientId(garageName, garageRef, onDone)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Could not read garage doc for Client ID", e)
                onDone("")
            }
    }

    private fun mintClientId(
        garageName: String,
        garageRef: com.google.firebase.firestore.DocumentReference,
        onDone: (String) -> Unit
    ) {
        val counterRef = db.collection(COUNTERS).document(CLIENT_ID_COUNTER)
        db.runTransaction { txn ->
            val snap = txn.get(counterRef)
            val serial = (snap.get("next") as? Number)?.toLong() ?: FIRST_CLIENT_SERIAL
            txn.set(counterRef, hashMapOf("next" to serial + 1), SetOptions.merge())
            serial
        }.addOnSuccessListener { serial ->
            val clientId = formatClientId(garageName, serial, System.currentTimeMillis())
            update(clientId = clientId, syncToCloud = false)
            garageRef.set(
                hashMapOf(
                    "clientId" to clientId,
                    "clientSerial" to serial,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            db.collection("admin").document(GarageSession.uid).set(
                hashMapOf("clientId" to clientId),
                SetOptions.merge()
            )
            Log.d(TAG, "Issued Client ID $clientId (serial $serial)")
            onDone(clientId)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Client ID counter transaction failed", e)
            onDone("")
        }
    }

    // ------------------------------------------------------------------
    // GSTIN
    // ------------------------------------------------------------------

    /**
     * Checks a GSTIN against the statutory shape: two state digits, a ten-character PAN, an
     * entity digit, a `Z`, and a checksum character. Deliberately a format check only — nothing
     * in the app can confirm the number is actually registered.
     */
    fun isValidGstin(gstin: String): Boolean =
        Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$").matches(gstin.trim().uppercase())

    // ------------------------------------------------------------------
    // Invoice numbering
    // ------------------------------------------------------------------

    /**
     * Hands out the next invoice number, e.g. `SG1001` then `SG1002`.
     *
     * [alreadyIssued] is every number currently on a job card. The counter normally decides the
     * next value on its own, but a restore from the cloud can bring back job cards the counter
     * has not caught up with, so anything already in use is skipped rather than duplicated.
     */
    fun allocateInvoiceNumber(alreadyIssued: Collection<String>): String {
        val snapshot = _state.value
        val prefix = snapshot.invoicePrefix.trim()
        val start = snapshot.invoiceStartNumber.coerceAtLeast(1)

        val highestUsed = alreadyIssued
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .mapNotNull { it.removePrefix(prefix).trim().toIntOrNull() }
            .maxOrNull() ?: 0

        var candidate = maxOf(snapshot.invoiceNextNumber, start, highestUsed + 1)
        val taken = alreadyIssued.map { it.uppercase() }.toSet()
        while ("$prefix$candidate".uppercase() in taken) candidate++

        update(invoiceNextNumber = candidate + 1)
        return "$prefix$candidate"
    }
}
