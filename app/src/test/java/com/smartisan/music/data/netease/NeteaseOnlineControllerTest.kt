package com.smartisan.music.data.netease

import com.smartisan.music.netease.NeteaseApiError
import com.smartisan.music.netease.NeteasePlaylistPage
import com.smartisan.music.netease.NeteasePlaylistDetail
import com.smartisan.music.netease.NeteasePlaylistSummary
import com.smartisan.music.netease.NeteaseResult
import com.smartisan.music.netease.NeteaseSessionSnapshot
import com.smartisan.music.netease.NeteaseUserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NeteaseOnlineControllerTest {
    @Test
    fun clientIsLazyAndDisablingPreservesTheEncryptedSession() = runTest {
        val gateway = FakeGateway(authenticated = false)
        val factory = FakeFactory(gateway)
        val controller = NeteaseOnlineController(factory, this)

        assertEquals(0, factory.createCount)
        controller.setEnabled(true)
        advanceUntilIdle()

        assertEquals(1, factory.createCount)
        assertEquals(NeteaseOnlinePhase.LoggedOut, controller.state.value.phase)

        controller.setEnabled(false)

        assertTrue(gateway.closed)
        assertEquals(0, gateway.clearCount)
        assertEquals(NeteaseOnlinePhase.Disabled, controller.state.value.phase)
    }

    @Test
    fun authenticatedAccountLoadsEveryPageInServerOrderAndDeduplicatesIds() = runTest {
        val gateway = FakeGateway(authenticated = true).apply {
            pages[0] = successPage(
                offset = 0,
                hasMore = true,
                playlists = listOf(playlist(3), playlist(1)),
            )
            pages[2] = successPage(
                offset = 2,
                hasMore = false,
                playlists = listOf(playlist(1), playlist(2)),
            )
        }
        val controller = NeteaseOnlineController(FakeFactory(gateway), this)

        controller.setEnabled(true)
        advanceUntilIdle()

        assertEquals(NeteaseOnlinePhase.Ready, controller.state.value.phase)
        assertEquals(listOf(3L, 1L, 2L), controller.state.value.playlists.map { it.id })
        assertEquals(listOf(0, 2), gateway.requestedOffsets)
        assertTrue(gateway.requestedLimits.all { it == 500 })
    }

    @Test
    fun expiredLoginIsClearedButNetworkFailureCanBeRetried() = runTest {
        val expiredGateway = FakeGateway(authenticated = true).apply {
            accountResult = NeteaseResult.Failure(
                NeteaseApiError.AuthenticationRequired(301, "expired"),
            )
        }
        val expiredController = NeteaseOnlineController(FakeFactory(expiredGateway), this)

        expiredController.setEnabled(true)
        advanceUntilIdle()

        assertEquals(1, expiredGateway.clearCount)
        assertEquals(NeteaseOnlinePhase.LoggedOut, expiredController.state.value.phase)

        val retryGateway = FakeGateway(authenticated = true).apply {
            accountResult = NeteaseResult.Failure(NeteaseApiError.Network("offline"))
        }
        val retryController = NeteaseOnlineController(FakeFactory(retryGateway), this)
        retryController.setEnabled(true)
        advanceUntilIdle()
        assertEquals(NeteaseOnlinePhase.Error, retryController.state.value.phase)
        assertEquals(0, retryGateway.clearCount)

        retryGateway.accountResult = NeteaseResult.Success(profile)
        retryGateway.pages[0] = successPage(0, false, emptyList())
        retryController.retry()
        advanceUntilIdle()

        assertEquals(NeteaseOnlinePhase.Ready, retryController.state.value.phase)
        assertTrue(retryController.state.value.sessionPresent)
    }

    @Test
    fun importedCookieIsVerifiedAndExplicitLogoutClearsSession() = runTest {
        val gateway = FakeGateway(authenticated = false).apply {
            pages[0] = successPage(0, false, listOf(playlist(7)))
        }
        val controller = NeteaseOnlineController(FakeFactory(gateway), this)
        controller.setEnabled(true)
        advanceUntilIdle()

        assertTrue(controller.importLoginCookie("MUSIC_U=secret"))
        assertEquals("MUSIC_U=secret", gateway.importedCookie)
        assertEquals(NeteaseOnlinePhase.LoadingPlaylists, controller.state.value.phase)
        advanceUntilIdle()
        assertEquals(NeteaseOnlinePhase.Ready, controller.state.value.phase)

        assertTrue(controller.logout())
        assertFalse(gateway.sessionSnapshot.value.authenticated)
        assertEquals(NeteaseOnlinePhase.LoggedOut, controller.state.value.phase)
    }

    @Test
    fun cancellingTheLoginPageDoesNotLeaveAccountVerificationStuck() = runTest {
        val accountGate = CompletableDeferred<Unit>()
        val gateway = FakeGateway(authenticated = false).apply {
            this.accountGate = accountGate
            pages[0] = successPage(0, false, emptyList())
        }
        val controller = NeteaseOnlineController(FakeFactory(gateway), this)
        controller.setEnabled(true)
        advanceUntilIdle()

        val login = async { controller.importLoginCookie("MUSIC_U=secret") }
        runCurrent()
        assertEquals(NeteaseOnlinePhase.CheckingSession, controller.state.value.phase)

        login.cancelAndJoin()
        runCurrent()
        accountGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(NeteaseOnlinePhase.Ready, controller.state.value.phase)
    }

    @Test
    fun verifiedLoginClosesBeforePlaylistLoadingAndKeepsSessionOnPlaylistFailure() = runTest {
        val playlistGate = CompletableDeferred<Unit>()
        val gateway = FakeGateway(authenticated = false).apply {
            this.playlistGate = playlistGate
            pages[0] = NeteaseResult.Failure(NeteaseApiError.Network("offline"))
        }
        val controller = NeteaseOnlineController(FakeFactory(gateway), this)
        controller.setEnabled(true)
        advanceUntilIdle()

        assertTrue(controller.importLoginCookie("MUSIC_U=secret"))
        assertEquals(NeteaseOnlinePhase.LoadingPlaylists, controller.state.value.phase)

        playlistGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(NeteaseOnlinePhase.Error, controller.state.value.phase)
        assertTrue(controller.state.value.sessionPresent)
        assertEquals(profile, controller.state.value.profile)
        assertEquals(0, gateway.clearCount)
    }

    @Test
    fun playlistDetailsReuseTheLazyAuthenticatedGateway() = runTest {
        val expected = NeteasePlaylistDetail(
            playlist = playlist(7),
            tracks = emptyList(),
        )
        val gateway = FakeGateway(authenticated = true).apply {
            pages[0] = successPage(0, false, listOf(playlist(7)))
            detailResult = NeteaseResult.Success(expected)
        }
        val factory = FakeFactory(gateway)
        val controller = NeteaseOnlineController(factory, this)
        controller.setEnabled(true)
        advanceUntilIdle()

        assertEquals(NeteaseResult.Success(expected), controller.playlistDetail(7))
        assertEquals(listOf(7L), gateway.requestedDetailIds)
        assertEquals(1, factory.createCount)
    }

    @Test
    fun expiredSessionWhileOpeningAPlaylistIsCleared() = runTest {
        val gateway = FakeGateway(authenticated = true).apply {
            pages[0] = successPage(0, false, listOf(playlist(7)))
            detailResult = NeteaseResult.Failure(
                NeteaseApiError.AuthenticationRequired(301, "expired"),
            )
        }
        val controller = NeteaseOnlineController(FakeFactory(gateway), this)
        controller.setEnabled(true)
        advanceUntilIdle()

        val result = controller.playlistDetail(7)

        assertTrue(result is NeteaseResult.Failure)
        assertEquals(1, gateway.clearCount)
        assertEquals(NeteaseOnlinePhase.LoggedOut, controller.state.value.phase)
    }

    private class FakeFactory(
        private val gateway: FakeGateway,
    ) : NeteaseAccountGatewayFactory {
        var createCount = 0

        override suspend fun create(): NeteaseAccountGateway {
            createCount += 1
            return gateway
        }
    }

    private class FakeGateway(
        authenticated: Boolean,
    ) : NeteaseAccountGateway {
        override val sessionSnapshot = MutableStateFlow(snapshot(authenticated))
        var accountResult: NeteaseResult<NeteaseUserProfile> = NeteaseResult.Success(profile)
        var detailResult: NeteaseResult<NeteasePlaylistDetail> = NeteaseResult.Failure(
            NeteaseApiError.Api(404, "missing"),
        )
        val pages = mutableMapOf<Int, NeteaseResult<NeteasePlaylistPage>>()
        val requestedOffsets = mutableListOf<Int>()
        val requestedLimits = mutableListOf<Int>()
        val requestedDetailIds = mutableListOf<Long>()
        var importedCookie: String? = null
        var accountGate: CompletableDeferred<Unit>? = null
        var playlistGate: CompletableDeferred<Unit>? = null
        var clearCount = 0
        var closed = false

        override suspend fun importCookieHeader(
            cookieHeader: String,
        ): NeteaseResult<NeteaseSessionSnapshot> {
            importedCookie = cookieHeader
            val next = snapshot(authenticated = true)
            sessionSnapshot.value = next
            return NeteaseResult.Success(next)
        }

        override suspend fun clearSession(): NeteaseResult<Unit> {
            clearCount += 1
            sessionSnapshot.value = snapshot(authenticated = false)
            return NeteaseResult.Success(Unit)
        }

        override suspend fun currentAccount(): NeteaseResult<NeteaseUserProfile> {
            accountGate?.await()
            return accountResult
        }

        override suspend fun userPlaylists(
            userId: Long,
            limit: Int,
            offset: Int,
        ): NeteaseResult<NeteasePlaylistPage> {
            playlistGate?.await()
            requestedOffsets += offset
            requestedLimits += limit
            return pages[offset] ?: successPage(offset, false, emptyList())
        }

        override suspend fun playlistDetail(
            playlistId: Long,
        ): NeteaseResult<NeteasePlaylistDetail> {
            requestedDetailIds += playlistId
            return detailResult
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        val profile = NeteaseUserProfile(userId = 42, nickname = "Smartisan")

        fun snapshot(authenticated: Boolean) = NeteaseSessionSnapshot(
            authenticated = authenticated,
            cookieCount = if (authenticated) 1 else 0,
            earliestExpiryEpochMillis = null,
        )

        fun playlist(id: Long) = NeteasePlaylistSummary(
            id = id,
            name = "Playlist $id",
            trackCount = id.toInt(),
        )

        fun successPage(
            offset: Int,
            hasMore: Boolean,
            playlists: List<NeteasePlaylistSummary>,
        ): NeteaseResult<NeteasePlaylistPage> = NeteaseResult.Success(
            NeteasePlaylistPage(
                playlists = playlists,
                total = null,
                hasMore = hasMore,
                offset = offset,
                limit = 500,
            ),
        )
    }
}
