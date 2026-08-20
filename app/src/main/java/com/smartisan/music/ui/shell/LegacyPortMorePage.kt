package com.smartisan.music.ui.shell

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.smartisan.music.R
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.data.settings.AudioFxPreset
import com.smartisan.music.data.settings.NavigationSettings
import com.smartisan.music.data.settings.PlaybackSettings
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.shell.titlebar.LegacyPortTitleBarShadow
import com.smartisan.music.ui.widgets.legacy.ListContentItemText
import kotlinx.coroutines.delay

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
    onDestinationSelected: (MusicDestination) -> Unit,
    onScratchEnabledChange: (Boolean) -> Unit,
    onHidePlayerAxisEnabledChange: (Boolean) -> Unit,
    onPopcornSoundEnabledChange: (Boolean) -> Unit,
    onAudioFxEnabledChange: (Boolean) -> Unit,
    onAudioFxPresetChange: (AudioFxPreset) -> Unit,
    onAudioFxCustomGainDbPointsChange: (List<Float>) -> Unit,
    onArtistSeparatorsChange: (Set<String>) -> Unit,
    onTabPinnedChange: (String, Boolean) -> Unit,
    onSettingsVisibleChange: (Boolean) -> Unit,
    onSettingsPageActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsPredictiveBackState = rememberLegacyPortPredictiveBackState()

    LaunchedEffect(active, settingsVisible) {
        if (active && settingsVisible) {
            onSettingsPageActiveChanged(true)
        } else {
            if (!settingsVisible) {
                delay(LegacyPageStackSlideMillis.toLong())
            }
            onSettingsPageActiveChanged(false)
        }
    }
    DisposableEffect(Unit) {
        onDispose { onSettingsPageActiveChanged(false) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LegacyPortPageStackTransition(
            secondaryKey = settingsVisible.takeIf { it },
            modifier = Modifier.fillMaxSize(),
            label = "legacy more settings stack",
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
                        onDestinationSelected = onDestinationSelected,
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
                    onClose = { onSettingsVisibleChange(false) },
                    onScratchEnabledChange = onScratchEnabledChange,
                    onHidePlayerAxisEnabledChange = onHidePlayerAxisEnabledChange,
                    onPopcornSoundEnabledChange = onPopcornSoundEnabledChange,
                    onAudioFxEnabledChange = onAudioFxEnabledChange,
                    onAudioFxPresetChange = onAudioFxPresetChange,
                    onAudioFxCustomGainDbPointsChange = onAudioFxCustomGainDbPointsChange,
                    onArtistSeparatorsChange = onArtistSeparatorsChange,
                    onTabPinnedChange = onTabPinnedChange,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }
}

@Composable
private fun LegacyMoreRootPage(
    active: Boolean,
    externalTitleAreaHeight: Dp,
    destinations: List<MusicDestination>,
    onDestinationSelected: (MusicDestination) -> Unit,
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
            onDestinationSelected = onDestinationSelected,
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
    onDestinationSelected: (MusicDestination) -> Unit,
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
            adapter.submitList(destinations)
            listView.setOnItemClickListener { _, _, position, _ ->
                adapter.itemAt(position)?.let(onDestinationSelected)
            }
        },
    )
}

private class LegacyMoreRootAdapter : BaseAdapter() {
    private var destinations: List<MusicDestination> = emptyList()

    fun submitList(nextDestinations: List<MusicDestination>) {
        if (destinations == nextDestinations) {
            return
        }
        destinations = nextDestinations
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): MusicDestination? = destinations.getOrNull(position)

    override fun getCount(): Int = destinations.size

    override fun getItem(position: Int): Any = destinations[position]

    override fun getItemId(position: Int): Long = destinations[position].route.hashCode().toLong()

    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.more_item, parent, false)
        val itemView = (view as? ListContentItemText)
            ?: view.findViewById<ListContentItemText>(R.id.list_content_item)
            ?: return view
        val destination = destinations[position]
        itemView.setIcon(destination.overflowIconRes)
        itemView.setTitle(parent.context.getString(destination.labelRes))
        itemView.setSummary(null)
        itemView.setSubtitle(null)
        itemView.setArrowVisible(true)
        return view
    }
}
