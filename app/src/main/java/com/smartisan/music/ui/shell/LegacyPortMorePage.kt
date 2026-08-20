package com.smartisan.music.ui.shell

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.smartisan.music.R
import com.smartisan.music.data.netease.NeteaseOnlinePhase
import com.smartisan.music.data.netease.NeteaseOnlineState
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.data.settings.AudioFxPreset
import com.smartisan.music.data.settings.NavigationSettings
import com.smartisan.music.data.settings.OnlineMusicSettings
import com.smartisan.music.data.settings.PlaybackSettings
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.shell.titlebar.LegacyPortTitleBarShadow
import com.smartisan.music.ui.widgets.legacy.ListContentItemText
import com.smartisan.music.ui.widgets.legacy.MenuDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * `More` 只负责两个职责：列出当前没有固定到底栏的同级目的地，以及承载设置页。
 * 内容目的地由主壳统一渲染，避免在这里复制一套页面栈和状态所有权。
 */
@Composable
internal fun LegacyPortMorePage(
    active: Boolean,
    settingsVisible: Boolean,
    externalTitleAreaHeight: Dp,
    overflowDestinations: List<MusicDestination>,
    playbackSettings: PlaybackSettings,
    artistSettings: ArtistSettings,
    navigationSettings: NavigationSettings,
    onlineMusicSettings: OnlineMusicSettings,
    neteaseState: NeteaseOnlineState,
    onDestinationSelected: (MusicDestination) -> Unit,
    onScratchEnabledChange: (Boolean) -> Unit,
    onHidePlayerAxisEnabledChange: (Boolean) -> Unit,
    onPopcornSoundEnabledChange: (Boolean) -> Unit,
    onAudioFxEnabledChange: (Boolean) -> Unit,
    onAudioFxPresetChange: (AudioFxPreset) -> Unit,
    onAudioFxCustomGainDbPointsChange: (List<Float>) -> Unit,
    onArtistSeparatorsChange: (Set<String>) -> Unit,
    onTabPinnedChange: (String, Boolean) -> Unit,
    onNeteaseEnabledChange: (Boolean) -> Unit,
    onNeteaseRetry: () -> Unit,
    onNeteaseLoginCookie: suspend (String) -> Boolean,
    onNeteaseLogout: suspend () -> Boolean,
    onSettingsVisibleChange: (Boolean) -> Unit,
    onSettingsPageActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsPredictiveBackState = rememberLegacyPortPredictiveBackState()
    var loginVisible by remember { mutableStateOf(false) }
    var logoutConfirmationVisible by remember { mutableStateOf(false) }
    val secondaryPage = LegacyMoreSecondaryPage.Settings.takeIf { settingsVisible }
    val accountItem = remember(onlineMusicSettings, neteaseState, context) {
        if (onlineMusicSettings.neteaseEnabled) {
            neteaseState.toMoreAccountItem(context)
        } else {
            null
        }
    }

    LaunchedEffect(active, secondaryPage) {
        if (active && secondaryPage != null) {
            onSettingsPageActiveChanged(true)
        } else {
            if (secondaryPage == null) {
                delay(LegacyPageStackSlideMillis.toLong())
            }
            onSettingsPageActiveChanged(false)
        }
    }
    LaunchedEffect(onlineMusicSettings.neteaseEnabled) {
        if (!onlineMusicSettings.neteaseEnabled) {
            loginVisible = false
            logoutConfirmationVisible = false
        }
    }
    LaunchedEffect(active) {
        if (!active) {
            loginVisible = false
            logoutConfirmationVisible = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { onSettingsPageActiveChanged(false) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LegacyPortPageStackTransition(
            secondaryKey = secondaryPage,
            modifier = Modifier.fillMaxSize(),
            label = "legacy more secondary stack",
            axisForKey = { LegacyPortPageStackAxis.VerticalPush },
            predictiveBackProgress = settingsPredictiveBackState.progress,
            predictiveBackExitConsumed = settingsPredictiveBackState.exitConsumed,
            onPredictiveBackExitConsumedReset = settingsPredictiveBackState::reset,
            primaryContent = {
                Box(modifier = Modifier.fillMaxSize()) {
                    LegacyMoreRootPage(
                        active = active,
                        externalTitleAreaHeight = externalTitleAreaHeight,
                        destinations = overflowDestinations,
                        accountItem = accountItem,
                        onDestinationSelected = onDestinationSelected,
                        onAccountSelected = {
                            when (neteaseState.phase) {
                                NeteaseOnlinePhase.LoggedOut -> loginVisible = true
                                NeteaseOnlinePhase.Ready -> logoutConfirmationVisible = true
                                NeteaseOnlinePhase.Error -> {
                                    if (neteaseState.sessionPresent) {
                                        logoutConfirmationVisible = true
                                    } else {
                                        onNeteaseRetry()
                                    }
                                }
                                else -> Unit
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    LegacyPortTitleBarShadow(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = externalTitleAreaHeight)
                            .fillMaxWidth()
                            .height(dimensionResource(R.dimen.title_bar_shadow_height))
                            .zIndex(1f),
                    )
                }
            },
            secondaryContent = {
                LegacyPortSettingsPage(
                    active = active,
                    playbackSettings = playbackSettings,
                    artistSettings = artistSettings,
                    navigationSettings = navigationSettings,
                    onlineMusicSettings = onlineMusicSettings,
                    onClose = { onSettingsVisibleChange(false) },
                    onScratchEnabledChange = onScratchEnabledChange,
                    onHidePlayerAxisEnabledChange = onHidePlayerAxisEnabledChange,
                    onPopcornSoundEnabledChange = onPopcornSoundEnabledChange,
                    onAudioFxEnabledChange = onAudioFxEnabledChange,
                    onAudioFxPresetChange = onAudioFxPresetChange,
                    onAudioFxCustomGainDbPointsChange = onAudioFxCustomGainDbPointsChange,
                    onArtistSeparatorsChange = onArtistSeparatorsChange,
                    onTabPinnedChange = onTabPinnedChange,
                    onNeteaseEnabledChange = onNeteaseEnabledChange,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }

    LegacyNeteaseLoginDialog(
        visible = loginVisible && active && onlineMusicSettings.neteaseEnabled,
        onClose = { loginVisible = false },
        onLoginCookie = onNeteaseLoginCookie,
    )

    if (logoutConfirmationVisible) {
        DisposableEffect(Unit) {
            val dialog = MenuDialog(context).apply {
                setTitle(R.string.netease_logout_confirm)
                setPositiveButton(R.string.netease_logout) {
                    logoutConfirmationVisible = false
                    scope.launch {
                        if (!onNeteaseLogout()) {
                            Toast.makeText(
                                context,
                                R.string.netease_logout_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
                setPositiveRedBg(true)
                setOnDismissListener {
                    logoutConfirmationVisible = false
                }
            }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
}

@Composable
private fun LegacyMoreRootPage(
    active: Boolean,
    externalTitleAreaHeight: Dp,
    destinations: List<MusicDestination>,
    accountItem: LegacyMoreAccountItem?,
    onDestinationSelected: (MusicDestination) -> Unit,
    onAccountSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize(),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(externalTitleAreaHeight),
        )
        LegacyMoreRootList(
            active = active,
            destinations = destinations,
            accountItem = accountItem,
            onDestinationSelected = onDestinationSelected,
            onAccountSelected = onAccountSelected,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(colorResource(R.color.page_background)),
        )
    }
}

@Composable
private fun LegacyMoreRootList(
    active: Boolean,
    destinations: List<MusicDestination>,
    accountItem: LegacyMoreAccountItem?,
    onDestinationSelected: (MusicDestination) -> Unit,
    onAccountSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val inflationParent = FrameLayout(viewContext)
            LayoutInflater.from(viewContext).inflate(
                R.layout.more_fragment_layout,
                inflationParent,
                false,
            ).apply {
                findViewById<ListView>(R.id.list)?.apply {
                    divider = null
                    cacheColorHint = Color.TRANSPARENT
                    setBackgroundColor(Color.TRANSPARENT)
                    layoutAnimation = AnimationUtils.loadLayoutAnimation(viewContext, R.anim.list_anim_layout)
                }
            }
        },
        update = { root ->
            root.visibility = if (active) View.VISIBLE else View.INVISIBLE
            val listView = root.findViewById<ListView>(R.id.list) ?: return@AndroidView
            val adapter = listView.adapter as? LegacyMoreRootAdapter
                ?: LegacyMoreRootAdapter().also { nextAdapter ->
                    listView.adapter = nextAdapter
                    listView.scheduleLayoutAnimation()
                }
            adapter.submitList(accountItem, destinations)
            listView.setOnItemClickListener { _, _, position, _ ->
                when (val item = adapter.itemAt(position)) {
                    LegacyMoreRootItem.Account -> {
                        if (accountItem?.enabled == true) onAccountSelected()
                    }
                    is LegacyMoreRootItem.Destination -> onDestinationSelected(item.destination)
                    null -> Unit
                }
            }
        },
    )
}

private class LegacyMoreRootAdapter : BaseAdapter() {
    private var accountItem: LegacyMoreAccountItem? = null
    private var items: List<LegacyMoreRootItem> = emptyList()

    fun submitList(
        nextAccountItem: LegacyMoreAccountItem?,
        nextDestinations: List<MusicDestination>,
    ) {
        val nextItems = buildList {
            if (nextAccountItem != null) add(LegacyMoreRootItem.Account)
            nextDestinations.forEach { destination -> add(LegacyMoreRootItem.Destination(destination)) }
        }
        if (accountItem == nextAccountItem && items == nextItems) {
            return
        }
        accountItem = nextAccountItem
        items = nextItems
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): LegacyMoreRootItem? = items.getOrNull(position)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = when (val item = items[position]) {
        LegacyMoreRootItem.Account -> Long.MIN_VALUE
        is LegacyMoreRootItem.Destination -> item.destination.route.hashCode().toLong()
    }

    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.more_item, parent, false)
        val itemView = (view as? ListContentItemText)
            ?: view.findViewById<ListContentItemText>(R.id.list_content_item)
            ?: return view
        when (val item = items[position]) {
            LegacyMoreRootItem.Account -> {
                val account = accountItem ?: return view
                itemView.setIcon(R.drawable.ic_account_box)
                itemView.setTitle(account.title)
                itemView.setSummary(account.summary)
                itemView.isEnabled = account.enabled
                itemView.alpha = if (account.enabled) 1f else 0.6f
            }
            is LegacyMoreRootItem.Destination -> {
                val destination = item.destination
                itemView.setIcon(destination.overflowIconRes)
                itemView.setTitle(parent.context.getString(destination.labelRes))
                itemView.setSummary(null)
                itemView.isEnabled = true
                itemView.alpha = 1f
            }
        }
        itemView.setSubtitle(null)
        itemView.setArrowVisible(true)
        return view
    }
}

private enum class LegacyMoreSecondaryPage {
    Settings,
}

private data class LegacyMoreAccountItem(
    val title: String,
    val summary: String?,
    val enabled: Boolean,
)

private sealed interface LegacyMoreRootItem {
    data object Account : LegacyMoreRootItem

    data class Destination(val destination: MusicDestination) : LegacyMoreRootItem
}

private fun NeteaseOnlineState.toMoreAccountItem(context: android.content.Context): LegacyMoreAccountItem {
    return when (phase) {
        NeteaseOnlinePhase.LoggedOut -> LegacyMoreAccountItem(
            title = context.getString(R.string.netease_login),
            summary = null,
            enabled = true,
        )
        NeteaseOnlinePhase.Disabled,
        NeteaseOnlinePhase.CheckingSession,
        -> LegacyMoreAccountItem(
            title = context.getString(R.string.netease_service_name),
            summary = context.getString(R.string.netease_login_checking),
            enabled = false,
        )
        NeteaseOnlinePhase.LoadingPlaylists -> LegacyMoreAccountItem(
            title = profile?.nickname ?: context.getString(R.string.netease_service_name),
            summary = context.getString(R.string.netease_login_checking),
            enabled = false,
        )
        NeteaseOnlinePhase.Ready -> LegacyMoreAccountItem(
            title = profile?.nickname ?: context.getString(R.string.netease_service_name),
            summary = context.getString(R.string.netease_service_name),
            enabled = true,
        )
        NeteaseOnlinePhase.Error -> LegacyMoreAccountItem(
            title = profile?.nickname ?: context.getString(R.string.netease_service_name),
            summary = context.getString(
                if (sessionPresent) {
                    R.string.netease_account_connection_error
                } else {
                    R.string.netease_login_retry
                },
            ),
            enabled = true,
        )
    }
}
