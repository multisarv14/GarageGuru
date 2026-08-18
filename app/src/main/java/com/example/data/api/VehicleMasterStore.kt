package com.example.data.api

import android.util.Log
import com.example.data.catalogue.VehicleCatalogue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The vehicle master behind the job card's Vehicle Type → Brand → Model → Variant chain, read
 * from Firestore.
 *
 * Reference data every workshop shares, so it lives in one collection that can be corrected
 * without shipping an app — a new model, or a model filed under the wrong type, is a one-document
 * fix rather than a Play release. The catalogue bundled into the app stays as the fallback for a
 * first run with no network, so the job card is never left with empty dropdowns.
 */
object VehicleMasterStore {

    private const val TAG = "VehicleMasterStore"
    private const val COLLECTION = "vehicleMaster"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /** One published model and the variants it comes in. */
    data class Entry(
        val type: String,
        val brand: String,
        val model: String,
        val variants: List<String>
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val hasCloud: Boolean get() = _entries.value.isNotEmpty()

    /**
     * Vehicle types this workshop should be offered.
     *
     * Falls back to the bundled catalogue, which is also what keeps four-wheeler types working —
     * the published master is two-wheeler only.
     */
    fun vehicleTypes(servicingType: GarageSettings.ServicingType): List<String> {
        val bundled = VehicleCatalogue.vehicleTypes(servicingType)
        if (!hasCloud || !servicingType.servicesTwoWheeler) return bundled

        // Cloud types first, in the order the master defines, then anything only the bundled
        // catalogue knows about (the four-wheeler side) so a "Both" workshop keeps all of it.
        val cloudTypes = _entries.value.map { it.type }.distinct()
        return (cloudTypes + bundled).distinct()
    }

    fun brandsFor(vehicleType: String): List<String> {
        val cloud = _entries.value.filter { it.type.equals(vehicleType, true) }
            .map { it.brand }.distinct().sorted()
        return cloud.ifEmpty { VehicleCatalogue.brandsFor(vehicleType) }
    }

    fun modelsFor(vehicleType: String, brand: String): List<String> {
        val cloud = _entries.value
            .filter { it.type.equals(vehicleType, true) && it.brand.equals(brand, true) }
            .map { it.model }.distinct()
        return cloud.ifEmpty { VehicleCatalogue.modelsFor(vehicleType, brand) }
    }

    fun variantsFor(vehicleType: String, brand: String, model: String): List<String> {
        val cloud = _entries.value.firstOrNull {
            it.type.equals(vehicleType, true) &&
                it.brand.equals(brand, true) &&
                it.model.equals(model, true)
        }?.variants.orEmpty()
        return cloud.ifEmpty { VehicleCatalogue.variantsFor(vehicleType, brand, model) }
    }

    /** Pulls the published master. Failure is silent — the bundled catalogue already covers it. */
    fun refresh(onDone: (count: Int) -> Unit = {}) {
        if (!GarageSession.isReady) {
            onDone(0)
            return
        }
        db.collection(COLLECTION).get()
            .addOnSuccessListener { snap ->
                val loaded = snap.documents.mapNotNull { doc ->
                    if (doc.id == "_meta") return@mapNotNull null
                    val type = doc.getString("type")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val brand = doc.getString("brand")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val model = doc.getString("model")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val variants = (doc.get("variants") as? List<String>).orEmpty()
                    Entry(type, brand, model, variants.ifEmpty { listOf("Standard") })
                }
                if (loaded.isNotEmpty()) _entries.value = loaded
                Log.d(TAG, "Vehicle master: ${loaded.size} models from cloud")
                onDone(loaded.size)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Could not read vehicle master; using the bundled catalogue", e)
                onDone(0)
            }
    }

    /** Owner-only; the Cloud Function enforces that too. */
    fun publish(onDone: (success: Boolean, message: String) -> Unit) {
        com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("publishVehicleMaster")
            .call()
            .addOnSuccessListener { result ->
                @Suppress("UNCHECKED_CAST")
                val written = ((result.data as? Map<String, Any?>)?.get("written") as? Number)?.toInt() ?: 0
                refresh()
                onDone(true, "Published $written vehicle models to the cloud")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "publishVehicleMaster failed", e)
                onDone(false, e.localizedMessage ?: "Could not publish the vehicle master")
            }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
