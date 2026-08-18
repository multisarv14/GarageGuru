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

    // ------------------------------------------------------------------
    // Duplicate registration guard
    // ------------------------------------------------------------------

    /**
     * Public directory of what is already registered, keyed by a hash rather than the value.
     *
     * A sign-up has to be able to say *which* of the mobile number and the email address is
     * already taken, and it has to say so before an account is created — but at that point nobody
     * is signed in yet, so the `admin` collection is unreadable. This tiny collection is readable
     * without auth for exactly that check.
     *
     * Only SHA-256 hashes are stored, so an unauthenticated reader can confirm a number they
     * already know is registered but cannot enumerate anybody's phone number or email out of it.
     */
    private const val REGISTRY = "registry"

    private fun sha256(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun normalizedPhone(phone: String): String = phone.filter(Char::isDigit)

    private fun phoneHash(phone: String) = sha256("phone:" + normalizedPhone(phone))

    private fun emailHash(email: String) = sha256("email:" + email.trim().lowercase())

    /** What an attempted sign-up collides with, if anything. */
    data class RegistrationConflict(val phoneTaken: Boolean, val emailTaken: Boolean) {
        val hasConflict: Boolean get() = phoneTaken || emailTaken

        /** The message the sign-up form shows — naming the actual conflict, not a generic one. */
        val message: String
            get() = when {
                phoneTaken && emailTaken ->
                    "This mobile number and email address are already registered."
                phoneTaken -> "This mobile number is already registered."
                emailTaken -> "This email address is already registered."
                else -> ""
            }
    }

    /**
     * Looks up both identifiers before anything is created.
     *
     * A lookup failure returns "no conflict": Firebase still refuses a duplicate mobile number at
     * account creation, so the worst case is the older, vaguer error — never a duplicate account.
     */
    fun checkExistingRegistration(
        phone: String,
        contactEmail: String,
        callback: (RegistrationConflict) -> Unit
    ) {
        val digits = normalizedPhone(phone)
        if (digits.isEmpty()) {
            callback(RegistrationConflict(phoneTaken = false, emailTaken = false))
            return
        }

        // Firebase Auth first — it knows about every account, including the ones that predate the
        // registry. The registry is still consulted for the email side, which Auth cannot answer.
        com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("isPhoneRegistered")
            .call(hashMapOf("phone" to digits))
            .addOnSuccessListener { authResult ->
                @Suppress("UNCHECKED_CAST")
                val phoneTakenInAuth =
                    ((authResult.data as? Map<String, Any?>)?.get("registered") as? Boolean) == true
                continueEmailCheck(phone, contactEmail, phoneTakenInAuth, callback)
            }
            .addOnFailureListener {
                db.collection(REGISTRY).document(phoneHash(phone)).get()
                    .addOnSuccessListener { snap ->
                        continueEmailCheck(phone, contactEmail, snap.exists(), callback)
                    }
                    .addOnFailureListener {
                        callback(RegistrationConflict(phoneTaken = false, emailTaken = false))
                    }
            }
    }

    /** Second half of the duplicate check: the email side, which only the registry can answer. */
    private fun continueEmailCheck(
        phone: String,
        contactEmail: String,
        phoneTaken: Boolean,
        callback: (RegistrationConflict) -> Unit
    ) {
        if (contactEmail.isBlank()) {
            callback(RegistrationConflict(phoneTaken, emailTaken = false))
            return
        }
        db.collection(REGISTRY)
            .whereEqualTo("emailHash", emailHash(contactEmail))
            .limit(1)
            .get()
            .addOnSuccessListener { emailSnap ->
                callback(RegistrationConflict(phoneTaken, !emailSnap.isEmpty))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Email registry lookup failed", e)
                callback(RegistrationConflict(phoneTaken, emailTaken = false))
            }
    }


    /** Files a freshly registered workshop in the directory the duplicate check reads. */
    private fun recordRegistration(phone: String, contactEmail: String, garageId: String, uid: String) {
        val entry = hashMapOf(
            "phoneHash" to phoneHash(phone),
            "emailHash" to if (contactEmail.isBlank()) "" else emailHash(contactEmail),
            "garageId" to garageId,
            "uid" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection(REGISTRY).document(phoneHash(phone))
            .set(entry, SetOptions.merge())
            .addOnFailureListener { e -> Log.e(TAG, "Could not record registration", e) }
    }

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

    // ------------------------------------------------------------------
    // Forgot password — mobile number + OTP
    // ------------------------------------------------------------------

    /**
     * OTP-based password reset, in three steps: confirm the number is registered, send and check
     * an SMS code, then set the new password.
     *
     * A word on why the last step needs a backend. Workshops sign in with email/password against a
     * synthetic `user_<digits>@garageguru.local` identity. Firebase will not let a client change
     * that account's password without the *old* one, and verifying an OTP signs the caller in as a
     * separate phone-identity user with a different uid — which owns none of the garage's data. So
     * the client proves it holds the number, and a Cloud Function (see `functions/index.js`) uses
     * the Admin SDK to set the password on the account that number actually belongs to. Everything
     * up to that point happens here.
     */
    object PasswordReset {

        /** How long a sent code stays valid, matching the value handed to Firebase. */
        const val OTP_TIMEOUT_SECONDS = 60L

        private var verificationId: String? = null
        private var resendToken: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken? = null

        /** Cleared between attempts so a stale code from an abandoned reset is never reused. */
        fun reset() {
            verificationId = null
            resendToken = null
        }

        /**
         * What a registration lookup could establish.
         *
         * [UNKNOWN] exists because only one source can actually deny. The Cloud Function reads
         * Firebase Auth and is authoritative both ways; the `registry` collection is an index that
         * was added late, so it can *confirm* a number and never *deny* one — a workshop that
         * predates it is simply absent. Collapsing that absence into "not registered" is what told
         * long-standing garages they did not exist and left them unable to reset a password.
         */
        enum class Registration { REGISTERED, NOT_REGISTERED, UNKNOWN }

        /**
         * Confirms the number belongs to a registered workshop before any SMS is sent.
         *
         * Deliberately callable while signed out, which is the whole point — somebody resetting a
         * forgotten password cannot sign in first.
         */
        fun isRegistered(phone: String, callback: (Registration) -> Unit) {
            val digits = normalizedPhone(phone)
            if (digits.length < 10) {
                callback(Registration.NOT_REGISTERED)
                return
            }
            // Firebase Auth, through a Cloud Function, is the only authoritative answer.
            com.google.firebase.functions.FirebaseFunctions.getInstance()
                .getHttpsCallable("isPhoneRegistered")
                .call(hashMapOf("phone" to digits))
                .addOnSuccessListener { result ->
                    @Suppress("UNCHECKED_CAST")
                    val registered = (result.data as? Map<String, Any?>)?.get("registered") as? Boolean
                    callback(if (registered == true) Registration.REGISTERED else Registration.NOT_REGISTERED)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Registration lookup failed; falling back to the registry", e)
                    // The registry can still vouch for a number, but its silence proves nothing.
                    db.collection(REGISTRY).document(phoneHash(phone)).get()
                        .addOnSuccessListener {
                            callback(if (it.exists()) Registration.REGISTERED else Registration.UNKNOWN)
                        }
                        .addOnFailureListener { callback(Registration.UNKNOWN) }
                }
        }

        /**
         * Sends the OTP. [onVerified] fires when Android auto-retrieves the code without the
         * owner typing anything, which is the common case on the same handset.
         */
        fun sendOtp(
            activity: android.app.Activity,
            phone: String,
            onCodeSent: () -> Unit,
            onVerified: (com.google.firebase.auth.PhoneAuthCredential) -> Unit,
            onError: (String) -> Unit
        ) {
            val digits = normalizedPhone(phone)
            // Firebase wants E.164; a bare 10-digit Indian number is the norm in this app.
            val e164 = if (phone.trim().startsWith("+")) phone.trim() else "+91$digits"

            val callbacks = object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                    onVerified(credential)
                }

                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    Log.e(TAG, "OTP send failed", e)
                    onError(
                        when (e) {
                            is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
                                "That mobile number is not valid."
                            is com.google.firebase.FirebaseTooManyRequestsException ->
                                "Too many attempts from this device. Try again later."
                            else -> e.localizedMessage ?: "Could not send the OTP."
                        }
                    )
                }

                override fun onCodeSent(
                    id: String,
                    token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
                ) {
                    verificationId = id
                    resendToken = token
                    onCodeSent()
                }
            }

            val builder = com.google.firebase.auth.PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(e164)
                .setTimeout(OTP_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
            resendToken?.let { builder.setForceResendingToken(it) }

            com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(builder.build())
        }

        /** Turns a typed code into a credential, rejecting an empty or stale attempt up front. */
        fun credentialFor(code: String): Result<com.google.firebase.auth.PhoneAuthCredential> {
            val id = verificationId
                ?: return Result.failure(IllegalStateException("Request a new OTP first."))
            if (code.length < 6) return Result.failure(IllegalArgumentException("Enter the 6-digit OTP."))
            return runCatching {
                com.google.firebase.auth.PhoneAuthProvider.getCredential(id, code)
            }
        }

        /**
         * Verifies the OTP and sets the new password.
         *
         * The phone sign-in is temporary: it exists only to prove ownership of the number to the
         * Cloud Function, and is signed out again whatever the outcome so the app never sits in a
         * half-authenticated state.
         */
        fun completeReset(
            phone: String,
            code: String,
            newPassword: String,
            callback: (success: Boolean, message: String) -> Unit
        ) {
            if (newPassword.length < 6) {
                callback(false, "Password must be at least 6 characters.")
                return
            }
            val credential = credentialFor(code).getOrElse {
                callback(false, it.message ?: "Invalid OTP.")
                return
            }
            finishWithCredential(phone, credential, newPassword, callback)
        }

        /** Shared tail of the typed-code and auto-retrieved paths. */
        fun finishWithCredential(
            phone: String,
            credential: com.google.firebase.auth.PhoneAuthCredential,
            newPassword: String,
            callback: (success: Boolean, message: String) -> Unit
        ) {
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
                    functions.getHttpsCallable("resetPasswordAfterOtp")
                        .call(hashMapOf("newPassword" to newPassword))
                        .addOnSuccessListener {
                            auth.signOut()
                            reset()
                            callback(true, "Password updated. Log in with your new password.")
                        }
                        .addOnFailureListener { e ->
                            auth.signOut()
                            Log.e(TAG, "Password reset function failed", e)
                            callback(
                                false,
                                e.localizedMessage
                                    ?: "Could not update the password. Please try again."
                            )
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OTP verification failed", e)
                    val reason = when (e) {
                        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
                            "That OTP is incorrect. Check the code and try again."
                        else -> "That OTP has expired or is incorrect. Request a new one."
                    }
                    callback(false, reason)
                }
        }
    }

    // ------------------------------------------------------------------
    // Team users
    // ------------------------------------------------------------------

    /** Where a mobile number is already in use, and whether that user can still log in. */
    data class UserConflict(val garageName: String, val active: Boolean)

    /**
     * Checks whether a mobile number already belongs to a user anywhere in the system.
     *
     * Deliberately global rather than per-garage: the number is the login id, so two workshops
     * cannot both own it. The registry stores hashes, so this confirms a number the admin has
     * typed without exposing anybody else's.
     */
    fun findExistingUser(phone: String, callback: (UserConflict?) -> Unit) {
        val digits = normalizedPhone(phone)
        if (digits.length < 10) {
            callback(null)
            return
        }
        db.collection(REGISTRY).document(userPhoneHash(phone)).get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    callback(null)
                } else {
                    callback(
                        UserConflict(
                            garageName = snap.getString("garageName").orEmpty().ifBlank { "another garage" },
                            active = snap.getBoolean("active") ?: true
                        )
                    )
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "User lookup failed", e)
                // Fail open on the lookup, closed on creation: the Cloud Function checks again.
                callback(null)
            }
    }

    /** Users live under their own hash prefix so they never collide with garage registrations. */
    private fun userPhoneHash(phone: String) = sha256("user:" + normalizedPhone(phone))

    /**
     * Creates a login for a team member.
     *
     * Done in a Cloud Function rather than here because `createUserWithEmailAndPassword` signs
     * the *new* user in — an admin adding a mechanic would be thrown out of their own session
     * halfway through. The function also re-checks the mobile number under a lock, so two admins
     * adding the same number at once cannot both succeed.
     */
    fun createTeamUser(
        name: String,
        phone: String,
        password: String,
        roleId: Int,
        roleName: String,
        teamId: Int,
        permissionTokens: List<String>,
        callback: (success: Boolean, uid: String, message: String) -> Unit
    ) {
        val payload = hashMapOf(
            "name" to name,
            "phone" to normalizedPhone(phone),
            "password" to password,
            "roleId" to roleId,
            "roleName" to roleName,
            "teamId" to teamId,
            "permissions" to permissionTokens,
            "garageId" to GarageSession.garageId,
            "garageName" to GarageSession.garageName,
            "phoneHash" to userPhoneHash(phone)
        )
        com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("createTeamUser")
            .call(payload)
            .addOnSuccessListener { result ->
                @Suppress("UNCHECKED_CAST")
                val data = result.data as? Map<String, Any?>
                callback(true, data?.get("uid") as? String ?: "", "User created")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "createTeamUser failed", e)
                callback(false, "", e.localizedMessage ?: "Could not create the user.")
            }
    }

    /**
     * Pushes a user's current permissions onto their `admin` document.
     *
     * The security rules read that document, so this is what makes a permission change take
     * effect on the server and not just in the menu.
     */
    fun syncUserPermissions(member: com.example.data.model.TeamMemberEntity, role: com.example.data.model.RoleEntity) {
        if (member.authUid.isBlank()) return
        val tokens = com.example.data.permissions.PermissionSet
            .fromJson(role.permissionsJson)
            .toTokens()
        db.collection("admin").document(member.authUid).set(
            hashMapOf(
                "roleId" to role.id,
                "role" to role.name,
                "permissions" to tokens,
                "active" to member.isActive,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).addOnFailureListener { e -> Log.e(TAG, "Could not sync permissions", e) }
    }

    /** Enables or disables a team member's login, server side. */
    fun setTeamUserActive(
        member: com.example.data.model.TeamMemberEntity,
        active: Boolean,
        callback: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (member.authUid.isBlank()) {
            callback(true, "")
            return
        }
        com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("setTeamUserActive")
            .call(hashMapOf("uid" to member.authUid, "active" to active, "phoneHash" to userPhoneHash(member.phone)))
            .addOnSuccessListener { callback(true, "") }
            .addOnFailureListener { e ->
                Log.e(TAG, "setTeamUserActive failed", e)
                callback(false, e.localizedMessage ?: "Could not change the user's status.")
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
        GarageSettings.clear()
        GarageLogoStore.clear()
        ServiceMasterStore.clear()
        VehicleMasterStore.clear()
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
        // Nothing is created until both identifiers are known to be free, so a second attempt
        // by an already-registered workshop can never leave a half-made account behind.
        checkExistingRegistration(phone, contactEmail) { conflict ->
            if (conflict.hasConflict) {
                callback(false, conflict.message)
            } else {
                createGarageAccount(garageName, ownerName, phone, password, city, contactEmail, callback)
            }
        }
    }

    private fun createGarageAccount(
        garageName: String,
        ownerName: String,
        phone: String,
        password: String,
        city: String,
        contactEmail: String,
        callback: (success: Boolean, message: String) -> Unit
    ) {
        val email = identityToEmail(phone)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e(TAG, "signUp failed", task.exception)
                    // The login identity is derived from the mobile number, so Firebase's
                    // "email address is already in use" always means the *number* is taken.
                    // Saying so plainly beats surfacing a message about an email the owner
                    // never typed.
                    val reason = when (task.exception) {
                        is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                            "This mobile number is already registered."
                        else -> task.exception?.localizedMessage ?: "Sign up failed"
                    }
                    callback(false, reason)
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
                    "appAdminEmail" to GarageSettings.APP_ADMIN_EMAIL,
                    "phone" to phone,
                    "address" to city,
                    "city" to city,
                    "state" to "",
                    "country" to "India",
                    "gstNumber" to "",
                    "gstApplicable" to true,
                    "cgstPercent" to 9,
                    "sgstPercent" to 9,
                    "currency" to "INR",
                    "currencySymbol" to "₹",
                    "taxRatePercent" to 18,
                    "invoicePrefix" to GarageSettings.garageCodeFor(garageName),
                    "invoiceStartNumber" to 1001,
                    "stockValidationEnabled" to true,
                    "showVehicleOnInvoice" to true,
                    "setupCompleted" to false,
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
                    "appAdminEmail" to GarageSettings.APP_ADMIN_EMAIL,
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
                fun openLocalSession() {
                    GarageSession.update(
                        uid = uid,
                        garageId = garageId,
                        garageName = garageName,
                        ownerName = ownerName,
                        ownerPhone = phone,
                        ownerEmail = email,
                        ownerContactEmail = contactEmail
                    )
                    // A fresh workshop has not been through the setup wizard yet, so the owner
                    // is walked through address/GST/invoice/logo on first entry. The invoice
                    // prefix is seeded from the garage initials as a sensible starting point.
                    GarageSettings.update(
                        address = city,
                        invoicePrefix = GarageSettings.garageCodeFor(garageName),
                        invoiceStartNumber = 1001,
                        setupCompleted = false,
                        syncToCloud = false
                    )
                }

                batch.commit()
                    .addOnSuccessListener {
                        openLocalSession()
                        // The Client ID serial comes from a shared counter, so it is issued
                        // after the garage document exists rather than guessed at locally.
                        GarageSettings.ensureClientId(garageName)
                        recordRegistration(phone, contactEmail, garageId, uid)
                        callback(true, "Signed up and registered garage")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed writing garage/admin docs", e)
                        // The auth account exists, so keep the user moving with a local session.
                        openLocalSession()
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

    /**
     * Rebuilds a permission set from the `module.action` tokens stored on the admin document.
     *
     * The tokens are the server's copy — the same list the security rules match on — so the app
     * derives what it shows from exactly what the backend will allow, rather than from a second
     * copy of the role that could drift out of step.
     */
    private fun permissionsFromTokens(tokens: List<String>): com.example.data.permissions.PermissionSet {
        var set = com.example.data.permissions.PermissionSet()
        tokens.forEach { token ->
            val parts = token.split(".")
            if (parts.size != 2) return@forEach
            val module = com.example.data.permissions.AppModule.from(parts[0]) ?: return@forEach
            val action = com.example.data.permissions.PermissionAction.from(parts[1]) ?: return@forEach
            set = set.withAction(module, action, granted = true)
        }
        return set
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
                var contactEmail = ""

                if (adminSnap.exists()) {
                    garageId = adminSnap.getString("garageId")?.takeIf { it.isNotBlank() }
                        ?: GarageSession.garageIdFor(uid)
                    ownerName = adminSnap.getString("name")?.takeIf { it.isNotBlank() } ?: "Owner"
                    ownerPhone = adminSnap.getString("phone")?.takeIf { it.isNotBlank() } ?: fallbackPhone
                    ownerEmail = adminSnap.getString("email")?.takeIf { it.isNotBlank() } ?: fallbackEmail
                    contactEmail = adminSnap.getString("contactEmail").orEmpty()

                    // Resolve who this actually is. An owner keeps full access; a team member
                    // gets exactly what their assigned role grants, read from the same document
                    // the security rules enforce against — so the menu and the server agree.
                    val role = adminSnap.getString("role").orEmpty().ifBlank { "Owner" }
                    val isOwnerAccount = role.equals("Owner", ignoreCase = true) ||
                        adminSnap.getString("ownerUid") == uid
                    @Suppress("UNCHECKED_CAST")
                    val tokens = (adminSnap.get("permissions") as? List<String>).orEmpty()
                    val active = adminSnap.getBoolean("active") ?: true

                    GarageSession.updateSignedInUser(
                        GarageSession.SignedInUser(
                            name = ownerName,
                            roleName = role,
                            isOwner = isOwnerAccount,
                            isActive = active,
                            permissions = permissionsFromTokens(tokens)
                        )
                    )
                } else {
                    garageId = GarageSession.garageIdFor(uid)
                    ownerName = "Owner"
                    ownerPhone = fallbackPhone
                    ownerEmail = fallbackEmail
                    db.collection("admin").document(uid).set(
                        hashMapOf(
                            "uid" to uid,
                            "clientId" to GarageSettings.current.clientId,
                            "name" to ownerName,
                            "email" to ownerEmail,
                            "appAdminEmail" to GarageSettings.APP_ADMIN_EMAIL,
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

                        // Billing and stock preferences travel with the workshop, so a reinstall
                        // or a second device bills exactly like the first one did.
                        GarageSettings.applyFromCloud(
                            clientId = garageSnap.getString("clientId"),
                            servicingType = garageSnap.getString("servicingType"),
                            servicingTypeChosen = garageSnap.getBoolean("servicingTypeChosen"),
                            address = garageSnap.getString("address"),
                            gstin = garageSnap.getString("gstNumber"),
                            gstApplicable = garageSnap.getBoolean("gstApplicable"),
                            cgstPercent = (garageSnap.get("cgstPercent") as? Number)?.toDouble(),
                            sgstPercent = (garageSnap.get("sgstPercent") as? Number)?.toDouble(),
                            invoicePrefix = garageSnap.getString("invoicePrefix"),
                            invoiceStartNumber = (garageSnap.get("invoiceStartNumber") as? Number)?.toInt(),
                            invoiceNextNumber = (garageSnap.get("invoiceNextNumber") as? Number)?.toInt(),
                            stockValidationEnabled = garageSnap.getBoolean("stockValidationEnabled"),
                            showVehicleOnInvoice = garageSnap.getBoolean("showVehicleOnInvoice"),
                            setupCompleted = garageSnap.getBoolean("setupCompleted")
                        )

                        // A workshop that registered before Client IDs existed — or whose issue
                        // failed while offline — picks one up here instead of never getting one.
                        GarageSettings.ensureClientId(garageName)

                        if (!garageSnap.exists()) {
                            db.collection("garages").document(garageId).set(
                                hashMapOf(
                                    "id" to garageId,
                                    "clientId" to GarageSettings.current.clientId,
                                    "name" to garageName,
                                    "ownerUid" to uid,
                                    "ownerName" to ownerName,
                                    "ownerEmail" to ownerEmail,
                                    "appAdminEmail" to GarageSettings.APP_ADMIN_EMAIL,
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
                            ownerEmail = ownerEmail,
                            ownerContactEmail = contactEmail.ifBlank {
                                garageSnap.getString("contactEmail").orEmpty()
                            }
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
