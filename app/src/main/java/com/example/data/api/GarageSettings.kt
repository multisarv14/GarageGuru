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
import java.security.MessageDigest

/**
 * Everything the owner configures about *their* workshop rather than about a single job:
 * postal address, GSTIN, whether GST is charged at all, and the two stock rules.
 *
 * [GarageSession] already holds the signed-in identity; this holds the preferences that identity
 * carries. It is a separate object because these values are edited from the profile screen and
 * read from the invoice, the PDF and the stock screens — a StateFlow keeps all of those in step
 * without threading a parameter through the whole composable tree.
 *
 * Values are mirrored into SharedPreferences (so a cold start bills correctly before the network
 * comes back) and into `garages/{garageId}` (so a reinstall or a second device inherits them).
 */
object GarageSettings {

    private const val TAG = "GarageSettings"
    private const val PREFS = "garage_guru_settings"

    /**
     * Every workshop registered through this app is also registered with the app operator, who
     * is reachable here. Stored on the garage/admin documents at sign up and shown in the profile
     * so an owner always knows who supports the product.
     */
    const val APP_ADMIN_EMAIL = "ceomultisarv@gmail.com"

    /** One snapshot of the whole configuration, so composables collect a single flow. */
    data class Snapshot(
        val clientId: String = "",
        val address: String = "",
        val gstin: String = "",
        val gstEnabled: Boolean = true,
        val gstRatePercent: Double = 18.0,
        val stockValidationEnabled: Boolean = true,
        val showVehicleOnInvoice: Boolean = true,
        val setupCompleted: Boolean = false
    ) {
        /** GST for a subtotal, honouring the enable/disable switch. */
        fun taxOn(subtotal: Double): Double =
            if (gstEnabled) subtotal * (gstRatePercent / 100.0) else 0.0
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
            address = p.getString("address", "").orEmpty(),
            gstin = p.getString("gstin", "").orEmpty(),
            gstEnabled = p.getBoolean("gstEnabled", true),
            gstRatePercent = p.getFloat("gstRatePercent", 18f).toDouble(),
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
        address: String = _state.value.address,
        gstin: String = _state.value.gstin,
        gstEnabled: Boolean = _state.value.gstEnabled,
        gstRatePercent: Double = _state.value.gstRatePercent,
        stockValidationEnabled: Boolean = _state.value.stockValidationEnabled,
        showVehicleOnInvoice: Boolean = _state.value.showVehicleOnInvoice,
        setupCompleted: Boolean = _state.value.setupCompleted,
        syncToCloud: Boolean = true
    ) {
        val next = Snapshot(
            clientId = clientId,
            address = address,
            gstin = gstin,
            gstEnabled = gstEnabled,
            gstRatePercent = gstRatePercent,
            stockValidationEnabled = stockValidationEnabled,
            showVehicleOnInvoice = showVehicleOnInvoice,
            setupCompleted = setupCompleted
        )
        _state.value = next

        prefs?.edit()
            ?.putString("clientId", next.clientId)
            ?.putString("address", next.address)
            ?.putString("gstin", next.gstin)
            ?.putBoolean("gstEnabled", next.gstEnabled)
            ?.putFloat("gstRatePercent", next.gstRatePercent.toFloat())
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
                    "address" to snapshot.address,
                    "gstNumber" to snapshot.gstin,
                    "gstEnabled" to snapshot.gstEnabled,
                    "taxRatePercent" to snapshot.gstRatePercent,
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
        address: String?,
        gstin: String?,
        gstEnabled: Boolean?,
        gstRatePercent: Double?,
        stockValidationEnabled: Boolean?,
        showVehicleOnInvoice: Boolean?,
        setupCompleted: Boolean?
    ) {
        val now = _state.value
        update(
            clientId = clientId?.takeIf { it.isNotBlank() } ?: now.clientId,
            address = address ?: now.address,
            gstin = gstin ?: now.gstin,
            gstEnabled = gstEnabled ?: now.gstEnabled,
            gstRatePercent = gstRatePercent ?: now.gstRatePercent,
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

    /**
     * The workshop's public Client ID, derived from its Firebase uid.
     *
     * Deterministic on purpose: a reinstall, a second device or a cloud restore all recompute the
     * same id instead of minting a second one for the same workshop. The alphabet drops the
     * characters that are misread when an id is copied off a printed invoice (0/O, 1/I).
     */
    fun clientIdFor(uid: String): String {
        if (uid.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray())
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (0 until 8).map { alphabet[(digest[it].toInt() and 0xFF) % alphabet.length] }
            .joinToString("")
        return "GG-${code.substring(0, 4)}-${code.substring(4, 8)}"
    }
}
