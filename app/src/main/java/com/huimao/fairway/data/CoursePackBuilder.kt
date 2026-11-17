package com.huimao.fairway.data

import com.huimao.fairway.domain.Geo
import com.huimao.fairway.domain.LatLng
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Overpass 原始元素 -> CoursePack（端上配对，逻辑与当初 Python 提取桃花源的脚本一致）。
 * 配对规则（实测结论）：Tee 配洞线起点段、果岭配洞线终点段；缺果岭的洞在领域层用洞线终点兜底。
 * OSM 上的 Tee/果岭/沙坑/水障都是 way（不是 node）；Tee 颜色在 tags["tee"]。
 */
object CoursePackBuilder {

    private const val TEE_MAX_M = 50.0
    private const val GREEN_MAX_M = 30.0
    private val TEE_ORDER = listOf("black", "blue", "white", "red")

    private data class HoleGeom(
        val ref: Int,
        val par: Int,
        val handicap: Int,
        val poly: List<LatLng>,
        val osmId: Long,
    )

    private fun centroid(geom: List<OverpassPoint>): LatLng? {
        if (geom.isEmpty()) return null
        return LatLng(geom.sumOf { it.lat } / geom.size, geom.sumOf { it.lon } / geom.size)
    }

    /** 离某点最近的洞（按洞线起点段距离）。 */
    private fun nearestHoleStart(pos: LatLng, holes: List<HoleGeom>): Pair<HoleGeom, Double>? =
        holes.map { h ->
            val d = if (h.poly.size >= 2) Geo.pointToSegmentMeters(pos, h.poly[0], h.poly[1])
            else Geo.haversineMeters(pos, h.poly.first())
            h to d
        }.minByOrNull { it.second }

    /** 离某点最近的洞（按洞线终点段距离）。 */
    private fun nearestHoleEnd(pos: LatLng, holes: List<HoleGeom>): Pair<HoleGeom, Double>? =
        holes.map { h ->
            val n = h.poly.size
            val d = if (n >= 2) Geo.pointToSegmentMeters(pos, h.poly[n - 2], h.poly[n - 1])
            else Geo.haversineMeters(pos, h.poly.first())
            h to d
        }.minByOrNull { it.second }

    /**
     * 返回 null 表示洞数据不可用（无 ref/par、或不是标准 18 洞）。
     * 调用方先用“完全没有洞”区分无洞数据，这里的 null 只表示“有洞但用不了”。
     */
    fun build(courseWay: OverpassElement, detail: OverpassResponse): CoursePack? {
        val holes = detail.elements
            .filter { it.type == "way" && it.tags["golf"] == "hole" }
            .mapNotNull { el ->
                val ref = el.tags["ref"]?.toIntOrNull() ?: return@mapNotNull null
                val par = el.tags["par"]?.toIntOrNull() ?: return@mapNotNull null
                if (par <= 0) return@mapNotNull null
                val poly = el.geometry.map { LatLng(it.lat, it.lon) }
                if (poly.size < 2) return@mapNotNull null
                HoleGeom(ref, par, el.tags["handicap"]?.toIntOrNull() ?: 0, poly, el.id)
            }
            .sortedBy { it.ref }
        // 只支持标准 18 洞（ref 1..18）；9 洞等其他规格要先改记分卡按洞数动态排
        if (holes.size != 18 || holes.map { it.ref } != (1..18).toList()) return null

        // Tee：每洞每色取离起点段最近的一个（50 米外不要，可能是练习场 Tee）
        val teeByHoleColor = mutableMapOf<Pair<Int, String>, TeeInfo>()
        val teeDist = mutableMapOf<Pair<Int, String>, Double>()
        for (el in detail.elements) {
            if (el.type != "way" || el.tags["golf"] != "tee") continue
            val color = el.tags["tee"] ?: el.tags["colour"] ?: el.tags["color"] ?: continue
            val pos = centroid(el.geometry) ?: continue
            val (hole, d) = nearestHoleStart(pos, holes) ?: continue
            if (d > TEE_MAX_M) continue
            val key = hole.ref to color
            if (d < (teeDist[key] ?: Double.MAX_VALUE)) {
                teeDist[key] = d
                teeByHoleColor[key] = TeeInfo(hole.ref, color, pos.lat, pos.lon, d.roundToInt())
            }
        }

        // 果岭：每洞取离终点段最近的一个为真果岭（30 米内），其余标疑似练习果岭
        val greenCandidates = mutableMapOf<Int, MutableList<Pair<GreenInfo, Double>>>()
        for (el in detail.elements) {
            if (el.type != "way" || el.tags["golf"] != "green") continue
            val pos = centroid(el.geometry) ?: continue
            val poly = el.geometry.map { LatLng(it.lat, it.lon) }
            val (hole, d) = nearestHoleEnd(pos, holes) ?: continue
            val info = GreenInfo(
                hole = hole.ref,
                lat = pos.lat,
                lon = pos.lon,
                dist_m = d.roundToInt(),
                poly = poly.map { listOf(it.lat, it.lon) },
                osm_id = "way/${el.id}",
            )
            greenCandidates.getOrPut(hole.ref) { mutableListOf() }.add(info to d)
        }
        val greens = mutableListOf<GreenInfo>()
        for ((_, list) in greenCandidates) {
            val sorted = list.sortedBy { it.second }
            sorted.forEachIndexed { idx, (info, d) ->
                greens.add(info.copy(practice_green_suspect = d > GREEN_MAX_M || idx > 0))
            }
        }

        val bunkers = detail.elements
            .filter { it.type == "way" && it.tags["golf"] == "bunker" }
            .mapNotNull { el -> centroid(el.geometry)?.let { PointInfo(it.lat, it.lon) } }
        val water = detail.elements
            .filter {
                it.type == "way" &&
                    (it.tags["golf"] == "water_hazard" || it.tags["golf"] == "lateral_water_hazard")
            }
            .mapNotNull { el -> centroid(el.geometry)?.let { PointInfo(it.lat, it.lon) } }

        val center = centroid(courseWay.geometry) ?: return null
        val name = courseWay.tags["name"]?.takeIf { it.isNotBlank() } ?: return null
        val missingGreens = holes
            .filter { h -> greens.none { it.hole == h.ref && !it.practice_green_suspect } }
            .map { it.ref }

        return CoursePack(
            course = CourseInfo(
                name = name,
                name_en = courseWay.tags["name:en"] ?: "",
                osm_id = "way/${courseWay.id}",
                lat = center.lat,
                lon = center.lon,
                total_par = holes.sumOf { it.par },
                source = "OpenStreetMap Overpass，应用内下载 ${LocalDate.now()}",
                ratings = emptyList(),
            ),
            holes = holes.map { h ->
                HoleInfo(h.ref, h.par, h.handicap, h.poly.map { listOf(it.lat, it.lon) }, "way/${h.osmId}")
            },
            tees = teeByHoleColor.values.sortedWith(
                compareBy({ it.hole }, { TEE_ORDER.indexOf(it.color) }),
            ),
            greens = greens.sortedBy { it.hole },
            bunkers = bunkers,
            water_hazards = water,
            gaps = GapsInfo(
                missing_greens = missingGreens,
                notes = if (missingGreens.isEmpty()) "在线下载，18 洞齐全"
                else "在线下载；缺果岭的洞：${missingGreens.joinToString("、")}，用洞线终点兜底",
            ),
        )
    }
}
