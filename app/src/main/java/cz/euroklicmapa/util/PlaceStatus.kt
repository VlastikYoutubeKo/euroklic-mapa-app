package cz.euroklicmapa.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Trust state of a WC location. Mirrors the website's `markerStyleFor()` / `statusBadgeFor()`
 * exactly — recompute per render from live `likes` / `dislikes` / `last_verified`, never cache.
 */
enum class PlaceStatus {
    /** dislikes dominate — hollow marker, "⚠ Nahlášeno nefunguje". */
    REPORTED,

    /** source == "cd" — filled + ring, "✓ Oficiální zdroj (ČD)". */
    OFFICIAL,

    /** last_verified within 183 days — filled + ring, "✓ Nedávno ověřeno". */
    RECENTLY_VERIFIED,

    /** nothing above — filled, no ring, "Bez ověření". */
    UNVERIFIED,
    ;

    /** REPORTED = hollow, OFFICIAL/RECENTLY_VERIFIED = filled + white ring, UNVERIFIED = filled. */
    val hasRing: Boolean get() = this == OFFICIAL || this == RECENTLY_VERIFIED
    val isHollow: Boolean get() = this == REPORTED
}

private val VERIFIED_WINDOW_MS = TimeUnit.DAYS.toMillis(183)

/** `"YYYY-MM-DD HH:MM:SS"` in UTC, as sent by the backend. */
private val lastVerifiedFormat = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
}

fun placeStatus(
    source: String?,
    likes: Int,
    dislikes: Int,
    lastVerified: String?,
    now: Long = System.currentTimeMillis(),
): PlaceStatus {
    if (dislikes > 0 && dislikes > likes) return PlaceStatus.REPORTED
    if (source?.lowercase() == "cd") return PlaceStatus.OFFICIAL
    val verifiedAt = lastVerified?.let { runCatching { lastVerifiedFormat.get().parse(it)?.time }.getOrNull() }
    if (verifiedAt != null && now - verifiedAt < VERIFIED_WINDOW_MS) return PlaceStatus.RECENTLY_VERIFIED
    return PlaceStatus.UNVERIFIED
}

/**
 * Community-trust badge — the "Oficiální / Komunitní zdroj" pill beside it already says the
 * origin, so this must NOT repeat it. **Mirrors the website popup badge 1:1**: purely
 * likes/dislikes/`cd` — deliberately NOT `last_verified`/183-day (that still drives the marker
 * ring via [PlaceStatus], just not this text). So a row with old votes but a null
 * `last_verified` still reads "Ověřeno · N👍" here.
 */
enum class VoteBadge { REPORTED, VERIFIED, CD_NO_VOTES, NONE }

fun voteBadge(likes: Int, dislikes: Int, isCd: Boolean): VoteBadge = when {
    dislikes > 0 && dislikes > likes -> VoteBadge.REPORTED
    likes > 0 -> VoteBadge.VERIFIED
    isCd -> VoteBadge.CD_NO_VOTES
    else -> VoteBadge.NONE
}

fun VoteBadge.label(likes: Int, dislikes: Int): String = when (this) {
    VoteBadge.REPORTED -> "Nahlášeno nefunguje ($dislikes👎)"
    VoteBadge.VERIFIED -> "Ověřeno · $likes👍"
    VoteBadge.CD_NO_VOTES -> "Zatím bez hlasů"
    VoteBadge.NONE -> ""
}

/** `"YYYY-MM-DD HH:MM:SS"` UTC — same shape the backend uses for `last_verified`. */
fun nowUtcTimestamp(now: Long = System.currentTimeMillis()): String =
    lastVerifiedFormat.get().format(java.util.Date(now))
