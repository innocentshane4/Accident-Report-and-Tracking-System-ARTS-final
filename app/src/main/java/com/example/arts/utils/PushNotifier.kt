package com.example.arts.utils

import android.util.Log
import com.example.arts.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ARTS-Push"

/**
 * Asks the free push-notification server (see /push-server at the project
 * root) to send an FCM push to a user.
 *
 * This is the free alternative to a Firestore-triggered Cloud Function,
 * which requires Firebase's paid Blaze plan to deploy under any
 * circumstances. Instead, the app itself calls this right after it writes
 * something worth notifying about — see IncidentRepository.submitReport().
 *
 * If ARTS_PUSH_SERVER_URL is not configured in local.properties, this
 * silently does nothing. Push notifications are a nice-to-have on top of
 * the app, never a requirement — every screen already works without them
 * via Firestore's real-time listeners while the app is open.
 */
object PushNotifier {

    suspend fun notify(targetUid: String, title: String, body: String, incidentId: String? = null) {
        withContext(Dispatchers.IO) {
            val serverUrl = try { BuildConfig.ARTS_PUSH_SERVER_URL } catch (e: Throwable) { "" }
            if (serverUrl.isNullOrBlank()) return@withContext

            val idToken = try {
                FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
            } catch (e: Exception) {
                Log.w(TAG, "Could not get ID token for push call: ${e.message}")
                null
            } ?: return@withContext

            try {
                val payload = mapOf(
                    "idToken" to idToken,
                    "targetUid" to targetUid,
                    "title" to title,
                    "body" to body,
                    "incidentId" to incidentId
                )
                val connection = (URL(serverUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                connection.outputStream.use { it.write(Gson().toJson(payload).toByteArray()) }
                // Reading the response code is enough to complete the request;
                // a failed push is never fatal to the feature that triggered it.
                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Push notify call failed (non-fatal): ${e.message}")
            }
        }
    }
}
