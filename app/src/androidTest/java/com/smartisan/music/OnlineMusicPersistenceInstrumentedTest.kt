package com.smartisan.music

import android.webkit.CookieManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartisan.music.data.settings.MusicSourceMode
import com.smartisan.music.data.settings.OnlineMusicSettingsStore
import com.smartisan.music.ui.shell.NeteaseLoginUrl
import com.smartisan.music.ui.shell.clearNeteaseWebCookies
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineMusicPersistenceInstrumentedTest {
    @Test
    fun sourceModePersistsThroughDataStore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val firstStore = OnlineMusicSettingsStore(context)
        val secondStore = OnlineMusicSettingsStore(context)
        val originalMode = firstStore.settings.first().sourceMode
        try {
            firstStore.setNeteaseEnabled(true)
            assertEquals(MusicSourceMode.Netease, secondStore.settings.first().sourceMode)

            secondStore.setNeteaseEnabled(false)
            assertEquals(MusicSourceMode.Local, firstStore.settings.first().sourceMode)
        } finally {
            firstStore.setNeteaseEnabled(originalMode == MusicSourceMode.Netease)
        }
    }

    @Test
    fun webLoginCookiesAreClearedAfterThePageFinishes() {
        clearCookiesAndWait()
        val cookieWritten = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().setCookie(NeteaseLoginUrl, "MUSIC_U=test") {
                cookieWritten.countDown()
            }
        }
        assertTrue(cookieWritten.await(5, TimeUnit.SECONDS))
        assertTrue(readLoginCookies().orEmpty().contains("MUSIC_U=test"))

        clearCookiesAndWait()

        assertFalse(readLoginCookies().orEmpty().contains("MUSIC_U=test"))
    }

    private fun clearCookiesAndWait() {
        val cleared = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            clearNeteaseWebCookies { cleared.countDown() }
        }
        assertTrue(cleared.await(5, TimeUnit.SECONDS))
    }

    private fun readLoginCookies(): String? {
        var cookies: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            cookies = CookieManager.getInstance().getCookie(NeteaseLoginUrl)
        }
        return cookies
    }
}
