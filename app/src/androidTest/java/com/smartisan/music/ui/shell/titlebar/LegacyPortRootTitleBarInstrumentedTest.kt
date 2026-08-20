package com.smartisan.music.ui.shell.titlebar

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.shell.LegacyPortPageStackTransition
import com.smartisan.music.ui.shell.legacyPortRootTitleBarForDestination
import com.smartisan.music.ui.shell.shouldKeepLegacyPortStableRootTitleBarHost
import com.smartisan.music.ui.shell.usesLegacyPortStableRootTitleBar
import com.smartisan.music.ui.widgets.legacy.TitleBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class LegacyPortRootTitleBarInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rootTitleBarMovesBetweenParentsWithoutRecreatingAndroidView() {
        val updatedInstances = mutableListOf<TitleBar>()
        lateinit var moveTitleBar: () -> Unit

        composeRule.setContent {
            var moved by remember { mutableStateOf(false) }
            val rootTitleBar = rememberLegacyPortRootTitleBar()
            SideEffect {
                moveTitleBar = { moved = !moved }
            }

            val content: @androidx.compose.runtime.Composable () -> Unit = {
                rootTitleBar(Modifier.fillMaxWidth(), false) { titleBar ->
                    updatedInstances += titleBar
                    titleBar.setCenterText(if (moved) "Moved" else "Initial")
                }
            }
            if (moved) {
                Box { content() }
            } else {
                Column { content() }
            }
        }

        lateinit var initialInstance: TitleBar
        composeRule.runOnIdle {
            initialInstance = updatedInstances.last()
            assertEquals("Initial", initialInstance.getTitleView().text.toString())
            moveTitleBar()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val movedInstance = updatedInstances.last()
            assertSame(initialInstance, movedInstance)
            assertEquals("Moved", movedInstance.getTitleView().text.toString())
        }
    }

    @Test
    fun stableTitleBarVisibilityChangeDoesNotReconfigureAndroidView() {
        val updatedInstances = mutableListOf<TitleBar>()
        lateinit var toggleVisibility: () -> Unit
        lateinit var changeLayout: () -> Unit

        composeRule.setContent {
            var visible by remember { mutableStateOf(true) }
            var layoutVersion by remember { mutableStateOf(0) }
            SideEffect {
                toggleVisibility = { visible = !visible }
                changeLayout = { layoutVersion += 1 }
            }

            LegacyPortSmartisanTitleBar(
                modifier = Modifier.alpha(if (visible) 1f else 0f),
                viewVisible = visible,
                updateKey = layoutVersion,
            ) { titleBar ->
                updatedInstances += titleBar
                titleBar.setCenterText("Layout $layoutVersion")
            }
        }

        lateinit var initialInstance: TitleBar
        composeRule.runOnIdle {
            assertEquals(1, updatedInstances.size)
            initialInstance = updatedInstances.single()
            toggleVisibility()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, updatedInstances.size)
            assertSame(initialInstance, updatedInstances.single())
            assertEquals("Layout 0", initialInstance.getTitleView().text.toString())
            changeLayout()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(2, updatedInstances.size)
            assertSame(initialInstance, updatedInstances.last())
            assertEquals("Layout 1", initialInstance.getTitleView().text.toString())
        }
    }

    @Test
    fun songsAndMoreShareStableTitleBarInstancesAndCallbacks() {
        lateinit var selectDestination: (MusicDestination) -> Unit
        var settingsClickCount = 0
        var searchClickCount = 0

        composeRule.setContent {
            var destination by remember { mutableStateOf(MusicDestination.Songs) }
            SideEffect {
                selectDestination = { destination = it }
            }

            LegacyPortStableRootTitleBarHost(
                destination = destination,
                visible = true,
                songsEditMode = false,
                selectedSongCount = 0,
                albumEditMode = false,
                selectedAlbumCount = 0,
                albumViewMode = AlbumViewMode.List,
                artistAlbumViewMode = AlbumViewMode.List,
                playlistEditMode = false,
                playlistSelectedCount = 0,
                onEnterSongsEditMode = {},
                onExitSongsEditMode = {},
                onRequestDeleteSelected = {},
                onEnterAlbumEditMode = {},
                onExitAlbumEditMode = {},
                onToggleAlbumViewMode = {},
                onRootBack = null,
                onSearchClick = { searchClickCount += 1 },
                onOpenMoreSettings = { settingsClickCount += 1 },
                onEnterPlaylistEditMode = {},
                onExitPlaylistEditMode = {},
                onDeleteSelectedPlaylists = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }

        lateinit var initialTitleBars: Map<String, TitleBar>
        lateinit var initialSongsLeftView: View
        lateinit var initialMoreLeftView: View
        lateinit var initialMoreRightView: View
        composeRule.runOnIdle {
            initialTitleBars = composeRule.activity.titleBarsByTitle()
            assertEquals(5, initialTitleBars.size)
            assertEquals(1, initialTitleBars.values.count { it.visibility == View.VISIBLE })
            assertEquals(
                composeRule.activity.getString(MusicDestination.Songs.labelRes),
                initialTitleBars.values.single { it.visibility == View.VISIBLE }.getTitleView().text.toString(),
            )
            initialSongsLeftView = requireNotNull(
                initialTitleBars.getValue(
                    composeRule.activity.getString(MusicDestination.Songs.labelRes),
                ).getLeftViewByIndex(0),
            )
            val moreTitleBar = initialTitleBars.getValue(
                composeRule.activity.getString(MusicDestination.More.labelRes),
            )
            initialMoreLeftView = requireNotNull(moreTitleBar.getLeftViewByIndex(0))
            initialMoreRightView = requireNotNull(moreTitleBar.getRightViewByIndex(0))
            selectDestination(MusicDestination.More)
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val switchedTitleBars = composeRule.activity.titleBarsByTitle()
            assertEquals(initialTitleBars.keys, switchedTitleBars.keys)
            initialTitleBars.forEach { (title, titleBar) ->
                assertSame(titleBar, switchedTitleBars.getValue(title))
            }
            assertEquals(1, switchedTitleBars.values.count { it.visibility == View.VISIBLE })
            val moreTitleBar = switchedTitleBars.getValue(
                composeRule.activity.getString(MusicDestination.More.labelRes),
            )
            assertEquals(View.VISIBLE, moreTitleBar.visibility)
            assertSame(initialMoreLeftView, moreTitleBar.getLeftViewByIndex(0))
            assertSame(initialMoreRightView, moreTitleBar.getRightViewByIndex(0))
            moreTitleBar.getLeftViewByIndex(0)?.performClick()
            moreTitleBar.getRightViewByIndex(0)?.performClick()
            selectDestination(MusicDestination.Songs)
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val returnedTitleBars = composeRule.activity.titleBarsByTitle()
            initialTitleBars.forEach { (title, titleBar) ->
                assertSame(titleBar, returnedTitleBars.getValue(title))
            }
            val songsTitleBar = returnedTitleBars.getValue(
                composeRule.activity.getString(MusicDestination.Songs.labelRes),
            )
            assertEquals(View.VISIBLE, songsTitleBar.visibility)
            assertSame(initialSongsLeftView, songsTitleBar.getLeftViewByIndex(0))
            assertEquals(1, settingsClickCount)
            assertEquals(1, searchClickCount)
        }
    }

    @Test
    fun stableTitleBarHostSurvivesNonStableBottomDestination() {
        lateinit var selectDestination: (MusicDestination) -> Unit

        composeRule.setContent {
            var destination by remember { mutableStateOf(MusicDestination.Songs) }
            SideEffect {
                selectDestination = { destination = it }
            }

            if (shouldKeepLegacyPortStableRootTitleBarHost(destination, presentedFromMore = false)) {
                LegacyPortStableRootTitleBarHost(
                    destination = destination,
                    visible = destination.usesLegacyPortStableRootTitleBar(),
                    songsEditMode = false,
                    selectedSongCount = 0,
                    albumEditMode = false,
                    selectedAlbumCount = 0,
                    albumViewMode = AlbumViewMode.List,
                    artistAlbumViewMode = AlbumViewMode.List,
                    playlistEditMode = false,
                    playlistSelectedCount = 0,
                    onEnterSongsEditMode = {},
                    onExitSongsEditMode = {},
                    onRequestDeleteSelected = {},
                    onEnterAlbumEditMode = {},
                    onExitAlbumEditMode = {},
                    onToggleAlbumViewMode = {},
                    onRootBack = null,
                    onSearchClick = {},
                    onOpenMoreSettings = {},
                    onEnterPlaylistEditMode = {},
                    onExitPlaylistEditMode = {},
                    onDeleteSelectedPlaylists = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        lateinit var initialTitleBars: Map<String, TitleBar>
        composeRule.runOnIdle {
            initialTitleBars = composeRule.activity.titleBarsByTitle()
            assertEquals(5, initialTitleBars.size)
            selectDestination(MusicDestination.Genre)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val parkedTitleBars = composeRule.activity.titleBarsByTitle()
            initialTitleBars.forEach { (title, titleBar) ->
                assertSame(titleBar, parkedTitleBars.getValue(title))
            }
            assertEquals(0, parkedTitleBars.values.count { it.visibility == View.VISIBLE })
            selectDestination(MusicDestination.More)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val returnedTitleBars = composeRule.activity.titleBarsByTitle()
            initialTitleBars.forEach { (title, titleBar) ->
                assertSame(titleBar, returnedTitleBars.getValue(title))
            }
            assertEquals(1, returnedTitleBars.values.count { it.visibility == View.VISIBLE })
        }
    }

    @Test
    fun movableTitleBarSurvivesMoreDestinationExitAnimation() {
        val updatedInstances = mutableListOf<TitleBar>()
        lateinit var returnToMore: () -> Unit

        composeRule.setContent {
            var currentDestination by remember { mutableStateOf(MusicDestination.Genre) }
            var presentedFromMore by remember { mutableStateOf(true) }
            val rootTitleBar = rememberLegacyPortRootTitleBar()
            SideEffect {
                returnToMore = {
                    presentedFromMore = false
                    currentDestination = MusicDestination.More
                }
            }

            LegacyPortPageStackTransition(
                secondaryKey = currentDestination.takeIf { presentedFromMore },
                primaryContent = {},
                secondaryContent = { destination ->
                    LegacyPortRootTitleBar(
                        rootTitleBar = legacyPortRootTitleBarForDestination(rootTitleBar, destination),
                    ) { titleBar ->
                        updatedInstances += titleBar
                        titleBar.setCenterText(destination.name)
                    }
                },
            )
        }

        composeRule.waitForIdle()
        lateinit var initialInstance: TitleBar
        composeRule.runOnIdle {
            initialInstance = updatedInstances.last()
            composeRule.mainClock.autoAdvance = false
            returnToMore()
        }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle {
            assertEquals(1, updatedInstances.distinctBy(System::identityHashCode).size)
            assertSame(initialInstance, updatedInstances.last())
        }
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun titleBarExitCompletionRunsAfterReplacementAnimation() {
        lateinit var closeDetail: () -> Unit
        var exitCompletionCount = 0

        composeRule.setContent {
            var detailKey by remember { mutableStateOf<String?>("Detail") }
            SideEffect {
                closeDetail = { detailKey = null }
            }
            LegacyPortTitleBarTransition(
                secondaryKey = detailKey,
                primaryVisibleWhenIdle = false,
                onSecondaryExitComplete = { exitCompletionCount += 1 },
                primaryContent = {},
                secondaryContent = {},
            )
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            composeRule.mainClock.autoAdvance = false
            closeDetail()
        }
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.runOnIdle {
            assertEquals(0, exitCompletionCount)
        }
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.runOnIdle {
            assertEquals(1, exitCompletionCount)
        }
        composeRule.mainClock.autoAdvance = true
    }

    private fun ComponentActivity.titleBarsByTitle(): Map<String, TitleBar> {
        return window.decorView
            .descendantTitleBars()
            .associateBy { it.getTitleView().text.toString() }
    }

    private fun View.descendantTitleBars(): List<TitleBar> {
        val titleBars = mutableListOf<TitleBar>()
        if (this is TitleBar) {
            titleBars += this
        }
        if (this is ViewGroup) {
            repeat(childCount) { index ->
                titleBars += getChildAt(index).descendantTitleBars()
            }
        }
        return titleBars
    }
}
