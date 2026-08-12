package com.example.data.api

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object FirebaseAuthHelper {
    private const val TAG = "FirebaseAuthHelper"
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /**
     * The login field is labelled "Mobile Number", but accounts provisioned from the Firebase
     * console use a real email address. Accept both: anything containing '@' is used verbatim,
     * everything else is mapped onto the synthetic phone identity the app signs workshops up with.
     */
    private fun identityToEmail(identity: String): String {
        val trimmed = identity.trim()
        if (trimmed.contains("@")) return trimmed
        val sanitized = trimmed.replace(Regex("[^0-9]"), "")
        return "user_$sanitized@garageguru.local"
    }

    fun isSignedIn(): Boolean = auth.currentUser != null

    fun currentUid(): String = auth.currentUser?.uid.orEmpty()

    /**
     * Sends a Firebase password-reset mail.
     *
     * A mobile number maps to the synthetic `user_<digits>@garageguru.local` identity, which no
     * mail can ever reach, so for those we look up the recovery address captured at sign up and
     * send there instead. Accounts with no recovery address get an honest refusal rather than a
     * "check your inbox" that never arrives.
     */
    fun sendPasswordReset(identity: String, callback: (success: Boolean, message: String) -> Unit) {
        val trimmed = identity.trim()
        if (trimmed.isEmpty()) {
            callback(false, "Enter your registered mobile number or email first")
            return
        }
        if (trimmed.contains("@")) {
            sendResetTo(trimmed, callback)
            return
        }

        // A mobile number was typed. The login identity itself is synthetic and unreachable, so
        // look up the recovery address the owner gave at sign up.
        val loginEmail = identityToEmail(trimmed)
        db.collection("admin").whereEqualTo("email", loginEmail).limit(1).get()
            .addOnSuccessListener { snap ->
                val recovery = snap.documents.firstOrNull()?.getString("contactEmail").orEmpty()
                if (recovery.isBlank()) {
                    callback(
                        false,
                        "No recovery email is saved for this mobile number. Ask your garage admin " +
                            "to reset it, or add an email id in Account settings."
                    )
                } else {
                    sendResetTo(recovery, callback)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Recovery lookup failed", e)
                callback(false, e.localizedMessage ?: "Could not look up your account")
            }
    }

    private fun sendResetTo(address: String, callback: (Boolean, String) -> Unit) {
        auth.sendPasswordResetEmail(address)
            .addOnSuccessListener { callback(true, "Password reset link sent to $address") }
            .addOnFailureListener { e ->
                Log.e(TAG, "Password reset failed", e)
                callback(false, e.localizedMessage ?: "Could not send reset link")
            }
    }

    fun signOut() {
        auth.signOut()
        GarageSession.clear()
        GarageLogoStore.clear()
    }

    fun signUp(
        garageName: String,
        ownerName: String,
        phone: String,
        password: String,
        city: String = "",
        contactEmail: String = "",
        callback: (success: Boolean, message: String) -> Unit
    ) {
        val email = identityToEmail(phone)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e(TAG, "signUp failed", task.exception)
                    callback(false, task.exception?.localizedMessage ?: "Sign up failed")
                    return@addOnCompleteListener
                }

                val uid = auth.currentUser?.uid
                if (uid == null) {
                    callback(false, "Signup succeeded but user UID missing")
                    return@addOnCompleteListener
                }

                val garageId = GarageSession.garageIdFor(uid)

                val garageDoc = hashMapOf(
                    "id" to garageId,
                    "name" to garageName,
                    "ownerUid" to uid,
                    "ownerName" to ownerName,
                    "ownerEmail" to email,
                    "contactEmail" to contactEmail,
                    "phone" to phone,
                    "address" to city,
                    "city" to city,
                    "state" to "",
                    "country" to "India",
                    "gstNumber" to "",
                    "currency" to "INR",
                    "currencySymbol" to "₹",
                    "taxRatePercent" to 18,
                    "timezone" to "Asia/Kolkata",
                    "workingHours" to "09:00 - 20:00",
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                val adminDoc = hashMapOf(
                    "uid" to uid,
                    "name" to ownerName,
                    "email" to email,
                    "contactEmail" to contactEmail,
                    "phone" to phone,
                    "garageId" to garageId,
                    "role" to "Owner",
                    "permissions" to listOf(
                        "jobCards", "cashItems", "inventoryItems",
                        "memberships", "customers", "serviceAlerts"
                    ),
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp()
                )

                val batch = db.batch()
                batch.set(db.collection("garages").document(garageId), garageDoc, SetOptions.merge())
                batch.set(db.collection("admin").document(uid), adminDoc, SetOptions.merge())
                batch.commit()
                    .addOnSuccessListener {
                        GarageSession.update(
                            uid = uid,
                            garageId = garageId,
                            garageName = garageName,
                            ownerName = ownerName,
                            ownerPhone = phone,
                            ownerEmail = email
                        )
                        callback(true, "Signed up and registered garage")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed writing garage/admin docs", e)
                        // The auth account exists, so keep the user moving with a local session.
                        GarageSession.update(
                            uid = uid,
                            garageId = garageId,
                            garageName = garageName,
                            ownerName = ownerName,
                            ownerPhone = phone,
                            ownerEmail = email
                        )
                        callback(false, "Signed up but failed to register garage: ${e.message}")
                    }
            }
    }

    fun signIn(
        phone: String,
        password: String,
        callback: (success: Boolean, ownerName: String?, garageName: String?, message: String?) -> Unit
    ) {
        val email = identityToEmail(phone)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e(TAG, "signIn failed", task.exception)
                    callback(false, null, null, task.exception?.localizedMessage ?: "Sign in failed")
                    return@addOnCompleteListener
                }
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    callback(false, null, null, "Sign in succeeded but UID missing")
                    return@addOnCompleteListener
                }
                loadProfile(uid, fallbackPhone = phone, fallbackEmail = email) { owner, garage, warning ->
                    db.collection("admin").document(uid)
                        .set(mapOf("lastLoginAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                    callback(true, owner, garage, warning)
                }
            }
    }

    /**
     * Re-hydrates the workshop identity for an already-authenticated user on cold start,
     * so a returning owner is not thrown back to the login form.
     */
    fun restoreSession(callback: (success: Boolean, ownerName: String?, garageName: String?, phone: String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            callback(false, null, null, null)
            return
        }
        loadProfile(user.uid, fallbackPhone = GarageSession.ownerPhone, fallbackEmail = user.email.orEmpty()) { owner, garage, _ ->
            callback(true, owner, garage, GarageSession.ownerPhone)
        }
    }

    /** Reads admin/{uid} then garages/{garageId}, self-healing the admin doc when it is absent. */
    private fun loadProfile(
        uid: String,
        fallbackPhone: String,
        fallbackEmail: String,
        onDone: (ownerName: String, garageName: String, warning: String?) -> Unit
    ) {
        db.collection("admin").document(uid).get()
            .addOnSuccessListener { adminSnap ->
                val garageId: String
                val ownerName: String
                val ownerPhone: String
                val ownerEmail: String

                if (adminSnap.exists()) {
                    garageId = adminSnap.getString("garageId")?.takeIf { it.isNotBlank() }
                        ?: GarageSession.garageIdFor(uid)
                    ownerName = adminSnap.getString("name")?.takeIf { it.isNotBlank() } ?: "Owner"
                    ownerPhone = adminSnap.getString("phone")?.takeIf { it.isNotBlank() } ?: fallbackPhone
                    ownerEmail = adminSnap.getString("email")?.takeIf { it.isNotBlank() } ?: fallbackEmail
                } else {
                    garageId = GarageSession.garageIdFor(uid)
                    ownerName = "Owner"
                    ownerPhone = fallbackPhone
                    ownerEmail = fallbackEmail
                    db.collection("admin").document(uid).set(
                        hashMapOf(
                            "uid" to uid,
                            "name" to ownerName,
                            "email" to ownerEmail,
                            "phone" to ownerPhone,
                            "garageId" to garageId,
                            "role" to "Owner",
                            "permissions" to listOf(
                                "jobCards", "cashItems", "inventoryItems",
                                "memberships", "customers", "serviceAlerts"
                            ),
                            "active" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "lastLoginAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                }

                db.collection("garages").document(garageId).get()
                    .addOnSuccessListener { garageSnap ->
                        val garageName = garageSnap.getString("name")?.takeIf { it.isNotBlank() }
                            ?: "Garage Guru"
                        GarageSession.logoUpdatedAt = garageSnap.getLong("logoUpdatedAt") ?: 0L
                        if (!garageSnap.exists()) {
                            db.collection("garages").document(garageId).set(
                                hashMapOf(
                                    "id" to garageId,
                                    "name" to garageName,
                                    "ownerUid" to uid,
                                    "ownerName" to ownerName,
                                    "ownerEmail" to ownerEmail,
                                    "phone" to ownerPhone,
                                    "currency" to "INR",
                                    "currencySymbol" to "₹",
                                    "active" to true,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                ),
                                SetOptions.merge()
                            )
                        }
                        GarageSession.update(
                            uid = uid,
                            garageId = garageId,
                            garageName = garageName,
                            ownerName = ownerName,
                            ownerPhone = ownerPhone,
                            ownerEmail = ownerEmail
                        )
                        onDone(ownerName, garageName, null)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed fetching garage doc", e)
                        GarageSession.update(
                            uid = uid,
                            garageId = garageId,
                            ownerName = ownerName,
                            ownerPhone = ownerPhone,
                            ownerEmail = ownerEmail
                        )
                        onDone(ownerName, GarageSession.garageName, "Signed in but failed to fetch garage data: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed fetching admin doc", e)
                val garageId = GarageSession.garageIdFor(uid)
                GarageSession.update(uid = uid, garageId = garageId, ownerPhone = fallbackPhone, ownerEmail = fallbackEmail)
                onDone(GarageSession.ownerName, GarageSession.garageName, "Signed in but failed to fetch profile: ${e.message}")
            }
    }
}
