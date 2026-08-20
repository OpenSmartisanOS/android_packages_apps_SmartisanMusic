package com.smartisan.music.netease.internal

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.smartisan.music.netease.NeteaseAlbum
import com.smartisan.music.netease.NeteaseAlbumDetail
import com.smartisan.music.netease.NeteaseApiError
import com.smartisan.music.netease.NeteaseArtist
import com.smartisan.music.netease.NeteaseArtistDetail
import com.smartisan.music.netease.NeteaseAudioQuality
import com.smartisan.music.netease.NeteaseLyrics
import com.smartisan.music.netease.NeteaseMusicApi
import com.smartisan.music.netease.NeteaseNewAlbumArea
import com.smartisan.music.netease.NeteasePlaylistDetail
import com.smartisan.music.netease.NeteasePlaylistPage
import com.smartisan.music.netease.NeteasePlaylistSummary
import com.smartisan.music.netease.NeteaseResult
import com.smartisan.music.netease.NeteaseSearchPage
import com.smartisan.music.netease.NeteaseSong
import com.smartisan.music.netease.NeteaseSongStream
import com.smartisan.music.netease.NeteaseUserProfile
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

internal class NeteaseMusicApiImpl(
    private val transport: NeteaseHttpTransport,
) : NeteaseMusicApi {
    override suspend fun searchSongs(
        query: String,
        limit: Int,
        offset: Int,
    ): NeteaseResult<NeteaseSearchPage<NeteaseSong>> = search(
        query = query,
        type = 1,
        limit = limit,
        offset = offset,
        arrayName = "songs",
        totalName = "songCount",
        parser = ::parseSong,
    )

    override suspend fun searchAlbums(
        query: String,
        limit: Int,
        offset: Int,
    ): NeteaseResult<NeteaseSearchPage<NeteaseAlbum>> = search(
        query = query,
        type = 10,
        limit = limit,
        offset = offset,
        arrayName = "albums",
        totalName = "albumCount",
        parser = ::parseAlbum,
    )

    override suspend fun searchArtists(
        query: String,
        limit: Int,
        offset: Int,
    ): NeteaseResult<NeteaseSearchPage<NeteaseArtist>> = search(
        query = query,
        type = 100,
        limit = limit,
        offset = offset,
        arrayName = "artists",
        totalName = "artistCount",
        parser = ::parseArtist,
    )

    override suspend fun songDetails(ids: List<Long>): NeteaseResult<List<NeteaseSong>> = safeApiCall {
        if (ids.isEmpty()) return@safeApiCall emptyList()
        require(ids.size <= maxSongDetailIds) { "At most $maxSongDetailIds song IDs may be requested" }
        ids.forEach(::requirePositiveId)
        val byId = fetchSongDetails(ids.distinct()).associateBy(NeteaseSong::id)
        ids.mapNotNull(byId::get)
    }

    override suspend fun lyrics(songId: Long): NeteaseResult<NeteaseLyrics> = safeApiCall {
        requirePositiveId(songId)
        val root = transport.postForm(
            path = "/api/song/lyric",
            fields = linkedMapOf(
                "id" to songId.toString(),
                "cp" to "false",
                "tv" to "0",
                "lv" to "0",
                "rv" to "0",
                "kv" to "0",
                "yv" to "0",
                "ytv" to "0",
                "yrv" to "0",
            ),
        ).checked()
        NeteaseLyrics(
            original = root.objectOrNull("lrc")?.stringOrNull("lyric"),
            translated = root.objectOrNull("tlyric")?.stringOrNull("lyric"),
        )
    }

    override suspend fun albumDetail(albumId: Long): NeteaseResult<NeteaseAlbumDetail> = safeApiCall {
        requirePositiveId(albumId)
        val root = transport.get("/api/v1/album/$albumId").checked()
        val album = root.objectOrNull("album")?.let(::parseAlbum)
            ?: throw JsonParseException("Album response did not contain album data")
        NeteaseAlbumDetail(
            album = album,
            songs = root.arrayOrEmpty("songs").mapObjects(::parseSong),
        )
    }

    override suspend fun artistDetail(artistId: Long): NeteaseResult<NeteaseArtistDetail> = safeApiCall {
        requirePositiveId(artistId)
        val root = transport.postForm(
            path = "/api/artist/head/info/get",
            fields = mapOf("id" to artistId.toString()),
        ).checked()
        val data = root.objectOrNull("data")
        val artistObject = data?.objectOrNull("artist") ?: root.objectOrNull("artist")
        val artist = artistObject?.let(::parseArtist)
            ?: throw JsonParseException("Artist response did not contain artist data")
        val songs = data?.arrayOrNull("hotSongs") ?: root.arrayOrEmpty("hotSongs")
        NeteaseArtistDetail(artist, songs.mapObjects(::parseSong))
    }

    override suspend fun artistAlbums(
        artistId: Long,
        limit: Int,
        offset: Int,
    ): NeteaseResult<NeteaseSearchPage<NeteaseAlbum>> = safeApiCall {
        requirePositiveId(artistId)
        validatePage(limit, offset, maxPageSize = 100)
        val root = transport.get(
            path = "/api/artist/albums/$artistId",
            query = mapOf("limit" to limit.toString(), "offset" to offset.toString()),
        ).checked()
        val albums = root.arrayOrEmpty("hotAlbums").mapObjects(::parseAlbum)
        val total = root.intOrNull("total") ?: if (root.booleanOrNull("more") == false) {
            offset + albums.size
        } else {
            null
        }
        NeteaseSearchPage(albums, total, offset, limit)
    }

    override suspend fun userDetail(userId: Long): NeteaseResult<NeteaseUserProfile> = safeApiCall {
        requirePositiveId(userId)
        val root = transport.get("/api/v1/user/detail/$userId").checked()
        val profile = root.objectOrNull("profile")
            ?: throw JsonParseException("User response did not contain a profile")
        parseUser(profile)
    }

    override suspend fun personalizedPlaylists(
        limit: Int,
    ): NeteaseResult<List<NeteasePlaylistSummary>> = safeApiCall {
        validateLimit(limit, 100)
        transport.get(
            path = "/api/personalized/playlist",
            query = mapOf("limit" to limit.toString()),
        ).checked().arrayOrEmpty("result").mapObjects(::parsePlaylist)
    }

    override suspend fun newAlbums(
        area: NeteaseNewAlbumArea,
        limit: Int,
        offset: Int,
    ): NeteaseResult<NeteaseSearchPage<NeteaseAlbum>> = safeApiCall {
        validatePage(limit, offset, maxPageSize = 100)
        val root = transport.get(
            path = "/api/album/new",
            query = mapOf(
                "area" to area.wireValue.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
            ),
        ).checked()
        val albums = root.arrayOrEmpty("albums").mapObjects(::parseAlbum)
        NeteaseSearchPage(albums, root.intOrNull("total"), offset, limit)
    }

    override suspend fun currentAccount(): NeteaseResult<NeteaseUserProfile> = safeApiCall {
        val root = transport.eapiPost(
            path = "/eapi/w/nuser/account/get",
            payload = emptyMap(),
        ).checked()
        val profile = root.objectOrNull("profile")
        val account = root.objectOrNull("account")
        val userId = profile?.longOrNull("userId") ?: account?.longOrNull("id")
        if (userId == null || userId <= 0L) {
            throw ApiResponseException(301, "NetEase login is required")
        }
        NeteaseUserProfile(
            userId = userId,
            nickname = profile?.stringOrNull("nickname")
                ?: account?.stringOrNull("userName")
                ?: "用户",
            avatarUrl = profile?.stringOrNull("avatarUrl") ?: account?.stringOrNull("avatarUrl"),
            signature = profile?.stringOrNull("signature"),
            followerCount = profile?.intOrNull("followeds"),
            followingCount = profile?.intOrNull("follows"),
        )
    }

    override suspend fun userPlaylists(
        userId: Long,
        limit: Int,
        offset: Int,
    ): NeteaseResult<NeteasePlaylistPage> = safeApiCall {
        requirePositiveId(userId)
        validatePage(limit, offset, maxPageSize = 500)
        val root = transport.eapiPost(
            path = "/eapi/user/playlist",
            payload = linkedMapOf(
                "uid" to userId.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "includeVideo" to "false",
            ),
        ).checked()
        NeteasePlaylistPage(
            playlists = root.arrayOrEmpty("playlist").mapObjects(::parsePlaylist),
            total = root.intOrNull("total"),
            hasMore = root.booleanOrNull("more") ?: false,
            offset = offset,
            limit = limit,
        )
    }

    override suspend fun playlistDetail(
        playlistId: Long,
    ): NeteaseResult<NeteasePlaylistDetail> = safeApiCall {
        requirePositiveId(playlistId)
        val root = transport.eapiPost(
            path = "/eapi/v6/playlist/detail",
            payload = linkedMapOf("id" to playlistId.toString(), "n" to "1000", "s" to "0"),
        ).checked()
        val playlistObject = root.objectOrNull("playlist")
            ?: throw JsonParseException("Playlist response did not contain playlist data")
        val partialTracks = playlistObject.arrayOrEmpty("tracks").mapObjects(::parseSong)
        val trackMap = partialTracks.associateByTo(linkedMapOf(), NeteaseSong::id)
        val orderedIds = playlistObject.arrayOrEmpty("trackIds")
            .mapNotNull { element -> element.asObjectOrNull()?.longOrNull("id") }
        if (orderedIds.isNotEmpty()) {
            val missingIds = orderedIds.distinct().filterNot(trackMap::containsKey)
            fetchSongDetails(missingIds).forEach { song -> trackMap[song.id] = song }
        }
        NeteasePlaylistDetail(
            playlist = parsePlaylist(playlistObject),
            tracks = if (orderedIds.isEmpty()) partialTracks else orderedIds.mapNotNull(trackMap::get),
        )
    }

    override suspend fun dailyRecommendedSongs(): NeteaseResult<List<NeteaseSong>> = safeApiCall {
        val root = transport.eapiPost(
            path = "/eapi/v2/discovery/recommend/songs",
            payload = emptyMap(),
        ).checked()
        val array = root.arrayOrNull("recommend")
            ?: root.arrayOrNull("data")
            ?: root.objectOrNull("data")?.arrayOrNull("dailySongs")
            ?: JsonArray()
        array.mapObjects(::parseSong)
    }

    override suspend fun recommendedPlaylists(): NeteaseResult<List<NeteasePlaylistSummary>> = safeApiCall {
        transport.eapiPost(
            path = "/eapi/v1/discovery/recommend/resource",
            payload = emptyMap(),
        ).checked().arrayOrEmpty("recommend").mapObjects(::parsePlaylist)
    }

    override suspend fun newSongs(
        limit: Int,
        offset: Int,
    ): NeteaseResult<NeteaseSearchPage<NeteaseSong>> = safeApiCall {
        validatePage(limit, offset, maxPageSize = 100)
        val root = transport.get(
            path = "/api/v1/discovery/new/songs",
            query = mapOf("limit" to limit.toString(), "offset" to offset.toString()),
        ).checked()
        val array = root.arrayOrNull("data") ?: root.arrayOrEmpty("songs")
        val songs = array.mapObjects(::parseSong)
        NeteaseSearchPage(songs, root.intOrNull("total"), offset, limit)
    }

    override suspend fun personalFm(): NeteaseResult<List<NeteaseSong>> = safeApiCall {
        transport.eapiPost(
            path = "/eapi/v1/radio/get",
            payload = emptyMap(),
        ).checked().arrayOrEmpty("data").mapObjects(::parseSong)
    }

    override suspend fun trashPersonalFm(songId: Long): NeteaseResult<Boolean> = safeApiCall {
        requirePositiveId(songId)
        transport.eapiPost(
            path = "/eapi/radio/trash/add",
            payload = linkedMapOf(
                "songId" to songId.toString(),
                "alg" to "itembased",
                "time" to "25",
            ),
        ).checked()
        true
    }

    override suspend fun songStream(
        songId: Long,
        preferredQuality: NeteaseAudioQuality,
    ): NeteaseResult<NeteaseSongStream> = safeApiCall {
        requirePositiveId(songId)
        val attempted = mutableListOf<NeteaseAudioQuality>()
        preferredQuality.fallbackOrder().forEach { quality ->
            attempted += quality
            val payload = linkedMapOf<String, Any?>(
                "ids" to "[$songId]",
                "level" to quality.wireValue,
                // The current v1 endpoint requires this negotiation field even for lossy tiers.
                "encodeType" to "flac",
            )
            val root = transport.eapiPost(
                path = "/eapi/song/enhance/player/url/v1",
                payload = payload,
                useInterface3 = true,
            ).checked()
            val item = root.arrayOrEmpty("data")
                .mapNotNull(JsonElement::asObjectOrNull)
                .firstOrNull { it.longOrNull("id") == songId }
                ?: return@forEach
            if ((item.intOrNull("code") ?: 200) != 200) return@forEach
            val url = item.stringOrNull("url")?.takeIf(String::isNotBlank) ?: return@forEach
            val parsedUrl = url.toHttpUrlOrNull() ?: return@forEach
            val secureUrl = when (parsedUrl.scheme) {
                "https" -> parsedUrl
                "http" -> parsedUrl.newBuilder().scheme("https").build()
                else -> return@forEach
            }
            val actualQuality = NeteaseAudioQuality.fromWireValue(item.stringOrNull("level")) ?: quality
            return@safeApiCall NeteaseSongStream(
                songId = songId,
                url = secureUrl.toString(),
                requestedQuality = preferredQuality,
                actualQuality = actualQuality,
                bitrate = item.longOrNull("br"),
                sizeBytes = item.longOrNull("size"),
                format = item.stringOrNull("type"),
                expiresInSeconds = item.longOrNull("time")?.div(1000L),
            )
        }
        throw NoPlayableResourceException(songId, attempted)
    }

    private suspend fun <T> search(
        query: String,
        type: Int,
        limit: Int,
        offset: Int,
        arrayName: String,
        totalName: String,
        parser: (JsonObject) -> T,
    ): NeteaseResult<NeteaseSearchPage<T>> = safeApiCall {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty()) { "Search query must not be blank" }
        require(normalizedQuery.length <= maxSearchQueryLength) { "Search query is too long" }
        validatePage(limit, offset, maxPageSize = 100)
        val root = transport.postForm(
            path = "/api/cloudsearch/pc",
            fields = linkedMapOf(
                "s" to normalizedQuery,
                "type" to type.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
            ),
        ).checked()
        val result = root.objectOrNull("result") ?: JsonObject()
        NeteaseSearchPage(
            items = result.arrayOrEmpty(arrayName).mapObjects(parser),
            total = result.intOrNull(totalName),
            offset = offset,
            limit = limit,
        )
    }

    private suspend fun fetchSongDetails(ids: List<Long>): List<NeteaseSong> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(songDetailBatchSize).flatMap { batch ->
            val c = batch.joinToString(prefix = "[", postfix = "]", separator = ",") { id ->
                "{\"id\":$id}"
            }
            transport.postForm(
                path = "/api/v3/song/detail",
                fields = mapOf("c" to c),
            ).checked().arrayOrEmpty("songs").mapObjects(::parseSong)
        }
    }

    private suspend fun <T> safeApiCall(block: suspend () -> T): NeteaseResult<T> = try {
        NeteaseResult.Success(block())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: IllegalArgumentException) {
        NeteaseResult.Failure(
            NeteaseApiError.InvalidRequest(exception.message ?: "Invalid API request"),
        )
    } catch (exception: NoPlayableResourceException) {
        NeteaseResult.Failure(
            NeteaseApiError.NoPlayableResource(exception.songId, exception.attemptedQualities),
        )
    } catch (exception: ApiResponseException) {
        val message = exception.message ?: "NetEase API returned code ${exception.code}"
        val error = if (exception.code in authenticationCodes) {
            NeteaseApiError.AuthenticationRequired(exception.code, message)
        } else {
            NeteaseApiError.Api(exception.code, message)
        }
        NeteaseResult.Failure(error)
    } catch (exception: HttpStatusException) {
        NeteaseResult.Failure(
            NeteaseApiError.Http(
                statusCode = exception.statusCode,
                message = "NetEase request failed with HTTP ${exception.statusCode}",
                retryAfterMillis = exception.retryAfterMillis,
            ),
        )
    } catch (exception: ResponseTooLargeException) {
        NeteaseResult.Failure(NeteaseApiError.ResponseTooLarge(exception.limitBytes))
    } catch (exception: JsonParseException) {
        NeteaseResult.Failure(
            NeteaseApiError.Parse(exception.message ?: "Unable to parse the NetEase response", exception),
        )
    } catch (exception: InvalidResponseException) {
        NeteaseResult.Failure(
            NeteaseApiError.Parse(exception.message ?: "Invalid NetEase response", exception),
        )
    } catch (exception: IOException) {
        NeteaseResult.Failure(
            NeteaseApiError.Network("Unable to reach NetEase Music", exception),
        )
    } catch (exception: Exception) {
        NeteaseResult.Failure(
            NeteaseApiError.Parse(exception.message ?: "Unexpected NetEase response", exception),
        )
    }

    private fun JsonObject.checked(): JsonObject {
        val code = intOrNull("code") ?: return this
        if (code != successCode) {
            throw ApiResponseException(
                code,
                stringOrNull("message") ?: stringOrNull("msg") ?: "NetEase API returned code $code",
            )
        }
        return this
    }

    private fun parseSong(value: JsonObject): NeteaseSong {
        val id = value.requiredLong("id")
        val artists = (value.arrayOrNull("ar") ?: value.arrayOrEmpty("artists"))
            .mapObjects(::parseArtist)
        val album = (value.objectOrNull("al") ?: value.objectOrNull("album"))?.let(::parseAlbum)
        return NeteaseSong(
            id = id,
            name = value.stringOrNull("name").orEmpty(),
            artists = artists,
            album = album,
            durationMillis = value.longOrNull("dt") ?: value.longOrNull("duration"),
            trackNumber = value.intOrNull("no"),
            mvId = value.longOrNull("mv")?.takeIf { it > 0L },
        )
    }

    private fun parseArtist(value: JsonObject): NeteaseArtist = NeteaseArtist(
        id = value.longOrNull("id") ?: 0L,
        name = value.stringOrNull("name").orEmpty(),
        imageUrl = value.stringOrNull("picUrl")
            ?: value.stringOrNull("cover")
            ?: value.stringOrNull("img1v1Url"),
        albumCount = value.intOrNull("albumSize"),
        songCount = value.intOrNull("musicSize"),
        aliases = value.arrayOrEmpty("alias").mapNotNull(JsonElement::stringOrNull),
    )

    private fun parseAlbum(value: JsonObject): NeteaseAlbum {
        val artist = value.objectOrNull("artist")?.let(::parseArtist)
            ?: value.arrayOrNull("artists")?.mapObjects(::parseArtist)?.firstOrNull()
        return NeteaseAlbum(
            id = value.longOrNull("id") ?: 0L,
            name = value.stringOrNull("name").orEmpty(),
            imageUrl = value.stringOrNull("picUrl") ?: value.stringOrNull("blurPicUrl"),
            artist = artist,
            publishTimeEpochMillis = value.longOrNull("publishTime"),
            trackCount = value.intOrNull("size") ?: value.intOrNull("trackCount"),
            company = value.stringOrNull("company"),
            description = value.stringOrNull("description"),
        )
    }

    private fun parseUser(value: JsonObject): NeteaseUserProfile = NeteaseUserProfile(
        userId = value.longOrNull("userId") ?: value.requiredLong("id"),
        nickname = value.stringOrNull("nickname") ?: value.stringOrNull("userName") ?: "用户",
        avatarUrl = value.stringOrNull("avatarUrl"),
        signature = value.stringOrNull("signature"),
        followerCount = value.intOrNull("followeds"),
        followingCount = value.intOrNull("follows"),
    )

    private fun parsePlaylist(value: JsonObject): NeteasePlaylistSummary = NeteasePlaylistSummary(
        id = value.requiredLong("id"),
        name = value.stringOrNull("name").orEmpty(),
        coverUrl = value.stringOrNull("coverImgUrl") ?: value.stringOrNull("picUrl"),
        playCount = value.longOrNull("playCount"),
        trackCount = value.intOrNull("trackCount"),
        creatorUserId = value.objectOrNull("creator")?.longOrNull("userId"),
        specialType = value.intOrNull("specialType"),
        privacy = value.intOrNull("privacy"),
    )

    private fun requirePositiveId(id: Long) {
        require(id > 0L) { "NetEase ID must be positive" }
    }

    private fun validateLimit(limit: Int, max: Int) {
        require(limit in 1..max) { "Limit must be between 1 and $max" }
    }

    private fun validatePage(limit: Int, offset: Int, maxPageSize: Int) {
        validateLimit(limit, maxPageSize)
        require(offset >= 0) { "Offset must not be negative" }
    }

    private companion object {
        const val successCode = 200
        const val songDetailBatchSize = 500
        const val maxSongDetailIds = 5_000
        const val maxSearchQueryLength = 200
        val authenticationCodes = setOf(301, 302, 401, 460)
    }
}

private class ApiResponseException(
    val code: Int,
    message: String,
) : Exception(message)

private class NoPlayableResourceException(
    val songId: Long,
    val attemptedQualities: List<NeteaseAudioQuality>,
) : Exception()

private fun JsonObject.objectOrNull(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.arrayOrNull(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.arrayOrEmpty(name: String): JsonArray = arrayOrNull(name) ?: JsonArray()

private fun JsonObject.stringOrNull(name: String): String? = get(name)?.stringOrNull()

private fun JsonElement.stringOrNull(): String? = runCatching {
    takeUnless { it.isJsonNull }?.asString
}.getOrNull()

private fun JsonObject.longOrNull(name: String): Long? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asLong
}.getOrNull()

private fun JsonObject.intOrNull(name: String): Int? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asInt
}.getOrNull()

private fun JsonObject.booleanOrNull(name: String): Boolean? = runCatching {
    get(name)?.takeUnless { it.isJsonNull }?.asBoolean
}.getOrNull()

private fun JsonObject.requiredLong(name: String): Long = longOrNull(name)
    ?: throw JsonParseException("Required numeric field '$name' was missing")

private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

private fun <T> JsonArray.mapObjects(transform: (JsonObject) -> T): List<T> =
    mapNotNull(JsonElement::asObjectOrNull).map(transform)
