# Security Policy

## Supported Versions

We actively provide security patches and updates for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0.0 | :x:                |

---

## Security Architecture & Practices

Expense Tracker prioritizes user security and data privacy through the following architectural principles:

### 1. Local-First Sandboxed Database
- All transaction records, accounts, and contact data reside in an encrypted/isolated SQLite Room database stored in the application's private internal storage (`/data/data/com.rjx.expensex/databases/`).
- Photos and receipt attachments are saved locally into private files directories (`/data/data/com.rjx.expensex/files/`).

### 2. PIN Code Protection
- 4-digit PIN locks are hashed with a cryptographic salt before storage in encrypted shared preferences.
- PIN protection prevents unauthorized physical access to financial data.

### 3. Firebase Cloud Security
- **Authentication**: Modern Google Sign-In via Android Credential Manager using short-lived OIDC ID tokens.
- **Firestore Security Rules**: User-level isolation rules guarantee that each user can only read, write, or delete their own data records (`auth.uid == userId`).
- **Realtime Database Security Rules**: Public read access is strictly limited to `/app_version`, `/app_config`, and `/changelog`. All other paths require administrator authentication.

---

## Reporting a Vulnerability

If you discover a security vulnerability or privacy flaw within Expense Tracker:

1. **Do not open a public GitHub issue**.
2. Please send a detailed report via email to **[support@expensex.app](mailto:support@expensex.app)** or reach out to the repository maintainer.
3. Include the following details in your report:
   - Description of the vulnerability.
   - Steps to reproduce or proof-of-concept.
   - Affected versions and device environments.
4. We will acknowledge receipt of your report within 48 hours and provide a remediation timeline.
