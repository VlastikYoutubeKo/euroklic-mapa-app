package cz.euroklicmapa.data.repository

import cz.euroklicmapa.data.local.EuroklicDao
import cz.euroklicmapa.data.local.WcLocationEntity
import cz.euroklicmapa.data.mapper.toEntity
import cz.euroklicmapa.data.model.Feature
import cz.euroklicmapa.data.model.FeatureCollection
import cz.euroklicmapa.data.model.Geometry
import cz.euroklicmapa.data.model.WcProperties
import cz.euroklicmapa.data.remote.EuroklicApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy

class FetchLocationTest {
    @Test
    fun fetchingDistantWcAddsOnlyThatRowAndKeepsOtherRowsAndFreshness() = runTest {
        val fixture = Fixture { feed(feature(42, "Vzdálené WC", lon = -3.7, lat = 40.4)) }
        val original = fixture.rows.getValue(1)

        assertTrue(fixture.repository.fetchLocation(42))

        assertEquals(setOf(1, 42), fixture.rows.keys)
        assertEquals(original, fixture.rows[1])
        assertEquals("Vzdálené WC", fixture.rows.getValue(42).name)
        assertEquals(40.4, fixture.rows.getValue(42).latitude, 0.0)
        assertEquals(-3.7, fixture.rows.getValue(42).longitude, 0.0)
        assertEquals(listOf(42), fixture.insertedIds)
        assertEquals(123456L, fixture.lastSync)
    }

    @Test
    fun fetchingCachedWcReplacesOnlyItsRow() = runTest {
        val fixture = Fixture { feed(feature(42, "Aktualizované WC")) }
        fixture.rows[42] = feature(42, "Původní WC").toEntity()
        val original = fixture.rows.getValue(1)

        assertTrue(fixture.repository.fetchLocation(42))

        assertEquals(setOf(1, 42), fixture.rows.keys)
        assertEquals(original, fixture.rows[1])
        assertEquals("Aktualizované WC", fixture.rows.getValue(42).name)
        assertEquals(listOf(42), fixture.insertedIds)
        assertEquals(123456L, fixture.lastSync)
    }

    @Test
    fun emptyResponseLeavesCacheUntouched() = runTest {
        verifyFailure { feed() }
    }

    @Test
    fun responseForAnotherIdLeavesCacheUntouched() = runTest {
        verifyFailure { feed(feature(99, "Jiné WC")) }
    }

    @Test
    fun malformedCoordinatesLeaveCacheUntouched() = runTest {
        verifyFailure { feed(feature(42, "WC").copy(geometry = Geometry("Point", emptyList()))) }
    }

    @Test
    fun offlineFailureLeavesCacheUntouched() = runTest {
        verifyFailure { throw IOException("Offline") }
    }

    private suspend fun verifyFailure(response: () -> FeatureCollection<WcProperties>) {
        val fixture = Fixture(response)
        val original = fixture.rows.toMap()
        assertFalse(fixture.repository.fetchLocation(42))
        assertEquals(original, fixture.rows)
        assertTrue(fixture.insertedIds.isEmpty())
        assertEquals(123456L, fixture.lastSync)
    }

    private class Fixture(response: () -> FeatureCollection<WcProperties>) {
        val rows = mutableMapOf(1 to feature(1, "Místní WC").toEntity())
        val insertedIds = mutableListOf<Int>()
        var lastSync = 123456L
        private val api = Proxy.newProxyInstance(
            EuroklicApi::class.java.classLoader, arrayOf(EuroklicApi::class.java),
        ) { _, method, args ->
            check(method.name == "getLocations") { "Unexpected API call: ${method.name}" }
            assertNull(args!![0]) // No near/radius scoping for a lookup by id.
            assertNull(args[1])
            assertEquals(42, args[2])
            response()
        } as EuroklicApi
        private val dao = Proxy.newProxyInstance(
            EuroklicDao::class.java.classLoader, arrayOf(EuroklicDao::class.java),
        ) { _, method, args ->
            when (method.name) {
                "insertLocations" -> {
                    @Suppress("UNCHECKED_CAST")
                    val inserted = args!![0] as List<WcLocationEntity>
                    assertEquals(1, inserted.size)
                    inserted.forEach { rows[it.id] = it; insertedIds += it.id }
                    Unit
                }
                "clearLocations" -> {
                    rows.clear()
                    error("Single-place lookup must not clear the feed")
                }
                "updateSyncMetadata" -> {
                    lastSync = -1L
                    error("Single-place lookup must not stamp feed freshness")
                }
                else -> error("Unexpected DAO call: ${method.name}")
            }
        } as EuroklicDao
        val repository = EuroklicRepositoryImpl(api, dao) { error("Single-place lookup must not read user location") }
    }

    companion object {
        private fun feature(id: Int, name: String, lon: Double = 14.4, lat: Double = 50.1) = Feature(
            type = "Feature",
            geometry = Geometry("Point", listOf(lon, lat)),
            properties = WcProperties(id = id, name = name, source = "osm"),
        )

        private fun feed(vararg features: Feature<WcProperties>) = FeatureCollection("FeatureCollection", features.toList())
    }
}
