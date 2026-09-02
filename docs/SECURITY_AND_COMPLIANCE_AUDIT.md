# ExpenseX — Phase 1 Audit Report (Skills #1 & #2)

Date: 2026-09-02 · App: com.rjx.expensex v1.1.17 · minSdk now 28, targetSdk 36

## Skill #1 — Android Intent Security Audit

### Manifest / exported components
| Component | exported | Verdict |
|---|---|---|
| MainActivity | true (LAUNCHER + VIEW deep-link filters) | ✅ Correct — launcher must be exported; deep-link filter added for Nav 3 |
| FileProvider | false | ✅ Correct |

### Findings & fixes applied
1. **FileProvider path scope (FIXED — was Medium severity).**
   `file_paths.xml` previously declared `<external-path path="."/>`, exposing all of
   shared external storage through the provider. Replaced with
   `<external-files-path path="Download/"/>`, scoped to the app-private directory
   where update APKs are written (`UpdateRepository.downloadAndInstallApk`).
2. **APK install intent (VERIFIED SAFE).** `launchApkInstaller()` builds the
   `ACTION_VIEW` intent itself with a FileProvider URI and
   `FLAG_GRANT_READ_URI_PERMISSION`. No external input flows into the intent —
   no intent-redirection risk. URI read grants are implicitly revoked when the
   installer task finishes; consider `context.revokeUriPermission()` post-install
   as defense-in-depth (optional).
3. **PendingIntent mutability (VERIFIED SAFE).** The only PendingIntent in the
   codebase is the Drive-consent `PendingIntent` created by Google Play Services
   (`Identity.getAuthorizationClient().authorize()`), which is created immutable
   by the provider. No app-constructed mutable PendingIntents exist; no
   notification PendingIntents (app uses in-app Realtime-DB inbox, not FCM).
4. **startActivity call sites (VERIFIED SAFE).** All call sites
   (LegalDocsDialog, ExportDataDialog/Screen share sheets, ContactDetailScreen
   dialer, UpdateRepository browser/installer) use explicitly constructed
   intents with `ACTION_VIEW`/`ACTION_SEND` — none forward attacker-controlled
   extras.

## Skill #2 — Play Policy Compliance Audit

### Permissions hygiene
| Permission | Justification | Status |
|---|---|---|
| INTERNET | Firebase sync, update checks, Cloudinary uploads | ✅ Justified |
| ACCESS_NETWORK_STATE | WorkManager network constraints, offline UX | ✅ Justified |
| REQUEST_INSTALL_PACKAGES | Self-update APK install (GitHub releases) | ⚠️ **Action required** — if distributing via Play Store, self-update via sideloaded APK conflicts with Play's update policy; gate this feature to non-Play builds or replace with Play In-App Updates before Play submission |

### Data safety (must match Play Console declaration)
- **Firebase Firestore/Realtime DB**: financial data synced when signed in → declare "Financial info", encrypted in transit.
- **Cloudinary**: receipt/profile image uploads (user-configured) → declare "Photos or videos".
- **Google Drive (appdata scope)**: backup file storage → declare "Files and docs".
- **Crash logs**: `CrashLogCapture` writes local logs shared only via user-initiated feedback → disclose in Data Safety if uploaded.
- `PRIVACY_POLICY.md` exists in repo root — ensure its Play Console link is live.

### Account & identity
- Google Sign-In present. Play policy (since 2024) requires **in-app account
  deletion** for apps with account creation: verify the Profile/Settings flow
  offers account deletion AND data deletion (Firestore docs + Firebase Auth
  `user.delete()`). If missing, add before next release.
- Demo credentials: none found in the client — ✅.

## Skill #4 — R8 optimization (applied)
- Removed blanket `-keep com.google.firebase.**` / `-keep com.google.android.gms.**`
  (rely on BOM consumer rules; app models in `com.example.data.model.**` kept
  explicitly for Firestore/Moshi reflection).
- Removed redundant `-dontwarn` blocks; consolidated duplicate line-number rules.
- Enabled `android.enableR8.fullMode=true`.
- Added kotlinx-serialization keeps for `@Serializable` Nav 3 routes.
- Verify with `./gradlew :app:analyzeReleaseR8Config` and compare APK size
  (expected 5–15% reduction).
