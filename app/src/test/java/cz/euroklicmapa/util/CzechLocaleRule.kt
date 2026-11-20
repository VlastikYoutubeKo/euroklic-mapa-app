package cz.euroklicmapa.util

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Locale

/**
 * `t()` follows the JVM locale, so tests that assert the Czech texts pin it (CI runs with an English
 * default, a Czech developer machine would pass by accident).
 */
class CzechLocaleRule(private val locale: Locale = Locale("cs", "CZ")) : TestWatcher() {
    private var previous: Locale = Locale.getDefault()

    override fun starting(description: Description?) {
        previous = Locale.getDefault()
        Locale.setDefault(locale)
    }

    override fun finished(description: Description?) {
        Locale.setDefault(previous)
    }
}
