package cz.euroklicmapa.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Conservative `opening_hours` parser (ČD-station free text). Mirrors the CLAUDE.md / TODO A6
 * contract: recognise only the handful of Czech formats we can trust, return `null` for
 * anything ambiguous — a missing badge beats a wrong "Zavřeno".
 *
 * All reference dates are in January 2024: the 1st is a Monday, so the 3rd is Wednesday,
 * the 6th Saturday, the 7th Sunday.
 */
class OpeningHoursTest {

    private fun at(day: Int, hour: Int, minute: Int): Calendar =
        GregorianCalendar(2024, Calendar.JANUARY, day, hour, minute)

    // ---- always open ---------------------------------------------------------

    @Test
    fun nonstop_variants_areAlwaysOpen() {
        for (raw in listOf(
            "nonstop", "NONSTOP", "nepřetržitě", "24 hodin", "24 hodin denně",
            "0–24", "00:00–24:00", "denně 0-24", "24/7",
        )) {
            val oh = parseOpeningHours(raw)
            assertNotNull("expected a model for \"$raw\"", oh)
            assertEquals("\"$raw\" @ Mon 03:00", OpenState.OPEN, oh!!.statusAt(at(1, 3, 0)))
            assertEquals("\"$raw\" @ Sat 23:59", OpenState.OPEN, oh.statusAt(at(6, 23, 59)))
        }
    }

    // ---- a normal weekday range, open vs closed by clock -------------------

    @Test
    fun weekdayRange_openInsideClosedOutside() {
        val oh = parseOpeningHours("Po–Pá 6:00–22:00")!!
        assertEquals(OpenState.OPEN, oh.statusAt(at(1, 10, 0)))   // Mon 10:00
        assertEquals(OpenState.CLOSED, oh.statusAt(at(1, 23, 0)))  // Mon 23:00
        assertEquals(OpenState.CLOSED, oh.statusAt(at(1, 5, 30)))  // Mon 05:30, before open
        assertEquals(OpenState.CLOSED, oh.statusAt(at(6, 10, 0)))  // Sat 10:00, not a listed day
    }

    @Test
    fun weekdayRange_halfHourAndAsciiDash() {
        val oh = parseOpeningHours("Po-Pá 5:30-23:00")!!
        assertEquals(OpenState.OPEN, oh.statusAt(at(2, 5, 45)))    // Tue 05:45
        assertEquals(OpenState.CLOSED, oh.statusAt(at(2, 5, 15)))  // Tue 05:15
        assertEquals(OpenState.OPEN, oh.statusAt(at(5, 22, 59)))   // Fri 22:59
    }

    @Test
    fun dotTimeSeparatorAndBareHours() {
        val oh = parseOpeningHours("Po–Pá 6.00–22.00")!!
        assertEquals(OpenState.OPEN, oh.statusAt(at(3, 12, 0)))    // Wed noon
        assertEquals(OpenState.CLOSED, oh.statusAt(at(3, 22, 30)))
    }

    @Test
    fun caseAndDiacriticsInsensitive() {
        val a = parseOpeningHours("PO–PÁ 6–22")!!
        val b = parseOpeningHours("po-pa 6-22")!!
        assertEquals(OpenState.OPEN, a.statusAt(at(1, 8, 0)))
        assertEquals(OpenState.OPEN, b.statusAt(at(1, 8, 0)))
        assertEquals(OpenState.CLOSED, a.statusAt(at(1, 23, 0)))
        assertEquals(OpenState.CLOSED, b.statusAt(at(1, 23, 0)))
    }

    // ---- past-midnight range ---------------------------------------------------

    @Test
    fun pastMidnightRange_spillsIntoNextDay() {
        val oh = parseOpeningHours("Po–Ne 4:00–00:30")!!
        assertEquals(OpenState.OPEN, oh.statusAt(at(1, 4, 30)))    // Mon 04:30, inside
        assertEquals(OpenState.OPEN, oh.statusAt(at(1, 23, 0)))    // Mon 23:00, inside evening
        assertEquals(OpenState.OPEN, oh.statusAt(at(7, 0, 15)))    // Sun 00:15, spill from Sat
        assertEquals(OpenState.CLOSED, oh.statusAt(at(1, 2, 0)))   // Mon 02:00, after spill ended
        assertEquals(OpenState.CLOSED, oh.statusAt(at(1, 3, 30)))  // Mon 03:30, before open
    }

    // ---- multi-clause weekday / weekend --------------------------------------

    @Test
    fun multiClause_weekdayAndWeekend() {
        val oh = parseOpeningHours("Po–Pá 6:00–20:00; So–Ne 8:00–18:00")!!
        assertEquals(OpenState.OPEN, oh.statusAt(at(1, 19, 0)))    // Mon 19:00, weekday clause
        assertEquals(OpenState.CLOSED, oh.statusAt(at(1, 21, 0)))  // Mon 21:00
        assertEquals(OpenState.OPEN, oh.statusAt(at(6, 9, 0)))     // Sat 09:00, weekend clause
        assertEquals(OpenState.CLOSED, oh.statusAt(at(6, 7, 0)))   // Sat 07:00, before weekend open
        assertEquals(OpenState.CLOSED, oh.statusAt(at(6, 19, 30))) // Sat 19:30, after weekend close
    }

    @Test
    fun enumeratedDays_withCommas() {
        val oh = parseOpeningHours("Po, St, Pá 8–16")!!
        assertEquals(OpenState.OPEN, oh.statusAt(at(3, 9, 0)))     // Wed 09:00, listed
        assertEquals(OpenState.CLOSED, oh.statusAt(at(2, 9, 0)))   // Tue 09:00, not listed
        assertEquals(OpenState.CLOSED, oh.statusAt(at(3, 17, 0)))  // Wed 17:00, after close
    }

    @Test
    fun denne_prefixMeansEveryDay() {
        val oh = parseOpeningHours("denně 7:00–19:00")!!
        assertEquals(OpenState.OPEN, oh.statusAt(at(7, 12, 0)))    // Sunday still counts
        assertEquals(OpenState.CLOSED, oh.statusAt(at(7, 20, 0)))
    }

    @Test
    fun trailingHodWordIsIgnored() {
        val oh = parseOpeningHours("Po-Pá 6-22 hod")!!
        assertEquals(OpenState.OPEN, oh.statusAt(at(1, 10, 0)))
    }

    // ---- deliberately UNKNOWN / null ---------------------------------------

    @Test
    fun unparseableStrings_returnNull() {
        for (raw in listOf(
            null, "", "   ",
            "zavřeno",
            "dle jízdního řádu",
            "v provozu dle vlaků",
            "otevřeno v létě",
            "Po-Pá",                       // days, no time
            "8:00",                        // single time, no range
            "Po-Pá od 6:00 do 22:00",      // "od/do" phrasing – not trusted
            "Po–Pá 6:00–22:00 kromě svátků", // leftover words
            "Po–Pá 6:00–22:00, jinak dle dohody",
        )) {
            assertNull("expected null for \"$raw\"", parseOpeningHours(raw))
        }
    }

    @Test
    fun malformedTimes_returnNull() {
        assertNull(parseOpeningHours("Po-Pá 25:00-26:00"))
        assertNull(parseOpeningHours("Po-Pá 6:70-22:00"))
    }

    // ---- real feed formats (2026-09-08 backend scrape fix) ----------------------------------

    @Test
    fun realFeed_singleDayRange_openClosedByClock() {
        val oh = parseOpeningHours("Po-Ne 04:30-23:30")!!
        assertEquals(OpenState.OPEN, oh.statusAt(at(1, 12, 0)))    // Mon 12:00
        assertEquals(OpenState.OPEN, oh.statusAt(at(7, 5, 0)))     // Sun 05:00
        assertEquals(OpenState.CLOSED, oh.statusAt(at(1, 2, 0)))   // Mon 02:00, before open
        assertEquals(OpenState.CLOSED, oh.statusAt(at(1, 23, 45))) // Mon 23:45, after close
    }

    @Test
    fun realFeed_spaceSeparatedWeekdayWeekendClauses() {
        // Two clauses, separated only by whitespace before the next day token.
        val oh = parseOpeningHours("Po-Pá 03:50-21:35 So-Ne 04:50-21:35")!!
        assertEquals(OpenState.OPEN, oh.statusAt(at(1, 4, 0)))     // Mon 04:00, weekday clause
        assertEquals(OpenState.CLOSED, oh.statusAt(at(6, 4, 0)))   // Sat 04:00, weekend opens later
        assertEquals(OpenState.OPEN, oh.statusAt(at(6, 5, 0)))     // Sat 05:00, weekend clause
        assertEquals(OpenState.OPEN, oh.statusAt(at(7, 21, 30)))   // Sun 21:30
        assertEquals(OpenState.CLOSED, oh.statusAt(at(5, 22, 0)))  // Fri 22:00, after weekday close
    }

    @Test
    fun realFeed_commaDayListsThreeClauses() {
        val oh = parseOpeningHours(
            "Po,St,Pá 03:50-19:30 Čt 03:50-21:00 So,Ne 04:50-21:00",
        )!!
        assertEquals(OpenState.OPEN, oh.statusAt(at(3, 10, 0)))    // Wed 10:00, in Po,St,Pá clause
        assertEquals(OpenState.CLOSED, oh.statusAt(at(3, 19, 45))) // Wed 19:45, after that clause
        assertEquals(OpenState.OPEN, oh.statusAt(at(4, 20, 30)))   // Thu 20:30, Čt clause runs later
        assertEquals(OpenState.CLOSED, oh.statusAt(at(6, 4, 0)))   // Sat 04:00, before weekend open
        assertEquals(OpenState.OPEN, oh.statusAt(at(7, 10, 0)))    // Sun 10:00, weekend clause
    }

    @Test
    fun realFeed_nightGapTwoIntervalsEveryDay() {
        val oh = parseOpeningHours("0:00-1:30 2:30-24:00")!!
        assertEquals(OpenState.OPEN, oh.statusAt(at(1, 0, 30)))    // Mon 00:30, first interval
        assertEquals(OpenState.CLOSED, oh.statusAt(at(1, 2, 0)))   // Mon 02:00, in the gap
        assertEquals(OpenState.OPEN, oh.statusAt(at(1, 3, 0)))     // Mon 03:00, second interval
        assertEquals(OpenState.OPEN, oh.statusAt(at(4, 23, 0)))    // Thu 23:00, still second interval
    }

    @Test
    fun realFeed_garbledDayTokenInListIsSkipped_notAWholeFailure() {
        // "Xx" is not a day — it must be dropped, leaving a usable Po / Pá + weekend result.
        val oh = parseOpeningHours("Po,Xx,Pá 8:00-16:00 So,Ne 9:00-15:00")
        assertNotNull("garbled token must not sink the parse", oh)
        assertEquals(OpenState.OPEN, oh!!.statusAt(at(1, 10, 0)))  // Mon 10:00, Po survived
        assertEquals(OpenState.CLOSED, oh.statusAt(at(3, 10, 0)))  // Wed 10:00, not listed
        assertEquals(OpenState.OPEN, oh.statusAt(at(6, 10, 0)))    // Sat 10:00, weekend clause
    }

    @Test
    fun realFeed_daySpecThatIsOnlyProse_staysUnknown() {
        // Nothing usable where a day-spec was expected -> null -> caller treats as UNKNOWN.
        val parsed = parseOpeningHours("xyz 8:00-16:00")
        assertNull(parsed)
        val state = parsed?.statusAt(at(1, 12, 0)) ?: OpenState.UNKNOWN
        assertEquals(OpenState.UNKNOWN, state)
    }

    @Test
    fun nullParse_callSiteTreatsAsUnknown() {
        // The screen does `parseOpeningHours(raw)?.statusAt(now) ?: OpenState.UNKNOWN`.
        val parsed = parseOpeningHours("dle situace")
        val state = parsed?.statusAt(at(1, 12, 0)) ?: OpenState.UNKNOWN
        assertEquals(OpenState.UNKNOWN, state)
    }
}
