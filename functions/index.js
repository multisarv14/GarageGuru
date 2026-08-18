/**
 * Cloud Functions for Garage Guru.
 *
 * Deploy with:  firebase deploy --only functions
 * Requires the Blaze plan and Phone sign-in enabled under Authentication → Sign-in method.
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Sets a new password for the workshop that owns the caller's verified mobile number.
 *
 * Why this cannot be done in the app: workshops sign in with email/password against a synthetic
 * `user_<digits>@garageguru.local` identity. Firebase refuses to change that password from a
 * client without the old one, and verifying an OTP authenticates the caller as a *phone* identity
 * with a different uid, which owns none of the garage's data. Only the Admin SDK can bridge the
 * two — the caller proves it holds the number, and this sets the password on the account that
 * number belongs to.
 *
 * The caller's `phone_number` claim is the authorisation: it is put there by Firebase after a
 * successful OTP verification and cannot be forged by the client.
 */
exports.resetPasswordAfterOtp = onCall(async (request) => {
  const phoneNumber = request.auth && request.auth.token && request.auth.token.phone_number;
  if (!phoneNumber) {
    throw new HttpsError(
      "unauthenticated",
      "Verify your mobile number with an OTP before setting a new password."
    );
  }

  const newPassword = request.data && request.data.newPassword;
  if (typeof newPassword !== "string" || newPassword.length < 6) {
    throw new HttpsError("invalid-argument", "Password must be at least 6 characters.");
  }

  // The app stores numbers as typed; match on the trailing digits so +91XXXXXXXXXX and a bare
  // ten-digit number resolve to the same account.
  const digits = phoneNumber.replace(/\D/g, "");
  const localTen = digits.slice(-10);
  if (localTen.length !== 10) {
    throw new HttpsError("invalid-argument", "That mobile number is not valid.");
  }

  // Resolved from Firebase Auth, which is the only authoritative record of who can log in.
  // Looking this up in Firestore instead meant every account created before the `admin`
  // documents existed could never reset its password — the account was real, but invisible here.
  let uid;
  try {
    const user = await admin.auth().getUserByEmail(`user_${localTen}@garageguru.local`);
    uid = user.uid;
  } catch (e) {
    throw new HttpsError("not-found", "No garage is registered against this mobile number.");
  }

  await admin.auth().updateUser(uid, { password: newPassword });
  await admin
    .firestore()
    .collection("admin")
    .doc(uid)
    .set({ passwordResetAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });

  // The temporary phone identity has served its purpose; leaving it behind would accumulate
  // orphan users, one per reset.
  if (request.auth.uid !== uid) {
    await admin.auth().deleteUser(request.auth.uid).catch(() => {
      /* best effort — the reset itself has already succeeded */
    });
  }

  return { ok: true };
});

/** The garage the caller belongs to, and whether they may administer it. */
async function requireGarageAdmin(request) {
  const uid = request.auth && request.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");

  const adminDoc = await admin.firestore().collection("admin").doc(uid).get();
  if (!adminDoc.exists) throw new HttpsError("permission-denied", "No profile for this account.");
  if (adminDoc.get("active") === false) throw new HttpsError("permission-denied", "This account is inactive.");

  const role = String(adminDoc.get("role") || "");
  const permissions = adminDoc.get("permissions") || [];
  const isOwner = role.toLowerCase() === "owner";
  // Managing people is itself a permission; only the owner or a role granted it may do this.
  if (!isOwner && !permissions.includes("team.create")) {
    throw new HttpsError("permission-denied", "You do not have permission to manage users.");
  }
  return { uid, garageId: adminDoc.get("garageId"), isOwner };
}

/**
 * Creates a login for a team member.
 *
 * Runs here rather than in the app because creating a user client-side signs that new user in,
 * throwing the admin out of their own session mid-task. Doing it with the Admin SDK also lets the
 * mobile number be re-checked against the registry immediately before the account is made, which
 * is what stops two admins adding the same number at the same moment.
 */
exports.createTeamUser = onCall(async (request) => {
  const caller = await requireGarageAdmin(request);
  const d = request.data || {};

  const phone = String(d.phone || "").replace(/\D/g, "");
  const name = String(d.name || "").trim();
  const password = String(d.password || "");
  const phoneHash = String(d.phoneHash || "");

  if (phone.length < 10) throw new HttpsError("invalid-argument", "Enter a valid 10-digit mobile number.");
  if (!name) throw new HttpsError("invalid-argument", "Name is required.");
  if (password.length < 6) throw new HttpsError("invalid-argument", "Password must be at least 6 characters.");
  if (!phoneHash) throw new HttpsError("invalid-argument", "Missing registry key.");

  const db = admin.firestore();
  const registryRef = db.collection("registry").doc(phoneHash);
  const existing = await registryRef.get();

  if (existing.exists && existing.get("active") !== false) {
    // The exact wording the workshop asked for, naming the garage that holds the number.
    const garageName = existing.get("garageName") || "another garage";
    throw new HttpsError(
      "already-exists",
      `This number is already registered and user is still active with "${garageName}".`
    );
  }

  // The login identity mirrors the owner sign-up scheme, so one login form serves everybody.
  const email = `user_${phone}@garageguru.local`;
  let userRecord;
  try {
    userRecord = await admin.auth().createUser({ email, password, displayName: name });
  } catch (e) {
    if (e.code === "auth/email-already-exists") {
      // The auth account outlived its registry entry — reuse it rather than stranding the number.
      userRecord = await admin.auth().getUserByEmail(email);
      await admin.auth().updateUser(userRecord.uid, { password, displayName: name, disabled: false });
    } else {
      throw new HttpsError("internal", e.message || "Could not create the user.");
    }
  }

  await db.collection("admin").doc(userRecord.uid).set(
    {
      uid: userRecord.uid,
      name,
      phone,
      email,
      garageId: caller.garageId,
      role: String(d.roleName || "User"),
      roleId: Number(d.roleId || 0),
      teamId: Number(d.teamId || 0),
      permissions: Array.isArray(d.permissions) ? d.permissions : [],
      active: true,
      createdBy: caller.uid,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  await registryRef.set(
    {
      kind: "user",
      garageId: caller.garageId,
      garageName: String(d.garageName || ""),
      uid: userRecord.uid,
      active: true,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  return { uid: userRecord.uid };
});

/**
 * Activates or deactivates a team member's login.
 *
 * Disabling at the Auth layer is what actually stops them: clearing a flag in Firestore alone
 * would leave an already-signed-in session working until its token expired.
 */
exports.setTeamUserActive = onCall(async (request) => {
  const caller = await requireGarageAdmin(request);
  const uid = String((request.data || {}).uid || "");
  const active = Boolean((request.data || {}).active);
  const phoneHash = String((request.data || {}).phoneHash || "");

  if (!uid) throw new HttpsError("invalid-argument", "Missing user id.");

  const db = admin.firestore();
  const target = await db.collection("admin").doc(uid).get();
  if (!target.exists) throw new HttpsError("not-found", "That user does not exist.");
  if (target.get("garageId") !== caller.garageId) {
    throw new HttpsError("permission-denied", "That user belongs to another garage.");
  }
  if (target.get("role") === "Owner") {
    throw new HttpsError("failed-precondition", "The garage owner cannot be deactivated.");
  }

  await admin.auth().updateUser(uid, { disabled: !active });
  await target.ref.set({ active }, { merge: true });
  if (phoneHash) {
    // Frees the number for reassignment once the user is inactive.
    await db.collection("registry").doc(phoneHash).set({ active }, { merge: true });
  }
  return { ok: true };
});

/**
 * Publishes the default service rate card to the global `serviceMaster` collection.
 *
 * Master data lives in one place so a price correction reaches every workshop without an app
 * release. Written with the Admin SDK because the collection is deliberately read-only to clients
 * — a garage must not be able to edit what every other garage imports.
 *
 * Idempotent: documents are keyed by a slug of the name, so re-running updates prices in place
 * rather than creating a second copy of the list.
 */
exports.publishServiceMaster = onCall(async (request) => {
  const caller = await requireGarageAdmin(request);
  if (!caller.isOwner) {
    throw new HttpsError("permission-denied", "Only a garage owner can publish master data.");
  }

  const entries = require("./serviceMaster.js");
  const db = admin.firestore();
  const slug = (name) => name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");

  // Firestore caps a batch at 500 writes; 199 fits, but chunking keeps this safe if the card grows.
  let written = 0;
  for (let i = 0; i < entries.length; i += 400) {
    const batch = db.batch();
    entries.slice(i, i + 400).forEach((e) => {
      batch.set(
        db.collection("serviceMaster").doc(slug(e.name)),
        {
          name: e.name,
          price: e.price,
          category: e.category || "TWO_WHEELER",
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
      written++;
    });
    await batch.commit();
  }

  await db.collection("serviceMaster").doc("_meta").set(
    {
      count: entries.length,
      publishedBy: caller.uid,
      publishedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  return { written };
});

/**
 * Publishes the two-wheeler vehicle master to the global `vehicleMaster` collection.
 *
 * Same reasoning as the service card: the job card's Vehicle Type → Brand → Model → Variant
 * chain is reference data every workshop shares, so it belongs in one place that can be corrected
 * without shipping an app. Client-writable would let one garage rewrite everyone's dropdowns, so
 * this goes through the Admin SDK.
 *
 * Idempotent — documents are keyed by type/brand/model, so re-running corrects in place.
 */
exports.publishVehicleMaster = onCall(async (request) => {
  const caller = await requireGarageAdmin(request);
  if (!caller.isOwner) {
    throw new HttpsError("permission-denied", "Only a garage owner can publish master data.");
  }

  const rows = require("./vehicleMaster.js");
  const db = admin.firestore();
  const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");

  let written = 0;
  for (let i = 0; i < rows.length; i += 400) {
    const batch = db.batch();
    rows.slice(i, i + 400).forEach((r) => {
      const id = `${slug(r.type)}__${slug(r.brand)}__${slug(r.model)}`;
      batch.set(
        db.collection("vehicleMaster").doc(id),
        {
          type: r.type,
          brand: r.brand,
          model: r.model,
          variants: r.variants,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
      written++;
    });
    await batch.commit();
  }

  await db.collection("vehicleMaster").doc("_meta").set(
    {
      models: rows.length,
      variants: rows.reduce((a, r) => a + r.variants.length, 0),
      publishedBy: caller.uid,
      publishedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  return { written };
});

/**
 * Whether a mobile number already has a login.
 *
 * Deliberately callable without being signed in: both the people who need this — somebody
 * resetting a forgotten password, and somebody registering a garage — are by definition not
 * logged in yet. Firebase Auth is the authoritative source; the `registry` collection only knows
 * about accounts created after it was introduced, which is why a check against it alone told
 * every older workshop that it did not exist.
 *
 * Returns nothing but a boolean, so it reveals no more than the sign-up form already would.
 */
exports.isPhoneRegistered = onCall(async (request) => {
  const digits = String((request.data || {}).phone || "").replace(/\D/g, "").slice(-10);
  if (digits.length !== 10) return { registered: false };
  try {
    await admin.auth().getUserByEmail(`user_${digits}@garageguru.local`);
    return { registered: true };
  } catch (e) {
    return { registered: false };
  }
});

/**
 * Fills the registry for accounts that predate it.
 *
 * The duplicate-number checks read the registry, so without this every workshop registered before
 * the registry existed is invisible to them. Idempotent, and never overwrites an entry that is
 * already there.
 */
exports.backfillRegistry = onCall(async (request) => {
  const caller = await requireGarageAdmin(request);
  if (!caller.isOwner) {
    throw new HttpsError("permission-denied", "Only a garage owner can run the backfill.");
  }

  const crypto = require("crypto");
  const sha256 = (v) => crypto.createHash("sha256").update(v).digest("hex");
  const db = admin.firestore();

  let created = 0;
  let scanned = 0;
  let pageToken;
  do {
    const page = await admin.auth().listUsers(1000, pageToken);
    for (const user of page.users) {
      const m = /^user_(\d{10})@garageguru\.local$/.exec(user.email || "");
      if (!m) continue;
      scanned++;
      const phone = m[1];
      const ref = db.collection("registry").doc(sha256(`phone:${phone}`));
      if ((await ref.get()).exists) continue;

      // Pick up the garage name where an admin document exists, so a later duplicate message can
      // name the workshop rather than saying "another garage".
      const adminDoc = await db.collection("admin").doc(user.uid).get();
      await ref.set(
        {
          phoneHash: sha256(`phone:${phone}`),
          emailHash: adminDoc.exists && adminDoc.get("contactEmail")
            ? sha256(`email:${String(adminDoc.get("contactEmail")).toLowerCase()}`)
            : "",
          garageId: adminDoc.exists ? adminDoc.get("garageId") || "" : "",
          uid: user.uid,
          backfilled: true,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
      created++;
    }
    pageToken = page.pageToken;
  } while (pageToken);

  return { scanned, created };
});
