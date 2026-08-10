package com.example.data.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Stores the garage logo as a Firestore [Blob].
 *
 * The picked image is decoded, rotated upright, downscaled and JPEG-compressed until it fits
 * comfortably inside Firestore's 1 MiB per-document ceiling, then written as raw bytes to
 * `garages/{garageId}/assets/logo`.
 *
 * It deliberately lives in its own subcollection document rather than on the garage profile:
 * Firestore has no partial document reads, so keeping the blob out of `garages/{garageId}`
 * means the profile lookup on every sign in stays small. The profile only carries a
 * `logoUpdatedAt` marker, which is enough to tell whether the local cache is stale.
 */
object GarageLogoStore {

    private const val TAG = "GarageLogoStore"
    private const val MAX_DIMENSION = 512
    private const val MAX_BYTES = 400 * 1024
    private const val PREFS = "garage_guru_logo"
    private const val KEY_STAMP = "cachedStamp"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _logo = MutableStateFlow<ByteArray?>(null)

    /** Raw JPEG bytes of the current garage logo, or null when none is set. */
    val logo: StateFlow<ByteArray?> = _logo.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromDisk()
    }

    private fun cacheFile(): File? {
        val ctx = appContext ?: return null
        val gid = GarageSession.garageId.ifBlank { "pending" }
        return File(ctx.filesDir, "garage_logo_$gid.jpg")
    }

    private fun cachedStamp(): Long =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getLong("$KEY_STAMP:${GarageSession.garageId}", 0L) ?: 0L

    private fun setCachedStamp(value: Long) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putLong("$KEY_STAMP:${GarageSession.garageId}", value)?.apply()
    }

    private fun loadFromDisk() {
        val file = cacheFile() ?: return
        _logo.value = if (file.exists()) runCatching { file.readBytes() }.getOrNull() else null
    }

    private fun writeToDisk(bytes: ByteArray) {
        runCatching { cacheFile()?.writeBytes(bytes) }
            .onFailure { Log.e(TAG, "Failed caching logo locally", it) }
    }

    /** Called on sign out — drops the in-memory logo so the next account starts clean. */
    fun clear() {
        _logo.value = null
    }

    // ------------------------------------------------------------------
    // Upload
    // ------------------------------------------------------------------

    /**
     * Reads [uri], shrinks it to a Firestore-safe JPEG and uploads it as a Blob.
     * Returns the encoded byte count on success.
     */
    suspend fun upload(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        if (!GarageSession.isReady) {
            return@withContext Result.failure(IllegalStateException("No active garage session"))
        }
        try {
            val bytes = encode(context, uri)
                ?: return@withContext Result.failure(IllegalStateException("Could not read that image"))

            val gid = GarageSession.garageId
            val now = System.currentTimeMillis()

            db.collection("garages").document(gid)
                .collection("assets").document("logo")
                .set(
                    hashMapOf(
                        "garageId" to gid,
                        "image" to Blob.fromBytes(bytes),
                        "mimeType" to "image/jpeg",
                        "sizeBytes" to bytes.size,
                        "updatedAtMillis" to now,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .awaitResult()

            // Lightweight marker on the profile so a cold start knows the cache is stale.
            db.collection("garages").document(gid)
                .set(mapOf("logoUpdatedAt" to now), SetOptions.merge())
                .awaitResult()

            writeToDisk(bytes)
            setCachedStamp(now)
            _logo.value = bytes
            Log.d(TAG, "Uploaded logo blob for $gid (${bytes.size} bytes)")
            Result.success(bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Logo upload failed", e)
            Result.failure(e)
        }
    }

    /** Decode -> orient -> downscale -> compress until the JPEG fits under [MAX_BYTES]. */
    private fun encode(context: Context, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION * 2 || bounds.outHeight / sample > MAX_DIMENSION * 2) {
            sample *= 2
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val upright = applyExifRotation(context, uri, decoded)
        val scaled = scaleWithin(upright, MAX_DIMENSION)

        var quality = 90
        var bytes = scaled.toJpeg(quality)
        while (bytes.size > MAX_BYTES && quality > 40) {
            quality -= 15
            bytes = scaled.toJpeg(quality)
        }

        if (scaled != upright) scaled.recycle()
        if (upright != decoded) upright.recycle()
        decoded.recycle()

        return if (bytes.size > MAX_BYTES) null else bytes
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    private fun scaleWithin(source: Bitmap, max: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= max) return source
        val ratio = max.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    /** Gallery photos carry their rotation in EXIF; without this a logo can land sideways. */
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            Log.w(TAG, "Could not read EXIF orientation", e)
            0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // ------------------------------------------------------------------
    // Download
    // ------------------------------------------------------------------

    /**
     * Pulls the logo blob down and decodes it back to bytes for display.
     *
     * [remoteStamp] is the `logoUpdatedAt` marker from the garage profile; when it matches what
     * is already cached the download is skipped entirely.
     */
    suspend fun refreshFromCloud(remoteStamp: Long? = null) = withContext(Dispatchers.IO) {
        if (!GarageSession.isReady) return@withContext

        loadFromDisk()

        if (remoteStamp != null && remoteStamp == cachedStamp() && _logo.value != null) {
            Log.d(TAG, "Logo cache is current, skipping download")
            return@withContext
        }
        if (remoteStamp == 0L) {
            // Profile says this garage has no logo.
            _logo.value = null
            return@withContext
        }

        try {
            val snap = db.collection("garages").document(GarageSession.garageId)
                .collection("assets").document("logo")
                .get()
                .awaitResult()

            val blob = snap.getBlob("image")
            if (blob == null) {
                _logo.value = null
                return@withContext
            }
            val bytes = blob.toBytes()
            writeToDisk(bytes)
            setCachedStamp(snap.getLong("updatedAtMillis") ?: System.currentTimeMillis())
            _logo.value = bytes
            Log.d(TAG, "Downloaded logo blob (${bytes.size} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Logo download failed", e)
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }
}
