package com.example.data.cloud

/**
 * Builds Cloudinary on-the-fly transformation URLs.
 *
 * Instead of loading the full-size uploaded image everywhere (slow, burns
 * bandwidth and defeats Coil's disk cache), call [CloudinaryUrl.resize] to get
 * a small, auto-optimised, CDN-cached derivative for list rows / avatars /
 * thumbnails, and keep the original secure_url only for full-screen previews.
 *
 * Docs: https://cloudinary.com/documentation/java_integration
 * Transformations: https://cloudinary.com/documentation/image_transformations
 */
object CloudinaryUrl {

    private const val UPLOAD_MARKER = "/upload/"
    private const val RES_CLOUDINARY_HOST = "res.cloudinary.com"

    /** Standard thumbnail (list rows, small previews). */
    const val THUMB_SIZE = 256

    /** Avatar / circular profile pictures. */
    const val AVATAR_SIZE = 128

    /** Full-screen preview — capped so we never pull a 12 MP original. */
    const val PREVIEW_SIZE = 1080

    /**
     * Returns a resized, auto-optimised (f_auto,q_auto) Cloudinary URL.
     *
     * If [url] is not a Cloudinary delivery URL (local file://, content://,
     * Google photo URL, already-transformed URL, ...) it is returned unchanged,
     * so this is always safe to apply.
     */
    fun resize(url: String?, width: Int, height: Int = width, crop: String = "limit"): String? {
        if (url.isNullOrBlank()) return url
        if (!url.startsWith("http")) return url
        if (!url.contains(RES_CLOUDINARY_HOST)) return url
        val idx = url.indexOf(UPLOAD_MARKER)
        if (idx < 0) return url

        // Already transformed (contains a transformation segment) — leave as is.
        val afterUpload = url.substring(idx + UPLOAD_MARKER.length)
        if (afterUpload.startsWith("c_") || afterUpload.startsWith("w_") ||
            afterUpload.startsWith("f_") || afterUpload.contains(",")
        ) {
            return url
        }

        val transformation = "c_$crop,w_$width,h_$height,q_auto,f_auto"
        return url.substring(0, idx + UPLOAD_MARKER.length) +
                transformation + "/" + afterUpload
    }

    /** Thumbnail for list rows / cards. */
    fun thumb(url: String?): String? = resize(url, THUMB_SIZE, THUMB_SIZE, crop = "fill")

    /** Small square avatar. */
    fun avatar(url: String?): String? = resize(url, AVATAR_SIZE, AVATAR_SIZE, crop = "thumb")

    /** Capped full preview (keeps aspect ratio, never larger than PREVIEW_SIZE). */
    fun preview(url: String?): String? = resize(url, PREVIEW_SIZE, PREVIEW_SIZE, crop = "limit")
}
