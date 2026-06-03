package com.botnick.rokidhermes

import com.botnick.rokidhermes.data.HermesSettings
import com.botnick.rokidhermes.loader.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for the update-decision and config-validation logic. */
class UpdateLogicTest {

    @Test fun newerVersionIsDetected() {
        assertTrue(UpdateChecker.isNewer("0.1.0", "0.2.0"))
        assertTrue(UpdateChecker.isNewer("0.1.0", "1.0.0"))
        assertTrue(UpdateChecker.isNewer("0.1.0", "0.1.1"))
    }

    @Test fun sameOrOlderIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("0.1.0", "0.1.0"))
        assertFalse(UpdateChecker.isNewer("0.2.0", "0.1.9"))
        assertFalse(UpdateChecker.isNewer("1.0.0", "0.9.9"))
    }

    @Test fun handlesVPrefixAndUnevenSegments() {
        assertTrue(UpdateChecker.isNewer("v0.1.0", "v0.2"))
        assertFalse(UpdateChecker.isNewer("0.1", "0.1.0"))
        assertTrue(UpdateChecker.isNewer("0.1", "0.1.1"))
    }

    @Test fun nonNumericSegmentsDoNotCrash() {
        // Garbage segments coerce to 0 rather than throwing.
        assertFalse(UpdateChecker.isNewer("abc", "x.y.z"))
        assertTrue(UpdateChecker.isNewer("0", "1.x"))
    }

    @Test fun isConfiguredRequiresUrlAndKey() {
        assertTrue(HermesSettings("http://h:8642/v1", "key", "m").isConfigured)
        assertFalse(HermesSettings("", "key", "m").isConfigured)
        assertFalse(HermesSettings("http://h:8642/v1", "", "m").isConfigured)
        assertFalse(HermesSettings("  ", "  ", "m").isConfigured)
    }
}
