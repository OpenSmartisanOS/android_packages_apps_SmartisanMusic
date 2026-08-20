package com.smartisan.music.ui.shell.titlebar

import android.view.View
import android.widget.CheckBox
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import com.smartisan.music.R
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.shell.LegacyArtistTarget
import com.smartisan.music.ui.shell.setupLegacyPlaylistTitleBar
import com.smartisan.music.ui.shell.showsAlbumSwitch
import com.smartisan.music.ui.widgets.legacy.TitleBar

@Composable
internal fun LegacyPortTitleBar(
    destination: MusicDestination,
    songsEditMode: Boolean,
    selectedSongCount: Int,
    albumEditMode: Boolean,
    selectedAlbumCount: Int,
    albumDetailTitle: String?,
    albumViewMode: AlbumViewMode,
    artistTarget: LegacyArtistTarget?,
    artistAlbumViewMode: AlbumViewMode,
    onEnterSongsEditMode: () -> Unit,
    onExitSongsEditMode: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onEnterAlbumEditMode: () -> Unit,
    onExitAlbumEditMode: () -> Unit,
    onToggleAlbumViewMode: () -> Unit,
    onAlbumDetailBack: () -> Unit,
    onArtistBack: () -> Unit,
    onToggleArtistAlbumViewMode: () -> Unit,
    onRootBack: (() -> Unit)?,
    onSearchClick: () -> Unit,
    onOpenMoreSettings: () -> Unit = {},
    playlistEditMode: Boolean = false,
    playlistSelectedCount: Int = 0,
    playlistActionsEnabled: Boolean = true,
    onEnterPlaylistEditMode: () -> Unit = {},
    onExitPlaylistEditMode: () -> Unit = {},
    onDeleteSelectedPlaylists: () -> Unit = {},
    rootTitleBar: LegacyPortRootTitleBar? = null,
    modifier: Modifier = Modifier,
) {
    LegacyPortRootTitleBar(
        rootTitleBar = rootTitleBar,
        modifier = modifier,
    ) { titleBar ->
        titleBar.setupLegacyMainTitleBar(
            destination = destination,
            songsEditMode = songsEditMode,
            selectedSongCount = selectedSongCount,
            albumEditMode = albumEditMode,
            selectedAlbumCount = selectedAlbumCount,
            albumDetailTitle = albumDetailTitle,
            albumViewMode = albumViewMode,
            artistTarget = artistTarget,
            artistAlbumViewMode = artistAlbumViewMode,
            onEnterSongsEditMode = onEnterSongsEditMode,
            onExitSongsEditMode = onExitSongsEditMode,
            onRequestDeleteSelected = onRequestDeleteSelected,
            onEnterAlbumEditMode = onEnterAlbumEditMode,
            onExitAlbumEditMode = onExitAlbumEditMode,
            onToggleAlbumViewMode = onToggleAlbumViewMode,
            onAlbumDetailBack = onAlbumDetailBack,
            onArtistBack = onArtistBack,
            onToggleArtistAlbumViewMode = onToggleArtistAlbumViewMode,
            onRootBack = onRootBack,
            onSearchClick = onSearchClick,
            onOpenMoreSettings = onOpenMoreSettings,
            playlistEditMode = playlistEditMode,
            playlistSelectedCount = playlistSelectedCount,
            playlistActionsEnabled = playlistActionsEnabled,
            onEnterPlaylistEditMode = onEnterPlaylistEditMode,
            onExitPlaylistEditMode = onExitPlaylistEditMode,
            onDeleteSelectedPlaylists = onDeleteSelectedPlaylists,
        )
    }
}

@Composable
internal fun LegacyPortStableRootTitleBarHost(
    destination: MusicDestination,
    visible: Boolean,
    songsEditMode: Boolean,
    selectedSongCount: Int,
    albumEditMode: Boolean,
    selectedAlbumCount: Int,
    albumViewMode: AlbumViewMode,
    artistAlbumViewMode: AlbumViewMode,
    playlistEditMode: Boolean,
    playlistSelectedCount: Int,
    playlistActionsEnabled: Boolean = true,
    onEnterSongsEditMode: () -> Unit,
    onExitSongsEditMode: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onEnterAlbumEditMode: () -> Unit,
    onExitAlbumEditMode: () -> Unit,
    onToggleAlbumViewMode: () -> Unit,
    onRootBack: (() -> Unit)?,
    onSearchClick: () -> Unit,
    onOpenMoreSettings: () -> Unit,
    onEnterPlaylistEditMode: () -> Unit,
    onExitPlaylistEditMode: () -> Unit,
    onDeleteSelectedPlaylists: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnEnterSongsEditMode by rememberUpdatedState(onEnterSongsEditMode)
    val latestOnExitSongsEditMode by rememberUpdatedState(onExitSongsEditMode)
    val latestOnRequestDeleteSelected by rememberUpdatedState(onRequestDeleteSelected)
    val latestOnEnterAlbumEditMode by rememberUpdatedState(onEnterAlbumEditMode)
    val latestOnExitAlbumEditMode by rememberUpdatedState(onExitAlbumEditMode)
    val latestOnToggleAlbumViewMode by rememberUpdatedState(onToggleAlbumViewMode)
    val latestOnRootBack by rememberUpdatedState(onRootBack)
    val latestOnSearchClick by rememberUpdatedState(onSearchClick)
    val latestOnOpenMoreSettings by rememberUpdatedState(onOpenMoreSettings)
    val latestOnEnterPlaylistEditMode by rememberUpdatedState(onEnterPlaylistEditMode)
    val latestOnExitPlaylistEditMode by rememberUpdatedState(onExitPlaylistEditMode)
    val latestOnDeleteSelectedPlaylists by rememberUpdatedState(onDeleteSelectedPlaylists)
    val stableOnEnterSongsEditMode = remember { { latestOnEnterSongsEditMode() } }
    val stableOnExitSongsEditMode = remember { { latestOnExitSongsEditMode() } }
    val stableOnRequestDeleteSelected = remember { { latestOnRequestDeleteSelected() } }
    val stableOnEnterAlbumEditMode = remember { { latestOnEnterAlbumEditMode() } }
    val stableOnExitAlbumEditMode = remember { { latestOnExitAlbumEditMode() } }
    val stableOnToggleAlbumViewMode = remember { { latestOnToggleAlbumViewMode() } }
    val stableOnRootBack = remember {
        {
            latestOnRootBack?.invoke()
            Unit
        }
    }
    val stableOnSearchClick = remember { { latestOnSearchClick() } }
    val stableOnOpenMoreSettings = remember { { latestOnOpenMoreSettings() } }
    val stableOnEnterPlaylistEditMode = remember { { latestOnEnterPlaylistEditMode() } }
    val stableOnExitPlaylistEditMode = remember { { latestOnExitPlaylistEditMode() } }
    val stableOnDeleteSelectedPlaylists = remember { { latestOnDeleteSelectedPlaylists() } }
    val hasRootBack = onRootBack != null

    Box(modifier = modifier) {
        StableRootTitleBarDestinations.forEach { layerDestination ->
            val layerEditMode = when (layerDestination) {
                MusicDestination.Playlist -> playlistEditMode
                MusicDestination.Songs -> songsEditMode
                MusicDestination.Album -> albumEditMode
                else -> false
            }
            val layerSelectionEnabled = when (layerDestination) {
                MusicDestination.Playlist -> playlistSelectedCount > 0
                MusicDestination.Songs -> selectedSongCount > 0
                MusicDestination.Album -> selectedAlbumCount > 0
                else -> false
            }
            val updateKey = LegacyPortStableRootTitleBarKey(
                destination = layerDestination,
                editMode = layerEditMode,
                selectionEnabled = layerSelectionEnabled,
                albumViewMode = albumViewMode.takeIf {
                    layerDestination == MusicDestination.Album
                },
                hasRootBack = hasRootBack,
                playlistActionsEnabled = playlistActionsEnabled.takeIf {
                    layerDestination == MusicDestination.Playlist
                },
            )
            val active = visible && destination == layerDestination
            LegacyPortSmartisanTitleBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (active) 1f else 0f)
                    .zIndex(if (active) 1f else 0f),
                viewVisible = active,
                updateKey = updateKey,
            ) { titleBar ->
                titleBar.setupLegacyMainTitleBar(
                    destination = layerDestination,
                    songsEditMode = layerDestination == MusicDestination.Songs && songsEditMode,
                    selectedSongCount = selectedSongCount,
                    albumEditMode = layerDestination == MusicDestination.Album && albumEditMode,
                    selectedAlbumCount = selectedAlbumCount,
                    albumDetailTitle = null,
                    albumViewMode = albumViewMode,
                    artistTarget = null,
                    artistAlbumViewMode = artistAlbumViewMode,
                    onEnterSongsEditMode = stableOnEnterSongsEditMode,
                    onExitSongsEditMode = stableOnExitSongsEditMode,
                    onRequestDeleteSelected = stableOnRequestDeleteSelected,
                    onEnterAlbumEditMode = stableOnEnterAlbumEditMode,
                    onExitAlbumEditMode = stableOnExitAlbumEditMode,
                    onToggleAlbumViewMode = stableOnToggleAlbumViewMode,
                    onAlbumDetailBack = {},
                    onArtistBack = {},
                    onToggleArtistAlbumViewMode = {},
                    onRootBack = stableOnRootBack.takeIf { hasRootBack },
                    onSearchClick = stableOnSearchClick,
                    onOpenMoreSettings = stableOnOpenMoreSettings,
                    playlistEditMode = playlistEditMode,
                    playlistSelectedCount = playlistSelectedCount,
                    playlistActionsEnabled = playlistActionsEnabled,
                    onEnterPlaylistEditMode = stableOnEnterPlaylistEditMode,
                    onExitPlaylistEditMode = stableOnExitPlaylistEditMode,
                    onDeleteSelectedPlaylists = stableOnDeleteSelectedPlaylists,
                )
            }
        }
    }
}

private data class LegacyPortStableRootTitleBarKey(
    val destination: MusicDestination,
    val editMode: Boolean,
    val selectionEnabled: Boolean,
    val albumViewMode: AlbumViewMode?,
    val hasRootBack: Boolean,
    val playlistActionsEnabled: Boolean?,
)

private val StableRootTitleBarDestinations = listOf(
    MusicDestination.Playlist,
    MusicDestination.Artist,
    MusicDestination.Album,
    MusicDestination.Songs,
    MusicDestination.More,
)

@Composable
internal fun LegacyPortSearchDetailTitleBar(
    destination: MusicDestination,
    albumDetailTitle: String?,
    artistTarget: LegacyArtistTarget?,
    onBack: () -> Unit,
    artistAlbumViewMode: AlbumViewMode = AlbumViewMode.List,
    onToggleArtistAlbumViewMode: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LegacyPortTitleBar(
        destination = destination,
        songsEditMode = false,
        selectedSongCount = 0,
        albumEditMode = false,
        selectedAlbumCount = 0,
        albumDetailTitle = albumDetailTitle,
        albumViewMode = AlbumViewMode.List,
        artistTarget = artistTarget,
        artistAlbumViewMode = artistAlbumViewMode,
        onEnterSongsEditMode = {},
        onExitSongsEditMode = {},
        onRequestDeleteSelected = {},
        onEnterAlbumEditMode = {},
        onExitAlbumEditMode = {},
        onToggleAlbumViewMode = {},
        onAlbumDetailBack = onBack,
        onArtistBack = onBack,
        onToggleArtistAlbumViewMode = onToggleArtistAlbumViewMode,
        onRootBack = null,
        onSearchClick = {},
        modifier = modifier,
    )
}

private fun TitleBar.setupLegacyMainTitleBar(
    destination: MusicDestination,
    songsEditMode: Boolean,
    selectedSongCount: Int,
    albumEditMode: Boolean,
    selectedAlbumCount: Int,
    albumDetailTitle: String?,
    albumViewMode: AlbumViewMode,
    artistTarget: LegacyArtistTarget?,
    artistAlbumViewMode: AlbumViewMode,
    onEnterSongsEditMode: () -> Unit,
    onExitSongsEditMode: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onEnterAlbumEditMode: () -> Unit,
    onExitAlbumEditMode: () -> Unit,
    onToggleAlbumViewMode: () -> Unit,
    onAlbumDetailBack: () -> Unit,
    onArtistBack: () -> Unit,
    onToggleArtistAlbumViewMode: () -> Unit,
    onRootBack: (() -> Unit)?,
    onSearchClick: () -> Unit,
    onOpenMoreSettings: () -> Unit,
    playlistEditMode: Boolean,
    playlistSelectedCount: Int,
    playlistActionsEnabled: Boolean,
    onEnterPlaylistEditMode: () -> Unit,
    onExitPlaylistEditMode: () -> Unit,
    onDeleteSelectedPlaylists: () -> Unit,
) {
    if (destination == MusicDestination.Playlist) {
        setupLegacyPlaylistTitleBar(
            target = null,
            detailTitle = "",
            rootEditMode = playlistEditMode,
            rootSelectedCount = playlistSelectedCount,
            rootActionsEnabled = playlistActionsEnabled,
            detailEditMode = false,
            onRootEnterEdit = onEnterPlaylistEditMode,
            onRootExitEdit = onExitPlaylistEditMode,
            onRootDeleteSelected = onDeleteSelectedPlaylists,
            onRootBack = onRootBack,
            onDetailBack = {},
            onDetailEnterEdit = {},
            onDetailExitEdit = {},
            onSearchClick = onSearchClick,
        )
        return
    }
    removeAllLeftViews()
    removeAllRightViews()
    setShadowVisible(false)
    val centerText = when {
        albumDetailTitle != null -> albumDetailTitle
        artistTarget != null -> artistTarget.title
        else -> context.getString(destination.labelRes)
    }
    setCenterText(centerText)

    if (destination == MusicDestination.Album && albumDetailTitle != null) {
        addLeftImageView(R.drawable.standard_icon_back_selector).apply {
            setOnClickListener {
                onAlbumDetailBack()
            }
        }
        return
    }

    if (destination == MusicDestination.Artist && artistTarget != null) {
        addLeftImageView(R.drawable.standard_icon_back_selector).apply {
            setOnClickListener {
                onArtistBack()
            }
        }
        if (artistTarget.showsAlbumSwitch) {
            val switchButton = CheckBox(context, null).apply {
                setButtonDrawable(R.drawable.album_switch_selector)
                background = null
                isChecked = artistAlbumViewMode == AlbumViewMode.List
                setOnClickListener {
                    onToggleArtistAlbumViewMode()
                }
            }
            addRightView(switchButton)
        }
        return
    }

    if (destination == MusicDestination.Songs && songsEditMode) {
        addLeftImageView(R.drawable.standard_icon_cancel_selector).apply {
            setOnClickListener {
                onExitSongsEditMode()
            }
        }
        addRightImageView(R.drawable.titlebar_btn_delete_selector).apply {
            isEnabled = selectedSongCount > 0
            setOnClickListener {
                if (selectedSongCount > 0) {
                    onRequestDeleteSelected()
                }
            }
        }
        return
    }

    if (destination == MusicDestination.Album && albumEditMode) {
        addLeftImageView(R.drawable.standard_icon_cancel_selector).apply {
            setOnClickListener {
                onExitAlbumEditMode()
            }
        }
        addRightImageView(R.drawable.titlebar_btn_delete_selector).apply {
            isEnabled = selectedAlbumCount > 0
        }
        return
    }

    when (destination) {
        MusicDestination.More -> {
            addLeftImageView(R.drawable.standard_icon_settings_selector).apply {
                setOnClickListener {
                    onOpenMoreSettings()
                }
            }
            addRightImageView(R.drawable.search_btn_selector).apply {
                setOnClickListener {
                    onSearchClick()
                }
            }
        }
        MusicDestination.Artist -> {
            if (onRootBack != null) {
                addLeftImageView(R.drawable.standard_icon_back_selector).apply {
                    setOnClickListener { onRootBack() }
                }
            } else {
                addLeftImageView(R.drawable.standard_icon_multi_select_selector).visibility = View.INVISIBLE
            }
            addRightImageView(R.drawable.search_btn_selector).apply {
                setOnClickListener {
                    onSearchClick()
                }
            }
        }
        else -> {
            val enterEdit = {
                when (destination) {
                    MusicDestination.Songs -> onEnterSongsEditMode()
                    MusicDestination.Album -> onEnterAlbumEditMode()
                    else -> Unit
                }
            }
            if (onRootBack != null) {
                addLeftImageView(R.drawable.standard_icon_back_selector).apply {
                    setOnClickListener { onRootBack() }
                }
                if (destination == MusicDestination.Songs || destination == MusicDestination.Album) {
                    addRightImageView(R.drawable.standard_icon_multi_select_selector, 0).apply {
                        setOnClickListener { enterEdit() }
                    }
                }
            } else {
                addLeftImageView(R.drawable.standard_icon_multi_select_selector).apply {
                    setOnClickListener { enterEdit() }
                }
            }
            addRightImageView(
                R.drawable.search_btn_selector,
                if (onRootBack != null) 1 else 0,
            ).apply {
                setOnClickListener {
                    onSearchClick()
                }
            }
            if (destination == MusicDestination.Album) {
                val switchButton = CheckBox(context, null).apply {
                    setButtonDrawable(R.drawable.album_switch_selector)
                    background = null
                    isChecked = albumViewMode == AlbumViewMode.List
                    setOnClickListener {
                        onToggleAlbumViewMode()
                    }
                }
                addRightView(switchButton)
            }
        }
    }
}
