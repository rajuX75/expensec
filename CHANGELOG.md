# Changelog

## [1.1.6] — 2026-08-25

Direct Cloudinary upload flow, realtime Firebase sync, and configurable in-app credentials.

### Fixed
- **Direct Upload Flow**: Images selected by the user are now immediately compressed, uploaded to Cloudinary, returned as HTTPS URLs, and synced directly to Firebase DB and Room DB.
- **Progress Indicators**: Added upload spinners across avatar pickers, shop covers, dhaar receipts, and transaction receipts while Cloudinary uploads are in-flight.
- **Worker Firebase Sync**: Background upload worker now pushes updated entities to Firebase Firestore immediately upon successful upload.
- **CI Secrets Injection**: Added `.env` generation step in GitHub Actions release workflow so Cloudinary credentials are fully injected into the release APK.
- **In-App Cloudinary Settings**: Added a Cloudinary configuration card and dialog in Settings with connection testing support and runtime preferences.

## [1.1.5] — 2026-08-25

Patch release focused on fixing Cloudinary image upload & delivery.

### Fixed

- **Images lost after "Clear Data" with nothing in the cloud** — uploads were
  failing silently when the `.env` still contained the placeholder credentials
  (`your_cloud_name` / `your_api_key` / `your_api_secret`). `CloudinaryUploader`
  now validates credentials up-front (`isConfigured()`), logs a clear
  configuration error, and keeps the local file so it can be retried later
  instead of pretending everything is fine.
- **HTTP connection leak in `CloudinaryUploader`** — the OkHttp `Response` was
  never closed (`response.body?.string()` without `use {}`), which exhausted
  the connection pool and made every upload time out after a few images.
- **Background upload worker silently did nothing / leaked coroutines** —
  `CloudinaryImageUploadWorker` now uses a lifecycle-aware scope instead of
  `GlobalScope`, fails fast with a clear log when Cloudinary is not configured,
  and has exponential backoff with a retry cap (5 attempts).
- **Local copies of images were never cleaned up** — the worker now deletes the
  local `file://` copy only *after* a successful upload, so storage doesn't
  grow forever and the cloud is the single source of truth.

### Changed

- **Use small cached Cloudinary images instead of full-size originals in the
  UI** — added `CloudinaryUrl` helper that builds on-the-fly transformation
  URLs (`c_thumb`/`c_fill`/`c_limit` + `q_auto`/`f_auto`). List rows and
  avatars now load a 128–256 px CDN-cached thumbnail instead of the original
  multi-MB image; full-screen previews are capped at 1080 px. This makes lists
  dramatically faster and lets Coil's disk cache actually work.
- **Local images are compressed before storing/uploading** — picked images are
  decoded, downscaled to ≤1280 px on the longest edge, EXIF-rotation corrected
  and saved as JPEG q=82 instead of a blind byte-copy of the full-resolution
  camera file. Faster uploads, ~10× smaller storage and bandwidth use.

### Docs

- Integrated per the official
  [Cloudinary Java integration guide](https://cloudinary.com/documentation/java_integration):
  credentials via config, alphabetical signature parameter signing, and
  URL-based transformations for delivery.

### Version

- `versionCode` 10 → **11**
- `versionName` `1.1.4` → **`1.1.5`**

## [1.1.11] - 2026-08-26
### Fixed
- **Category list duplicating on every app update / sync** — root cause: `MIGRATION_1_2` left existing rows with a blank `uuid`, and the syncer inserted with `id = 0` (REPLACE only matched the PK), so each sync re-inserted the full default set. Now: blank UUIDs are backfilled, duplicate name+type rows are collapsed on startup, seeding only runs on an empty table, and the category syncer upserts by `uuid` then `name+type` so it UPDATEs instead of duplicate-INSERTing.
- **Shop Baki payments now appear in Recent Transactions** — paying a shop owner logs a mirrored EXPENSE transaction (merchant = shop name, tag `ShopBaki`, note "Paid shop baki to …") and pushes it with the real row id.
- **Bill-paid / transfer Firestore push duplication** — `markBillAsPaid` and `transferFunds` now capture the inserted row id before pushing (prevents the realtime listener inserting a duplicate copy).

### Improved
- **Shop Baki UI/UX (per UI/UX Pro Max guidance)** — gradient overview header with "You Owe" vs "Active Shops" semantic color zones, stronger visual hierarchy and elevation, content descriptions on action buttons, 44dp+ touch targets preserved, no emoji-as-icon usage.

### Version
- `versionCode` 16 → **17**, `versionName` 1.1.10 → **1.1.11**
