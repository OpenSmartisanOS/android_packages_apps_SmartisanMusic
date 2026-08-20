package com.smartisan.music.netease

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NeteaseSessionManagerInstrumentedTest {
    @Test
    fun importedSessionIsEncryptedPersistedAndExplicitlyCleared() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstClient = NeteaseApiClient.create(context)
        firstClient.sessionManager.clear()

        val imported = firstClient.sessionManager.importCookieHeader(
            "MUSIC_U=instrumentation-secret; __csrf=token",
        )
        assertTrue(imported is NeteaseResult.Success)
        firstClient.close()

        val restoredClient = NeteaseApiClient.create(context)
        assertTrue(restoredClient.sessionManager.snapshot.value.authenticated)
        assertEquals(2, restoredClient.sessionManager.snapshot.value.cookieCount)

        restoredClient.sessionManager.clear()
        assertFalse(restoredClient.sessionManager.snapshot.value.authenticated)
        restoredClient.close()
    }
}
