package com.smartisan.music.netease.internal

import com.google.gson.Gson
import com.smartisan.music.netease.NeteaseApiError
import com.smartisan.music.netease.NeteaseResult
import kotlinx.coroutines.test.runTest
import okhttp3.CookieJar
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class NeteaseTransportSecurityTest {
    private val servers = mutableListOf<MockWebServer>()
    private val transports = mutableListOf<NeteaseHttpTransport>()

    @After
    fun tearDown() {
        transports.forEach(NeteaseHttpTransport::close)
        servers.forEach(MockWebServer::shutdown)
    }

    @Test
    fun oversizedResponseIsRejectedBeforeJsonParsing() = runTest {
        val (server, api) = api(maxResponseBytes = 32)
        server.enqueue(MockResponse().setBody("x".repeat(33)))

        val result = api.searchSongs("test") as NeteaseResult.Failure

        assertTrue(result.error is NeteaseApiError.ResponseTooLarge)
    }

    @Test
    fun redirectToHostOutsideAllowlistIsBlocked() = runTest {
        val (server, api) = api()
        val redirected = MockWebServer().also {
            it.start()
            servers += it
        }
        val redirectUrl = redirected.url("/capture").newBuilder().host("127.0.0.1").build()
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", redirectUrl),
        )

        val result = api.searchSongs("test") as NeteaseResult.Failure

        assertTrue(result.error is NeteaseApiError.Network)
        assertNull(redirected.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    private fun api(maxResponseBytes: Long = 8L * 1024L * 1024L): Pair<MockWebServer, NeteaseMusicApiImpl> {
        val server = MockWebServer().also {
            it.start()
            servers += it
        }
        val base = server.url("/")
        val transport = NeteaseHttpTransport(
            gson = Gson(),
            cookieJar = CookieJar.NO_COOKIES,
            endpoints = NeteaseEndpoints(base, base, base),
            maxResponseBytes = maxResponseBytes,
        ).also(transports::add)
        return server to NeteaseMusicApiImpl(transport)
    }
}
