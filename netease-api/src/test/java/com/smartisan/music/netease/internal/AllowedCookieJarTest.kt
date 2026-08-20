package com.smartisan.music.netease.internal

import com.smartisan.music.netease.NeteaseResult
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AllowedCookieJarTest {
    @Test
    fun importedCookiesAreAvailableOnlyToAllowlistedNeteaseHosts() = runTest {
        val jar = AllowedCookieJar(emptyList(), store = null, clockMillis = { now })

        val result = jar.importCookieHeader("MUSIC_U=secret; __csrf=token; Path=/")

        assertTrue(result is NeteaseResult.Success)
        assertTrue(jar.snapshot.value.authenticated)
        assertEquals(2, jar.snapshot.value.cookieCount)
        assertEquals(
            setOf("MUSIC_U", "__csrf"),
            jar.loadForRequest("https://interface3.music.163.com/eapi/test".toHttpUrl())
                .map { cookie -> cookie.name }
                .toSet(),
        )
        assertTrue(jar.loadForRequest("https://example.com/".toHttpUrl()).isEmpty())
    }

    @Test
    fun expiredCookiesArePrunedBeforeARequest() {
        var clock = now
        val cookie = Cookie.Builder()
            .name("MUSIC_U")
            .value("secret")
            .domain("music.163.com")
            .path("/")
            .secure()
            .expiresAt(now + 100)
            .build()
        val jar = AllowedCookieJar(listOf(cookie), store = null, clockMillis = { clock })

        clock += 101

        assertTrue(jar.loadForRequest("https://music.163.com/api/test".toHttpUrl()).isEmpty())
        assertFalse(jar.snapshot.value.authenticated)
    }

    @Test
    fun responseCookieReplacesMatchingCookieAndClearRemovesEverything() = runTest {
        val jar = AllowedCookieJar(emptyList(), store = null, clockMillis = { now })
        val url = "https://music.163.com/api/test".toHttpUrl()
        jar.saveFromResponse(
            url,
            listOf(
                Cookie.Builder().name("MUSIC_U").value("first").domain("music.163.com")
                    .path("/").expiresAt(now + 10_000).secure().build(),
            ),
        )
        jar.saveFromResponse(
            url,
            listOf(
                Cookie.Builder().name("MUSIC_U").value("second").domain("music.163.com")
                    .path("/").expiresAt(now + 20_000).secure().build(),
            ),
        )

        assertEquals("second", jar.allCookiesForTest().single().value)

        jar.clear()

        assertEquals(0, jar.snapshot.value.cookieCount)
        assertTrue(jar.allCookiesForTest().isEmpty())
    }

    @Test
    fun responseCookieIsNotPublishedWhenEncryptedPersistenceFails() {
        val store = FailingCookieStore()
        val jar = AllowedCookieJar(emptyList(), store = store, clockMillis = { now })
        val url = "https://music.163.com/api/test".toHttpUrl()

        jar.saveFromResponse(
            url,
            listOf(
                Cookie.Builder().name("MUSIC_U").value("secret").domain("music.163.com")
                    .path("/").expiresAt(now + 10_000).secure().build(),
            ),
        )

        assertTrue(jar.allCookiesForTest().isEmpty())
        assertFalse(jar.snapshot.value.authenticated)
        assertTrue(jar.snapshot.value.lastPersistenceError != null)

        store.failSave = false
        jar.saveFromResponse(
            url,
            listOf(
                Cookie.Builder().name("MUSIC_U").value("secret").domain("music.163.com")
                    .path("/").expiresAt(now + 10_000).secure().build(),
            ),
        )

        assertTrue(jar.snapshot.value.authenticated)
        assertTrue(jar.snapshot.value.lastPersistenceError == null)
        assertEquals("MUSIC_U", store.cookies.single().name)
    }

    private class FailingCookieStore : CookieStore {
        var failSave = true
        var cookies: List<Cookie> = emptyList()

        override suspend fun load(): List<Cookie> = cookies

        override suspend fun save(cookies: List<Cookie>) {
            if (failSave) throw IOException("disk full")
            this.cookies = cookies
        }

        override suspend fun clear() {
            cookies = emptyList()
        }
    }

    private companion object {
        const val now = 1_800_000_000_000L
    }
}
