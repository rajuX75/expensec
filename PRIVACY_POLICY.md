# Privacy Policy for Expense Tracker

**Last Updated: August 19, 2026**

Welcome to **Expense Tracker** ("we", "our", or "the App"). We are committed to protecting your personal and financial privacy. This Privacy Policy explains how your information is handled, stored, and protected when you use our Android application.

---

## 1. Summary — Local-First by Design

Expense Tracker is engineered with a **local-first architecture**:
- **Your data belongs to you**: All financial records, transactions, budgets, bills, and debt records (Dhaar) are stored primarily on your local device in a secure SQLite/Room database.
- **Zero Third-Party Tracking**: We do NOT sell, rent, monetize, or track your personal financial records with third-party advertisers.
- **No Unsolicited Data Harvesting**: The app only communicates with cloud services (Firebase) when you explicitly sign in to synchronize your data across devices.

---

## 2. Information We Collect & How We Use It

### A. Local Data (Stored Exclusively on Your Device)
- **Financial Records**: Transactions (income, expense, transfer), accounts, budgets, bills, and due dates.
- **Dhaar (Debts & Loans)**: Contact names, phone numbers, loan amounts, transaction notes, and payment receipts.
- **Profile & Contact Photos**: Any custom profile photo or contact avatar you upload is saved securely into your app's private internal storage (`context.filesDir`) and never transmitted to external third parties.
- **Preferences & PIN Code**: App currency, theme settings, date formats, and your hashed 4-digit security PIN.

### B. Cloud Data (Optional Firebase Synchronization)
If you choose to sign in with your Google Account:
- **Authentication**: We use Firebase Authentication via Google Sign-In to verify your identity. We receive your display name, email address, and profile photo URL.
- **Cloud Database (Firestore)**: Your transactions, accounts, budgets, bills, contacts, and debt records are synced to your private, user-isolated collection in Google Firebase Firestore.
- **App Configuration (Realtime Database)**: The app reads public app metadata (latest version, changelog, remote configuration) from Firebase Realtime Database. No personal user data is ever written to or read from the public app configuration node.

---

## 3. Device Permissions

The App requests minimal device permissions strictly needed for functionality:
- **Internet (`android.permission.INTERNET`)**: Used exclusively for optional Firebase Firestore synchronization, checking app updates from GitHub/Firebase, and downloading official update packages.
- **Read Media Images (`READ_MEDIA_IMAGES` / Photo Picker)**: Used solely when you select a profile picture, contact photo, or receipt attachment.
- **Install Packages (`REQUEST_INSTALL_PACKAGES`)**: Used to trigger the Android Package Installer when you explicitly download an in-app update APK.
- **No Contact Harvesting**: The app uses Android's system contact picker (`ACTION_PICK`) which provides transient, user-selected contact details without requiring full `READ_CONTACTS` background permissions.

---

## 4. Data Security & Encryption

- **On-Device Protection**: Your database is stored within your device's sandboxed application storage, inaccessible to other applications without root access.
- **PIN Lock**: You can enable a 4-digit PIN lock within Settings. PINs are salted and hashed on-device before verification.
- **Transit Encryption**: All network communications with Firebase and GitHub use industry-standard HTTPS / TLS 1.3 encryption.
- **Cloud Security Rules**: Firebase Firestore security rules enforce strict user-level isolation (`request.auth.uid == resource.data.userId`), ensuring no other user can access your financial data.

---

## 5. Data Retention & Deletion

- **Local Reset**: You can erase all local data at any time from **Settings > Data Management > Erase All Local Data**.
- **Account Disconnection**: Signing out disconnects your Google Account and halts real-time cloud synchronization.
- **Cloud Data Deletion**: If you wish to delete your synced cloud records permanently, simply clear your records within the app or contact our support team.

---

## 6. Children's Privacy

Expense Tracker does not knowingly collect or solicit personal information from children under the age of 13.

---

## 7. Changes to This Privacy Policy

We may update this Privacy Policy periodically to reflect new features or regulatory requirements. Any updates will be posted in this repository and accessible directly within the App's **Settings > Legal & Documentation** section.

---

## 8. Contact Us

If you have questions, concerns, or requests regarding this Privacy Policy or your data, please reach out to us:
- **Email**: [support@expensex.app](mailto:support@expensex.app)
- **GitHub**: [https://github.com/rajuX75/expensec](https://github.com/rajuX75/expensec)
