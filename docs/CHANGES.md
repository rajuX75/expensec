# ExpenseX — Android Skills Implementation (All 3 Phases)

Branch contents vs. upstream `main`. App: com.rjx.expensex, v1.1.17 (code 23).

## Decisions applied
- **minSdk 24 → 28** (Restore Credentials support; API 24–27 dropped)
- **Navigation 3: Option A — full migration in one pass**
- **All 3 phases executed**

## Phase 1 — Security & Compliance

### Skill #1: android-intent-security
- `app/src/main/res/xml/file_paths.xml` — removed `<external-path path="."/>`
  (exposed ALL shared storage); now scoped to app-private `Download/` for APK updates.
- Audited all `startActivity` / `FileProvider` / `PendingIntent` call sites — no
  mutable PendingIntents, no intent-redirection vectors found (report in
  `docs/SECURITY_AND_COMPLIANCE_AUDIT.md`).
- `AndroidManifest.xml` — added `expensex://open/...` deep-link intent filter
  (consumed by the new Navigation 3 back stack).

### Skill #2: play-policy-insights
- Static compliance audit delivered in `docs/SECURITY_AND_COMPLIANCE_AUDIT.md`:
  permissions hygiene (⚠️ `REQUEST_INSTALL_PACKAGES` needs gating or Play In-App
  Updates before Play Store submission), data-safety mapping for
  Firebase/Cloudinary/Drive, and the **account-deletion requirement** (must exist
  in-app before next Play release).

## Phase 2 — Quality & APK Optimization

### Skill #3: testing-setup
New tests in `app/src/test/java/com/example/`:
- `ExpenseViewModelTest.kt` — Robolectric ViewModel construction + PIN session state
- `AnalyticsDelegateFlowTest.kt` — Turbine StateFlow recomposition test
- `CloudDelegateTest.kt` — MockK Google sign-in success/failure delegation
- `PinLockScreenTest.kt` — Compose UI behavior: unlock, error state, rendering
- `ExpenseRepositoryTest.kt` — Room in-memory DAO/repository integration tests
- `ScreenScreenshotTest.kt` — Roborazzi baselines, light + dark variants

Build changes: MockK + Turbine deps, **JaCoCo** coverage task
(`./gradlew :app:jacocoTestReport`).

### Skill #4: r8-analyzer
- `proguard-rules.pro` rewritten: removed blanket Firebase/GMS keeps and redundant
  `-dontwarn` blocks (library consumer rules cover them); kept only app-model
  reflection targets (Moshi/Firestore) + Retrofit annotations + kotlinx-serialization
  keeps for Nav 3 routes; consolidated duplicate attribute rules.
- `gradle.properties`: `android.enableR8.fullMode=true`.
- Verify: `./gradlew :app:analyzeReleaseR8Config` + compare APK size (expect 5–15% ↓).

## Phase 3 — Features

### Skill #5: restore-credentials
- New `data/cloud/RestoreCredentialManager.kt` — create key after sign-in,
  Tier-2 silent fetch on launcher `onCreate`, clear on sign-out. Scoped to
  key management with the Firebase uid as payload (full WebAuthn server
  verification = future backend work).
- `GoogleAuthManager` — creates restore key on both modern + legacy sign-in;
  clears it on sign-out.
- `MainActivity` — `attemptSilentRestore()` runs before sign-in UI.
- Credentials library bumped 1.5.0 → 1.7.0-alpha03.

### Skill #6: navigation-3 (full migration)
- `ui/navigation/AppNavigation.kt` rewritten: `@Serializable AppRoute` route
  hierarchy (12 destinations incl. `ContactDetail(contactId)` / `ShopDetail(shopId)`
  type-safe args), `rememberNavBackStack` + `NavDisplay` with fade
  ContentTransforms, tab switching with stack reset, `expensex://open/...`
  deep-link parsing.
- `MainActivity` passes `intent?.data` into `ExpenseAppMain(deepLink = ...)`.
- Deps: navigation3-runtime/ui 1.0.0, lifecycle-viewmodel-navigation3,
  kotlinx-serialization-json + serialization plugin. Old `navigation-compose`
  dep removed (no code referenced it).
- Modal overlays (transaction sheet, settings sheet, update dialog, notification
  popup) intentionally remain local state.

## Verification checklist
```
gradlew.bat test                        # unit tests (11 classes now)
gradlew.bat recordRoborazziDebug        # first-time screenshot baselines
gradlew.bat verifyRoborazziDebug        # subsequent verification
gradlew.bat connectedAndroidTest
gradlew.bat :app:analyzeReleaseR8Config
gradlew.bat assembleRelease
```
> Note: these sources were produced without a local Android SDK build; run the
> checklist above once on your machine (CI) before merging. Most likely touch-up
> areas: exact navigation3 1.0.0 API surface names and Roborazzi first-record.
