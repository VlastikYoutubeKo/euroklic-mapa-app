package cz.euroklicmapa.data.mapper

import cz.euroklicmapa.data.model.Feature
import cz.euroklicmapa.data.model.Geometry
import cz.euroklicmapa.data.model.PickupPointProperties
import cz.euroklicmapa.data.model.WcProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A7 — the GeoJSON `[lon, lat]` convention is applied in exactly one place: the `toEntity()`
 * mappers. These pin the coordinate order, the `NaN`-on-malformed behaviour, and the
 * field-name pass-through (`opening_hours` → `openingHours`, …). `toFavoriteEntity()` /
 * `FavoriteEntity.to*()` are covered by [FavoriteMapperTest] and not retested here.
 */
class MappersTest {

    private fun wcFeature(
        geometry: Geometry,
        properties: WcProperties,
    ) = Feature(type = "Feature", geometry = geometry, properties = properties)

    private fun pickupFeature(
        geometry: Geometry,
        properties: PickupPointProperties,
    ) = Feature(type = "Feature", geometry = geometry, properties = properties)

    private fun geom(vararg coords: Double) = Geometry(type = "Point", coordinates = coords.toList())

    // ---- [lon, lat] order (the single most important assertion) --------------------

    @Test
    fun wc_coordinates_areLonThenLat_notSwapped() {
        val entity = wcFeature(geom(14.42, 50.08), WcProperties(id = 1)).toEntity()
        assertEquals(14.42, entity.longitude, 0.0)
        assertEquals(50.08, entity.latitude, 0.0)
    }

    @Test
    fun pickup_coordinates_areLonThenLat_notSwapped() {
        val entity = pickupFeature(geom(16.61, 49.19), PickupPointProperties(id = 1)).toEntity()
        assertEquals(16.61, entity.longitude, 0.0)
        assertEquals(49.19, entity.latitude, 0.0)
    }

    // ---- malformed / missing coordinates → NaN -----------------------------------

    @Test
    fun wc_emptyCoordinates_bothNaN() {
        val entity = wcFeature(geom(), WcProperties(id = 1)).toEntity()
        assertTrue(entity.longitude.isNaN())
        assertTrue(entity.latitude.isNaN())
    }

    @Test
    fun wc_singleCoordinate_lonKept_latNaN() {
        val entity = wcFeature(geom(14.42), WcProperties(id = 1)).toEntity()
        assertEquals(14.42, entity.longitude, 0.0)
        assertTrue(entity.latitude.isNaN())
    }

    @Test
    fun pickup_emptyCoordinates_bothNaN() {
        val entity = pickupFeature(geom(), PickupPointProperties(id = 1)).toEntity()
        assertTrue(entity.longitude.isNaN())
        assertTrue(entity.latitude.isNaN())
    }

    @Test
    fun pickup_singleCoordinate_lonKept_latNaN() {
        val entity = pickupFeature(geom(16.61), PickupPointProperties(id = 1)).toEntity()
        assertEquals(16.61, entity.longitude, 0.0)
        assertTrue(entity.latitude.isNaN())
    }

    // ---- WC field pass-through --------------------------------------------------

    @Test
    fun wc_everyFieldMapsToTheRightEntityField() {
        val props = WcProperties(
            id = 638,
            name = "WC Hlavní nádraží",
            source = "cd",
            description = "U pokladen, přízemí",
            note = "Klíč u informací",
            amenity = "toilets",
            photo_url = "https://euroklic.odjezdy.online/uploads/638.jpg",
            likes = 9,
            dislikes = 3,
            last_verified = "2026-08-15 12:34:56",
            web_url = "https://euroklic.odjezdy.online/lokace/638-wc",
            opening_hours = "Po-Ne 04:30 - 23:30",
            wc_opening_hours = "Po-Pá 05:00 - 22:00",
            access = "eurokey",
            wheelchair = "yes",
            accessibility_note = "Bezbariérový vstup z boční strany.\nRampa 1:12.",
            country = "CZ",
            floor_plan_url = "https://www.cd.cz/planek/123",
        )
        val entity = wcFeature(geom(15.5, 50.1), props).toEntity()

        assertEquals(638, entity.id)
        assertEquals("WC Hlavní nádraží", entity.name)
        assertEquals("cd", entity.source)
        assertEquals("U pokladen, přízemí", entity.description)
        assertEquals("Klíč u informací", entity.note)
        assertEquals("https://euroklic.odjezdy.online/uploads/638.jpg", entity.photoUrl)
        assertEquals(9, entity.likes)
        assertEquals(3, entity.dislikes)
        assertEquals("2026-08-15 12:34:56", entity.lastVerified)
        assertEquals("https://euroklic.odjezdy.online/lokace/638-wc", entity.webUrl)
        assertEquals("Po-Ne 04:30 - 23:30", entity.openingHours)
        assertEquals("Po-Pá 05:00 - 22:00", entity.wcOpeningHours)
        assertEquals("eurokey", entity.access)
        assertEquals("yes", entity.wheelchair)
        assertEquals("Bezbariérový vstup z boční strany.\nRampa 1:12.", entity.accessibilityNote)
        assertEquals("CZ", entity.country)
        assertEquals("https://www.cd.cz/planek/123", entity.floorPlanUrl)
        assertEquals(15.5, entity.longitude, 0.0)
        assertEquals(50.1, entity.latitude, 0.0)
        // `amenity` is intentionally not carried onto the entity (free-ish string, not an enum).
    }

    @Test
    fun wc_nullableFields_passNullThrough() {
        // Only `id` given; every nullable-with-default field stays null on the entity.
        val entity = wcFeature(geom(15.0, 50.0), WcProperties(id = 7)).toEntity()

        assertEquals(7, entity.id)
        assertEquals("", entity.name)
        assertEquals("", entity.source)
        assertEquals(0, entity.likes)
        assertEquals(0, entity.dislikes)
        assertNull(entity.description)
        assertNull(entity.note)
        assertNull(entity.photoUrl)
        assertNull(entity.lastVerified)
        assertNull(entity.webUrl)
        assertNull(entity.openingHours)
        assertNull(entity.wcOpeningHours)
        assertNull(entity.access)
        assertNull(entity.wheelchair)
        assertNull(entity.accessibilityNote)
        assertNull(entity.country)
        assertNull(entity.floorPlanUrl)
    }

    // ---- pickup field pass-through --------------------------------------------

    @Test
    fun pickup_everyFieldMapsToTheRightEntityField() {
        val props = PickupPointProperties(
            id = 99,
            kraj = "Jihomoravský",
            district = "Brno-město",
            org_name = "Úřad městské části",
            address = "Dominikánské nám. 1, Brno",
            phone = "542 173 111, 542 173 222",
            email = "podatelna@brno.cz",
            hours = "Po, St 8:00-17:00",
            note = "Výdej v 2. patře",
            precision = "address",
            source_url = "https://example.org/vydej",
        )
        val entity = pickupFeature(geom(16.6, 49.2), props).toEntity()

        assertEquals(99, entity.id)
        assertEquals("Jihomoravský", entity.kraj)
        assertEquals("Brno-město", entity.district)
        assertEquals("Úřad městské části", entity.orgName)
        assertEquals("Dominikánské nám. 1, Brno", entity.address)
        assertEquals("542 173 111, 542 173 222", entity.phone)
        assertEquals("podatelna@brno.cz", entity.email)
        assertEquals("Po, St 8:00-17:00", entity.hours)
        assertEquals("Výdej v 2. patře", entity.note)
        assertEquals("address", entity.precision)
        assertEquals("https://example.org/vydej", entity.sourceUrl)
        assertEquals(16.6, entity.longitude, 0.0)
        assertEquals(49.2, entity.latitude, 0.0)
    }

    @Test
    fun pickup_nullableFields_passNullThrough() {
        val entity = pickupFeature(geom(16.0, 49.0), PickupPointProperties(id = 3)).toEntity()

        assertEquals(3, entity.id)
        assertEquals("", entity.orgName)
        assertNull(entity.kraj)
        assertNull(entity.district)
        assertNull(entity.address)
        assertNull(entity.phone)
        assertNull(entity.email)
        assertNull(entity.hours)
        assertNull(entity.note)
        assertNull(entity.precision)
        assertNull(entity.sourceUrl)
    }
}
