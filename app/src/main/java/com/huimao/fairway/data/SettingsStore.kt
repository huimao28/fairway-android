package com.huimao.fairway.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 应用设置：目前只有 GolfCore API key。
 * key 只存在本机 DataStore 里，不进仓库、不打日志；用户在 platform.golfcore.app
 * 免费注册（免卡）后自己填；2026-12-04 前不填也能用（免 key 访问），之后必填。
 */
class SettingsStore(context: Context) {

    private val dataStore = context.settingsDataStore

    private val golfCoreApiKey = stringPreferencesKey("golfcore_api_key")

    /** 用户填的 GolfCore key（空白视为未填）。 */
    val golfCoreKey: Flow<String?> = dataStore.data.map { prefs ->
        prefs[golfCoreApiKey]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveGolfCoreKey(key: String) {
        dataStore.edit { prefs -> prefs[golfCoreApiKey] = key.trim() }
    }

    suspend fun clearGolfCoreKey() {
        dataStore.edit { prefs -> prefs.remove(golfCoreApiKey) }
    }
}
