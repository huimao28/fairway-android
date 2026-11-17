package com.huimao.fairway.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HoleScore(
    val hole: Int,
    val strokes: Int = 0,
    val putts: Int = 0,
    val penalties: Int = 0,
    /** 开球上球道（仅 par 4/5 有意义；null = 未记录） */
    val fairwayHit: Boolean? = null,
    /** 标准杆上果岭（null = 未记录） */
    val gir: Boolean? = null,
)

@Serializable
data class RoundRecord(
    val id: Long,
    val courseName: String,
    val dateEpochMs: Long,
    val scores: List<HoleScore>,
)

private val Context.roundsDataStore: DataStore<Preferences> by preferencesDataStore(name = "rounds")

/** 成绩持久化：DataStore Preferences 存一整个 JSON 数组（v1 数据量小，够用）。 */
class RoundStore(context: Context) {

    private val dataStore = context.roundsDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private val roundsKey = stringPreferencesKey("rounds_json")

    val rounds: Flow<List<RoundRecord>> = dataStore.data.map { prefs ->
        val raw = prefs[roundsKey] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<RoundRecord>>(raw) }.getOrElse { emptyList() }
    }

    suspend fun saveRound(record: RoundRecord) {
        rewrite {
            removeAll { it.id == record.id }
            add(record)
        }
    }

    /** 修改一条已保存的记录（按 id 替换整条）。 */
    suspend fun updateRound(record: RoundRecord) {
        rewrite {
            val idx = indexOfFirst { it.id == record.id }
            if (idx >= 0) this[idx] = record else add(record)
        }
    }

    /** 删除一条已保存的记录。 */
    suspend fun deleteRound(id: Long) {
        rewrite { removeAll { it.id == id } }
    }

    private suspend fun rewrite(transform: MutableList<RoundRecord>.() -> Unit) {
        val current = rounds.first().toMutableList()
        current.transform()
        current.sortByDescending { it.dateEpochMs }
        dataStore.edit { prefs ->
            prefs[roundsKey] = json.encodeToString(current)
        }
    }
}
