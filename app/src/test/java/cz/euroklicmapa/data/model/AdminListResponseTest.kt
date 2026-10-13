package cz.euroklicmapa.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AdminListResponseTest {
    // Matches the Retrofit converter configuration in EuroklicApplication.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesReportsWithNullsAndUnknownBackendFields() {
        val response = json.decodeFromString<AdminListResponse>("""
            {
              "report_count": 2,
              "location_reports": [
                {
                  "id": 42, "location_id": 123, "location_name": "Nádraží",
                  "location_lat": 50.1, "location_lon": 14.4,
                  "reason": "key_broken", "note": null, "author_name": null,
                  "discord_user_id": null, "submitted_via": "app", "status": "pending",
                  "created_at": "2026-10-13 10:00:00", "resolved_at": null, "resolved_by": null,
                  "posted_to_discord": 1, "discord_message_id": "987654321"
                },
                {
                  "id": 41, "location_id": 124, "reason": "future_reason",
                  "location_lat": null, "location_lon": null,
                  "note": "Kontrola", "author_name": "Tester", "submitted_via": "web"
                }
              ],
              "future_counter": 99
            }
        """.trimIndent())

        assertEquals(2, response.report_count)
        assertEquals(listOf(42, 41), response.location_reports.map { it.id })
        val first = response.location_reports.first()
        assertEquals(123, first.location_id)
        assertEquals("Nádraží", first.location_name)
        assertEquals(50.1, first.location_lat!!, 0.00001)
        assertEquals(14.4, first.location_lon!!, 0.00001)
        assertEquals("key_broken", first.reason)
        assertNull(first.note)
        assertNull(first.author_name)
        assertNull(first.discord_user_id)
        assertNull(first.resolved_at)
        assertNull(first.resolved_by)
        assertEquals("pending", first.status)
        assertEquals("app", first.submitted_via)
        assertEquals("2026-10-13 10:00:00", first.created_at)
        val second = response.location_reports.last()
        assertEquals("future_reason", second.reason)
        assertEquals("Kontrola", second.note)
        assertEquals("Tester", second.author_name)
        assertEquals("web", second.submitted_via)
        assertNull(second.location_name)
        assertNull(second.location_lat)
        assertNull(second.location_lon)
        assertNull(second.created_at)
        assertNull(second.status)
    }

    @Test
    fun olderResponseWithoutReportsStillParses() {
        val response = json.decodeFromString<AdminListResponse>("""{"count":0,"locations":[]}""")
        assertEquals(0, response.report_count)
        assertTrue(response.location_reports.isEmpty())
    }
}
