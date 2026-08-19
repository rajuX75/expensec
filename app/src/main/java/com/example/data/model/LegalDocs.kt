package com.example.data.model

enum class LegalDocType(val title: String, val iconName: String) {
    PRIVACY_POLICY("Privacy Policy", "Security"),
    TERMS_OF_SERVICE("Terms of Service", "Description"),
    SECURITY("Security & Privacy", "Lock"),
    ABOUT("About Expense Tracker", "Info")
}

object LegalDocs {

    fun getDocumentContent(type: LegalDocType): String {
        return when (type) {
            LegalDocType.PRIVACY_POLICY -> """
# Privacy Policy for Expense Tracker
**Last Updated: August 19, 2026**

## 1. Summary — Local-First by Design
Expense Tracker is built with your privacy at the forefront:
• All transactions, budgets, bills, and debt records are stored locally on your device in a secure SQLite Room database.
• We do NOT sell, rent, monetize, or track your personal financial records with third-party advertisers.
• Zero third-party analytics or invasive tracking frameworks.

## 2. Information We Collect
• Local Data: Transactions, accounts, budgets, bills, and Dhaar entries.
• Photos: Custom profile and contact pictures are stored exclusively inside your app's private files directory.
• Cloud Sync (Optional): If you sign in with Google, your financial records are synced to your private, user-isolated collection in Google Firebase Firestore.
• App Config: The app reads public metadata (version, changelog) from Firebase Realtime Database.

## 3. Device Permissions
• Internet: Used strictly for optional Firebase Firestore sync and app updates.
• Storage / Photo Picker: Used solely when you select a profile or contact photo.
• Install Packages: Used to launch the Android Package Installer when updating the app.
• No Background Contact Harvesting: We use Android's system contact picker without requiring full READ_CONTACTS permissions.

## 4. Data Security & Encryption
• Local Sandboxing: App data is isolated within Android's private app sandbox.
• PIN Protection: 4-digit PIN locks are salted and hashed on-device.
• Transit Encryption: All network communication uses HTTPS / TLS 1.3 encryption.

## 5. Contact & Support
Email: support@expensex.app
GitHub: https://github.com/rajuX75/expensec
            """.trimIndent()

            LegalDocType.TERMS_OF_SERVICE -> """
# Terms of Service for Expense Tracker
**Last Updated: August 19, 2026**

## 1. Agreement to Terms
By installing, accessing, or using Expense Tracker, you agree to be bound by these Terms of Service.

## 2. Personal Finance Disclaimer
• Expense Tracker is a self-service organizational tool designed solely for personal financial record-keeping.
• Calculations, graphs, category breakdowns, and summaries provided by the App do not constitute professional financial, accounting, investment, tax, or legal advice.
• You are solely responsible for verifying the accuracy of all numbers and financial decisions made using the App.

## 3. User Responsibilities
• You are responsible for maintaining the confidentiality of your Google sign-in credentials and your 4-digit PIN lock.
• While the App provides automatic cloud sync, we recommend periodically exporting your records via Settings > Export.

## 4. Intellectual Property
• Expense Tracker source code and assets are licensed under the MIT License.
• All referenced trademarks and logos belong to their respective owners.

## 5. Disclaimer of Warranties
The Application is provided on an "AS IS" and "AS AVAILABLE" basis without warranties of any kind, whether express or implied.

## 6. Contact
Email: support@expensex.app
Repository: https://github.com/rajuX75/expensec
            """.trimIndent()

            LegalDocType.SECURITY -> """
# Security & Data Protection Policy

## 1. Local-First Isolation
• All financial records are stored within your device's sandboxed SQLite Room database.
• Photos and receipt attachments are saved locally into private storage directories (/data/data/com.rjx.expensex/files/).

## 2. Cryptographic PIN Lock
• 4-digit PIN passcodes are hashed with a cryptographic salt before storage.
• Verification occurs entirely on-device without network transmission.

## 3. Firebase Cloud Security
• Authentication: Google Sign-In via Android Credential Manager using secure OIDC ID tokens.
• Firestore Rules: Strict user-level isolation guarantees that each user can only read, write, or delete their own data records (request.auth.uid == resource.data.userId).
• Realtime Database Rules: Public read access is strictly limited to /app_version, /app_config, and /changelog.

## 4. Vulnerability Reporting
If you discover a security vulnerability, please contact us privately at support@expensex.app.
            """.trimIndent()

            LegalDocType.ABOUT -> """
# Expense Tracker (ExpenseX)
**Version 1.0.0 (Official Release)**

A modern, private personal financial manager built with Jetpack Compose and Material 3.

## Key Features:
• Local-First Financial Tracker with zero-balance start
• Dhaar (Debts & Loans) tracker with contact photo avatars & phone picker
• Real-time Firebase Firestore database synchronization
• Firebase Realtime Database smart versioning & in-app updates
• Custom profile & contact photo upload with permanent local storage
• Interactive financial analytics, spending breakdown, and cash flow charts
• 4-Digit PIN Lock Security & CSV/JSON Data Export

## Developed with:
• Kotlin 2.2 & Jetpack Compose Material 3
• Android Room Database (SQLite)
• Google Firebase (Auth, Firestore, Realtime Database)
• Coil Image Loading & OkHttp

GitHub: https://github.com/rajuX75/expensec
Support: support@expensex.app
            """.trimIndent()
        }
    }
}
