package com.smartisan.music.netease.internal

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class EncryptedCookieStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = EncryptedCookieStore(context, Gson())

    @Before
    fun setUp() = runBlocking {
        store.clear()
    }

    @After
    fun tearDown() = runBlocking {
        store.clear()
    }

    @Test
    fun cookiesPersistAndClearThroughEncryptedStore() = runBlocking {
        store.save(listOf(loginCookie()))

        val restored = store.load()

        assertEquals("MUSIC_U", restored.single().name)
        assertEquals("instrumentation-secret", restored.single().value)

        store.clear()

        assertTrue(store.load().isEmpty())
    }

    @Test
    fun missingKeystoreKeyClearsUndecryptableBackupState() = runBlocking {
        store.save(listOf(loginCookie()))
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(keyAlias)
        }

        assertTrue(store.load().isEmpty())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun corruptedPreferencesFileIsReplacedWithAnEmptyUsableSession() = runBlocking {
        val corruptFile = File.createTempFile(
            "netease_corrupt_",
            ".preferences_pb",
            context.cacheDir,
        ).apply {
            writeBytes(byteArrayOf(0x80.toByte()))
        }
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val corruptDataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = neteaseSessionCorruptionHandler,
            scope = dataStoreScope,
            produceFile = { corruptFile },
        )
        val corruptStore = EncryptedCookieStore(context, Gson(), corruptDataStore)

        try {
            assertTrue(corruptStore.load().isEmpty())

            corruptStore.save(listOf(loginCookie()))

            assertEquals("MUSIC_U", corruptStore.load().single().name)
        } finally {
            dataStoreScope.cancel()
            corruptFile.delete()
        }
    }

    private fun loginCookie(): Cookie = Cookie.Builder()
        .name("MUSIC_U")
        .value("instrumentation-secret")
        .domain("music.163.com")
        .path("/")
        .expiresAt(System.currentTimeMillis() + 60_000)
        .secure()
        .httpOnly()
        .build()

    private companion object {
        const val keyAlias = "smartisan_music_netease_session_v1"
    }
}
