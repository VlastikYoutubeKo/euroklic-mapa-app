package cz.euroklicmapa.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kdy se nabídne chůze: do ~45 min po cestě, jinak jen MHD. */
class NavigationPolicyTest {

    @Test fun shortDistanceIsWalkable() {
        assertTrue(walkingMinutes(500.0) <= MAX_WALK_MINUTES)
        assertTrue(walkingMinutes(2000.0) <= MAX_WALK_MINUTES)
    }

    @Test fun fiveKilometresIsNotWalkable() {
        assertFalse(walkingMinutes(5000.0) <= MAX_WALK_MINUTES)
    }

    @Test fun pathFactorMakesWalkLongerThanStraightLine() {
        // 1 km vzdušnou čarou při 4,5 km/h = 13,3 min; po cestě (×1,3) asi 17 min.
        assertEquals(17, walkingMinutes(1000.0))
    }

    @Test fun neverBelowOneMinute() {
        assertEquals(1, walkingMinutes(1.0))
    }

    @Test fun transitMapsToGoogleTravelMode() {
        assertEquals("walking", NavMode.WALKING.travelMode)
        assertEquals("transit", NavMode.TRANSIT.travelMode)
    }
}
