package cz.euroklicmapa.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test fun newerPatchMinorMajor() {
        assertTrue(UpdateChecker.isNewer("1.4.1", "1.4.0"))
        assertTrue(UpdateChecker.isNewer("1.5.0", "1.4.9"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0"))
    }

    @Test fun sameOrOlderIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.4.0", "1.4.0"))
        assertFalse(UpdateChecker.isNewer("1.3.9", "1.4.0"))
        assertFalse(UpdateChecker.isNewer("1.4", "1.4.0"))
    }

    @Test fun betaBuildsNeverGetPrompted() {
        assertFalse(UpdateChecker.isNewer("1.4.0", "1.4.0-beta.1"))
        assertFalse(UpdateChecker.isNewer("9.9.9", "1.4.0-beta.3"))
    }

    @Test fun garbageIsIgnored() {
        assertFalse(UpdateChecker.isNewer("", "1.4.0"))
        assertFalse(UpdateChecker.isNewer("abc", "1.4.0"))
        assertFalse(UpdateChecker.isNewer("1.5.0", "unknown"))
    }

    @Test fun leadingVAndShortFormsWork() {
        assertTrue(UpdateChecker.isNewer("v1.5.0", "1.4.0"))
        assertTrue(UpdateChecker.isNewer("1.5", "1.4.9"))
    }
}
