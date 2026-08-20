package com.smartisan.music.data.netease

import android.content.Context
import com.smartisan.music.netease.NeteaseApiClient
import com.smartisan.music.netease.NeteaseApiError
import com.smartisan.music.netease.NeteasePlaylistPage
import com.smartisan.music.netease.NeteasePlaylistDetail
import com.smartisan.music.netease.NeteasePlaylistSummary
import com.smartisan.music.netease.NeteaseResult
import com.smartisan.music.netease.NeteaseSessionSnapshot
import com.smartisan.music.netease.NeteaseUserProfile
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class NeteaseOnlinePhase {
    Disabled,
    CheckingSession,
    LoggedOut,
    LoadingPlaylists,
    Ready,
    Error,
}

internal data class NeteaseOnlineState(
    val phase: NeteaseOnlinePhase = NeteaseOnlinePhase.Disabled,
    val sessionPresent: Boolean = false,
    val profile: NeteaseUserProfile? = null,
    val playlists: List<NeteasePlaylistSummary> = emptyList(),
    val error: NeteaseApiError? = null,
) {
    val enabled: Boolean
        get() = phase != NeteaseOnlinePhase.Disabled
}

internal interface NeteaseAccountGateway : Closeable {
    val sessionSnapshot: StateFlow<NeteaseSessionSnapshot>

    suspend fun importCookieHeader(cookieHeader: String): NeteaseResult<NeteaseSessionSnapshot>

    suspend fun clearSession(): NeteaseResult<Unit>

    suspend fun currentAccount(): NeteaseResult<NeteaseUserProfile>

    suspend fun userPlaylists(
        userId: Long,
        limit: Int,
        offset: Int,
    ): NeteaseResult<NeteasePlaylistPage>

    suspend fun playlistDetail(playlistId: Long): NeteaseResult<NeteasePlaylistDetail>
}

internal fun interface NeteaseAccountGatewayFactory {
    suspend fun create(): NeteaseAccountGateway
}

internal class DefaultNeteaseAccountGatewayFactory(
    context: Context,
) : NeteaseAccountGatewayFactory {
    private val applicationContext = context.applicationContext

    override suspend fun create(): NeteaseAccountGateway {
        return withContext(Dispatchers.IO) {
            val client = NeteaseApiClient.create(applicationContext)
            NeteaseAccountGatewayClient(client)
        }
    }
}

private class NeteaseAccountGatewayClient(
    private val client: NeteaseApiClient,
) : NeteaseAccountGateway {
    override val sessionSnapshot: StateFlow<NeteaseSessionSnapshot>
        get() = client.sessionManager.snapshot

    override suspend fun importCookieHeader(
        cookieHeader: String,
    ): NeteaseResult<NeteaseSessionSnapshot> = withContext(Dispatchers.IO) {
        client.sessionManager.importCookieHeader(cookieHeader)
    }

    override suspend fun clearSession(): NeteaseResult<Unit> = withContext(Dispatchers.IO) {
        client.sessionManager.clear()
    }

    override suspend fun currentAccount(): NeteaseResult<NeteaseUserProfile> = withContext(Dispatchers.IO) {
        client.api.currentAccount()
    }

    override suspend fun userPlaylists(
        userId: Long,
        limit: Int,
        offset: Int,
    ): NeteaseResult<NeteasePlaylistPage> = withContext(Dispatchers.IO) {
        client.api.userPlaylists(userId, limit, offset)
    }

    override suspend fun playlistDetail(
        playlistId: Long,
    ): NeteaseResult<NeteasePlaylistDetail> = withContext(Dispatchers.IO) {
        client.api.playlistDetail(playlistId)
    }

    override fun close() {
        client.close()
    }
}

internal class NeteaseOnlineController(
    private val gatewayFactory: NeteaseAccountGatewayFactory,
    private val scope: CoroutineScope,
) : Closeable {
    private val mutableState = MutableStateFlow(NeteaseOnlineState())
    val state: StateFlow<NeteaseOnlineState> = mutableState.asStateFlow()

    private var enabled = false
    private var gateway: NeteaseAccountGateway? = null
    private var refreshJob: Job? = null

    fun setEnabled(nextEnabled: Boolean) {
        if (enabled == nextEnabled) return
        enabled = nextEnabled
        refreshJob?.cancel()
        refreshJob = null
        if (!nextEnabled) {
            closeGateway()
            mutableState.value = NeteaseOnlineState()
            return
        }
        mutableState.value = NeteaseOnlineState(
            phase = NeteaseOnlinePhase.CheckingSession,
        )
        refreshJob = scope.launch {
            refreshInternal()
        }
    }

    fun retry() {
        if (!enabled) return
        refreshJob?.cancel()
        refreshJob = scope.launch {
            refreshInternal()
        }
    }

    suspend fun importLoginCookie(cookieHeader: String): Boolean {
        try {
            if (!enabled) return false
            refreshJob?.cancelAndJoin()
            val activeGateway = gateway ?: createGatewayOrReportError() ?: return false
            mutableState.value = mutableState.value.copy(
                phase = NeteaseOnlinePhase.CheckingSession,
                error = null,
            )
            return when (val result = activeGateway.importCookieHeader(cookieHeader)) {
                is NeteaseResult.Success -> {
                    when (val accountResult = activeGateway.currentAccount()) {
                        is NeteaseResult.Failure -> {
                            handleAccountFailure(activeGateway, accountResult.error)
                            false
                        }
                        is NeteaseResult.Success -> {
                            mutableState.value = NeteaseOnlineState(
                                phase = NeteaseOnlinePhase.LoadingPlaylists,
                                sessionPresent = true,
                                profile = accountResult.value,
                            )
                            refreshJob = scope.launch {
                                loadAllPlaylists(activeGateway, accountResult.value)
                            }
                            true
                        }
                    }
                }
                is NeteaseResult.Failure -> {
                    mutableState.value = NeteaseOnlineState(
                        phase = NeteaseOnlinePhase.Error,
                        sessionPresent = activeGateway.sessionSnapshot.value.authenticated,
                        error = result.error,
                    )
                    false
                }
            }
        } catch (exception: CancellationException) {
            // The login WebView owns the awaiting coroutine. If the user closes it during
            // verification, continue recovery in the application scope so state cannot remain
            // permanently stuck in CheckingSession after a Cookie was already persisted.
            retry()
            throw exception
        } catch (exception: Exception) {
            if (enabled) {
                mutableState.value = mutableState.value.copy(
                    phase = NeteaseOnlinePhase.Error,
                    sessionPresent = gateway?.sessionSnapshot?.value?.authenticated == true,
                    error = NeteaseApiError.Network(
                        message = "Unable to verify the NetEase login",
                        cause = exception,
                    ),
                )
            }
            return false
        }
    }

    suspend fun logout(): Boolean {
        if (!enabled) return false
        refreshJob?.cancelAndJoin()
        val activeGateway = gateway ?: createGatewayOrReportError() ?: return false
        return when (val result = activeGateway.clearSession()) {
            is NeteaseResult.Success -> {
                mutableState.value = NeteaseOnlineState(
                    phase = NeteaseOnlinePhase.LoggedOut,
                )
                true
            }
            is NeteaseResult.Failure -> {
                mutableState.value = mutableState.value.copy(
                    phase = NeteaseOnlinePhase.Error,
                    error = result.error,
                )
                false
            }
        }
    }

    suspend fun playlistDetail(playlistId: Long): NeteaseResult<NeteasePlaylistDetail> {
        if (!enabled || playlistId <= 0L) {
            return NeteaseResult.Failure(
                NeteaseApiError.InvalidRequest("Online mode is disabled or the playlist ID is invalid"),
            )
        }
        val activeGateway = gateway ?: createGatewayOrReportError()
            ?: return NeteaseResult.Failure(
                NeteaseApiError.Storage("Unable to initialize the NetEase session"),
            )
        return try {
            activeGateway.playlistDetail(playlistId).also { result ->
                val error = (result as? NeteaseResult.Failure)?.error
                if (error is NeteaseApiError.AuthenticationRequired) {
                    handleAccountFailure(activeGateway, error)
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            NeteaseResult.Failure(
                NeteaseApiError.Network(
                    message = "Unable to load the NetEase playlist",
                    cause = exception,
                ),
            )
        }
    }

    override fun close() {
        enabled = false
        refreshJob?.cancel()
        refreshJob = null
        closeGateway()
        mutableState.value = NeteaseOnlineState()
    }

    private suspend fun refreshInternal(existingGateway: NeteaseAccountGateway? = null) {
        try {
            if (!enabled) return
            val activeGateway = existingGateway ?: gateway ?: createGatewayOrReportError() ?: return
            val sessionPresent = activeGateway.sessionSnapshot.value.authenticated
            if (!sessionPresent) {
                mutableState.value = NeteaseOnlineState(
                    phase = NeteaseOnlinePhase.LoggedOut,
                )
                return
            }
            mutableState.value = NeteaseOnlineState(
                phase = NeteaseOnlinePhase.CheckingSession,
                sessionPresent = true,
            )
            when (val accountResult = activeGateway.currentAccount()) {
                is NeteaseResult.Failure -> {
                    handleAccountFailure(activeGateway, accountResult.error)
                }
                is NeteaseResult.Success -> {
                    loadAllPlaylists(activeGateway, accountResult.value)
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (enabled) {
                mutableState.value = mutableState.value.copy(
                    phase = NeteaseOnlinePhase.Error,
                    error = NeteaseApiError.Network(
                        message = "Unable to refresh the NetEase account",
                        cause = exception,
                    ),
                )
            }
        }
    }

    private suspend fun handleAccountFailure(
        activeGateway: NeteaseAccountGateway,
        error: NeteaseApiError,
    ) {
        if (error is NeteaseApiError.AuthenticationRequired) {
            when (val clearResult = activeGateway.clearSession()) {
                is NeteaseResult.Success -> {
                    mutableState.value = NeteaseOnlineState(
                        phase = NeteaseOnlinePhase.LoggedOut,
                    )
                }
                is NeteaseResult.Failure -> {
                    mutableState.value = NeteaseOnlineState(
                        phase = NeteaseOnlinePhase.Error,
                        sessionPresent = true,
                        error = clearResult.error,
                    )
                }
            }
            return
        }
        mutableState.value = NeteaseOnlineState(
            phase = NeteaseOnlinePhase.Error,
            sessionPresent = true,
            error = error,
        )
    }

    private suspend fun loadAllPlaylists(
        activeGateway: NeteaseAccountGateway,
        profile: NeteaseUserProfile,
    ) {
        mutableState.value = NeteaseOnlineState(
            phase = NeteaseOnlinePhase.LoadingPlaylists,
            sessionPresent = true,
            profile = profile,
        )
        val playlistsById = linkedMapOf<Long, NeteasePlaylistSummary>()
        var offset = 0
        while (enabled) {
            when (
                val pageResult = activeGateway.userPlaylists(
                    userId = profile.userId,
                    limit = PlaylistPageSize,
                    offset = offset,
                )
            ) {
                is NeteaseResult.Failure -> {
                    if (pageResult.error is NeteaseApiError.AuthenticationRequired) {
                        handleAccountFailure(activeGateway, pageResult.error)
                    } else {
                        mutableState.value = NeteaseOnlineState(
                            phase = NeteaseOnlinePhase.Error,
                            sessionPresent = true,
                            profile = profile,
                            playlists = playlistsById.values.toList(),
                            error = pageResult.error,
                        )
                    }
                    return
                }
                is NeteaseResult.Success -> {
                    val page = pageResult.value
                    page.playlists.forEach { playlist -> playlistsById.putIfAbsent(playlist.id, playlist) }
                    val reachedTotal = page.total?.let { total -> playlistsById.size >= total } == true
                    if (!page.hasMore || reachedTotal || page.playlists.isEmpty()) {
                        mutableState.value = NeteaseOnlineState(
                            phase = NeteaseOnlinePhase.Ready,
                            sessionPresent = true,
                            profile = profile,
                            playlists = playlistsById.values.toList(),
                        )
                        return
                    }
                    offset += page.playlists.size
                }
            }
        }
    }

    private suspend fun createGatewayOrReportError(): NeteaseAccountGateway? {
        return try {
            gatewayFactory.create().also { created ->
                if (!enabled) {
                    created.close()
                    return null
                }
                gateway = created
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (enabled) {
                mutableState.value = NeteaseOnlineState(
                    phase = NeteaseOnlinePhase.Error,
                    error = NeteaseApiError.Storage(
                        message = "Unable to initialize the NetEase session",
                        cause = exception,
                    ),
                )
            }
            null
        }
    }

    private fun closeGateway() {
        gateway?.close()
        gateway = null
    }

    private companion object {
        const val PlaylistPageSize = 500
    }
}
