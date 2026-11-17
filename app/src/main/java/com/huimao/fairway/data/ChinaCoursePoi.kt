package com.huimao.fairway.data

import android.content.Context
import com.huimao.fairway.domain.Geo
import com.huimao.fairway.domain.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 国内球场 POI：OSM 上的球场名 + 坐标（assets/poi/china-courses.json）。
 * 数据来自 OpenStreetMap（2026-11-17 提取，CN + TW + MO 三个 area 合并），
 * 已去掉练习场/训练场/学院/迷你等打不了的条目；aliases 只放已核实的中文别名，不编造。
 */
@Serializable
data class CoursePoi(
    @SerialName("osm_id") val osmId: Long,
    val name: String,
    @SerialName("name_en") val nameEn: String = "",
    val aliases: List<String> = emptyList(),
    val lat: Double,
    val lon: Double,
    /** 逆地理编码补的城市/国家（Nominatim，提取时一次算好）；拿不到时为空，界面自动隐藏该行。 */
    val city: String = "",
    val country: String = "",
)

/**
 * 一条 POI 对查询的匹配分（越小越靠前），不匹配返回 null。
 * 中文名与别名优先（0 全等 / 1 前缀 / 2 包含），英文名次之（3/4/5）。
 */
fun poiMatchScore(poi: CoursePoi, query: String): Int? {
    val q = query.trim()
    if (q.isEmpty()) return null
    fun scoreOf(text: String, base: Int): Int? {
        if (text.isEmpty()) return null
        return when {
            text.equals(q, ignoreCase = true) -> base
            text.startsWith(q, ignoreCase = true) -> base + 1
            text.contains(q, ignoreCase = true) -> base + 2
            else -> null
        }
    }
    scoreOf(poi.name, 0)?.let { return it }
    for (alias in poi.aliases) {
        scoreOf(alias, 0)?.let { return it }
    }
    return scoreOf(poi.nameEn, 3)
}

/** 离线按名搜索：按匹配分、再按名字排序，最多返回 limit 条。 */
fun searchPois(all: List<CoursePoi>, query: String, limit: Int = 50): List<CoursePoi> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return all.mapNotNull { poi -> poiMatchScore(poi, q)?.let { it to poi } }
        .sortedWith(compareBy({ it.first }, { it.second.name }))
        .take(limit)
        .map { it.second }
}

/** 离线找附近：radiusKm 公里内按距离排序。 */
fun nearbyPois(
    all: List<CoursePoi>,
    lat: Double,
    lon: Double,
    radiusKm: Double = 50.0,
): List<CoursePoi> {
    val origin = LatLng(lat, lon)
    val radiusM = radiusKm * 1000.0
    return all.mapNotNull { poi ->
        val d = Geo.haversineMeters(origin, LatLng(poi.lat, poi.lon))
        if (d <= radiusM) d to poi else null
    }.sortedBy { it.first }.map { it.second }
}

/**
 * 国内球场 POI 仓库：首次使用时从 assets 读一次、常驻内存。
 * 资产缺失或损坏时返回空列表（调用方自动走在线搜索兜底，不崩）。
 */
class ChinaCoursePoiRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val loadMutex = Mutex()
    private var cached: List<CoursePoi>? = null

    suspend fun load(): List<CoursePoi> {
        cached?.let { return it }
        return loadMutex.withLock {
            cached?.let { return it }
            val list = runCatching {
                withContext(Dispatchers.IO) {
                    context.assets.open("poi/china-courses.json").use { stream ->
                        json.decodeFromString<List<CoursePoi>>(stream.readBytes().decodeToString())
                    }
                }
            }.getOrElse { emptyList() }
            cached = list
            list
        }
    }

    /** 离线按名搜索。 */
    suspend fun search(query: String): List<CoursePoi> = searchPois(load(), query)

    /** 离线找附近（默认 50 公里内）。 */
    suspend fun nearby(lat: Double, lon: Double, radiusKm: Double = 50.0): List<CoursePoi> =
        nearbyPois(load(), lat, lon, radiusKm)
}
