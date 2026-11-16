const admin = require("firebase-admin");

// Reused across invocations on the same warm instance — initialising once
// keeps this fast and avoids hitting Firebase's SDK-init limits.
if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert({
      projectId: process.env.FIREBASE_PROJECT_ID,
      clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
      privateKey: (process.env.FIREBASE_PRIVATE_KEY || "").replace(/\\n/g, "\n"),
    }),
  });
}

/**
 * ARTS push notifications — free alternative to a paid Cloud Functions plan.
 *
 * Firebase Cloud Functions require the Blaze (pay-as-you-go) billing plan to
 * deploy at all, even if usage stays within the free quota. This function
 * does the exact same job — send an FCM push using the Firebase Admin SDK —
 * but runs on Vercel's free Hobby tier instead, which needs no card on file.
 *
 * The trade-off: a Cloud Function watches Firestore and fires itself the
 * moment a document changes, anywhere, by anyone. This function only fires
 * when the Android app calls it directly, right after the app itself writes
 * something worth notifying about (a submitted report, an SOS). That covers
 * every real notification the app currently sends. The one thing it does
 * NOT cover is a future admin website changing an incident's status directly
 * in Firestore without going through this endpoint — if that is added later,
 * the admin website should call this same endpoint after making its update.
 *
 * Security: every request must include a valid Firebase ID token for a
 * signed-in ARTS user. A user can always notify themselves. Notifying a
 * DIFFERENT user requires the caller to be a registered admin
 * (a document at admins/{uid} in Firestore) — the same rule the app's own
 * Firestore Security Rules already enforce.
 */
module.exports = async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Use POST" });
    return;
  }

  const { idToken, targetUid, title, body, incidentId } = req.body || {};
  if (!idToken || !targetUid || !title || !body) {
    res.status(400).json({ error: "Missing idToken, targetUid, title, or body" });
    return;
  }

  let decoded;
  try {
    decoded = await admin.auth().verifyIdToken(idToken);
  } catch (e) {
    res.status(401).json({ error: "Invalid or expired sign-in token" });
    return;
  }

  const callerUid = decoded.uid;

  if (callerUid !== targetUid) {
    const adminDoc = await admin.firestore().collection("admins").doc(callerUid).get();
    if (!adminDoc.exists) {
      res.status(403).json({ error: "Not allowed to notify another user" });
      return;
    }
  }

  const userDoc = await admin.firestore().collection("users").doc(targetUid).get();
  const token = userDoc.data()?.fcmToken;
  if (!token) {
    // Not an error — the target device just doesn't have a push token on
    // file yet (e.g. never granted notification permission).
    res.status(200).json({ sent: false, reason: "No FCM token on file for that user" });
    return;
  }

  try {
    await admin.messaging().send({
      token,
      notification: { title, body },
      data: incidentId ? { type: "incident", incidentId } : {},
      android: {
        priority: "high",
        notification: { channelId: "arts_alerts" },
      },
    });
    res.status(200).json({ sent: true });
  } catch (err) {
    res.status(200).json({ sent: false, reason: err.message });
  }
};
