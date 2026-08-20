package com.smartisan.music.ui.shell

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.smartisan.music.R
import com.smartisan.music.data.netease.NeteaseOnlinePhase
import com.smartisan.music.data.netease.NeteaseOnlineState
import com.smartisan.music.data.playlist.PlaylistCreateResult
import com.smartisan.music.data.playlist.PlaylistRenameResult
import com.smartisan.music.data.playlist.PlaylistRepository
import com.smartisan.music.data.playlist.UserPlaylistDetail
import com.smartisan.music.data.playlist.UserPlaylistSummary
import com.smartisan.music.data.settings.OnlineMusicSettings
import com.smartisan.music.playback.LocalAudioLibrary
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.replaceQueueAndPlay
import com.smartisan.music.playback.replaceQueueAndPlayShuffled
import com.smartisan.music.netease.NeteasePlaylistDetail
import com.smartisan.music.netease.NeteasePlaylistSummary
import com.smartisan.music.netease.NeteaseResult
import com.smartisan.music.netease.NeteaseSong
import com.smartisan.music.ui.shell.songs.LegacyPortSongsPage
import com.smartisan.music.ui.shell.titlebar.LegacyPortTitleBarShadow
import com.smartisan.music.ui.widgets.CustomCheckBox
import com.smartisan.music.ui.widgets.EditableLayout
import com.smartisan.music.ui.widgets.EditableListViewItem
import com.smartisan.music.ui.widgets.StretchTextView
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.smartisan.music.ui.widgets.legacy.ActionButtonGroup
import com.smartisan.music.ui.widgets.legacy.MenuDialog
import com.smartisan.music.ui.widgets.legacy.QuickBarEx
import com.smartisan.music.ui.widgets.legacy.TitleBar
import java.text.Normalizer
import java.util.Locale

private const val PlaylistAddModeSlideMillis = 300
private const val NeteasePlaylistIdPrefix = "netease:"
internal const val PlaylistRootFooterThreshold = 8
private val PlaylistAddModeEasing = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction)
}
internal val View.PlaylistPrimaryTextColor: Int
    get() = context.getColor(R.color.setting_item_text_color)
internal val View.PlaylistSecondaryTextColor: Int
    get() = context.getColor(R.color.list_text_color_small)
internal val View.PlaylistFooterTextColor: Int
    get() = context.getColor(R.color.footer_text_color)

internal data class LegacyPlaylistTarget(
    val playlistId: String,
    val title: String,
)

private data class LegacyPlaylistDetailSnapshot(
    val playlistId: String,
    val playlist: UserPlaylistDetail?,
    val title: String,
    val tracks: List<MediaItem>,
    val libraryLoading: Boolean,
)

internal sealed interface LegacyPlaylistNameDialogRequest {
    val initialName: String

    data class Create(
        override val initialName: String,
        @param:StringRes val titleRes: Int = R.string.new_playlist,
    ) : LegacyPlaylistNameDialogRequest

    data class Rename(
        val playlistId: String,
        override val initialName: String,
    ) : LegacyPlaylistNameDialogRequest
}

internal enum class LegacyPlaylistDeleteRequest {
    RootSelected,
    DetailPlaylist,
    DetailTracks,
}

@Composable
internal fun LegacyPortPlaylistPage(
    mediaItems: List<MediaItem>,
    libraryLoaded: Boolean,
    active: Boolean,
    rootEditMode: Boolean,
    selectedPlaylistIds: Set<String>,
    rootDeleteRequested: Boolean,
    hiddenMediaIds: Set<String>,
    onlineMusicSettings: OnlineMusicSettings,
    neteaseState: NeteaseOnlineState,
    onNeteaseRetry: () -> Unit,
    onNeteasePlaylistDetail: suspend (Long) -> NeteaseResult<NeteasePlaylistDetail>,
    onTrackMoreClick: (MediaItem) -> Unit,
    onRootEditModeChange: (Boolean) -> Unit,
    onSelectedPlaylistIdsChange: (Set<String>) -> Unit,
    onRootDeleteRequestConsumed: () -> Unit,
    onRootPageActiveChanged: (Boolean) -> Unit,
    onAddModeActiveChanged: (Boolean) -> Unit,
    onLibraryNeeded: () -> Unit,
    onSearchClick: () -> Unit,
    onClose: (() -> Unit)?,
    closePredictiveBackState: LegacyPortPredictiveBackState?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val browser = LocalPlaybackBrowser.current
    val scope = rememberCoroutineScope()
    val playlistRepository = remember(context.applicationContext) {
        PlaylistRepository.getInstance(context.applicationContext)
    }
    val playlists by playlistRepository.playlists.collectAsState(initial = emptyList())
    val visibleSongs = remember(mediaItems, hiddenMediaIds) {
        mediaItems.filterNot { item -> item.mediaId in hiddenMediaIds }
    }
    val songsById = remember(visibleSongs) {
        visibleSongs.associateBy(MediaItem::mediaId)
    }

    var target by remember { mutableStateOf<LegacyPlaylistTarget?>(null) }
    var detailEditMode by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf(emptySet<String>()) }
    var addMode by remember { mutableStateOf(false) }
    var addModeTarget by remember { mutableStateOf<LegacyPlaylistTarget?>(null) }
    var addModeReturnsToRoot by remember { mutableStateOf(false) }
    var selectedAddSongIds by remember { mutableStateOf(emptySet<String>()) }
    var nameDialogRequest by remember { mutableStateOf<LegacyPlaylistNameDialogRequest?>(null) }
    var deleteRequest by remember { mutableStateOf<LegacyPlaylistDeleteRequest?>(null) }
    var detailTitleTransitionActive by remember { mutableStateOf(target != null) }
    var neteaseDetail by remember { mutableStateOf<NeteasePlaylistDetail?>(null) }
    var neteaseDetailLoading by remember { mutableStateOf(false) }
    var neteaseDetailFailed by remember { mutableStateOf(false) }
    var neteaseDetailRetryVersion by remember { mutableStateOf(0) }
    val detailPredictiveBackState = rememberLegacyPortPredictiveBackState()
    val neteaseEnabled = onlineMusicSettings.neteaseEnabled

    val activePlaylistId = target?.playlistId
    val activeLocalPlaylistId = activePlaylistId.takeUnless { neteaseEnabled }
    val activePlaylistFlow = remember(activeLocalPlaylistId, playlistRepository) {
        activeLocalPlaylistId?.let(playlistRepository::observePlaylistDetail) ?: flowOf(null)
    }
    val activePlaylist by activePlaylistFlow.collectAsState(initial = null)
    val activeSummary = remember(playlists, activeLocalPlaylistId) {
        activeLocalPlaylistId?.let { id -> playlists.firstOrNull { playlist -> playlist.id == id } }
    }
    val localDetailTracks = remember(activePlaylist, songsById) {
        activePlaylist?.mediaIds?.mapNotNull(songsById::get).orEmpty()
    }
    val neteaseDetailPlaylist = remember(neteaseDetail) {
        neteaseDetail?.toLegacyPlaylistDetail()
    }
    val neteaseDetailTracks = remember(neteaseDetail) {
        neteaseDetail?.tracks?.map(NeteaseSong::toLegacyPlaylistMediaItem).orEmpty()
    }
    val detailPlaylist = if (neteaseEnabled) neteaseDetailPlaylist else activePlaylist
    val detailTracks = if (neteaseEnabled) neteaseDetailTracks else localDetailTracks
    val detailTitle = detailPlaylist?.name ?: activeSummary?.name ?: target?.title.orEmpty()
    val detailPlaylistHasKnownTracks = activePlaylist?.mediaIds?.isNotEmpty() == true ||
        (activePlaylist == null && (activeSummary?.songCount ?: 0) > 0)
    val detailLibraryLoading = if (neteaseEnabled) {
        target != null && !neteaseDetailFailed &&
            (neteaseDetailLoading || neteaseDetail == null)
    } else {
        target != null && !libraryLoaded && detailPlaylistHasKnownTracks
    }
    val addModeExistingIds = remember(addModeTarget, activePlaylistId, activePlaylist) {
        if (addModeTarget?.playlistId == activePlaylistId) {
            activePlaylist?.mediaIds?.toSet().orEmpty()
        } else {
            emptySet()
        }
    }
    var retainedDetailSnapshot by remember {
        mutableStateOf<LegacyPlaylistDetailSnapshot?>(null)
    }
    val addModeVisible = addMode && addModeTarget != null
    val rootPlaylists = remember(neteaseEnabled, neteaseState.phase, neteaseState.playlists, playlists) {
        if (neteaseEnabled) {
            if (neteaseState.phase == NeteaseOnlinePhase.Ready) {
                neteaseState.playlists.map(NeteasePlaylistSummary::toLegacyPlaylistSummary)
            } else {
                emptyList()
            }
        } else {
            playlists
        }
    }
    val rootBlankContent = if (neteaseEnabled) {
        neteaseState.toLegacyPlaylistBlankContent(context, onNeteaseRetry)
    } else {
        null
    }

    fun closeAddMode() {
        addMode = false
        selectedAddSongIds = emptySet()
        if (addModeReturnsToRoot) {
            target = null
        }
        addModeReturnsToRoot = false
    }

    LaunchedEffect(activeLocalPlaylistId, activePlaylist, playlists) {
        if (activeLocalPlaylistId != null &&
            activePlaylist == null &&
            playlists.none { it.id == activeLocalPlaylistId }
        ) {
            target = null
            detailEditMode = false
            addMode = false
            addModeTarget = null
            addModeReturnsToRoot = false
            selectedTrackIds = emptySet()
            selectedAddSongIds = emptySet()
        }
    }
    LaunchedEffect(neteaseEnabled, activePlaylistId, neteaseDetailRetryVersion) {
        neteaseDetail = null
        neteaseDetailLoading = false
        neteaseDetailFailed = false
        if (!neteaseEnabled) return@LaunchedEffect
        val playlistId = activePlaylistId?.neteasePlaylistIdOrNull() ?: return@LaunchedEffect
        neteaseDetailLoading = true
        when (val result = onNeteasePlaylistDetail(playlistId)) {
            is NeteaseResult.Success -> {
                neteaseDetail = result.value
                neteaseDetailLoading = false
            }
            is NeteaseResult.Failure -> {
                neteaseDetailLoading = false
                neteaseDetailFailed = true
            }
        }
    }
    LaunchedEffect(neteaseEnabled, neteaseState.phase) {
        if (neteaseEnabled && neteaseState.phase == NeteaseOnlinePhase.LoggedOut) {
            target = null
            detailEditMode = false
            selectedTrackIds = emptySet()
            neteaseDetail = null
            neteaseDetailLoading = false
            neteaseDetailFailed = false
        }
    }
    LaunchedEffect(neteaseEnabled) {
        target = null
        detailEditMode = false
        addMode = false
        addModeTarget = null
        addModeReturnsToRoot = false
        selectedTrackIds = emptySet()
        selectedAddSongIds = emptySet()
        nameDialogRequest = null
        deleteRequest = null
        neteaseDetail = null
        neteaseDetailLoading = false
        neteaseDetailFailed = false
        onRootEditModeChange(false)
        onSelectedPlaylistIdsChange(emptySet())
    }
    LaunchedEffect(activePlaylistId, detailPlaylist, detailTitle, detailTracks, detailLibraryLoading) {
        val playlistId = activePlaylistId ?: return@LaunchedEffect
        retainedDetailSnapshot = LegacyPlaylistDetailSnapshot(
            playlistId = playlistId,
            playlist = detailPlaylist,
            title = detailTitle,
            tracks = detailTracks,
            libraryLoading = detailLibraryLoading,
        )
    }
    LaunchedEffect(addModeVisible) {
        onAddModeActiveChanged(addModeVisible)
    }
    LaunchedEffect(active, rootDeleteRequested) {
        if (active && rootDeleteRequested) {
            deleteRequest = LegacyPlaylistDeleteRequest.RootSelected
            onRootDeleteRequestConsumed()
        }
    }
    LaunchedEffect(target) {
        if (target != null) {
            detailTitleTransitionActive = true
        }
    }
    LaunchedEffect(active, target, addModeVisible, detailTitleTransitionActive) {
        onRootPageActiveChanged(
            active && target == null && !addModeVisible && !detailTitleTransitionActive,
        )
    }
    LaunchedEffect(active) {
        if (!active) {
            target = null
            onRootEditModeChange(false)
            onSelectedPlaylistIdsChange(emptySet())
            detailEditMode = false
            selectedTrackIds = emptySet()
            addMode = false
            addModeTarget = null
            addModeReturnsToRoot = false
            selectedAddSongIds = emptySet()
            nameDialogRequest = null
            deleteRequest = null
            retainedDetailSnapshot = null
            neteaseDetail = null
            neteaseDetailLoading = false
            neteaseDetailFailed = false
            detailTitleTransitionActive = false
            detailPredictiveBackState.reset()
        }
    }
    LaunchedEffect(active, target, addMode, neteaseEnabled) {
        if (active && !neteaseEnabled && (target != null || addMode)) {
            onLibraryNeeded()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            onAddModeActiveChanged(false)
            onRootPageActiveChanged(false)
        }
    }

    BackHandler(enabled = active && addModeVisible) {
        closeAddMode()
    }
    BackHandler(enabled = active && !addModeVisible && detailEditMode) {
        detailEditMode = false
        selectedTrackIds = emptySet()
    }
    BackHandler(enabled = active && !neteaseEnabled && target == null && rootEditMode) {
        onRootEditModeChange(false)
        onSelectedPlaylistIdsChange(emptySet())
    }
    if (closePredictiveBackState != null && onClose != null) {
        LegacyPortPredictiveBackHandler(
            enabled = active && target == null && !rootEditMode && !addModeVisible,
            state = closePredictiveBackState,
            onBack = onClose,
        )
    } else if (onClose != null) {
        BackHandler(enabled = active && target == null && !rootEditMode && !addModeVisible) {
            onClose()
        }
    }
    LegacyPortPredictiveBackHandler(
        enabled = active && !addModeVisible && !detailEditMode && target != null,
        state = detailPredictiveBackState,
    ) {
        target = null
    }

    val titleAreaHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        dimensionResource(R.dimen.title_bar_height)
    val titleShadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.page_background)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            LegacyPlaylistTitleArea(
                target = target,
                detailTitle = detailTitle,
                rootEditMode = rootEditMode,
                rootSelectedCount = selectedPlaylistIds.size,
                rootActionsEnabled = !neteaseEnabled,
                detailActionsEnabled = !neteaseEnabled,
                detailEditMode = detailEditMode,
                predictiveBackProgress = detailPredictiveBackState.progress,
                predictiveBackExitConsumed = detailPredictiveBackState.exitConsumed,
                onPredictiveBackExitConsumedReset = detailPredictiveBackState::reset,
                onDetailExitComplete = {
                    detailTitleTransitionActive = false
                },
                onRootEnterEdit = {
                    onRootEditModeChange(true)
                    onSelectedPlaylistIdsChange(emptySet())
                },
                onRootExitEdit = {
                    onRootEditModeChange(false)
                    onSelectedPlaylistIdsChange(emptySet())
                },
                onRootDeleteSelected = {
                    if (selectedPlaylistIds.isNotEmpty()) {
                        deleteRequest = LegacyPlaylistDeleteRequest.RootSelected
                    }
                },
                onRootBack = onClose,
                onDetailBack = {
                    target = null
                    detailEditMode = false
                    addMode = false
                    addModeTarget = null
                    addModeReturnsToRoot = false
                    selectedTrackIds = emptySet()
                    selectedAddSongIds = emptySet()
                },
                onDetailEnterEdit = {
                    if (!neteaseEnabled) {
                        detailEditMode = true
                        selectedTrackIds = emptySet()
                    }
                },
                onDetailExitEdit = {
                    detailEditMode = false
                    selectedTrackIds = emptySet()
                },
                onSearchClick = onSearchClick,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LegacyPortPageStackTransition(
                    secondaryKey = target,
                    modifier = Modifier.fillMaxSize(),
                    label = "legacy playlist transition",
                    predictiveBackProgress = detailPredictiveBackState.progress,
                    predictiveBackExitConsumed = detailPredictiveBackState.exitConsumed,
                    onPredictiveBackExitConsumedReset = detailPredictiveBackState::reset,
                    primaryContent = {
                        LegacyPlaylistRootPage(
                            active = active,
                            playlists = rootPlaylists,
                            editMode = rootEditMode && !neteaseEnabled,
                            selectedPlaylistIds = selectedPlaylistIds.takeUnless { neteaseEnabled }
                                ?: emptySet(),
                            onCreatePlaylist = {
                                if (!neteaseEnabled) scope.launch {
                                    nameDialogRequest = LegacyPlaylistNameDialogRequest.Create(
                                        initialName = playlistRepository.suggestNextUntitledName(),
                                    )
                                }
                            },
                            onRenamePlaylist = { playlist ->
                                if (!neteaseEnabled) {
                                    nameDialogRequest = LegacyPlaylistNameDialogRequest.Rename(
                                        playlistId = playlist.id,
                                        initialName = playlist.name,
                                    )
                                }
                            },
                            onPlaylistClick = { playlist ->
                                if (!neteaseEnabled && rootEditMode) {
                                    onSelectedPlaylistIdsChange(
                                        selectedPlaylistIds.togglePlaylistSelection(playlist.id),
                                    )
                                } else {
                                    if (!neteaseEnabled) onLibraryNeeded()
                                    target = LegacyPlaylistTarget(
                                        playlistId = playlist.id,
                                        title = playlist.name,
                                    )
                                }
                            },
                            onPlaylistSelectionChange = { playlist, selected ->
                                if (!neteaseEnabled) {
                                    onSelectedPlaylistIdsChange(
                                        selectedPlaylistIds.withSelection(playlist.id, selected),
                                    )
                                }
                            },
                            showAddRow = !neteaseEnabled,
                            blankContent = rootBlankContent,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    secondaryContent = { playlistTarget ->
                        val detailSnapshot = if (playlistTarget == target) {
                            LegacyPlaylistDetailSnapshot(
                                playlistId = playlistTarget.playlistId,
                                playlist = detailPlaylist,
                                title = detailTitle,
                                tracks = detailTracks,
                                libraryLoading = detailLibraryLoading,
                            )
                        } else {
                            retainedDetailSnapshot?.takeIf { snapshot ->
                                snapshot.playlistId == playlistTarget.playlistId
                            } ?: LegacyPlaylistDetailSnapshot(
                                playlistId = playlistTarget.playlistId,
                                playlist = detailPlaylist,
                                title = playlistTarget.title,
                                tracks = detailTracks,
                                libraryLoading = detailLibraryLoading,
                            )
                        }
                        LegacyPlaylistDetailPage(
                            active = active && !addModeVisible,
                            playlist = detailSnapshot.playlist,
                            title = detailSnapshot.title,
                            tracks = detailSnapshot.tracks,
                            libraryLoading = detailSnapshot.libraryLoading,
                            editMode = detailEditMode && !neteaseEnabled,
                            selectedTrackIds = selectedTrackIds,
                            browser = browser,
                            onShuffle = {
                                if (neteaseEnabled || detailSnapshot.tracks.isEmpty()) {
                                    return@LegacyPlaylistDetailPage
                                }
                                browser.replaceQueueAndPlayShuffled(detailSnapshot.tracks)
                            },
                            onDeletePlaylist = {
                                if (!neteaseEnabled) {
                                    deleteRequest = LegacyPlaylistDeleteRequest.DetailPlaylist
                                }
                            },
                            onEditModeChange = { enabled ->
                                if (!neteaseEnabled) {
                                    detailEditMode = enabled
                                    selectedTrackIds = emptySet()
                                }
                            },
                            onAddOrRemoveClick = {
                                if (neteaseEnabled) return@LegacyPlaylistDetailPage
                                if (selectedTrackIds.isEmpty()) {
                                    onLibraryNeeded()
                                    addModeTarget = target
                                    addModeReturnsToRoot = false
                                    addMode = true
                                    selectedAddSongIds = emptySet()
                                } else {
                                    deleteRequest = LegacyPlaylistDeleteRequest.DetailTracks
                                }
                            },
                            onToggleAll = { checked ->
                                selectedTrackIds = if (checked) {
                                    detailSnapshot.tracks.map(MediaItem::mediaId).toSet()
                                } else {
                                    emptySet()
                                }
                            },
                            onReorderTracks = { orderedMediaIds ->
                                if (neteaseEnabled) return@LegacyPlaylistDetailPage
                                val playlistId = target?.playlistId ?: return@LegacyPlaylistDetailPage
                                scope.launch {
                                    playlistRepository.reorderVisibleMediaIds(playlistId, orderedMediaIds)
                                }
                            },
                            onTrackSelectionChange = { mediaId, selected ->
                                selectedTrackIds = selectedTrackIds.withSelection(mediaId, selected)
                            },
                            onTrackClick = { item, index ->
                                if (neteaseEnabled) return@LegacyPlaylistDetailPage
                                if (detailEditMode) {
                                    selectedTrackIds = selectedTrackIds.togglePlaylistSelection(item.mediaId)
                                    return@LegacyPlaylistDetailPage
                                }
                                browser.replaceQueueAndPlay(detailSnapshot.tracks, index)
                            },
                            onTrackMoreClick = onTrackMoreClick,
                            headerActionsEnabled = !neteaseEnabled,
                            trackClicksEnabled = !neteaseEnabled,
                            trackMoreEnabled = !neteaseEnabled,
                            emptyContent = if (neteaseEnabled) {
                                LegacyPlaylistBlankContent(
                                    primaryText = stringResource(
                                        if (neteaseDetailFailed) {
                                            R.string.netease_playlist_detail_error
                                        } else {
                                            R.string.netease_playlist_detail_empty
                                        },
                                    ),
                                    secondaryText = stringResource(
                                        if (neteaseDetailFailed) {
                                            R.string.netease_playlists_retry
                                        } else {
                                            R.string.netease_playlist_detail_empty_hint
                                        },
                                    ),
                                    onClick = if (neteaseDetailFailed) {
                                        { neteaseDetailRetryVersion += 1 }
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                null
                            },
                            loadingContent = if (neteaseEnabled) {
                                LegacyPlaylistBlankContent(
                                    primaryText = stringResource(R.string.netease_playlist_detail_loading),
                                    secondaryText = stringResource(R.string.netease_playlists_loading_hint),
                                )
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
        }
        if (!addModeVisible) {
            LegacyPortTitleBarShadow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = titleAreaHeight)
                    .fillMaxWidth()
                    .height(titleShadowHeight)
                    .zIndex(1f),
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = addModeVisible,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f),
            enter = slideInVertically(
                animationSpec = tween(
                    durationMillis = PlaylistAddModeSlideMillis,
                    easing = PlaylistAddModeEasing,
                ),
                initialOffsetY = { it },
            ),
            exit = slideOutVertically(
                animationSpec = tween(
                    durationMillis = PlaylistAddModeSlideMillis,
                    easing = PlaylistAddModeEasing,
                ),
                targetOffsetY = { it },
            ),
        ) {
            val currentAddModeTarget = addModeTarget ?: return@AnimatedVisibility
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorResource(R.color.page_background)),
            ) {
                LegacyPlaylistAddModeTitleArea(
                    target = currentAddModeTarget,
                    onConfirm = {
                        val playlistId = addModeTarget?.playlistId ?: return@LegacyPlaylistAddModeTitleArea
                        val mediaIds = selectedAddSongIds.toList()
                        if (mediaIds.isEmpty()) {
                            closeAddMode()
                            return@LegacyPlaylistAddModeTitleArea
                        }
                        scope.launch {
                            playlistRepository.addMediaIds(playlistId, mediaIds)
                            closeAddMode()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                LegacyPortSongsPage(
                    mediaItems = visibleSongs,
                    libraryLoaded = libraryLoaded,
                    active = active && addModeVisible,
                    editMode = true,
                    selectedSongIds = selectedAddSongIds,
                    hiddenMediaIds = addModeExistingIds,
                    onSongSelectionChange = { mediaId, selected ->
                        selectedAddSongIds = selectedAddSongIds.withSelection(mediaId, selected)
                    },
                    onTrackMoreClick = {},
                    onRequestSongDeleteConfirmation = { _, onDismiss ->
                        onDismiss?.invoke()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }

    LegacyPlaylistNameDialogOverlay(
        request = nameDialogRequest.takeIf { active },
        onDismiss = {
            nameDialogRequest = null
        },
        onConfirm = { request, name ->
            scope.launch {
                when (request) {
                    is LegacyPlaylistNameDialogRequest.Create -> {
                        when (val result = playlistRepository.createPlaylist(name)) {
                            is PlaylistCreateResult.Success -> {
                                nameDialogRequest = null
                                val createdTarget = LegacyPlaylistTarget(
                                    playlistId = result.playlistId,
                                    title = name.trim(),
                                )
                                target = createdTarget
                                detailEditMode = false
                                selectedTrackIds = emptySet()
                                if (visibleSongs.isNotEmpty() || !libraryLoaded) {
                                    onLibraryNeeded()
                                    addModeTarget = createdTarget
                                    addModeReturnsToRoot = true
                                    target = null
                                    addMode = true
                                    selectedAddSongIds = emptySet()
                                }
                            }
                            PlaylistCreateResult.DuplicateName -> {
                                Toast.makeText(context, R.string.playlist_duplicate_name, Toast.LENGTH_SHORT).show()
                            }
                            PlaylistCreateResult.EmptyName -> Unit
                        }
                    }
                    is LegacyPlaylistNameDialogRequest.Rename -> {
                        when (playlistRepository.renamePlaylist(request.playlistId, name)) {
                            PlaylistRenameResult.Success -> {
                                nameDialogRequest = null
                                if (target?.playlistId == request.playlistId) {
                                    target = target?.copy(title = name.trim())
                                }
                            }
                            PlaylistRenameResult.DuplicateName -> {
                                Toast.makeText(context, R.string.playlist_duplicate_name, Toast.LENGTH_SHORT).show()
                            }
                            PlaylistRenameResult.EmptyName,
                            PlaylistRenameResult.MissingPlaylist,
                            -> Unit
                        }
                    }
                }
            }
        },
    )

    LegacyPlaylistDeleteDialog(
        request = deleteRequest.takeIf { active },
        onDismiss = {
            deleteRequest = null
        },
        onConfirm = { request ->
            scope.launch {
                when (request) {
                    LegacyPlaylistDeleteRequest.RootSelected -> {
                        playlistRepository.deletePlaylists(selectedPlaylistIds)
                        onSelectedPlaylistIdsChange(emptySet())
                        onRootEditModeChange(false)
                    }
                    LegacyPlaylistDeleteRequest.DetailPlaylist -> {
                        val playlistId = target?.playlistId
                        if (playlistId != null) {
                            playlistRepository.deletePlaylists(setOf(playlistId))
                        }
                        target = null
                        detailEditMode = false
                        addMode = false
                        selectedTrackIds = emptySet()
                    }
                    LegacyPlaylistDeleteRequest.DetailTracks -> {
                        val playlistId = target?.playlistId
                        if (playlistId != null) {
                            playlistRepository.removeMediaIds(playlistId, selectedTrackIds)
                        }
                        selectedTrackIds = emptySet()
                        detailEditMode = false
                    }
                }
                deleteRequest = null
            }
        },
    )
}

internal fun NeteasePlaylistSummary.toLegacyPlaylistSummary(): UserPlaylistSummary {
    return UserPlaylistSummary(
        id = "$NeteasePlaylistIdPrefix$id",
        name = name,
        songCount = trackCount ?: 0,
        createdAt = 0L,
        updatedAt = 0L,
    )
}

internal fun NeteaseSong.toLegacyPlaylistMediaItem(): MediaItem {
    val artistText = artists.joinToString(separator = " / ", transform = { artist -> artist.name })
    return MediaItem.Builder()
        .setMediaId("netease:$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setDisplayTitle(name)
                .setArtist(artistText)
                .setAlbumTitle(album?.name)
                .setDurationMs(durationMillis)
                .build(),
        )
        .build()
}

private fun NeteasePlaylistDetail.toLegacyPlaylistDetail(): UserPlaylistDetail {
    return UserPlaylistDetail(
        id = "$NeteasePlaylistIdPrefix${playlist.id}",
        name = playlist.name,
        mediaIds = tracks.map { song -> "netease:${song.id}" },
        createdAt = 0L,
        updatedAt = 0L,
    )
}

private fun String.neteasePlaylistIdOrNull(): Long? {
    return takeIf { value -> value.startsWith(NeteasePlaylistIdPrefix) }
        ?.removePrefix(NeteasePlaylistIdPrefix)
        ?.toLongOrNull()
        ?.takeIf { id -> id > 0L }
}

private fun NeteaseOnlineState.toLegacyPlaylistBlankContent(
    context: Context,
    onRetry: () -> Unit,
): LegacyPlaylistBlankContent {
    return when (phase) {
        NeteaseOnlinePhase.LoggedOut -> LegacyPlaylistBlankContent(
            primaryText = context.getString(R.string.netease_playlists_login_required),
            secondaryText = context.getString(R.string.netease_playlists_login_hint),
        )
        NeteaseOnlinePhase.Disabled,
        NeteaseOnlinePhase.CheckingSession,
        NeteaseOnlinePhase.LoadingPlaylists,
        -> LegacyPlaylistBlankContent(
            primaryText = context.getString(R.string.netease_playlists_loading),
            secondaryText = context.getString(R.string.netease_playlists_loading_hint),
        )
        NeteaseOnlinePhase.Ready -> LegacyPlaylistBlankContent(
            primaryText = context.getString(R.string.netease_playlists_empty),
            secondaryText = context.getString(R.string.netease_playlists_empty_hint),
        )
        NeteaseOnlinePhase.Error -> LegacyPlaylistBlankContent(
            primaryText = context.getString(R.string.netease_playlists_error),
            secondaryText = context.getString(R.string.netease_playlists_retry),
            onClick = onRetry,
        )
    }
}

internal class LegacyPlaylistBlankView(
    context: Context,
    iconRes: Int,
    primaryText: String,
    secondaryText: String,
) : LinearLayout(context) {
    private val iconView = ImageView(context).apply {
        alpha = 0.42f
    }
    private val primaryView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(context.getColor(R.color.text_disabled_gray))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
        includeFontPadding = false
    }
    private val secondaryView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(context.getColor(R.color.text_disabled_gray))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        includeFontPadding = false
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.account_background)
        addView(
            iconView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            },
        )
        addView(
            primaryView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
        )
        addView(
            secondaryView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            },
        )
        bind(
            iconRes = iconRes,
            primaryText = primaryText,
            secondaryText = secondaryText,
        )
    }

    fun bind(
        iconRes: Int,
        primaryText: String,
        secondaryText: String,
        onClick: (() -> Unit)? = null,
    ) {
        iconView.setImageResource(iconRes)
        primaryView.text = primaryText
        secondaryView.text = secondaryText
        secondaryView.visibility = if (secondaryText.isBlank()) View.GONE else View.VISIBLE
        isClickable = onClick != null
        isFocusable = onClick != null
        setOnClickListener(onClick?.let { action -> View.OnClickListener { action() } })
    }
}

internal fun String.ellipsizeMiddle(maxChars: Int): String {
    if (length <= maxChars) {
        return this
    }
    return take((maxChars - 3).coerceAtLeast(1)) + "..."
}

private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
