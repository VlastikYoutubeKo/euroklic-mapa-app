package cz.euroklicmapa.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A7 — the pure bits of [QueuePollWorker]: Czech pluralisation for the notification text and
 * the admin "should we notify" predicate. `doWork()` itself needs Android + a live
 * `EuroklicApplication` and is not unit-tested.
 */
class QueuePollLogicTest {

    // ---- places(n) — "místo / místa / míst" ----------------------------------

    @Test
    fun places_pluralBoundaries() {
        assertEquals("0 míst", QueuePollLogic.places(0))
        assertEquals("1 místo", QueuePollLogic.places(1))
        assertEquals("2 místa", QueuePollLogic.places(2))
        assertEquals("4 místa", QueuePollLogic.places(4))
        assertEquals("5 míst", QueuePollLogic.places(5))
        assertEquals("22 míst", QueuePollLogic.places(22))
    }

    // ---- photos(n) — "fotka / fotky / fotek" --------------------------------

    @Test
    fun photos_pluralBoundaries() {
        assertEquals("0 fotek", QueuePollLogic.photos(0))
        assertEquals("1 fotka", QueuePollLogic.photos(1))
        assertEquals("2 fotky", QueuePollLogic.photos(2))
        assertEquals("4 fotky", QueuePollLogic.photos(4))
        assertEquals("5 fotek", QueuePollLogic.photos(5))
        assertEquals("22 fotek", QueuePollLogic.photos(22))
    }

    // ---- newPlacesNearby(n) ------------------------------------------------

    @Test
    fun newPlacesNearby_pluralBoundaries() {
        assertEquals("0 nových míst v okolí", QueuePollLogic.newPlacesNearby(0))
        assertEquals("1 nové místo v okolí", QueuePollLogic.newPlacesNearby(1))
        assertEquals("2 nová místa v okolí", QueuePollLogic.newPlacesNearby(2))
        assertEquals("4 nová místa v okolí", QueuePollLogic.newPlacesNearby(4))
        assertEquals("5 nových míst v okolí", QueuePollLogic.newPlacesNearby(5))
        assertEquals("22 nových míst v okolí", QueuePollLogic.newPlacesNearby(22))
    }

    // ---- adminShouldNotify truth table -----------------------------------

    @Test
    fun adminShouldNotify_falseWhenNothingPending() {
        assertFalse(QueuePollLogic.adminShouldNotify(placeCount = 0, photoCount = 0, lastPlaceCount = 0, lastPhotoCount = 0))
        // Even if the last counts differ, zero pending means nothing to say.
        assertFalse(QueuePollLogic.adminShouldNotify(placeCount = 0, photoCount = 0, lastPlaceCount = 5, lastPhotoCount = 2))
    }

    @Test
    fun adminShouldNotify_falseWhenCountsUnchanged() {
        assertFalse(QueuePollLogic.adminShouldNotify(placeCount = 3, photoCount = 2, lastPlaceCount = 3, lastPhotoCount = 2))
        assertFalse(QueuePollLogic.adminShouldNotify(placeCount = 5, photoCount = 0, lastPlaceCount = 5, lastPhotoCount = 0))
    }

    @Test
    fun adminShouldNotify_trueWhenPlaceCountChangedAndPending() {
        assertTrue(QueuePollLogic.adminShouldNotify(placeCount = 4, photoCount = 2, lastPlaceCount = 3, lastPhotoCount = 2))
    }

    @Test
    fun adminShouldNotify_trueWhenPhotoCountChangedAndPending() {
        assertTrue(QueuePollLogic.adminShouldNotify(placeCount = 3, photoCount = 5, lastPlaceCount = 3, lastPhotoCount = 2))
    }

    @Test
    fun adminShouldNotify_firstPoll_sentinelMinusOne() {
        // NotificationPrefs seeds last*Count at -1; the first real non-zero counts count as changed.
        assertTrue(QueuePollLogic.adminShouldNotify(placeCount = 2, photoCount = 0, lastPlaceCount = -1, lastPhotoCount = -1))
        assertTrue(QueuePollLogic.adminShouldNotify(placeCount = 0, photoCount = 1, lastPlaceCount = -1, lastPhotoCount = -1))
        // …but an empty queue on the first poll still doesn't notify.
        assertFalse(QueuePollLogic.adminShouldNotify(placeCount = 0, photoCount = 0, lastPlaceCount = -1, lastPhotoCount = -1))
    }
}
