package com.example.data.api

import android.util.Log
import com.example.data.catalogue.CatalogueEntry
import com.example.data.catalogue.VehicleCatalogue
import com.example.data.catalogue.VehicleCategory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The default service rate card, read from Firestore rather than only from the app.
 *
 * Master data on the server means a price correction reaches every workshop by editing one
 * collection, instead of waiting for a Play release. The list bundled into the app is kept as a
 * fallback for exactly two moments that matter: a first run with no network, and a workshop whose
 * operator has not published the master yet. Neither should leave the services picker empty.
 *
 * Nothing here is a workshop's *own* Service Master — that is the Service rows in its inventory,
 * which the owner edits freely. This is only the starting point they import from.
 */
object ServiceMasterStore {

    private const val TAG = "ServiceMasterStore"
    private const val COLLECTION = "serviceMaster"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _entries = MutableStateFlow<List<CatalogueEntry>>(emptyList())

    /** What the cloud published, or empty until it has been fetched. */
    val entries: StateFlow<List<CatalogueEntry>> = _entries.asStateFlow()

    /** True once the cloud list has arrived, so callers can tell "empty" from "not loaded". */
    @Volatile
    var loadedFromCloud: Boolean = false
        private set

    /**
     * The list to offer for [servicingType]: the published master when it is available, otherwise
     * the copy bundled with the app.
     */
    fun defaultsFor(servicingType: GarageSettings.ServicingType): List<CatalogueEntry> {
        val cloud = _entries.value
        if (cloud.isEmpty()) return VehicleCatalogue.defaultServicesFor(servicingType)
        return cloud.filter { VehicleCatalogue.matches(servicingType, it.category) }
    }

    /**
     * Pulls the published master.
     *
     * A failure is not surfaced to the user: the bundled list already covers them, so a flaky
     * connection should not turn into an error dialog on a screen they came to for something else.
     */
    fun refresh(onDone: (count: Int) -> Unit = {}) {
        if (!GarageSession.isReady) {
            onDone(0)
            return
        }
        db.collection(COLLECTION).get()
            .addOnSuccessListener { snap ->
                val loaded = snap.documents.mapNotNull { doc ->
                    // "_meta" carries the publish stamp, not a service.
                    if (doc.id == "_meta") return@mapNotNull null
                    val name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val price = (doc.get("price") as? Number)?.toDouble() ?: return@mapNotNull null
                    CatalogueEntry(name, price, VehicleCategory.from(doc.getString("category")))
                }.sortedBy { it.title }

                if (loaded.isNotEmpty()) {
                    _entries.value = loaded
                    loadedFromCloud = true
                }
                Log.d(TAG, "Service master: ${loaded.size} entries from cloud")
                onDone(loaded.size)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Could not read service master; using the bundled list", e)
                onDone(0)
            }
    }

    /**
     * Asks the backend to (re)publish the rate card the functions hold.
     *
     * Only an owner may do this — the Cloud Function enforces that too, so a modified client
     * cannot rewrite what every other workshop imports.
     */
    fun publish(onDone: (success: Boolean, message: String) -> Unit) {
        com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("publishServiceMaster")
            .call()
            .addOnSuccessListener { result ->
                @Suppress("UNCHECKED_CAST")
                val written = ((result.data as? Map<String, Any?>)?.get("written") as? Number)?.toInt() ?: 0
                refresh()
                onDone(true, "Published $written services to the cloud")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "publishServiceMaster failed", e)
                onDone(false, e.localizedMessage ?: "Could not publish the service master")
            }
    }

    fun clear() {
        _entries.value = emptyList()
        loadedFromCloud = false
    }
}
