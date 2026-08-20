package com.smartisan.music.netease.internal

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.smartisan.music.netease.NeteaseApiError
import com.smartisan.music.netease.NeteaseAudioQuality
import com.smartisan.music.netease.NeteaseNewAlbumArea
import com.smartisan.music.netease.NeteasePlaylistDetail
import com.smartisan.music.netease.NeteaseResult
import com.smartisan.music.netease.NeteaseSearchPage
import com.smartisan.music.netease.NeteaseSong
import com.smartisan.music.netease.NeteaseSongStream
import kotlinx.coroutines.test.runTest
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NeteaseMusicApiImplTest {
    private lateinit var server: MockWebServer
    private lateinit var transport: NeteaseHttpTransport
    private lateinit var api: NeteaseMusicApiImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/")
        transport = NeteaseHttpTransport(
            gson = Gson(),
            cookieJar = CookieJar.NO_COOKIES,
            endpoints = NeteaseEndpoints(baseUrl, baseUrl, baseUrl),
        )
        api = NeteaseMusicApiImpl(transport)
    }

    @After
    fun tearDown() {
        transport.close()
        server.shutdown()
    }

    @Test
    fun songSearchMapsDomainDataAndSendsPaginationFields() = runTest {
        server.enqueue(
            jsonResponse(
                """{
                  "code":200,
                  "result":{"songCount":8,"songs":[{
                    "id":347230,"name":"海阔天空","dt":320000,
                    "ar":[{"id":111,"name":"Beyond"}],
                    "al":{"id":222,"name":"乐与怒","picUrl":"https://img/cover.jpg"}
                  }]}
                }""".trimIndent(),
            ),
        )

        val page = api.searchSongs("  海阔天空 ", limit = 5, offset = 2).successValue()

        assertEquals(8, page.total)
        assertEquals(347230L, page.items.single().id)
        assertEquals("Beyond", page.items.single().artists.single().name)
        val request = server.takeRequest()
        assertEquals("/api/cloudsearch/pc", request.requestUrl?.encodedPath)
        val form = "http://localhost/?${request.body.readUtf8()}".toHttpUrl()
        assertEquals("海阔天空", form.queryParameter("s"))
        assertEquals("1", form.queryParameter("type"))
        assertEquals("5", form.queryParameter("limit"))
        assertEquals("2", form.queryParameter("offset"))
        assertTrue(request.getHeader("User-Agent")?.contains("SmartisanMusicRevived") == true)
    }

    @Test
    fun playlistDetailFetchesMissingSongsInFiveHundredItemBatchesAndKeepsOrder() = runTest {
        val ids = (1L..1002L).toList()
        server.enqueue(
            jsonResponse(
                buildString {
                    append("{\"code\":200,\"playlist\":{\"id\":99,\"name\":\"Large\",")
                    append("\"tracks\":[${songJson(1)}],\"trackIds\":[")
                    append(ids.joinToString(",") { "{\"id\":$it}" })
                    append("]}}")
                },
            ),
        )
        ids.drop(1).chunked(500).forEach { batch ->
            server.enqueue(jsonResponse("{\"code\":200,\"songs\":[${batch.joinToString(",") { songJson(it) }}]}"))
        }

        val detail: NeteasePlaylistDetail = api.playlistDetail(99).successValue()

        assertEquals(ids, detail.tracks.map(NeteaseSong::id))
        assertEquals(4, server.requestCount)
        assertEquals("/eapi/v6/playlist/detail", server.takeRequest().requestUrl?.encodedPath)
        assertEquals(listOf(500, 500, 1), List(3) {
            val request = server.takeRequest()
            val form = "http://localhost/?${request.body.readUtf8()}".toHttpUrl()
            JsonParser.parseString(form.queryParameter("c")).asJsonArray.size()
        })
    }

    @Test
    fun streamResolutionFallsBackUntilAPlayableUrlIsReturned() = runTest {
        server.enqueue(jsonResponse("{\"code\":200,\"data\":[{\"id\":7,\"url\":null,\"code\":404}]}"))
        server.enqueue(
            jsonResponse(
                """{"code":200,"data":[{
                  "id":7,"url":"http://cdn.example/song.mp3","code":200,
                  "level":"exhigh","br":320000,"size":1234,"type":"mp3","time":300000
                }]}""".trimIndent(),
            ),
        )

        val stream: NeteaseSongStream = api.songStream(
            songId = 7,
            preferredQuality = NeteaseAudioQuality.LOSSLESS,
        ).successValue()

        assertEquals(NeteaseAudioQuality.LOSSLESS, stream.requestedQuality)
        assertEquals(NeteaseAudioQuality.EXTREME, stream.actualQuality)
        assertEquals("https://cdn.example/song.mp3", stream.url)
        assertEquals(300L, stream.expiresInSeconds)
        assertEquals(2, server.requestCount)
        repeat(2) {
            assertEquals(
                "/eapi/song/enhance/player/url/v1",
                server.takeRequest().requestUrl?.encodedPath,
            )
        }
    }

    @Test
    fun allUnavailableQualitiesReturnStructuredFailure() = runTest {
        repeat(5) {
            server.enqueue(jsonResponse("{\"code\":200,\"data\":[{\"id\":7,\"url\":null,\"code\":404}]}"))
        }

        val result = api.songStream(7, NeteaseAudioQuality.HI_RES)

        assertTrue(result is NeteaseResult.Failure)
        val error = (result as NeteaseResult.Failure).error as NeteaseApiError.NoPlayableResource
        assertEquals(NeteaseAudioQuality.entries, error.attemptedQualities)
    }

    @Test
    fun responseFailuresAreMappedWithoutLeakingTransportTypes() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "2"))
        server.enqueue(jsonResponse("{not-json"))
        server.enqueue(jsonResponse("{\"code\":301,\"message\":\"need login\"}"))

        val http = api.searchSongs("test") as NeteaseResult.Failure
        val parse = api.searchSongs("test") as NeteaseResult.Failure
        val auth = api.currentAccount() as NeteaseResult.Failure

        assertEquals(2_000L, (http.error as NeteaseApiError.Http).retryAfterMillis)
        assertTrue(parse.error is NeteaseApiError.Parse)
        assertEquals(301, (auth.error as NeteaseApiError.AuthenticationRequired).code)
    }

    @Test
    fun endpointFamiliesMapTheirPublicModels() = runTest {
        server.enqueue(jsonResponse("{\"code\":200,\"lrc\":{\"lyric\":\"original\"},\"tlyric\":{\"lyric\":\"translated\"}}"))
        server.enqueue(jsonResponse("{\"code\":200,\"album\":{\"id\":2,\"name\":\"Album\"},\"songs\":[${songJson(1)}]}"))
        server.enqueue(jsonResponse("{\"code\":200,\"data\":{\"artist\":{\"id\":3,\"name\":\"Artist\"},\"hotSongs\":[${songJson(1)}]}}"))
        server.enqueue(jsonResponse("{\"code\":200,\"profile\":{\"userId\":4,\"nickname\":\"User\"}}"))
        server.enqueue(jsonResponse("{\"code\":200,\"result\":[{\"id\":5,\"name\":\"Mix\"}]}"))
        server.enqueue(jsonResponse("{\"code\":200,\"profile\":{\"userId\":4,\"nickname\":\"User\"}}"))
        server.enqueue(jsonResponse("{\"code\":200,\"recommend\":[${songJson(1)}]}"))
        server.enqueue(jsonResponse("{\"code\":200,\"recommend\":[{\"id\":5,\"name\":\"Mix\"}]}"))
        server.enqueue(jsonResponse("{\"code\":200,\"data\":[${songJson(1)}]}"))
        server.enqueue(jsonResponse("{\"code\":200}"))

        assertEquals("translated", api.lyrics(1).successValue().translated)
        assertEquals("Album", api.albumDetail(2).successValue().album.name)
        assertEquals("Artist", api.artistDetail(3).successValue().artist.name)
        assertEquals("User", api.userDetail(4).successValue().nickname)
        assertEquals("Mix", api.personalizedPlaylists().successValue().single().name)
        assertEquals(4L, api.currentAccount().successValue().userId)
        assertEquals(1L, api.dailyRecommendedSongs().successValue().single().id)
        assertEquals(5L, api.recommendedPlaylists().successValue().single().id)
        assertEquals(1L, api.personalFm().successValue().single().id)
        assertTrue(api.trashPersonalFm(1).successValue())
        val requestPaths = List(10) { server.takeRequest().requestUrl?.encodedPath }
        assertEquals("/api/artist/head/info/get", requestPaths[2])
        assertEquals("/api/personalized/playlist", requestPaths[4])
    }

    @Test
    fun collectionEndpointsMapPaginationAndSpecializedSearchResults() = runTest {
        server.enqueue(jsonResponse("{\"code\":200,\"result\":{\"albumCount\":3,\"albums\":[{\"id\":2,\"name\":\"Album\",\"artist\":{\"id\":3,\"name\":\"Artist\"}}]}}"))
        server.enqueue(jsonResponse("{\"code\":200,\"result\":{\"artistCount\":4,\"artists\":[{\"id\":3,\"name\":\"Artist\",\"albumSize\":7}]}}"))
        server.enqueue(jsonResponse("{\"code\":200,\"hotAlbums\":[{\"id\":2,\"name\":\"Album\"}],\"more\":false}"))
        server.enqueue(jsonResponse("{\"code\":200,\"albums\":[{\"id\":6,\"name\":\"New Album\"}],\"total\":9}"))
        server.enqueue(jsonResponse("{\"code\":200,\"playlist\":[{\"id\":5,\"name\":\"Mix\",\"creator\":{\"userId\":4}}],\"total\":2,\"more\":true}"))
        server.enqueue(jsonResponse("{\"code\":200,\"data\":[${songJson(8)}],\"total\":12}"))
        server.enqueue(jsonResponse("{\"code\":200,\"songs\":[${songJson(8)}]}"))

        assertEquals(3, api.searchAlbums("album", 10, 0).successValue().total)
        assertEquals(7, api.searchArtists("artist", 10, 0).successValue().items.single().albumCount)
        assertEquals(1, api.artistAlbums(3, 10, 0).successValue().total)
        assertEquals(
            "New Album",
            api.newAlbums(NeteaseNewAlbumArea.CHINA, 10, 0).successValue().items.single().name,
        )
        assertTrue(api.userPlaylists(4, 10, 0).successValue().hasMore)
        assertEquals(12, api.newSongs(10, 0).successValue().total)
        assertEquals(8L, api.songDetails(listOf(8)).successValue().single().id)
    }

    @Test
    fun invalidInputsFailBeforeARequestIsCreated() = runTest {
        val blankSearch = api.searchSongs("   ") as NeteaseResult.Failure
        val invalidId = api.lyrics(0) as NeteaseResult.Failure
        val invalidPage = api.newSongs(limit = 101) as NeteaseResult.Failure

        assertTrue(blankSearch.error is NeteaseApiError.InvalidRequest)
        assertTrue(invalidId.error is NeteaseApiError.InvalidRequest)
        assertTrue(invalidPage.error is NeteaseApiError.InvalidRequest)
        assertEquals(0, server.requestCount)
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)

    private fun songJson(id: Long): String =
        "{\"id\":$id,\"name\":\"Song $id\",\"dt\":1000,\"ar\":[{\"id\":10,\"name\":\"Artist\"}],\"al\":{\"id\":20,\"name\":\"Album\"}}"

    @Suppress("UNCHECKED_CAST")
    private fun <T> NeteaseResult<T>.successValue(): T {
        assertTrue("Expected success but was $this", this is NeteaseResult.Success)
        return (this as NeteaseResult.Success<T>).value
    }
}
