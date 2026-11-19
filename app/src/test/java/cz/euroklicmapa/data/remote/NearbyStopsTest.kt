package cz.euroklicmapa.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyStopsTest {
    @Test
    fun dedupesByNameSortsAndLimits() {
        val r = nearestStops(
            listOf(
                NearbyStop("a2", "Větrov", 300),
                NearbyStop("b", "Muzeum", 200),
                NearbyStop("a1", "Větrov", 250),
                NearbyStop("c", "Můstek", 500),
                NearbyStop("d", "Lázeňská", 400),
                NearbyStop("e", " ", 10),
            ),
        )
        assertEquals(listOf("Muzeum", "Větrov", "Lázeňská"), r.map { it.name })
        assertEquals("a1", r[1].id)
    }
}
