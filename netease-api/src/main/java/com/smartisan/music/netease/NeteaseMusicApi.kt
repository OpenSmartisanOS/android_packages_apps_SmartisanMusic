package com.smartisan.music.netease

interface NeteaseMusicApi {
    suspend fun searchSongs(
        query: String,
        limit: Int = 30,
        offset: Int = 0,
    ): NeteaseResult<NeteaseSearchPage<NeteaseSong>>

    suspend fun searchAlbums(
        query: String,
        limit: Int = 30,
        offset: Int = 0,
    ): NeteaseResult<NeteaseSearchPage<NeteaseAlbum>>

    suspend fun searchArtists(
        query: String,
        limit: Int = 30,
        offset: Int = 0,
    ): NeteaseResult<NeteaseSearchPage<NeteaseArtist>>

    suspend fun songDetails(ids: List<Long>): NeteaseResult<List<NeteaseSong>>

    suspend fun lyrics(songId: Long): NeteaseResult<NeteaseLyrics>

    suspend fun albumDetail(albumId: Long): NeteaseResult<NeteaseAlbumDetail>

    suspend fun artistDetail(artistId: Long): NeteaseResult<NeteaseArtistDetail>

    suspend fun artistAlbums(
        artistId: Long,
        limit: Int = 50,
        offset: Int = 0,
    ): NeteaseResult<NeteaseSearchPage<NeteaseAlbum>>

    suspend fun userDetail(userId: Long): NeteaseResult<NeteaseUserProfile>

    suspend fun personalizedPlaylists(
        limit: Int = 30,
    ): NeteaseResult<List<NeteasePlaylistSummary>>

    suspend fun newAlbums(
        area: NeteaseNewAlbumArea = NeteaseNewAlbumArea.ALL,
        limit: Int = 10,
        offset: Int = 0,
    ): NeteaseResult<NeteaseSearchPage<NeteaseAlbum>>

    suspend fun currentAccount(): NeteaseResult<NeteaseUserProfile>

    suspend fun userPlaylists(
        userId: Long,
        limit: Int = 100,
        offset: Int = 0,
    ): NeteaseResult<NeteasePlaylistPage>

    suspend fun playlistDetail(playlistId: Long): NeteaseResult<NeteasePlaylistDetail>

    suspend fun dailyRecommendedSongs(): NeteaseResult<List<NeteaseSong>>

    suspend fun recommendedPlaylists(): NeteaseResult<List<NeteasePlaylistSummary>>

    suspend fun newSongs(
        limit: Int = 30,
        offset: Int = 0,
    ): NeteaseResult<NeteaseSearchPage<NeteaseSong>>

    suspend fun personalFm(): NeteaseResult<List<NeteaseSong>>

    suspend fun trashPersonalFm(songId: Long): NeteaseResult<Boolean>

    suspend fun songStream(
        songId: Long,
        preferredQuality: NeteaseAudioQuality = NeteaseAudioQuality.LOSSLESS,
    ): NeteaseResult<NeteaseSongStream>
}
