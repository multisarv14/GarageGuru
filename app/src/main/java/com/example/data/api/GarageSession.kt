package com.example.data.api

import android.content.Context
import android.content.SharedPreferences

/**
 * Holds the signed-in workshop identity for the lifetime of the process.
 *
 * Every Firestore document the app writes is tagged with [garageId], so this has to be
 * readable synchronously from anywhere (including [FirebaseSyncHelper], which is an object
 * with no lifecycle). It is mirrored into SharedPreferences so a cold start already knows
 * which workshop it belongs to before the network comes back.
 */
object GarageSession {

    private const val PREFS = "garage_guru_session"
    private const val KEY_UID = "uid"
    private const val KEY_GARAGE_ID = "garageId"
    private const val KEY_GARAGE_NAME = "garageName"
    private const val KEY_OWNER_NAME = "ownerName"
    private const val KEY_OWNER_PHONE = "ownerPhone"
    private const val KEY_OWNER_EMAIL = "ownerEmail"

    private var prefs: SharedPreferences? = null

    @Volatile var uid: String = ""
        private set
    @Volatile var garageId: String = ""
        private set
    @Volatile var garageName: String = "Garage Guru"
        private set
    @Volatile var ownerName: String = "Owner"
        private set
    @Volatile var ownerPhone: String = ""
        private set
    @Volatile var ownerEmail: String = ""
        private set

    /** `logoUpdatedAt` marker from the garage profile; 0 means this garage has no logo. */
    @Volatile var logoUpdatedAt: Long = 0L

    val isReady: Boolean get() = uid.isNotBlank() && garageId.isNotBlank()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        uid = p.getString(KEY_UID, "").orEmpty()
        garageId = p.getString(KEY_GARAGE_ID, "").orEmpty()
        garageName = p.getString(KEY_GARAGE_NAME, "Garage Guru").orEmpty()
        ownerName = p.getString(KEY_OWNER_NAME, "Owner").orEmpty()
        ownerPhone = p.getString(KEY_OWNER_PHONE, "").orEmpty()
        ownerEmail = p.getString(KEY_OWNER_EMAIL, "").orEmpty()
    }

    fun update(
        uid: String = this.uid,
        garageId: String = this.garageId,
        garageName: String = this.garageName,
        ownerName: String = this.ownerName,
        ownerPhone: String = this.ownerPhone,
        ownerEmail: String = this.ownerEmail
    ) {
        this.uid = uid
        this.garageId = garageId
        this.garageName = garageName
        this.ownerName = ownerName
        this.ownerPhone = ownerPhone
        this.ownerEmail = ownerEmail
        prefs?.edit()
            ?.putString(KEY_UID, uid)
            ?.putString(KEY_GARAGE_ID, garageId)
            ?.putString(KEY_GARAGE_NAME, garageName)
            ?.putString(KEY_OWNER_NAME, ownerName)
            ?.putString(KEY_OWNER_PHONE, ownerPhone)
            ?.putString(KEY_OWNER_EMAIL, ownerEmail)
            ?.apply()
    }

    fun clear() {
        uid = ""
        garageId = ""
        logoUpdatedAt = 0L
        garageName = "Garage Guru"
        ownerName = "Owner"
        ownerPhone = ""
        ownerEmail = ""
        prefs?.edit()?.clear()?.apply()
    }

    /** Deterministic workspace id for a brand new owner account. */
    fun garageIdFor(uid: String): String = "garage_$uid"

    // --- Which workshop's rows are currently sitting in Room ---

    private const val KEY_LOCAL_GARAGE = "localDataGarageId"

    /**
     * The garage whose data the local Room database currently holds. When this stops matching
     * [garageId] the local tables belong to somebody else and have to be wiped before syncing.
     */
    var localDataGarageId: String
        get() = prefs?.getString(KEY_LOCAL_GARAGE, "").orEmpty()
        set(value) { prefs?.edit()?.putString(KEY_LOCAL_GARAGE, value)?.apply() }
}
