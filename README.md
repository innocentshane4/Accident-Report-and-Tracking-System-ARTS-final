# ARTS (Accident Report and Tracking System)

A comprehensive Android-based emergency incident reporting and tracking application built with Kotlin, Firebase, and Android Jetpack. The project is designed to help users report accidents quickly, monitor nearby safety conditions, receive alerts, and access support resources while integrating AI-powered assistance and push notifications.

This repository contains:
- The main Android application in `app/`
- A lightweight Node/Vercel push notification server in `push-server/`
- Firebase configuration and security rules for backend data access
- Gradle build configuration for Android/Kotlin development

## Project Summary

ARTS is a mobile-first emergency incident response platform intended for rapid accident reporting and situational awareness. It allows users to:
- Create, view, and track accident reports
- Submit location-aware incident data
- Access AI guidance and assistant support
- View weather and safety-related alerts
- Receive push notifications for critical updates
- Authenticate securely using Firebase, Google sign-in, biometrics, or TOTP
- Engage with emergency resources such as helplines and nearby units

The app is designed to function as an end-to-end system for reporting and responding to road incidents, safety risks, and urgent community alerts.

## Key Features

### 1. Accident Reporting
- Report accidents with structured forms
- Capture incident details, severity, and context
- Save and review previous reports
- Monitor incident feed and recent activity

### 2. Incident Tracking & Feed
- Central dashboard that lists recent incidents
- Detail view for each report
- Support for tracking report lifecycle and updates
- User-facing feed cards to surface relevant information quickly

### 3. AI Assistant
- Gemini-based AI integration for guidance and assistance
- Helps with triage support, incident context, and user help
- Configurable via `ARTS_AI_KEY` and `ARTS_AI_MODEL` in `local.properties`
- Supports intelligent interaction within the app UI

### 4. Weather & Safety Intelligence
- Open-Meteo API integration
- Weather alert and safety context display
- Weather-driven risk awareness for incident conditions

### 5. Notifications & Push Messaging
- Firebase Messaging support for alerts
- Free Vercel-based push server to avoid paid Firebase Cloud Functions
- Notification channel and icon support for Android

### 6. User Security & Authentication
- Firebase Authentication integration
- Google Sign-In support
- Biometric authentication support
- TOTP-based helper utilities for secure verification
- Session and validation helpers for safer user workflows

### 7. Maps, Location, and Nearby Resources
- Fine and coarse location permissions
- Nearby units and help resources
- Geocoding and location utilities
- MapLibre Android SDK integration

### 8. Medical and Emergency Support
- Medical information section
- Helpline access
- Safety score and triage support

### 9. Community & Alerts
- Community-focused UI sections
- Notifications and emergency alerts
- User guidance and safety-related communications

## Technology Stack

### Android / Mobile
- Kotlin
- Android SDK 34
- Jetpack Navigation
- ViewModel and LiveData
- Material Components
- AndroidX UI and lifecycle libraries
- RecyclerView + CardView

### Backend & Services
- Firebase Authentication
- Firebase Firestore
- Firebase Cloud Messaging
- Google Sign-In
- Gemini AI API integration
- Open-Meteo weather API
- Retrofit for HTTP networking
- Gson for JSON parsing

### Security & Identity Tools
- Biometrics
- Google Identity Credentials API
- TOTP library
- SharedPreferences-based local storage

### Push Notification Server
- Node.js serverless functions
- Vercel hosting
- Firebase Admin SDK for sending push notifications

### Build System
- Gradle
- Android Gradle Plugin 8.2.2
- Kotlin Android plugin 1.9.0

## Repository Structure

```text
ARTS-final/
├── .github/
│   └── workflows/
│       └── android.yml
├── .idea/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/example/arts/
│   │   │   │   ├── activities/
│   │   │   │   ├── adapters/
│   │   │   │   ├── data/
│   │   │   │   ├── fragments/
│   │   │   │   ├── network/
│   │   │   │   ├── repository/
│   │   │   │   ├── services/
│   │   │   │   ├── utils/
│   │   │   │   └── viewmodels/
│   │   │   └── res/
│   │   │       ├── drawable/
│   │   │       ├── layout/
│   │   │       ├── menu/
│   │   │       ├── navigation/
│   │   │       └── values/
│   │   └── test/java/com/example/arts/
│   ├── build.gradle
│   ├── google-services.json
│   └── proguard-rules.pro
├── gradle/
│   └── wrapper/
├── push-server/
│   ├── api/
│   ├── .vercel/
│   ├── README.md
│   ├── package.json
│   └── ...
├── .gitignore
├── build.gradle
├── firebase.json
├── firestore.rules
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle
└── README.md
```

## Main Application Components

### Activities
- `SplashActivity.kt` – startup/splash experience
- `MainActivity.kt` – main host activity for the application shell

### Fragments
The app includes multiple feature-focused fragments, including:
- `HomeFragment.kt`
- `IncidentsFragment.kt`
- `ReportAccidentFragment.kt`
- `IncidentDetailFragment.kt`
- `AlertsFragment.kt`
- `WeatherAlertFragment.kt`
- `NearbyUnitsFragment.kt`
- `CommunityFragment.kt`
- `LoginFragment.kt`
- `RegisterFragment.kt`
- `ProfileFragment.kt`
- `SettingsFragment.kt`
- `MedicalInfoFragment.kt`
- `AiAssistantFragment.kt`
- `VoiceReportFragment.kt`
- `SafetyScoreFragment.kt`
- `HelpFragment.kt`
- `HelplinesFragment.kt`

### Data Layer
- `Models.kt` – data models used across the app
- `Mappers.kt` – transformations between Firebase and app data models
- `FirestoreLiveData.kt` – Firestore observation utilities

### Network & External APIs
- `RetrofitClient.kt` – HTTP client configuration
- `OpenMeteoApi.kt` and `OpenMeteoModels.kt` – weather data access

### Repositories
- `IncidentRepository.kt`
- `AlertRepository.kt`
- `UserRepository.kt`

### Utilities
The project contains many helper modules for production functionality, such as:
- `AiAssistant.kt`
- `GeminiHelper.kt`
- `GeocodeHelper.kt`
- `LocationHelper.kt`
- `NotificationHelper.kt`
- `ImageStorageHelper.kt`
- `QrHelper.kt`
- `BiometricHelper.kt`
- `TotpHelper.kt`
- `TriageHelper.kt`
- `Validators.kt`
- `WeatherCodeMapper.kt`
- `SessionGate.kt`
- `PrefsHelper.kt`
- `PushNotifier.kt`
- `GoogleSignInHelper.kt`
- `AuthErrors.kt`

### ViewModels
- `AuthViewModel.kt`
- `IncidentViewModel.kt`
- `NotificationViewModel.kt`
- `ProfileViewModel.kt`
- `WeatherViewModel.kt`

## Firebase Configuration

This project includes Firebase configuration files and security rules for app authentication and database access.

Files included:
- `app/google-services.json` – required Google Services configuration for Android
- `firebase.json` – Firebase hosting or project config
- `firestore.rules` – access rules for Firestore data

### Firebase Setup Requirements
1. Create a Firebase project
2. Register the Android app using the package name `com.example.arts`
3. Download `google-services.json` and place it in `app/`
4. Enable Firebase Authentication and Firestore
5. Configure app permissions and Firestore rules based on your deployment needs

## AI Configuration

The app supports Gemini AI configuration through `local.properties`.

Add this to your local project properties file:

```properties
ARTS_AI_KEY=your_api_key_here
ARTS_AI_MODEL=gemini-3.1-flash-lite
```

The app reads these values during build and exposes them via `BuildConfig`.

## Push Notification Setup

The repository includes a free Vercel push server in `push-server/` to avoid requiring Firebase Blaze billing for Cloud Functions.

Add to `local.properties`:

```properties
ARTS_PUSH_SERVER_URL=https://your-vercel-project.vercel.app/api/send-push
```

If left blank, the app simply skips sending push notifications gracefully.

For full push server docs, see:
- `push-server/README.md`

## Local Development Setup

### Prerequisites
- Android Studio (latest stable recommended)
- JDK 17
- Android SDK 34
- Git
- Firebase project access
- Optional: Node.js/npm for the push server

### 1. Clone the Repository

```bash
git clone https://github.com/innocentshane4/Accident-Report-and-Tracking-System-ARTS-final.git
cd Accident-Report-and-Tracking-System-ARTS-final
```

### 2. Configure local.properties

At the root of the project create a `local.properties` file:

```properties
sdk.dir=/path/to/Android/Sdk
ARTS_AI_KEY=your_api_key_here
ARTS_AI_MODEL=gemini-3.1-flash-lite
ARTS_PUSH_SERVER_URL=https://your-vercel-project.vercel.app/api/send-push
```

Note: `ARTS_PUSH_SERVER_URL` is optional; if you do not want push notifications, leave it blank.

### 3. Add Firebase Configuration

Place your `google-services.json` file inside:

```text
app/google-services.json
```

### 4. Sync the Project

Open the project in Android Studio and let Gradle sync.

### 5. Run the App

Use Android Studio's run configuration or execute:

```bash
./gradlew assembleDebug
```

or:

```bash
./gradlew installDebug
```

## Build & Test Commands

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

### Run Unit Tests
```bash
./gradlew test
```

### Run Lint / Verification
```bash
./gradlew check
```

## App Permissions

The app requests the following Android permissions in the manifest:
- INTERNET
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- USE_BIOMETRIC
- RECORD_AUDIO
- CAMERA
- POST_NOTIFICATIONS
- READ_EXTERNAL_STORAGE (for devices up to API 32)
- READ_MEDIA_IMAGES

These permissions support location-aware incident reporting, voice input, camera access, and notification handling.

## Design and UX Notes

The app uses a modern mobile app layout with:
- Material design components
- Fragments for navigation
- Reusable card layouts for incidents, status, help, and alerts
- Custom XML drawables for badges, chips, and action states
- Navigation graph setup using Android Navigation Component

## App Behavior Highlights

The project is designed to provide an emergency and safety-management experience with the following flow:
1. User opens the app
2. Authenticates using Firebase/Google or local security tools
3. Navigates to home feed and reports
4. Reports an accident with details and location
5. Viewes safety or weather warnings
6. Accesses AI help or emergency guidance
7. Receives alert notifications based on incident updates

## Security Notes

- Firebase rules should be reviewed and hardened before production use
- Never commit real API keys or service account credentials
- Keep `local.properties` local and untracked
- Ensure `google-services.json` is not exposed publicly
- Use Firebase security rules to prevent unauthorized access to user data

## Production Considerations

Before deploying or distributing the app publicly, consider:
- replacing placeholder package identifiers if necessary
- validating Firestore access rules
- securing API keys and service account credentials
- testing the AI assistant with production-like prompts
- verifying push notifications through test users
- reviewing all permissions and privacy requirements
- configuring production branding and app release metadata

## Testing

The project includes unit tests for core logic, including:
- `GeminiHelperTest.kt`
- `IncidentModelTest.kt`
- `TimeFormatTest.kt`
- `TotpHelperTest.kt`
- `TriageHelperTest.kt`
- `ValidatorsTest.kt`

These tests cover logic validation, formatting helpers, incident models, TOTP utilities, and support triage functions.

## Notes on the Push Server

The repository intentionally avoids requiring a Firebase paid plan for Cloud Functions by using a free Vercel serverless endpoint. The `push-server` directory contains serverless APIs that send Firebase Cloud Messaging notifications without requiring Blaze.

This is one of the most practical architectural choices in the project because it allows a low-cost deployment model while keeping app functionality intact.

## License

This repository does not currently declare a license file in the root directory. If you intend to distribute or publish the project, it is recommended to add an appropriate license such as MIT or Apache 2.0.

## Contribution

Contributions are welcome if you're improving:
- the accident reporting workflow
- emergency safety intelligence
- AI guidance quality
- frontend UX and navigation
- Firebase rules and backend security
- push notification reliability

## Final Notes

ARTS is a feature-rich Android emergency response application with a strong focus on usability, safety, and rapid situational awareness. It combines modern Android development patterns with Firebase, AI assistance, weather data, and notification infrastructure to create a practical incident-reporting platform.

The project is a strong full-stack mobile solution that can be extended for production deployment, additional emergency services, analytics, improved map integration, and more advanced reporting workflows.

Team Manager: Ayanda Maseko
Developer: Njabulo Fushane 
System Tester : Ntando Nxumalo
Team: The Round Table
