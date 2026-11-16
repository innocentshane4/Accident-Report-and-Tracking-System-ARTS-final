const admin = require("firebase-admin");

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
 * Fully removes an ARTS user — both their Firestore profile AND their
 * actual Firebase Authentication account.
 *
 * Why this exists: the admin dashboard's "Remove user" button previously
 * only deleted the Firestore users/{uid} document (Store.remove in
 * users.js). It never touched the person's real sign-in account, because
 * the client-side Firebase SDK can only ever delete the CURRENTLY signed-in
 * user — never someone else's account. A "removed" user could therefore
 * still sign back in, at which point the app would silently recreate a
 * blank profile for them.
 *
 * Deleting someone else's Authentication account requires the Firebase
 * Admin SDK, which only a trusted server can hold — exactly like sending a
 * push notification. This endpoint follows the same free, no-Blaze-plan
 * pattern as send-push.js: it runs on Vercel's free tier, authenticates the
 * caller with a Firebase ID token, and only proceeds if that caller is a
 * registered ARTS administrator.
 *
 * The Firestore profile is still deleted by the website's own code
 * (Store.remove) exactly as before; this endpoint only needs to additionally
 * delete the Authentication account to finish the job.
 */
module.exports = async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Use POST" });
    return;
  }

  const { idToken, targetUid } = req.body || {};
  if (!idToken || !targetUid) {
    res.status(400).json({ error: "Missing idToken or targetUid" });
    return;
  }

  let decoded;
  try {
    decoded = await admin.auth().verifyIdToken(idToken);
  } catch (e) {
    res.status(401).json({ error: "Invalid or expired sign-in token" });
    return;
  }

  // Only a registered admin may delete someone else's account. A user is
  // never allowed to delete another user's login through this endpoint,
  // and self-deletion should go through the client SDK directly, not here.
  const adminDoc = await admin.firestore().collection("admins").doc(decoded.uid).get();
  if (!adminDoc.exists) {
    res.status(403).json({ error: "Only an administrator can remove another user's account" });
    return;
  }

  try {
    await admin.auth().deleteUser(targetUid);
    res.status(200).json({ deleted: true });
  } catch (err) {
    // "user-not-found" just means the Authentication account was already
    // gone (e.g. deleted previously, or never existed for a demo/seed user)
    // — that is not a failure from the caller's point of view.
    if (err.code === "auth/user-not-found") {
      res.status(200).json({ deleted: true, note: "No matching auth account existed." });
    } else {
      res.status(200).json({ deleted: false, reason: err.message });
    }
  }
};
