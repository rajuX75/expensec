# 💳 ExpenseX - Personal Finance Tracker

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android%2014%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

A modern, privacy-focused personal finance manager for Android with cloud sync, debt tracking, and smart updates.

[Download Latest](https://github.com/rajuX75/expensec/releases/latest) • [Features](#-features) • [Tech Stack](#-tech-stack) • [Setup](#-setup)

</div>

---

## ✨ Features

### 📊 Financial Management
- **Expense & Income Tracking**: Log transactions with categories, tags, and notes
- **Budget Management**: Set monthly budgets with visual progress tracking
- **Bills & Subscriptions**: Track recurring payments and due dates
- **Analytics Dashboard**: Interactive charts showing spending trends and patterns

### 🤝 Dhaar (Debt Tracking)
- Track money you've lent or borrowed from contacts
- Custom contact avatars and photo attachments
- One-tap settlement with automated ledger updates
- Clear dashboard showing outstanding balances

### 🛒 Shop Baki (Shop Credit)
- Manage running credit balances with local shops and vendors
- Product inventory with default prices for quick logging
- Detailed transaction history per shop

### ☁️ Cloud Sync
- Google Sign-In authentication
- Real-time Firebase Firestore synchronization
- Encrypted, user-isolated data storage
- Sync across multiple devices

### 🚀 Smart Updates
- In-app update notifications with changelog
- Direct APK download and installation
- Flexible and mandatory update modes

### 🔒 Privacy & Security
- **Local-first**: All data stored locally on your device
- **No ads or tracking**: Zero third-party data sharing
- **PIN protection**: Secure app access with 4-digit PIN
- **Data export**: Backup to CSV/JSON format

---

## 🏗️ Tech Stack

- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM with Kotlin Coroutines & StateFlow
- **Local Storage**: Room Database + DataStore
- **Cloud**: Firebase (Auth, Firestore, Realtime Database)
- **Image Loading**: Coil
- **Networking**: OkHttp + Retrofit + Moshi
- **Dependency Injection**: Manual injection (lightweight)

---

## 🚀 Setup

### Prerequisites
- Android Studio Ladybug (2024.2+)
- JDK 17 or 21
- Android SDK 35+

### Quick Start
```bash
git clone https://github.com/rajuX75/expensec.git
cd expensec
./gradlew assembleDebug
```

### Firebase Setup

1. Create a Firebase project at [firebase.google.com](https://firebase.google.com)
2. Add your Android app with package name `com.rjx.expensex`
3. Download `google-services.json` to `app/` directory

#### Realtime Database Setup
Import the template from [`firebase_realtime_db_template.json`](./firebase_realtime_db_template.json) for app version control:

```json
{
  "app_version": {
    "versionCode": 1,
    "versionName": "1.0.0",
    "releaseTitle": "Initial Release",
    "downloadUrl": "https://github.com/rajuX75/expensec/releases/latest/download/expense-tracker-release.apk",
    "isMandatory": false
  }
}
```

Set database rules from [`firebase_database_rules.json`](./firebase_database_rules.json) to allow public read for version info.

---

## 🔧 Building for Release

### Local Build
```bash
# Create a keystore (first time only)
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias release

# Build release APK
./gradlew assembleRelease
```

### GitHub Actions CI/CD
This project includes automated CI/CD. Add these secrets to your repository:
- `KEYSTORE_BASE64`: Base64-encoded keystore file
- `STORE_PASSWORD`: Keystore password
- `KEY_ALIAS`: Key alias
- `KEY_PASSWORD`: Key password
- `FIREBASE_RTDB_URL`: Firebase Realtime Database URL
- `FIREBASE_RTDB_SECRET`: Firebase database secret

Pushing to `main` automatically builds and releases a signed APK.

---

## 📖 Documentation

- [Privacy Policy](./PRIVACY_POLICY.md)
- [Terms of Service](./TERMS.md)
- [Security Policy](./SECURITY.md)
- [Changelog](./CHANGELOG.md)

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](./LICENSE) file for details.

---

## 🙏 Acknowledgments

Built with modern Android development best practices and powered by Firebase.
