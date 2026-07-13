package my.noveldokusha.core

/**
 * Check if content is valid for processing.
 * Returns false if content is too short OR contains Cloudflare markers.
 */
fun isValidChapterContent(text: String): Boolean {
    if (text.length < 100) return false

    val lowerText = text.lowercase()
    val cloudflareMarkers = listOf(
        "cf-content",
        "turnstile",
        "but-captcha",
        "cf-browser-verification",
        "challenge-running",
        "captcha-container",
        "hcaptcha",
    )

    return !cloudflareMarkers.any { lowerText.contains(it) }
}
