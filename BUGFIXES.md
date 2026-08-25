# Bug Fixes Applied — Expense Tracker (v1.1.9, versionCode 15)

All fixes are defensive, production-grade, and preserve user data. No user needs
to "clear data" after an update anymore.

## From BUGS.txt

- **BUG #1 (WorkManager crash on version mismatch)** — `ExpenseViewModel.kt`:
  `ExistingWorkPolicy.KEEP` → `REPLACE` so stale serialized work from an older
  version is discarded instead of crashing; the whole enqueue is wrapped in
  try/catch so WorkManager can never crash app startup.

- **BUG #2 (Auth/Firestore sync race)** — `ExpenseViewModel.kt`:
  `currentUser.collect` now uses `distinctUntilChanged { old?.uid == new?.uid }`
  so duplicate/late auth emissions can't fire overlapping syncs; both
  `startRealtimeSync` and `stopRealtimeSync` are individually guarded.

- **BUG #3 (OkHttp response/connection leak)** — `CloudinaryUploader.kt`:
  body reading and `JSONObject` parsing moved fully inside the `use {}` block
  with local try/catch. The response (and its pooled connection) is now closed
  on every path — no leak, no pool exhaustion over many uploads.

- **BUG #4 (Realtime DB persistence crash)** — `FirebaseConfigManager.kt`:
  `setPersistenceEnabled(true)` is now called exactly once per process via an
  `AtomicBoolean`; the database init is restructured so a failure falls back to
  REST sync instead of leaving a null/broken database that later NPEs.

- **BUG #5 (Worker NPE on DB/Firebase)** — `CloudinaryImageUploadWorker.kt`:
  `AppDatabase.getDatabase()` and `FirebaseAuth.getInstance()` are guarded; on a
  Room migration failure the worker returns `Result.retry()` (up to 5 attempts)
  instead of crashing with an NPE.

- **BUG #6 (DataStore/SharedPreferences null & type crashes)** —
  `UserPreferencesRepository.kt`: all preference reads go through safe typed
  readers (`safeString/safeBoolean/safeInt/safeLong/safeStringOrNull`) that
  catch `ClassCastException` from stale/corrupt entries left by an old version,
  self-heal (remove the bad key), and return a sane default. Same safe reads
  applied inside `restorePrefs()`.

- **BUG #7 (Google sign-in crash when Firebase not ready)** —
  `GoogleAuthManager.kt`: `FirebaseAuth.getInstance()` is now lazy + nullable.
  Constructing the manager can never crash; `signIn` surfaces a readable
  `Result.failure` instead of an uncaught exception; `currentUserId`,
  `isAuthenticated` and `signOut` are all null-safe.

- **BUG #8 (no version-check on startup → stale state crash)** — `MainActivity.kt`:
  added `handleAppUpgrade()` which detects a versionCode change, cancels the
  uniquely-named WorkManager jobs enqueued by the older version (their
  serialized state may be incompatible), and records the new version — all
  without wiping user data.

## Additional issues found by repo analysis

- **BUG #9 (Firestore getInstance crash + `!!` NPE)** — `FirestoreSyncManager.kt`:
  `FirebaseFirestore.getInstance()` made lazy so constructing the manager (from
  the ViewModel or the worker) can't crash on app update; the
  `registerPrefChangeListener(prefChangeListener!!)` force-unwrap replaced with
  a safe `?.let`.

- Verified `ImageStorageHelper.enqueueUploadWorker()` — already guarded with
  try/catch and uses `APPEND_OR_REPLACE`; no change required.
