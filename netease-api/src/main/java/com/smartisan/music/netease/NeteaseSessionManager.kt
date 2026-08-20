package com.smartisan.music.netease

import kotlinx.coroutines.flow.StateFlow

data class NeteaseSessionSnapshot(
    val authenticated: Boolean,
    val cookieCount: Int,
    val earliestExpiryEpochMillis: Long?,
    val lastPersistenceError: NeteaseApiError.Storage? = null,
)

interface NeteaseSessionManager {
    val snapshot: StateFlow<NeteaseSessionSnapshot>

    /** Imports a browser-style `name=value; name2=value2` Cookie header. */
    suspend fun importCookieHeader(cookieHeader: String): NeteaseResult<NeteaseSessionSnapshot>

    suspend fun clear(): NeteaseResult<Unit>
}
