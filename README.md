# 💳 Expense Tracker (ExpenseX)

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android%2014%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.2-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Firebase](https://img.shields.io/badge/Cloud-Firestore%20%2B%20RTDB-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

**A private, local-first personal financial manager with real-time Firebase cloud synchronization, Dhaar (debt & loan) tracking, Shop Baki tracking, custom avatars, and in-app smart updates.**

[Download Latest APK](https://github.com/rajuX75/expensec/releases/latest/download/expense-tracker-release.apk) • [Features](#-key-features) • [Architecture](#-architecture) • [Firebase Setup](#-firebase-configuration) • [Building & CI/CD](#-building--cicd) • [Documentation](#-legal--documentation)

</div>

---

## 🌟 Key Features

### 📊 1. Personal Financial Management
- **Transactions & Wallets**: Log expenses, income, and account transfers with customizable categories, merchant tags, and notes.
- **Budgeting & Spending Goals**: Set monthly budgets per category with live visual progress bars and alert notifications.
- **Bills & Subscriptions**: Track recurring bills, due dates, and auto-mark paid bills with transaction auto-logging.
- **Interactive Analytics**: Monthly and annual trend line charts, spending category donut charts, and cash flow breakdowns.

### 🤝 2. Dhaar (Debts & Loans) Tracker
- **Who Owes You vs. Who You Owe**: Clear dashboard summarizing total money lent out vs. borrowed.
- **Contact Photo Avatars**: Upload custom contact avatars or import photos directly from phone contacts.
- **Receipts & Attachments**: Attach photos of payment receipts or transaction notes saved securely on device.
- **One-Tap Settle Up**: Record partial or full repayments with automated ledger balancing.
- **Safe Phone Contact Picker**: Permission-free, crash-safe Android contact picker integration.

### 🛒 3. Shop Baki (Shop Credit Tracker)
- **Track Shop Debts**: Manage running credit balances (Baki) across different local shops, stores, or vendors.
- **Rich Shop Profiles**: Add custom cover images, profile pictures, verified badges, emails, categories, and business IDs.
- **Product Inventory**: Define common products with default units and prices for quick purchase logging.
- **Ledger Entries**: Log individual purchases and payments to maintain a precise, chronological history of what you bought and what you paid.

### ☁️ 4. Real-Time Firebase Cloud Sync
- **Google Sign-In**: Authenticate seamlessly via Android Credential Manager.
- **Automatic Profile Population**: Display name, Google email, and profile avatar are automatically configured on login.
- **Firestore Cloud Sync**: Encrypted, user-isolated bidirectional cloud synchronization across all personal devices.

### 🚀 5. Smart Versioning & In-App Updates
- **Firebase Realtime Database Driven**: Remote versioning and configuration powered by Firebase RTDB (`/app_version`, `/app_config`, `/changelog`).
- **Flexible & Mandatory Updates**: Supports critical forced updates as well as skippable flexible releases.
- **Direct Auto-Download & Install**: Downloads release APKs with real-time progress indicators and automatically triggers Android package installation.
- **What's New Changelog**: Interactive release notes viewer with category color badges (New, Improved, Fix).

### 🔒 6. Privacy & Security
- **Local-First Sandboxed Storage**: Zero data sold to third-party ad networks.
- **PIN Lock Protection**: 4-digit salted-hash PIN lock to secure app access.
- **Persistent Media**: Images are saved in private internal storage (`filesDir`), preventing disappearance across app restarts.
- **Data Export & Import**: Backup and restore your complete records in CSV/JSON format.

---

## 🏗️ Architecture & Tech Stack

```
com.example/
├── data/
│   ├── cloud/             # FirestoreSyncManager, ShopBakiSyncer, GoogleAuthManager
│   ├── local/             # Room Database, AppDatabase, DAOs, Entities
│   ├── model/             # Domain & UI Models, ShopBakiModels, UpdateModels
│   └── repository/        # ExpenseRepository, DhaarRepository, UserPrefs
├── ui/
│   ├── components/        # UpdateDialog, ChangelogDialog, Charts, ImageStorage
│   ├── screens/           # Dashboard, Transactions, Analytics, Dhaar, ShopBaki
│   ├── theme/             # Material 3 Color Schemes, Typography, Shape Definitions
│   └── viewmodel/         # ExpenseViewModel (Single Source of Truth) + Delegates
└── MainActivity.kt        # Jetpack Compose Navigation Host
```

- **UI Framework**: Jetpack Compose (100% Kotlin) + Material 3
- **Local Storage**: Room (SQLite) + Jetpack DataStore Preferences
- **Cloud Backend**: Google Firebase (Authentication, Firestore, Realtime Database)
- **Image Loading**: Coil 2.7 with persistent local disk storage
- **Networking**: OkHttp 4 + Retrofit 2 + Moshi
- **Architecture**: MVVM with Kotlin Coroutines and StateFlow

---

## ⚙️ Firebase Configuration

### 1. Realtime Database Data Setup
Import [`firebase_realtime_db_template.json`](./firebase_realtime_db_template.json) into **Firebase Console > Realtime Database > Data > Import JSON**:

```json
{
  "app_version": {
    "versionCode": 2,
    "versionName": "1.1.0",
    "minSupportedVersionCode": 1,
    "releaseTitle": "v1.1.0 — Shop Baki Feature",
    "releaseNotes": "• New Shop Baki Tracker\n• Custom Cover and Profile Images for Shops\n• CI/CD Automated Releases",
    "downloadUrl": "https://github.com/rajuX75/expensec/releases/latest/download/expense-tracker-release.apk",
    "releaseDate": "2026-08-22",
    "apkSizeMb": 20.1,
    "isMandatory": false
  }
}
```

### 2. Realtime Database Security Rules
Paste the contents of [`firebase_database_rules.json`](./firebase_database_rules.json) into **Firebase Console > Realtime Database > Rules**:

```json
{
  "rules": {
    "app_version": {
      ".read": true,
      ".write": "auth != null && auth.token.admin === true"
    },
    "app_config": {
      ".read": true,
      ".write": "auth != null && auth.token.admin === true"
    },
    "changelog": {
      ".read": true,
      ".write": "auth != null && auth.token.admin === true"
    },
    "$other": {
      ".read": false,
      ".write": false
    }
  }
}
```

---

## 🛠️ Building & CI/CD

### Prerequisites
- [Android Studio Ladybug (2024.2+) or newer](https://developer.android.com/studio)
- JDK 17 or JDK 21
- Android SDK 35/36 installed

### Local Build
```bash
# Clone the repository
git clone https://github.com/rajuX75/expensec.git
cd expensec

# Build signed release APK
./gradlew assembleRelease
```
The output APK will be located at `app/build/outputs/apk/release/app-release.apk`.

### Automated GitHub Actions Releases
This repository includes a fully automated CI/CD pipeline using **GitHub Actions**. On every push to the `main` branch, the pipeline will:
1. Compile and build the release APK.
2. Draft a new release on GitHub using the version tag from `build.gradle.kts`.
3. Upload the APK to the release.

To enable production signing and automatic in-app updates in the automated pipeline, add the following Repository Secrets to GitHub (**Settings > Secrets and variables > Actions > New repository secret**):
- `KEYSTORE_BASE64`: The base64-encoded string of your release `.jks` file.
- `STORE_PASSWORD`: Keystore password.
- `KEY_ALIAS`: Key alias.
- `KEY_PASSWORD`: Key password.
- `FIREBASE_RTDB_URL`: The URL of your Firebase Realtime Database (e.g. `https://expenstracke-default-rtdb.firebaseio.com/`).
- `FIREBASE_RTDB_SECRET`: Your Firebase Database Secret (from Project Settings > Service Accounts > Database Secrets).

*(If these secrets are omitted, the action will safely fall back to debug signing).*

---

## 📚 Legal & Documentation

- 🔒 **[Privacy Policy](./PRIVACY_POLICY.md)**: Full details on data retention, device permissions, and local storage.
- 📜 **[Terms of Service](./TERMS.md)**: Usage guidelines and financial advisory disclaimers.
- 🛡️ **[Security Policy](./SECURITY.md)**: Security standards, encryption, and vulnerability disclosure.

---

## 📄 License

This project is licensed under the [MIT License](./LICENSE).
