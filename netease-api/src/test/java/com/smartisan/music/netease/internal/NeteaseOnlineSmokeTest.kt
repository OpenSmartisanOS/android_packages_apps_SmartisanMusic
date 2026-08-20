package com.smartisan.music.netease.internal

import com.google.gson.Gson
import com.smartisan.music.netease.NeteaseAudioQuality
import com.smartisan.music.netease.NeteaseResult
import com.smartisan.music.netease.NeteaseSongStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Run manually with `NETEASE_LIVE_TEST=1`; normal unit-test runs skip external traffic. */
class NeteaseOnlineSmokeTest {
    private lateinit var transport: NeteaseHttpTransport
    private lateinit var api: NeteaseMusicApiImpl

    @Before
    fun setUp() {
        assumeTrue(System.getenv("NETEASE_LIVE_TEST") == "1")
        transport = NeteaseHttpTransport(Gson(), CookieJar.NO_COOKIES)
        api = NeteaseMusicApiImpl(transport)
    }

    @After
    fun tearDown() {
        if (::transport.isInitialized) transport.close()
    }

    @Test
    fun anonymousSearchDetailLyricsAndStreamResolution() = runTest {
        val search = api.searchSongs("海阔天空") as NeteaseResult.Success
        val songs = search.value.items.take(10)
        val song = songs.first()

        assertTrue(api.songDetails(listOf(song.id)) is NeteaseResult.Success)
        assertTrue(api.lyrics(song.id) is NeteaseResult.Success)
        var playableStream: NeteaseSongStream? = null
        for (candidate in songs) {
            val result = api.songStream(candidate.id, NeteaseAudioQuality.STANDARD)
            if (result is NeteaseResult.Success) {
                playableStream = result.value
                break
            }
        }
        val stream = checkNotNull(playableStream) {
            "No anonymous search result exposed a standard stream"
        }
        assertEquals("https", stream.url.toHttpUrl().scheme)
        assertTrue("Resolved HTTPS stream returned no audio bytes", stream.hasReadableBytes())

        val album = (api.searchAlbums("周杰伦") as NeteaseResult.Success).value.items.first()
        assertTrue(api.albumDetail(album.id) is NeteaseResult.Success)
        val artist = (api.searchArtists("周杰伦") as NeteaseResult.Success).value.items.first()
        val artistDetail = api.artistDetail(artist.id)
        assertTrue(artistDetail.toString(), artistDetail is NeteaseResult.Success)
        assertTrue(api.artistAlbums(artist.id, limit = 1) is NeteaseResult.Success)
        assertTrue(api.personalizedPlaylists(limit = 1) is NeteaseResult.Success)
        assertTrue(api.newAlbums(limit = 1) is NeteaseResult.Success)
        assertTrue(api.newSongs(limit = 1) is NeteaseResult.Success)
    }

    private suspend fun NeteaseSongStream.hasReadableBytes(): Boolean = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1023")
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful && (response.body?.byteStream()?.read() ?: -1) >= 0
            }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
