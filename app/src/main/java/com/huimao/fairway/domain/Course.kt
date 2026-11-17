package com.huimao.fairway.domain

import com.huimao.fairway.data.CoursePack
import com.huimao.fairway.data.TeeRating

/** 经纬度点（不直接用 android.location 的类，领域层保持纯 Kotlin，方便单元测试）。 */
data class LatLng(val lat: Double, val lon: Double)

data class Green(
    val center: LatLng,
    /** 果岭多边形；缺果岭的洞用洞线终点兜底时为空 */
    val poly: List<LatLng>,
)

data class Hole(
    val ref: Int,
    val par: Int,
    val handicap: Int,
    val poly: List<LatLng>,
    /** color -> 发球台位置 */
    val tees: Map<String, LatLng>,
    /** 缺果岭的洞：用洞线终点兜底（poly 为空）；poly 和果岭都没有时为 null */
    val green: Green?,
)

data class Course(
    val name: String,
    val nameEn: String,
    val totalPar: Int,
    val holes: List<Hole>,
    val bunkers: List<LatLng>,
    val waterHazards: List<LatLng>,
    /** 发球台颜色 -> rating/slope（WHS 用；空表示该球场未提供） */
    val ratings: Map<String, TeeRating> = emptyMap(),
)

/** CoursePack（原始 JSON）-> Course（领域模型）。 */
fun CoursePack.toCourse(): Course {
    // 疑似练习果岭不参与配对
    val greensByHole = greens
        .filter { !it.practice_green_suspect }
        .groupBy { it.hole }

    val resolvedHoles = holes.map { holeInfo ->
        val poly = holeInfo.poly.mapNotNull { p ->
            if (p.size >= 2) LatLng(p[0], p[1]) else null
        }
        val greenInfo = greensByHole[holeInfo.ref]?.firstOrNull()
        val green = when {
            greenInfo != null -> Green(
                center = LatLng(greenInfo.lat, greenInfo.lon),
                poly = greenInfo.poly.mapNotNull { p ->
                    if (p.size >= 2) LatLng(p[0], p[1]) else null
                },
            )
            // 缺果岭的洞：用洞线终点兜底
            poly.isNotEmpty() -> Green(center = poly.last(), poly = emptyList())
            else -> null
        }
        Hole(
            ref = holeInfo.ref,
            par = holeInfo.par,
            handicap = holeInfo.handicap,
            poly = poly,
            tees = tees
                .filter { it.hole == holeInfo.ref }
                .associate { it.color to LatLng(it.lat, it.lon) },
            green = green,
        )
    }.sortedBy { it.ref }

    return Course(
        name = course.name,
        nameEn = course.name_en,
        totalPar = course.total_par,
        holes = resolvedHoles,
        bunkers = bunkers.map { LatLng(it.lat, it.lon) },
        waterHazards = water_hazards.map { LatLng(it.lat, it.lon) },
        ratings = course.ratings.associateBy { it.color },
    )
}
