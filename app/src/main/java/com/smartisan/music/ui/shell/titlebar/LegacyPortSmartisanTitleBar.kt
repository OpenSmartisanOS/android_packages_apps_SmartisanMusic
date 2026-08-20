package com.smartisan.music.ui.shell.titlebar

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.smartisan.music.R
import com.smartisan.music.ui.widgets.legacy.TitleBar

internal typealias LegacyPortRootTitleBar =
    @Composable (Modifier, Boolean, (TitleBar) -> Unit) -> Unit

@Composable
internal fun rememberLegacyPortRootTitleBar(): LegacyPortRootTitleBar {
    return remember {
        movableContentOf { modifier: Modifier, showShadow: Boolean, update: (TitleBar) -> Unit ->
            LegacyPortSmartisanTitleBar(
                modifier = modifier,
                showShadow = showShadow,
                update = update,
            )
        }
    }
}

@Composable
internal fun LegacyPortRootTitleBar(
    rootTitleBar: LegacyPortRootTitleBar?,
    modifier: Modifier = Modifier,
    showShadow: Boolean = false,
    update: (TitleBar) -> Unit,
) {
    if (rootTitleBar == null) {
        LegacyPortSmartisanTitleBar(
            modifier = modifier,
            showShadow = showShadow,
            update = update,
        )
    } else {
        rootTitleBar(modifier, showShadow, update)
    }
}

@Composable
internal fun LegacyPortSmartisanTitleBar(
    modifier: Modifier = Modifier,
    includeStatusBar: Boolean = true,
    showShadow: Boolean = false,
    viewVisible: Boolean = true,
    updateKey: Any? = LegacyPortAlwaysUpdateTitleBar,
    update: (TitleBar) -> Unit,
) {
    val titleContentHeight = dimensionResource(R.dimen.title_bar_height)
    val shadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)
    val appliedUpdate = remember { LegacyPortTitleBarAppliedUpdate() }
    Column(
        modifier = modifier
            .then(if (showShadow) Modifier.zIndex(1f) else Modifier)
            .fillMaxWidth()
            .background(colorResource(R.color.title_bar_background)),
    ) {
        if (includeStatusBar) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleContentHeight),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    TitleBar(context).apply {
                        visibility = if (viewVisible) View.VISIBLE else View.INVISIBLE
                        setShadowVisible(false)
                        update(this)
                        appliedUpdate.key = updateKey
                        appliedUpdate.initialized = true
                    }
                },
                update = { titleBar ->
                    titleBar.visibility = if (viewVisible) View.VISIBLE else View.INVISIBLE
                    titleBar.setShadowVisible(false)
                    if (
                        updateKey === LegacyPortAlwaysUpdateTitleBar ||
                        !appliedUpdate.initialized ||
                        appliedUpdate.key != updateKey
                    ) {
                        update(titleBar)
                        appliedUpdate.key = updateKey
                        appliedUpdate.initialized = true
                    }
                },
            )
            if (showShadow) {
                LegacyPortTitleBarShadow(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = shadowHeight)
                        .fillMaxWidth()
                        .height(shadowHeight),
                )
            }
        }
    }
}

private val LegacyPortAlwaysUpdateTitleBar = Any()

private class LegacyPortTitleBarAppliedUpdate {
    var initialized: Boolean = false
    var key: Any? = null
}

@Composable
internal fun LegacyPortTitleBarShadow(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            android.view.View(context).apply {
                setBackgroundResource(R.drawable.title_bar_shadow)
            }
        },
    )
}
