package com.smartisan.music.ui.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyNeteaseLoginPageTest {
    @Test
    fun loginUsesADesktopWindowsChromeUserAgent() {
        assertTrue(NeteaseLoginDesktopUserAgent.contains("Windows NT 10.0"))
        assertTrue(NeteaseLoginDesktopUserAgent.contains("Chrome/"))
        assertFalse(NeteaseLoginDesktopUserAgent.contains(" Android "))
        assertFalse(NeteaseLoginDesktopUserAgent.contains(" Mobile "))
    }

    @Test
    fun topLevelNavigationOnlyAllowsSecure163Domains() {
        assertTrue(isAllowedNeteaseLoginUrl("about:blank"))
        assertTrue(isAllowedNeteaseLoginUrl("https://music.163.com/"))
        assertTrue(isAllowedNeteaseLoginUrl("https://passport.163.com/login"))
        assertFalse(isAllowedNeteaseLoginUrl("http://music.163.com/#/login"))
        assertFalse(isAllowedNeteaseLoginUrl("https://music.163.com:444/login"))
        assertFalse(isAllowedNeteaseLoginUrl("https://163.com.evil.example/login"))
        assertFalse(isAllowedNeteaseLoginUrl("javascript:alert(1)"))
    }

    @Test
    fun autoOpenOnlyRunsOnTheSecureMusicHomePage() {
        assertTrue(shouldAutoOpenNeteaseLogin("https://music.163.com/"))
        assertTrue(shouldAutoOpenNeteaseLogin("https://MUSIC.163.COM/#/discover"))
        assertFalse(shouldAutoOpenNeteaseLogin("http://music.163.com/"))
        assertFalse(shouldAutoOpenNeteaseLogin("https://music.163.com:444/"))
        assertFalse(shouldAutoOpenNeteaseLogin("https://passport.163.com/login"))
        assertFalse(shouldAutoOpenNeteaseLogin("https://music.163.com.evil.example/"))
    }

    @Test
    fun authenticationCookieRequiresAnExactCookieName() {
        assertTrue(containsNeteaseAuthenticationCookie("foo=1; MUSIC_U=secret; bar=2"))
        assertTrue(containsNeteaseAuthenticationCookie("MUSIC_A=anonymous"))
        assertFalse(containsNeteaseAuthenticationCookie("NOT_MUSIC_U=secret"))
        assertFalse(containsNeteaseAuthenticationCookie("MUSIC_U_NOT=secret"))
        assertFalse(containsNeteaseAuthenticationCookie(null))
    }

    @Test
    fun qrJavascriptResultOnlyAcceptsBoundedPngDataUrls() {
        assertEquals(
            "QUJD",
            extractNeteaseQrBase64("\"data:image/png;base64,QUJD\""),
        )
        assertNull(extractNeteaseQrBase64("null"))
        assertNull(extractNeteaseQrBase64("\"data:image/jpeg;base64,QUJD\""))
        assertNull(extractNeteaseQrBase64("\"not a data URL\""))
        assertNull(extractNeteaseQrBase64("broken JSON"))
    }
}
