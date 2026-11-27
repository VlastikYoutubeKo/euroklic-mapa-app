package cz.euroklicmapa.odjezdy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyParserTest {

    @Test fun parsesRideAndOldStringWalk() {
        val body = """
            {"apiVersion":2,"journeys":[{"departure":"13:43","arrival":"13:49","transfers":0,"totalSeconds":1358,
            "walkToStart":{"meters":386,"seconds":322},"walkFromEnd":{"meters":483,"seconds":402},
            "legs":[{"kind":"ride","line":"582504","lineLabel":"504","mode":"bus","headsign":"Louny",
            "from":{"name":"A","lat":50.1,"lng":14.4},"to":{"name":"B","lat":50.2,"lng":14.5},
            "departure":"13:43","arrival":"13:49","stopCount":4},
            {"kind":"walk","from":"Praha","to":"Praha hl. n.","seconds":274}]}]}
        """.trimIndent()
        val result = parseJourneyResponse(body)
        assertNull(result.errorCode)
        val journey = result.journeys.single()
        assertEquals(2, journey.legs.size)
        val ride = journey.legs[0] as JourneyLeg.Ride
        assertEquals("504", ride.lineLabel)
        assertEquals("bus", ride.mode)
        assertNull(ride.wheelchairAccessible)
        assertEquals("Praha", (journey.legs[1] as JourneyLeg.Walk).from.name)
        assertEquals(386.0, journey.walkToStart.meters, 0.01)
    }

    @Test fun lineLabelFallsBackToLine() {
        val body = """{"journeys":[{"departure":"8:00","arrival":"8:10","legs":[{"kind":"ride","line":"R 607","mode":"train",
            "from":{"name":"A"},"to":{"name":"B"},"departure":"8:00","arrival":"8:10"}]}]}"""
        val ride = parseJourneyResponse(body).journeys.single().legs.single() as JourneyLeg.Ride
        assertEquals("R 607", ride.lineLabel)
    }

    @Test fun errorAsStringAndAsObject() {
        val old = parseJourneyResponse("""{"error":"NO_STOPS_NEARBY","side":"to","journeys":[]}""")
        assertEquals("NO_STOPS_NEARBY", old.errorCode)
        assertEquals("to", old.side)
        val new = parseJourneyResponse("""{"error":{"code":"SCHEDULE_NOT_AVAILABLE","message":"x"},"journeys":[]}""")
        assertEquals("SCHEDULE_NOT_AVAILABLE", new.errorCode)
        assertTrue(new.journeys.isEmpty())
    }

    @Test fun clockHandlesMidnight() {
        assertEquals(24 * 60 + 3, JourneyClock.minutes("24:03"))
        // An older server sent 00:03 after midnight: arrival before departure means the next day.
        assertEquals(23 * 60 + 56 to 24 * 60 + 3, JourneyClock.span("23:56", "00:03"))
        assertEquals("0:03 +1", JourneyClock.format(24 * 60 + 3))
        assertEquals("13:05", JourneyClock.format(13 * 60 + 5))
    }

    @Test fun mapyWalkingUrl() {
        val url = mapyWalkingUrl(50.0, 14.0, 50.1, 14.1)
        assertTrue(url.startsWith("https://mapy.com/fnc/v1/route?"))
        assertTrue(url.contains("routeType=foot_fast"))
        assertTrue(url.contains("start=14.000000,50.000000"))
        assertTrue(url.contains("end=14.100000,50.100000"))
    }
}
