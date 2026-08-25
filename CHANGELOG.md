# Changelog

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
