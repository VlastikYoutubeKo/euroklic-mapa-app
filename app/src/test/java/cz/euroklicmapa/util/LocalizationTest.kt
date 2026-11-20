package cz.euroklicmapa.util

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class LocalizationTest {
    @get:Rule
    val locale = CzechLocaleRule(Locale.ENGLISH)

    @Test
    fun englishLooksUpTheTranslation() {
        assertEquals("Nearest toilet", t("Nejbližší WC"))
        assertEquals("Saved: 3", t("Uloženo: %s", 3))
    }

    @Test
    fun missingKeyFallsBackToTheCzechText() {
        assertEquals("Tenhle text nemá překlad", t("Tenhle text nemá překlad"))
    }

    @Test
    fun czechKeepsTheLiteral() {
        Locale.setDefault(Locale("cs", "CZ"))
        assertEquals("Nejbližší WC", t("Nejbližší WC"))
        assertEquals("Uloženo: 3", t("Uloženo: %s", 3))
    }
}
