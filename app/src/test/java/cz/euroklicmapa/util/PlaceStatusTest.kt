package cz.euroklicmapa.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Website-parity trust-state logic. Mirrors the contract documented in CLAUDE.md /
 * the BACKLOG A7 item — every branch + the day-183 boundary.
 */
class PlaceStatusTest {

    private val dayMs = TimeUnit.DAYS.toMillis(1)

    // A fixed "now" well clear of the epoch so subtracting a few hundred days stays positive.
    private val now = TimeUnit.DAYS.toMillis(20_000) // ~2024-10

    // ---- placeStatus: REPORTED -------------------------------------------------

    @Test
    fun reported_whenDislikesDominate() {
        assertEquals(PlaceStatus.REPORTED, placeStatus("osm", likes = 0, dislikes = 1, lastVerified = null, now = now))
        assertEquals(PlaceStatus.REPORTED, placeStatus("user", likes = 2, dislikes = 3, lastVerified = null, now = now))
    }

    @Test
    fun reported_winsOverOfficialSource() {
        assertEquals(
            PlaceStatus.REPORTED,
            placeStatus("cd", likes = 1, dislikes = 5, lastVerified = nowUtcTimestamp(now), now = now),
        )
    }

    @Test
    fun reported_notTriggeredWhenDislikesEqualLikes() {
        // dislikes == likes -> not REPORTED; falls through (not cd, no lastVerified) -> UNVERIFIED
        assertEquals(
            PlaceStatus.UNVERIFIED,
            placeStatus("osm", likes = 2, dislikes = 2, lastVerified = null, now = now),
        )
    }

    @Test
    fun reported_notTriggeredWhenNoDislikes() {
        assertEquals(
            PlaceStatus.UNVERIFIED,
            placeStatus("osm", likes = 0, dislikes = 0, lastVerified = null, now = now),
        )
    }

    // ---- placeStatus: OFFICIAL ----------------------------------------------------

    @Test
    fun official_whenSourceIsCd_caseInsensitive() {
        assertEquals(PlaceStatus.OFFICIAL, placeStatus("cd", 0, 0, null, now))
        assertEquals(PlaceStatus.OFFICIAL, placeStatus("CD", 0, 0, null, now))
        assertEquals(PlaceStatus.OFFICIAL, placeStatus("Cd", 0, 0, null, now))
    }

    @Test
    fun official_whenCdWithSomeVotesButNotDominatedByDislikes() {
        assertEquals(PlaceStatus.OFFICIAL, placeStatus("cd", likes = 5, dislikes = 2, lastVerified = null, now = now))
    }

    // ---- placeStatus: RECENTLY_VERIFIED / window boundary -----------------------

    @Test
    fun recentlyVerified_justInsideWindow() {
        val verified = nowUtcTimestamp(now - 182 * dayMs)
        assertEquals(
            PlaceStatus.RECENTLY_VERIFIED,
            placeStatus("osm", likes = 0, dislikes = 0, lastVerified = verified, now = now),
        )
    }

    @Test
    fun recentlyVerified_justOutsideWindow() {
        val verified = nowUtcTimestamp(now - 184 * dayMs)
        assertEquals(
            PlaceStatus.UNVERIFIED,
            placeStatus("osm", likes = 0, dislikes = 0, lastVerified = verified, now = now),
        )
    }

    @Test
    fun recentlyVerified_exactlyAtWindowIsNotRecent() {
        // now - verifiedAt == 183 days exactly; the check is strict `<`, so this is UNVERIFIED.
        val verified = nowUtcTimestamp(now - 183 * dayMs)
        assertEquals(
            PlaceStatus.UNVERIFIED,
            placeStatus("osm", likes = 0, dislikes = 0, lastVerified = verified, now = now),
        )
    }

    @Test
    fun recentlyVerified_notAppliedWhenSourceIsCd() {
        // cd short-circuits to OFFICIAL before lastVerified is even parsed.
        val verified = nowUtcTimestamp(now - 1 * dayMs)
        assertEquals(PlaceStatus.OFFICIAL, placeStatus("cd", 0, 0, verified, now))
    }

    @Test
    fun unverified_whenLastVerifiedMalformed() {
        assertEquals(
            PlaceStatus.UNVERIFIED,
            placeStatus("osm", 0, 0, "not-a-real-date", now),
        )
        assertEquals(
            PlaceStatus.UNVERIFIED,
            placeStatus("osm", 0, 0, "", now),
        )
    }

    @Test
    fun unverified_whenLastVerifiedNull() {
        assertEquals(PlaceStatus.UNVERIFIED, placeStatus("osm", 0, 0, null, now))
    }

    @Test
    fun unverified_whenSourceNull() {
        assertEquals(PlaceStatus.UNVERIFIED, placeStatus(null, 0, 0, null, now))
    }

    // ---- PlaceStatus.hasRing / isHollow ---------------------------------------

    @Test
    fun hasRing_trueForOfficialAndRecentlyVerified() {
        assertTrue(PlaceStatus.OFFICIAL.hasRing)
        assertTrue(PlaceStatus.RECENTLY_VERIFIED.hasRing)
    }

    @Test
    fun hasRing_falseForReportedAndUnverified() {
        assertFalse(PlaceStatus.REPORTED.hasRing)
        assertFalse(PlaceStatus.UNVERIFIED.hasRing)
    }

    @Test
    fun isHollow_onlyForReported() {
        assertTrue(PlaceStatus.REPORTED.isHollow)
        assertFalse(PlaceStatus.OFFICIAL.isHollow)
        assertFalse(PlaceStatus.RECENTLY_VERIFIED.isHollow)
        assertFalse(PlaceStatus.UNVERIFIED.isHollow)
    }

    // ---- voteBadge ----------------------------------------------------------------

    @Test
    fun voteBadge_reportedWhenDislikesDominate() {
        assertEquals(VoteBadge.REPORTED, voteBadge(likes = 0, dislikes = 1, isCd = false))
        assertEquals(VoteBadge.REPORTED, voteBadge(likes = 2, dislikes = 3, isCd = true))
    }

    @Test
    fun voteBadge_notReportedWhenDislikesEqualLikes() {
        // 2/2 -> not REPORTED; likes > 0 -> VERIFIED
        assertEquals(VoteBadge.VERIFIED, voteBadge(likes = 2, dislikes = 2, isCd = false))
    }

    @Test
    fun voteBadge_verifiedWhenLikesPositive() {
        assertEquals(VoteBadge.VERIFIED, voteBadge(likes = 1, dislikes = 0, isCd = false))
        assertEquals(VoteBadge.VERIFIED, voteBadge(likes = 3, dislikes = 1, isCd = true))
    }

    @Test
    fun voteBadge_cdNoVotesWhenCdAndNoLikesNoReport() {
        assertEquals(VoteBadge.CD_NO_VOTES, voteBadge(likes = 0, dislikes = 0, isCd = true))
    }

    @Test
    fun voteBadge_noneWhenNothingApplies() {
        assertEquals(VoteBadge.NONE, voteBadge(likes = 0, dislikes = 0, isCd = false))
    }

    // ---- VoteBadge.label --------------------------------------------------------

    @Test
    fun label_reportedString() {
        assertEquals("Nahlášeno nefunguje (4👎)", VoteBadge.REPORTED.label(likes = 1, dislikes = 4))
    }

    @Test
    fun label_verifiedString() {
        assertEquals("Ověřeno · 7👍", VoteBadge.VERIFIED.label(likes = 7, dislikes = 0))
    }

    @Test
    fun label_cdNoVotesString() {
        assertEquals("Zatím bez hlasů", VoteBadge.CD_NO_VOTES.label(likes = 0, dislikes = 0))
    }

    @Test
    fun label_noneIsEmpty() {
        assertEquals("", VoteBadge.NONE.label(likes = 0, dislikes = 0))
    }

    // ---- nowUtcTimestamp --------------------------------------------------------

    @Test
    fun nowUtcTimestamp_formatsUtcEpoch() {
        assertEquals("1970-01-01 00:00:00", nowUtcTimestamp(0L))
    }

    @Test
    fun nowUtcTimestamp_roundTripsThroughPlaceStatus() {
        // A timestamp produced by nowUtcTimestamp must parse back and count as recent.
        val stamp = nowUtcTimestamp(now - 10 * dayMs)
        assertEquals(
            PlaceStatus.RECENTLY_VERIFIED,
            placeStatus("osm", 0, 0, stamp, now),
        )
    }
}
