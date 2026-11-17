package com.huimao.fairway.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 球场数据包（通用格式，OSM 导出）。
 * 一个 JSON 文件就是一个球场：加球场 = 在 assets/courses/ 里加一个文件，不用改代码。
 * 用 ignoreUnknownKeys 解析，未来字段扩展不炸。
 */
@Serializable
data class CourseInfo(
    val name: String,
    val name_en: String = "",
    val osm_id: String = "",
    val lat: Double,
    val lon: Double,
    val total_par: Int = 0,
    val source: String = "",
)

@Serializable
data class HoleInfo(
    val ref: Int,
    val par: Int,
    val handicap: Int = 0,
    /** 洞线节点 [[lat, lon], ...]，OSM 原始顺序 */
    val poly: List<List<Double>> = emptyList(),
    val osm_id: String = "",
)

@Serializable
data class TeeInfo(
    val hole: Int,
    /** black | blue | white | red */
    val color: String,
    val lat: Double,
    val lon: Double,
    val dist_m: Int = 0,
)

@Serializable
data class GreenInfo(
    val hole: Int,
    val lat: Double,
    val lon: Double,
    val dist_m: Int = 0,
    /** 果岭多边形 [[lat, lon], ...]；前/中/后沿测距就靠它 */
    val poly: List<List<Double>> = emptyList(),
    val osm_id: String = "",
    /** 距洞线终点过远的疑似练习果岭，测距时排除 */
    val practice_green_suspect: Boolean = false,
)

@Serializable
data class PointInfo(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class GapsInfo(
    val missing_greens: List<Int> = emptyList(),
    val notes: String = "",
)

@Serializable
data class CoursePack(
    val course: CourseInfo,
    val holes: List<HoleInfo> = emptyList(),
    val tees: List<TeeInfo> = emptyList(),
    val greens: List<GreenInfo> = emptyList(),
    val bunkers: List<PointInfo> = emptyList(),
    val water_hazards: List<PointInfo> = emptyList(),
    /** 球道：v1 数据里是空数组；形状未定先用 JsonElement 兜底，不阻塞解析 */
    val fairways: List<JsonElement> = emptyList(),
    val gaps: GapsInfo = GapsInfo(),
)
