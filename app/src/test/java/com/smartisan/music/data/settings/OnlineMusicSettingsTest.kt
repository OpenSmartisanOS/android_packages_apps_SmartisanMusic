package com.smartisan.music.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineMusicSettingsTest {
    @Test
    fun unknownOrMissingPreferenceDefaultsToLocalMode() {
        assertEquals(MusicSourceMode.Local, MusicSourceMode.fromPreference(null))
        assertEquals(MusicSourceMode.Local, MusicSourceMode.fromPreference("unexpected"))
        assertFalse(OnlineMusicSettings().neteaseEnabled)
    }

    @Test
    fun neteasePreferenceEnablesOnlineMode() {
        val mode = MusicSourceMode.fromPreference("netease")

        assertEquals(MusicSourceMode.Netease, mode)
        assertTrue(OnlineMusicSettings(mode).neteaseEnabled)
    }
}
