package com.smartisan.music.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val OnlineMusicSettingsStoreName = "online_music_settings"

private val Context.onlineMusicSettingsDataStore by preferencesDataStore(
    name = OnlineMusicSettingsStoreName,
)

enum class MusicSourceMode(
    val preferenceValue: String,
) {
    Local("local"),
    Netease("netease");

    companion object {
        fun fromPreference(value: String?): MusicSourceMode =
            entries.firstOrNull { mode -> mode.preferenceValue == value } ?: Local
    }
}

data class OnlineMusicSettings(
    val sourceMode: MusicSourceMode = MusicSourceMode.Local,
) {
    val neteaseEnabled: Boolean
        get() = sourceMode == MusicSourceMode.Netease
}

class OnlineMusicSettingsStore(
    private val context: Context,
) {
    val settings: Flow<OnlineMusicSettings> = context.onlineMusicSettingsDataStore.data
        .map(Preferences::toOnlineMusicSettings)
        .distinctUntilChanged()

    suspend fun setNeteaseEnabled(enabled: Boolean) {
        context.onlineMusicSettingsDataStore.edit { preferences ->
            preferences[SourceModeKey] = if (enabled) {
                MusicSourceMode.Netease.preferenceValue
            } else {
                MusicSourceMode.Local.preferenceValue
            }
        }
    }
}

internal fun Preferences.toOnlineMusicSettings(): OnlineMusicSettings = OnlineMusicSettings(
    sourceMode = MusicSourceMode.fromPreference(this[SourceModeKey]),
)

private val SourceModeKey = stringPreferencesKey("music_source_mode")
