package cz.euroklicmapa.ui.viewmodel

import cz.euroklicmapa.data.local.PickupPointEntity
import cz.euroklicmapa.data.local.WcLocationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

/**
 * Website-parity merge logic for the shared list feed (BACKLOG A7).
 * Covers category filtering, distance vs. alphabetical sort, navType, title/subtitle fallbacks.
 */
class PlacesTest {

    private fun wc(
        id: Int,
        name: String = "WC $id",
        source: String = "osm",
        likes: Int = 0,
        dislikes: Int = 0,
        lastVerified: String? = null,
        country: String? = null,
        lat: Double = 50.0,
        lon: Double = 14.0,
    ) = WcLocationEntity(
        id = id,
        name = name,
        source = source,
        description = null,
        note = null,
        photoUrl = null,
        likes = likes,
        dislikes = dislikes,
        lastVerified = lastVerified,
        webUrl = null,
        openingHours = null,
        access = null,
        wheelchair = null,
        accessibilityNote = null,
        country = country,
        floorPlanUrl = null,
        longitude = lon,
        latitude = lat,
    )

    private fun pickup(
        id: Int,
        orgName: String = "Pickup $id",
        kraj: String? = null,
        district: String? = null,
        address: String? = null,
        lat: Double = 50.0,
        lon: Double = 14.0,
    ) = PickupPointEntity(
        id = id,
        kraj = kraj,
        district = district,
        orgName = orgName,
        address = address,
        phone = null,
        email = null,
        hours = null,
        note = null,
        precision = null,
        sourceUrl = null,
        longitude = lon,
        latitude = lat,
    )

    // ---- category filtering --------------------------------------------------

    @Test
    fun category_all_returnsBothFeeds() {
        val out = flattenPlaces(
            locations = listOf(wc(1)),
            pickupPoints = listOf(pickup(2)),
            userLocation = null,
            category = PlaceCategory.ALL,
        )
        assertEquals(2, out.size)
        assertEquals(setOf(false, true), out.map { it.isPickup }.toSet())
    }

    @Test
    fun category_toilet_dropsPickups() {
        val out = flattenPlaces(
            locations = listOf(wc(1), wc(3)),
            pickupPoints = listOf(pickup(2)),
            userLocation = null,
            category = PlaceCategory.TOILET,
        )
        assertEquals(2, out.size)
        assertTrue(out.none { it.isPickup })
    }

    @Test
    fun category_pickup_dropsWcs() {
        val out = flattenPlaces(
            locations = listOf(wc(1), wc(3)),
            pickupPoints = listOf(pickup(2)),
            userLocation = null,
            category = PlaceCategory.PICKUP,
        )
        assertEquals(1, out.size)
        assertTrue(out.all { it.isPickup })
    }

    // ---- sorting -----------------------------------------------------------

    @Test
    fun withUserLocation_sortedByAscendingDistance_andDistancePopulated() {
        val user = GeoPoint(50.0, 14.0)
        val near = wc(1, name = "Near", lat = 50.1, lon = 14.0)
        val mid = wc(2, name = "Mid", lat = 50.5, lon = 14.0)
        val far = wc(3, name = "Far", lat = 51.0, lon = 14.0)

        val out = flattenPlaces(
            locations = listOf(far, near, mid),
            pickupPoints = emptyList(),
            userLocation = user,
            category = PlaceCategory.ALL,
        )

        assertEquals(listOf(1, 2, 3), out.map { it.id })
        out.forEach { assertNotNull(it.distanceMeters) }
        // strictly ascending
        val ds = out.map { it.distanceMeters!! }
        assertTrue(ds[0] < ds[1] && ds[1] < ds[2])
    }

    @Test
    fun withoutUserLocation_sortedByLowercaseTitle_andDistanceNull() {
        val out = flattenPlaces(
            locations = listOf(wc(1, name = "Zebra"), wc(2, name = "alpha")),
            pickupPoints = listOf(pickup(3, orgName = "Beta")),
            userLocation = null,
            category = PlaceCategory.ALL,
        )
        assertEquals(listOf("alpha", "Beta", "Zebra"), out.map { it.title })
        out.forEach { assertNull(it.distanceMeters) }
    }

    // ---- navType ----------------------------------------------------------

    @Test
    fun navType_isWcForLocation_andPickupForPickup() {
        val out = flattenPlaces(
            locations = listOf(wc(1)),
            pickupPoints = listOf(pickup(2)),
            userLocation = null,
            category = PlaceCategory.ALL,
        )
        val byPickup = out.associateBy { it.isPickup }
        assertEquals("WC", byPickup[false]!!.navType)
        assertEquals("PICKUP", byPickup[true]!!.navType)
    }

    // ---- title fallbacks -------------------------------------------------

    @Test
    fun blankWcName_fallsBackToBezbariéroveWc() {
        val out = flattenPlaces(
            locations = listOf(wc(1, name = "   ")),
            pickupPoints = emptyList(),
            userLocation = null,
            category = PlaceCategory.TOILET,
        )
        assertEquals("Bezbariérové WC", out.single().title)
    }

    @Test
    fun blankPickupOrgName_fallsBackToVydejniMisto() {
        val out = flattenPlaces(
            locations = emptyList(),
            pickupPoints = listOf(pickup(1, orgName = "")),
            userLocation = null,
            category = PlaceCategory.PICKUP,
        )
        assertEquals("Výdejní místo Euroklíče", out.single().title)
    }

    // ---- pickup subtitle -----------------------------------------------

    @Test
    fun pickupSubtitle_prefersAddress() {
        val out = flattenPlaces(
            locations = emptyList(),
            pickupPoints = listOf(pickup(1, address = "Hlavní 1", district = "Brno-město", kraj = "Jihomoravský")),
            userLocation = null,
            category = PlaceCategory.PICKUP,
        )
        assertEquals("Hlavní 1", out.single().subtitle)
    }

    @Test
    fun pickupSubtitle_joinsDistrictAndKrajWhenNoAddress() {
        val out = flattenPlaces(
            locations = emptyList(),
            pickupPoints = listOf(pickup(1, district = "Brno-město", kraj = "Jihomoravský")),
            userLocation = null,
            category = PlaceCategory.PICKUP,
        )
        assertEquals("Brno-město, Jihomoravský", out.single().subtitle)
    }

    @Test
    fun pickupSubtitle_singlePartWhenOnlyKraj() {
        val out = flattenPlaces(
            locations = emptyList(),
            pickupPoints = listOf(pickup(1, kraj = "Jihomoravský")),
            userLocation = null,
            category = PlaceCategory.PICKUP,
        )
        assertEquals("Jihomoravský", out.single().subtitle)
    }

    @Test
    fun pickupSubtitle_nullWhenNoAddressNoDistrictNoKraj() {
        val out = flattenPlaces(
            locations = emptyList(),
            pickupPoints = listOf(pickup(1)),
            userLocation = null,
            category = PlaceCategory.PICKUP,
        )
        assertNull(out.single().subtitle)
    }

    // ---- passthrough sanity ------------------------------------------------

    @Test
    fun wcItem_carriesStatusAndVotes_pickupDoesNot() {
        val out = flattenPlaces(
            locations = listOf(wc(1, source = "cd", likes = 3, dislikes = 0)),
            pickupPoints = listOf(pickup(2)),
            userLocation = null,
            category = PlaceCategory.ALL,
        )
        val wcItem = out.single { !it.isPickup }
        val ppItem = out.single { it.isPickup }
        assertNotNull(wcItem.status)
        assertEquals(3, wcItem.likes)
        assertNull(ppItem.status)
        assertNull(ppItem.likes)
        assertNull(ppItem.source)
    }
}
