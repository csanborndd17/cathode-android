package com.twelvepts.cathode.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StableTrackKeyTest {
    @Test fun sameFileIdentityProducesSameKey() {
        assertEquals(
            stableTrackKey("Music/Album/", "Track.FLAC", 1234),
            stableTrackKey("music/album/", "track.flac", 1234),
        )
    }

    @Test fun differentFilesProduceDifferentKeys() {
        assertNotEquals(
            stableTrackKey("Music/A/", "track.flac", 1234),
            stableTrackKey("Music/B/", "track.flac", 1234),
        )
    }
}
