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
    private const val KEY_CONTACT_EMAIL = "contactEmail"

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
    /** Synthetic login identity (`user_<digits>@garageguru.local`) — never shown to the user. */
    @Volatile var ownerEmail: String = ""
        private set

    /** The real address the owner gave at registration. This is what the profile screen shows. */
    @Volatile var ownerContactEmail: String = ""
        private set

    /** `logoUpdatedAt` marker from the garage profile; 0 means this garage has no logo. */
    @Volatile var logoUpdatedAt: Long = 0L

    /**
     * Who is signed in and what they may do.
     *
     * A workshop no longer has one login: the owner has theirs, and every team member the admin
     * creates has their own. What each may see comes from their assigned role, so this is read by
     * the navigation bar, the tab router and every screen that offers a destructive action.
     */
    data class SignedInUser(
        val name: String = "Owner",
        val roleName: String = "Owner",
        val isOwner: Boolean = true,
        val isActive: Boolean = true,
        val permissions: com.example.data.permissions.PermissionSet =
            com.example.data.permissions.PermissionSet.all()
    ) {
        /** The owner is never locked out of their own garage, whatever a role says. */
        fun can(
            module: com.example.data.permissions.AppModule,
            action: com.example.data.permissions.PermissionAction
        ): Boolean = isOwner || permissions.can(module, action)

        fun canView(module: com.example.data.permissions.AppModule): Boolean =
            isOwner || permissions.canView(module)
    }

    private val _signedInUser = kotlinx.coroutines.flow.MutableStateFlow(SignedInUser())
    val signedInUser: kotlinx.coroutines.flow.StateFlow<SignedInUser> = _signedInUser

    /** Snapshot for non-composable callers. */
    val currentUser: SignedInUser get() = _signedInUser.value

    fun updateSignedInUser(user: SignedInUser) {
        _signedInUser.value = user
    }

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
        ownerContactEmail = p.getString(KEY_CONTACT_EMAIL, "").orEmpty()
    }

    fun update(
        uid: String = this.uid,
        garageId: String = this.garageId,
        garageName: String = this.garageName,
        ownerName: String = this.ownerName,
        ownerPhone: String = this.ownerPhone,
        ownerEmail: String = this.ownerEmail,
        ownerContactEmail: String = this.ownerContactEmail
    ) {
        this.uid = uid
        this.garageId = garageId
        this.garageName = garageName
        this.ownerName = ownerName
        this.ownerPhone = ownerPhone
        this.ownerEmail = ownerEmail
        this.ownerContactEmail = ownerContactEmail
        prefs?.edit()
            ?.putString(KEY_UID, uid)
            ?.putString(KEY_GARAGE_ID, garageId)
            ?.putString(KEY_GARAGE_NAME, garageName)
            ?.putString(KEY_OWNER_NAME, ownerName)
            ?.putString(KEY_OWNER_PHONE, ownerPhone)
            ?.putString(KEY_OWNER_EMAIL, ownerEmail)
            ?.putString(KEY_CONTACT_EMAIL, ownerContactEmail)
            ?.apply()
    }

    fun clear() {
        uid = ""
        garageId = ""
        logoUpdatedAt = 0L
        _signedInUser.value = SignedInUser()
        garageName = "Garage Guru"
        ownerName = "Owner"
        ownerPhone = ""
        ownerEmail = ""
        ownerContactEmail = ""
        prefs?.edit()?.clear()?.apply()
    }

    /** Deterministic workspace id for a brand new owner account. */
    fun garageIdFor(uid: String): String = "garage_$uid"

    // --- Which workshop's rows are currently sitting in Room ---

    private const val KEY_LOCAL_GARAGE = "localDataGarageId"
    private const val KEY_LANGUAGE = "appLanguage"

    /** Chosen UI language (en / hi / mr). Survives restarts so the app opens in it. */
    var appLanguage: String
        get() = prefs?.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) { prefs?.edit()?.putString(KEY_LANGUAGE, value)?.apply() }

    /**
     * The garage whose data the local Room database currently holds. When this stops matching
     * [garageId] the local tables belong to somebody else and have to be wiped before syncing.
     */
    var localDataGarageId: String
        get() = prefs?.getString(KEY_LOCAL_GARAGE, "").orEmpty()
        set(value) { prefs?.edit()?.putString(KEY_LOCAL_GARAGE, value)?.apply() }
}
