package com.smartisan.music.netease

data class NeteaseSearchPage<T>(
    val items: List<T>,
    val total: Int?,
    val offset: Int,
    val limit: Int,
) {
    val hasMore: Boolean
        get() = total?.let { offset + items.size < it } ?: (items.size >= limit)
}

data class NeteaseArtist(
    val id: Long,
    val name: String,
    val imageUrl: String? = null,
    val albumCount: Int? = null,
    val songCount: Int? = null,
    val aliases: List<String> = emptyList(),
)

data class NeteaseAlbum(
    val id: Long,
    val name: String,
    val imageUrl: String? = null,
    val artist: NeteaseArtist? = null,
    val publishTimeEpochMillis: Long? = null,
    val trackCount: Int? = null,
    val company: String? = null,
    val description: String? = null,
)

data class NeteaseSong(
    val id: Long,
    val name: String,
    val artists: List<NeteaseArtist> = emptyList(),
    val album: NeteaseAlbum? = null,
    val durationMillis: Long? = null,
    val trackNumber: Int? = null,
    val mvId: Long? = null,
)

data class NeteaseLyrics(
    val original: String?,
    val translated: String?,
)

data class NeteaseAlbumDetail(
    val album: NeteaseAlbum,
    val songs: List<NeteaseSong>,
)

data class NeteaseArtistDetail(
    val artist: NeteaseArtist,
    val hotSongs: List<NeteaseSong>,
)

data class NeteaseUserProfile(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String? = null,
    val signature: String? = null,
    val followerCount: Int? = null,
    val followingCount: Int? = null,
)

data class NeteasePlaylistSummary(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val playCount: Long? = null,
    val trackCount: Int? = null,
    val creatorUserId: Long? = null,
    val specialType: Int? = null,
    val privacy: Int? = null,
)

data class NeteasePlaylistPage(
    val playlists: List<NeteasePlaylistSummary>,
    val total: Int?,
    val hasMore: Boolean,
    val offset: Int,
    val limit: Int,
)

data class NeteasePlaylistDetail(
    val playlist: NeteasePlaylistSummary,
    val tracks: List<NeteaseSong>,
)

enum class NeteaseAudioQuality(val wireValue: String) {
    HI_RES("hires"),
    LOSSLESS("lossless"),
    EXTREME("exhigh"),
    HIGHER("higher"),
    STANDARD("standard"),
    ;

    internal fun fallbackOrder(): List<NeteaseAudioQuality> =
        entries.drop(entries.indexOf(this))

    internal companion object {
        fun fromWireValue(value: String?): NeteaseAudioQuality? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class NeteaseSongStream(
    val songId: Long,
    val url: String,
    val requestedQuality: NeteaseAudioQuality,
    val actualQuality: NeteaseAudioQuality,
    val bitrate: Long? = null,
    val sizeBytes: Long? = null,
    val format: String? = null,
    val expiresInSeconds: Long? = null,
)

enum class NeteaseNewAlbumArea(val wireValue: Int) {
    ALL(0),
    CHINA(7),
    EUROPE_AND_AMERICA(96),
    JAPAN(8),
    KOREA(16),
}
