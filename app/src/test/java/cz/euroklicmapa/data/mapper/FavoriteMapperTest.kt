package cz.euroklicmapa.data.mapper

import cz.euroklicmapa.data.local.FavoriteEntity
import cz.euroklicmapa.data.local.PickupPointEntity
import cz.euroklicmapa.data.local.WcLocationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A11b — Favourites are a full detail snapshot (DB v8). A favourited place must open with the
 * complete `DetailScreen` body even when its live Room row is gone. These tests pin the
 * round-trip `entity -> FavoriteEntity (as FavoritesRepository.add builds it) -> entity` for
 * every field `DetailScreen.WcBody` / `PickupBody` actually renders.
 */
class FavoriteMapperTest {

    private val wc = WcLocationEntity(
        id = 42,
        name = "WC Hlavní nádraží",
        source = "cd",
        description = "U pokladen, přízemí",
        note = "Klíč u informací",
        photoUrl = "https://euroklic.odjezdy.online/uploads/42.jpg",
        likes = 7,
        dislikes = 2,
        lastVerified = "2026-08-01 10:00:00",
        webUrl = "https://euroklic.odjezdy.online/lokace/42-wc",
        openingHours = "Po–Pá 6:00–22:00",
        access = "eurokey",
        wheelchair = "yes",
        accessibilityNote = "Bezbariérový vstup z boční strany.\nRampa 1:12.",
        country = "CZ",
        floorPlanUrl = "https://www.cd.cz/planek/123",
        longitude = 15.5,
        latitude = 50.1,
    )

    private val pp = PickupPointEntity(
        id = 99,
        kraj = "Jihomoravský",
        district = "Brno-město",
        orgName = "Úřad městské části",
        address = "Dominikánské nám. 1, Brno",
        phone = "542 173 111, 542 173 222",
        email = "podatelna@brno.cz",
        hours = "Po, St 8:00–17:00",
        note = "Výdej v 2. patře",
        precision = "address",
        sourceUrl = "https://example.org/vydej",
        longitude = 16.6,
        latitude = 49.2,
    )

    // ---- WC round-trip -------------------------------------------------------

    @Test
    fun wc_roundTrip_preservesEveryFieldWcBodyRenders() {
        val restored = wc.toFavoriteEntity().toWcLocationEntity()

        assertEquals(wc.id, restored.id)
        assertEquals(wc.name, restored.name)
        assertEquals(wc.source, restored.source)
        assertEquals(wc.description, restored.description)
        assertEquals(wc.note, restored.note)
        assertEquals(wc.photoUrl, restored.photoUrl)
        assertEquals(wc.likes, restored.likes)
        assertEquals(wc.dislikes, restored.dislikes)
        assertEquals(wc.lastVerified, restored.lastVerified)
        assertEquals(wc.webUrl, restored.webUrl)
        assertEquals(wc.openingHours, restored.openingHours)
        assertEquals(wc.access, restored.access)
        assertEquals(wc.wheelchair, restored.wheelchair)
        assertEquals(wc.accessibilityNote, restored.accessibilityNote)
        assertEquals(wc.country, restored.country)
        assertEquals(wc.floorPlanUrl, restored.floorPlanUrl)
        assertEquals(wc.longitude, restored.longitude, 0.0)
        assertEquals(wc.latitude, restored.latitude, 0.0)
    }

    @Test
    fun wc_toFavoriteEntity_setsSnapshotShape() {
        val fav = wc.toFavoriteEntity()
        assertEquals(42, fav.placeId)
        assertEquals(false, fav.isPickup)
        assertEquals("WC Hlavní nádraží", fav.title)
        assertEquals("cd", fav.source)
    }

    @Test
    fun wc_nullSnapshotStrings_roundTripToNull() {
        val bare = wc.copy(
            description = null, note = null, photoUrl = null, webUrl = null,
            openingHours = null, access = null, wheelchair = null,
            accessibilityNote = null, country = null, floorPlanUrl = null,
            lastVerified = null,
        )
        val restored = bare.toFavoriteEntity().toWcLocationEntity()
        assertNull(restored.description)
        assertNull(restored.note)
        assertNull(restored.photoUrl)
        assertNull(restored.webUrl)
        assertNull(restored.openingHours)
        assertNull(restored.access)
        assertNull(restored.wheelchair)
        assertNull(restored.accessibilityNote)
        assertNull(restored.country)
        assertNull(restored.floorPlanUrl)
        assertNull(restored.lastVerified)
    }

    @Test
    fun favoriteWithNullSource_becomesEmptyStringOnWcEntity() {
        // FavoriteEntity.source is nullable; WcLocationEntity.source is not.
        val fav = FavoriteEntity(
            placeId = 1, isPickup = false, title = "X", source = null,
            lastVerified = null, likes = 0, dislikes = 0,
            longitude = 1.0, latitude = 2.0, savedAt = 0L,
        )
        assertEquals("", fav.toWcLocationEntity().source)
    }

    // ---- pickup round-trip -------------------------------------------------------

    @Test
    fun pickup_roundTrip_preservesEveryFieldPickupBodyRenders() {
        val restored = pp.toFavoriteEntity().toPickupPointEntity()

        assertEquals(pp.id, restored.id)
        assertEquals(pp.orgName, restored.orgName)
        assertEquals(pp.kraj, restored.kraj)
        assertEquals(pp.district, restored.district)
        assertEquals(pp.address, restored.address)
        assertEquals(pp.phone, restored.phone)
        assertEquals(pp.email, restored.email)
        assertEquals(pp.hours, restored.hours)
        assertEquals(pp.note, restored.note)
        assertEquals(pp.precision, restored.precision)
        assertEquals(pp.sourceUrl, restored.sourceUrl)
        assertEquals(pp.longitude, restored.longitude, 0.0)
        assertEquals(pp.latitude, restored.latitude, 0.0)
    }

    @Test
    fun pickup_toFavoriteEntity_setsSnapshotShape() {
        val fav = pp.toFavoriteEntity()
        assertEquals(99, fav.placeId)
        assertEquals(true, fav.isPickup)
        assertEquals("Úřad městské části", fav.title)
        assertNull(fav.source)
        assertEquals(0, fav.likes)
        assertEquals(0, fav.dislikes)
    }

    @Test
    fun pickup_nullableFields_roundTripToNull() {
        val bare = pp.copy(
            kraj = null, district = null, address = null, phone = null,
            email = null, hours = null, note = null, precision = null, sourceUrl = null,
        )
        val restored = bare.toFavoriteEntity().toPickupPointEntity()
        assertNull(restored.kraj)
        assertNull(restored.district)
        assertNull(restored.address)
        assertNull(restored.phone)
        assertNull(restored.email)
        assertNull(restored.hours)
        assertNull(restored.note)
        assertNull(restored.precision)
        assertNull(restored.sourceUrl)
    }
}
