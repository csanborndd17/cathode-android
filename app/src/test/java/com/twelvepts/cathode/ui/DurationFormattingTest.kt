package com.twelvepts.cathode.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormattingTest {
    @Test fun formatsZero() = assertEquals("0:00", formatDuration(0))
    @Test fun formatsMinutesAndSeconds() = assertEquals("3:07", formatDuration(187_000))
    @Test fun clampsNegativeValues() = assertEquals("0:00", formatDuration(-4_000))
    @Test fun preservesLongDurations() = assertEquals("62:03", formatDuration(3_723_000))
}
