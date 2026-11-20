package cz.euroklicmapa.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangelogTest {
    private val sample = """
        # Changelog

        Úvod, který se ignoruje.

        ## 1.2.1 — 2026-11-20
        - [Android] Jen pro Android
        - [iOS] Jen pro iOS
        - Pro obě

        ## 1.1.0 — 2026-11-19
        - [iOS] Jen iOS verze
    """.trimIndent()

    @Test
    fun parsesVersionsDatesAndFiltersByPlatform() {
        val entries = Changelog.parse(sample)
        assertEquals(listOf("1.2.1"), entries.map { it.version })
        assertEquals("2026-11-20", entries[0].date)
        // the [iOS] bullet is hidden, the [Android] tag is stripped, untagged stays
        assertEquals(listOf("Jen pro Android", "Pro obě"), entries[0].items)
    }

    @Test
    fun normalizedTreatsTrailingZerosAsSameRelease() {
        assertEquals(Changelog.normalized("1.0"), Changelog.normalized("1.0.0"))
        assertEquals("1.2.1", Changelog.entryFor("1.2.1", Changelog.parse(sample))?.version)
        assertNull(Changelog.entryFor("2.0", Changelog.parse(sample)))
    }
}
