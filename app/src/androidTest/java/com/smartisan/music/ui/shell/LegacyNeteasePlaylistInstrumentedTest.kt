package com.smartisan.music.ui.shell

import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.smartisan.music.R
import com.smartisan.music.data.netease.NeteaseOnlinePhase
import com.smartisan.music.data.netease.NeteaseOnlineState
import com.smartisan.music.data.playlist.UserPlaylistSummary
import com.smartisan.music.data.settings.MusicSourceMode
import com.smartisan.music.data.settings.OnlineMusicSettings
import com.smartisan.music.netease.NeteaseArtist
import com.smartisan.music.netease.NeteasePlaylistDetail
import com.smartisan.music.netease.NeteasePlaylistSummary
import com.smartisan.music.netease.NeteaseResult
import com.smartisan.music.netease.NeteaseSong
import com.smartisan.music.netease.NeteaseUserProfile
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LegacyNeteasePlaylistInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loggedOutAndReadyStatesReuseTheClickablePlaylistRootWithoutLocalActions() {
        lateinit var showReadyState: () -> Unit
        var clickedPlaylistId: String? = null
        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    NeteaseOnlineState(
                        phase = NeteaseOnlinePhase.LoggedOut,
                    ),
                )
            }
            SideEffect {
                showReadyState = {
                    state = NeteaseOnlineState(
                        phase = NeteaseOnlinePhase.Ready,
                        sessionPresent = true,
                        profile = NeteaseUserProfile(42, "Smartisan"),
                        playlists = listOf(
                            NeteasePlaylistSummary(
                                id = 7,
                                name = "Remote favorites",
                                trackCount = 23,
                            ),
                        ),
                    )
                }
            }
            val playlists = if (state.phase == NeteaseOnlinePhase.Ready) {
                state.playlists.map { playlist -> playlist.toLegacyPlaylistSummary() }
            } else {
                emptyList()
            }
            LegacyPlaylistRootPage(
                active = true,
                playlists = playlists,
                editMode = false,
                selectedPlaylistIds = emptySet(),
                onCreatePlaylist = {},
                onRenamePlaylist = {},
                onPlaylistClick = { playlist -> clickedPlaylistId = playlist.id },
                onPlaylistSelectionChange = { _, _ -> },
                showAddRow = false,
                blankContent = LegacyPlaylistBlankContent(
                    primaryText = composeRule.activity.getString(
                        R.string.netease_playlists_login_required,
                    ),
                    secondaryText = composeRule.activity.getString(
                        R.string.netease_playlists_login_hint,
                    ),
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }

        onView(withText(R.string.netease_playlists_login_required)).check(matches(isDisplayed()))
        onView(withText(R.string.new_playlist)).check(matches(not(isDisplayed())))

        composeRule.runOnIdle { showReadyState() }
        composeRule.waitForIdle()

        onView(withText("Remote favorites")).check(matches(isDisplayed()))
        onView(
            allOf(
                withText(
                    composeRule.activity.resources.getQuantityString(
                        R.plurals.legacy_playlist_song_count,
                        23,
                        23,
                    ),
                ),
                isDisplayed(),
            ),
        ).check(matches(isDisplayed()))
        onView(withText(R.string.new_playlist)).check(matches(not(isDisplayed())))
        clickFirstPlaylistRow()
        composeRule.runOnIdle {
            assertEquals("netease:7", clickedPlaylistId)
        }
    }

    @Test
    fun clickingAnOnlinePlaylistUsesTheExistingDetailStackAndTrackList() {
        val playlist = NeteasePlaylistSummary(
            id = 7,
            name = "Remote favorites",
            trackCount = 1,
        )
        composeRule.setContent {
            LegacyPortPlaylistPage(
                mediaItems = emptyList(),
                libraryLoaded = true,
                active = true,
                rootEditMode = false,
                selectedPlaylistIds = emptySet(),
                rootDeleteRequested = false,
                hiddenMediaIds = emptySet(),
                onlineMusicSettings = OnlineMusicSettings(MusicSourceMode.Netease),
                neteaseState = NeteaseOnlineState(
                    phase = NeteaseOnlinePhase.Ready,
                    sessionPresent = true,
                    profile = NeteaseUserProfile(42, "Smartisan"),
                    playlists = listOf(playlist),
                ),
                onNeteaseRetry = {},
                onNeteasePlaylistDetail = {
                    NeteaseResult.Success(
                        NeteasePlaylistDetail(
                            playlist = playlist,
                            tracks = listOf(
                                NeteaseSong(
                                    id = 99,
                                    name = "A remote track",
                                    artists = listOf(NeteaseArtist(3, "Remote artist")),
                                    durationMillis = 183_000,
                                ),
                            ),
                        ),
                    )
                },
                onTrackMoreClick = {},
                onRootEditModeChange = {},
                onSelectedPlaylistIdsChange = {},
                onRootDeleteRequestConsumed = {},
                onRootPageActiveChanged = {},
                onAddModeActiveChanged = {},
                onLibraryNeeded = {},
                onSearchClick = {},
                onClose = null,
                closePredictiveBackState = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        onView(withText("Remote favorites")).check(matches(isDisplayed()))
        clickFirstPlaylistRow()
        onView(withText("A remote track")).check(matches(isDisplayed()))
        onView(withText("Remote artist")).check(matches(isDisplayed()))
        onView(withText(R.string.s_remove_track_list)).check(matches(not(isDisplayed())))
        onView(withText(R.string.s_edit_track_list)).check(matches(not(isDisplayed())))
    }

    private fun clickFirstPlaylistRow() {
        composeRule.runOnIdle {
            val listView = requireNotNull(
                composeRule.activity.window.decorView.findPlaylistListView(),
            )
            val row = requireNotNull(listView.getChildAt(0))
            check(listView.performItemClick(row, 0, listView.adapter.getItemId(0)))
        }
    }

    private fun View.findPlaylistListView(): ListView? {
        if (this is ListView && adapter?.getItem(0) is UserPlaylistSummary) {
            return this
        }
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            getChildAt(index).findPlaylistListView()?.let { return it }
        }
        return null
    }

}
