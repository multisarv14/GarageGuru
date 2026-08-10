# Firestore Schema — Garage Guru

Live schema for the **MultiSarv-MobileApps** Firebase project. This documents what is actually
deployed, not a proposal — rules and indexes in this repo (`firestore.rules`,
`firestore.indexes.json`) are the source of truth and are pushed with `firebase deploy`.

| | |
| :--- | :--- |
| Firebase project | `multisarv-mobileapps` (project number `1085525087237`) |
| Android package | `com.multisarv.garageguru` |
| Owner account | `multisarvindia@gmail.com` |
| Auth providers | Email/Password, Phone, Anonymous |
| Config file | `app/google-services.json` |

---

## 1. Tenancy model

The app is multi-tenant: one **garage** (workshop) per owner account.

- Business records live in **root collections**, not subcollections, so a single query per
  collection serves the whole workshop and collection-group queries stay unnecessary.
- Every business document carries a **`garageId`** field. That field is what the security
  rules enforce on write and what the app filters on when reading.
- Document ids are **`{garageId}_{roomId}`**. Room uses autoincrement integers that restart at 1
  for each install, so the garage prefix is what stops two workshops from overwriting each
  other's `jobCards/1`.

```
admin/{uid}                                  -> who is signed in, and which garage they own
garages/{garageId}                           -> workshop profile
garages/{garageId}/assets/logo               -> garage logo, stored as a Firestore Blob
jobCards/{garageId}_{id}                     -> business records, all tagged with garageId
cashItems/{garageId}_{id}
inventoryItems/{garageId}_{id}
memberships/{garageId}_{id}
customers/{garageId}_{id}
serviceAlerts/{garageId}_{id}
```

New workshops get `garageId = "garage_{uid}"`. The original workshop in this project keeps its
historical id, `garage_main`.

---

## 2. Collections

### `admin/{uid}` — owner profile
Document id is the Firebase Auth uid. Read on every sign in to discover `garageId`.

| Field | Type | Notes |
| :--- | :--- | :--- |
| `uid` | String | Matches the document id. |
| `name` | String | Owner / admin name. |
| `email` | String | Login identity (see §3). |
| `phone` | String | Contact number as typed at sign up. |
| `garageId` | String | **The tenant key.** Everything else hangs off this. |
| `role` | String | `Owner`. |
| `permissions` | Array\<String\> | Collection names this account may touch. |
| `active` | Boolean | |
| `createdAt` / `lastLoginAt` | Timestamp | `lastLoginAt` is refreshed on each sign in. |

### `garages/{garageId}` — workshop profile

| Field | Type | Notes |
| :--- | :--- | :--- |
| `id` | String | Matches the document id. |
| `name` | String | Shown in the app's top bar. |
| `ownerUid` / `ownerName` / `ownerEmail` / `phone` | String | |
| `address` / `city` / `state` / `country` | String | `city` comes from the sign-up form. |
| `gstNumber` | String | |
| `currency` / `currencySymbol` | String | `INR` / `₹` for new workshops. |
| `taxRatePercent` | Number | |
| `timezone` / `workingHours` | String | |
| `active` | Boolean | |
| `logoUpdatedAt` | Number | Epoch millis marker; `0`/absent means no logo. Lets a cold start tell whether the cached logo is stale without downloading it. |
| `createdAt` / `updatedAt` | Timestamp | |

### `garages/{garageId}/assets/logo` — garage logo Blob

The only image upload in the app. Handled by `GarageLogoStore`.

| Field | Type | Notes |
| :--- | :--- | :--- |
| `garageId` | String | |
| `image` | **Blob** | Raw JPEG bytes. |
| `mimeType` | String | Always `image/jpeg` — everything is re-encoded on upload. |
| `sizeBytes` | Number | |
| `updatedAtMillis` | Number | Matches `logoUpdatedAt` on the profile. |
| `updatedAt` | Timestamp | Server time. |

**Upload path.** The picked `Uri` is decoded with an `inSampleSize` pre-pass, rotated upright
from its EXIF orientation, scaled so its longest edge is at most **512 px**, then JPEG-encoded
starting at quality 90 and stepping down until it is under **400 KB**. Those bytes become
`Blob.fromBytes(...)`. A typical logo lands around 20 KB.

**Download path.** `Blob.toBytes()` → `ByteArray`, cached to `filesDir/garage_logo_{garageId}.jpg`
and exposed as a `StateFlow<ByteArray?>`. Coil renders the `ByteArray` model directly, so no
temp file or base64 string is involved in display.

**Why a subcollection.** Firestore cannot read part of a document. Keeping the blob on
`garages/{garageId}` would mean re-downloading it on every profile read, including each sign in.
In its own document it is fetched only when `logoUpdatedAt` says the local cache is stale.

**Why the size ceiling matters.** A Firestore document is capped at 1 MiB *including* its blob
fields. The 512 px / 400 KB budget keeps a logo far below that with room to spare; an image that
still cannot be squeezed under the cap is rejected rather than written. For anything larger than
a logo (job photos, damage evidence), Firebase Storage with a download URL is the right tool —
blobs do not scale to that.

### `jobCards/{garageId}_{id}`

`garageId`, `id`, `customerName`, `customerPhone`, `vehicleName`, `vehiclePlate`, `symptoms`,
`diagnosis`, `status`, `tasks`, `materials`, `serviceCharges`, `partsCharges`, `taxes`,
`totalAmount`, `creationTime`, `updatedAt`

`status` moves `ESTIMATE → OPEN → COMPLETED → CLOSED`. `tasks` and `materials` are
comma-separated strings, mirroring the Room columns. `creationTime` is epoch millis (Number);
`updatedAt` is a server Timestamp stamped by the sync layer.

### `cashItems/{garageId}_{id}`

`garageId`, `id`, `type` (`INCOME`/`EXPENSE`), `amount`, `category`, `paymentMode`,
`description`, `timestamp`, `updatedAt`

### `inventoryItems/{garageId}_{id}`

`garageId`, `id`, `name`, `quantity`, `minStockAlert`, `unitPrice`, `updatedAt`

An item is low stock when `quantity <= minStockAlert`.

### `memberships/{garageId}_{id}`

`garageId`, `id`, `customerName`, `customerPhone`, `membershipType`, `monthlyWashLimit`,
`washesUsed`, `renewalDate` (String `YYYY-MM-DD`), `pendingBalance`, `updatedAt`

### `customers/{garageId}_{id}`

`garageId`, `id`, `name`, `phone`, `email`, `vehicleName`, `vehiclePlate`, `address`, `notes`,
`creationTime`, `updatedAt`

### `serviceAlerts/{garageId}_{id}`

`garageId`, `id`, `customerName`, `customerPhone`, `vehiclePlate`, `serviceType`, `intervalKm`,
`dueDaysLeft`, `dueDateString`, `dueTimestamp`, `status`, `reminderNotes`, `updatedAt`

`status` is one of `UPCOMING`, `DUE_SOON`, `OVERDUE`, `SERVICED`.

---

## 3. Authentication

The login field is labelled "Mobile Number", but the app accepts either form:

- Input containing `@` is used as the email verbatim — this is how console-provisioned
  accounts such as `multisarvindia@gmail.com` sign in.
- Anything else is stripped to digits and mapped to a synthetic identity,
  `user_{digits}@garageguru.local`, which is what the Garage Sign Up flow registers.

Firebase persists the session itself, so a returning owner goes straight to the dashboard.
`FirebaseAuthHelper.restoreSession()` re-reads `admin/{uid}` and `garages/{garageId}` on launch,
and `GarageSession` mirrors the result into SharedPreferences so `garageId` is available
synchronously before the network responds.

---

## 4. Sync behaviour

- **Write-through**: every add / update / delete writes Room first, then mirrors the row to
  Firestore with `SetOptions.merge()`.
- **Sign in / cold start**: `FirebaseSyncHelper.pullAll()` queries each collection with
  `whereEqualTo("garageId", …)` and inserts the results into Room. Ids match, so Room's
  `OnConflictStrategy.REPLACE` keeps both sides in step.
- **Fresh sign up**: the cloud is empty, so the locally seeded rows are uploaded instead. Both
  sides end up agreeing either way.

---

## 5. Indexes

18 composite indexes are deployed (`firestore.indexes.json`). Each collection has a
`garageId`-leading index for tenant-scoped reads, plus the original reporting indexes:

| Collection | Indexed fields |
| :--- | :--- |
| `jobCards` | `garageId + creationTime` · `garageId + status + creationTime` · `status + creationTime` · `customerPhone + creationTime` |
| `cashItems` | `garageId + timestamp` · `garageId + type + timestamp` · `type + timestamp` · `category + timestamp` |
| `inventoryItems` | `garageId + name` · `garageId + quantity` · `quantity + name` |
| `memberships` | `garageId + customerName` · `pendingBalance + renewalDate` |
| `customers` | `garageId + name` · `phone + creationTime` |
| `serviceAlerts` | `garageId + dueTimestamp` · `status + dueTimestamp` · `customerPhone + dueTimestamp` |

The app's own pull uses a single equality filter, which Firestore serves from the automatic
single-field index — the composite indexes above are for reporting and sorted queries.

---

## 6. Security rules

See `firestore.rules`. The shape:

- `admin/{uid}` — any signed-in user may read; only the owner of that uid may write it.
- `garages/{garageId}/**` — signed-in read/write.
- All six business collections — signed-in read; writes must carry a non-empty `garageId`
  string, so an untagged document can never be created. Deletes are allowed for signed-in
  callers (a delete carries no `request.resource`).

Tagging is enforced at write time rather than by comparing against the caller's `admin` doc,
which would cost an extra document read on every single operation.

---

## 7. Deploying

```bash
firebase deploy --only firestore:rules   --project multisarv-mobileapps
firebase deploy --only firestore:indexes --project multisarv-mobileapps
```
