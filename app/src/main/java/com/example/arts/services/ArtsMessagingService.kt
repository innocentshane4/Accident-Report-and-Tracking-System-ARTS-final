package com.example.arts.services

import android.util.Log
import com.example.arts.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

private const val TAG = "ARTS-FCM"

/**
 * Receives push notifications sent by the ARTS Cloud Function (see
 * /functions/index.js) and keeps this device's token current in Firestore.
 *
 * Two things happen here:
 *
 * 1. onNewToken() fires whenever Firebase issues a fresh token for this
 *    install (first launch, app data cleared, token rotation). It is saved
 *    to users/{uid}.fcmToken so the Cloud Function knows where to send
 *    this user's pushes. If no one is signed in yet, the token is simply
 *    not saved — AuthViewModel.finalizeLogin() saves it again right after
 *    the next successful sign-in.
 *
 * 2. onMessageReceived() fires when a push arrives while the app is in the
 *    foreground (Android does not call this for background/killed-state
 *    delivery of notification-only payloads — those are handled by the
 *    system tray directly using the channel declared in AndroidManifest.xml).
 */
class ArtsMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .update("fcmToken", token)
            .addOnFailureListener { e -> Log.w(TAG, "Could not save refreshed FCM token: ${e.message}") }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "ARTS"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val incidentId = message.data["incidentId"]
        NotificationHelper.show(applicationContext, title, body, incidentId)
    }
}
