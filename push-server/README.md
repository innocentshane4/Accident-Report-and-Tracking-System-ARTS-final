# ARTS Push Server (free, no Blaze plan needed)

A single serverless function, hosted free on Vercel, that sends ARTS push
notifications. This replaces Firebase Cloud Functions, which require a paid
Blaze billing plan to deploy at all — this needs no card on file, ever.

## One-time setup

### 1. Get a Firebase service account key

Firebase Console → the gear icon → **Project settings** → **Service accounts**
tab → **Generate new private key**. This downloads a JSON file. Open it — you
need three values from it:

- `project_id`
- `client_email`
- `private_key`

Keep this file private. Never commit it, never put it in the Android app.

### 2. Create a free Vercel account and deploy

1. Go to vercel.com and sign up free (GitHub login is easiest) — no card required.
2. Install the Vercel CLI: `npm install -g vercel`
3. From inside this `push-server` folder, run: `vercel`
4. Follow the prompts (link or create a new project). When it asks about
   build settings, the defaults are fine — this is a zero-config Node function.

### 3. Add the three secrets to Vercel

In the Vercel dashboard for this project → **Settings** → **Environment
Variables**, add:

| Name | Value |
|---|---|
| `FIREBASE_PROJECT_ID` | the `project_id` from the JSON file |
| `FIREBASE_CLIENT_EMAIL` | the `client_email` from the JSON file |
| `FIREBASE_PRIVATE_KEY` | the `private_key` from the JSON file, including the `-----BEGIN PRIVATE KEY-----` and `-----END PRIVATE KEY-----` lines |

After adding them, redeploy: `vercel --prod`

### 4. Get your function's URL

Vercel gives you a URL like `https://arts-push-server.vercel.app`. Your
actual endpoint is that URL plus `/api/send-push`, for example:

```
https://arts-push-server.vercel.app/api/send-push
```

### 5. Put that URL in the Android app

In `local.properties` (same file as your other keys), add:

```
ARTS_PUSH_SERVER_URL=https://arts-push-server.vercel.app/api/send-push
```

Sync Gradle. Push notifications are now live — submit a test report on the
app and a notification should arrive.

## What this costs

Nothing. Vercel's free Hobby tier covers far more traffic than this app will
ever produce. Firebase Authentication, Firestore reads, and Cloud Messaging
are free on Firebase's Spark plan at this scale — only Cloud Functions
themselves require Blaze, and this setup never uses them.
