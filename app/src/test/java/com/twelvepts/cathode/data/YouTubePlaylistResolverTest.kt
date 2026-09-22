package com.twelvepts.cathode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubePlaylistResolverTest {
    @Test fun extractsPlaylistIdFromYouTubeAndMusicLinks() {
        assertEquals("PL123abc", YouTubePlaylistResolver.extractPlaylistId("https://www.youtube.com/playlist?list=PL123abc"))
        assertEquals("PLmusic", YouTubePlaylistResolver.extractPlaylistId("https://music.youtube.com/playlist?list=PLmusic&feature=share"))
    }

    @Test fun rejectsVideoWithoutPlaylistParameter() {
        assertNull(YouTubePlaylistResolver.extractPlaylistId("https://youtu.be/abc123"))
    }

    @Test fun parsesArtistAndCleansOfficialVideoSuffix() {
        val track = YouTubePlaylistResolver.parseVideoTitle("twenty one pilots - Chlorine (Official Video)")
        assertEquals("twenty one pilots", track.artist)
        assertEquals("Chlorine", track.title)
    }

    @Test fun fallsBackToTopicChannelAsArtist() {
        val track = YouTubePlaylistResolver.parseVideoTitle("Old Song", "Example Artist - Topic")
        assertEquals("Example Artist", track.artist)
        assertEquals("Old Song", track.title)
    }
}
