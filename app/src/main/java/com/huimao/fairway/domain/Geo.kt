package com.huimao.fairway.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** 纯 Kotlin 的距离计算（无 Android 依赖，可单元测试）。 */
object Geo {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 两点间大圆距离（米）。 */
    fun haversineMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2).pow(2) + cos(la1) * cos(la2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    private fun Double.pow(exp: Int): Double = Math.pow(this, exp.toDouble())

    /** 从 from 到 to 的方位角（度，0 = 正北，顺时针 0–360）。 */
    fun bearing(from: LatLng, to: LatLng): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLon = Math.toRadians(to.lon - from.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * 点到线段的最短距离（米）。
     * 用等距圆柱投影把局部坐标换算成米：高尔夫场景下（几百米内）误差可忽略。
     */
    fun pointToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Double {
        val lat0 = Math.toRadians(p.lat)
        fun toXY(q: LatLng): Pair<Double, Double> {
            val x = Math.toRadians(q.lon - p.lon) * cos(lat0) * EARTH_RADIUS_M
            val y = Math.toRadians(q.lat - p.lat) * EARTH_RADIUS_M
            return x to y
        }
        val (ax, ay) = toXY(a)
        val (bx, by) = toXY(b)
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        // p 在局部坐标系里就是原点 (0, 0)
        val t = if (len2 == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / len2
        val tc = t.coerceIn(0.0, 1.0)
        val cx = ax + tc * dx
        val cy = ay + tc * dy
        return hypot(cx, cy)
    }

    /**
     * 果岭前/中/后沿距离（米）。
     * 前沿 = 到多边形各边的最短距离；中沿 = 到中心点；后沿 = 到多边形顶点的最远距离。
     * 多边形为空（兜底洞）时三者都等于到中心点的距离。
     */
    fun greenDistances(player: LatLng, green: Green): Triple<Double, Double, Double> {
        val mid = haversineMeters(player, green.center)
        if (green.poly.isEmpty()) return Triple(mid, mid, mid)
        var front = Double.MAX_VALUE
        for (i in green.poly.indices) {
            val a = green.poly[i]
            val b = green.poly[(i + 1) % green.poly.size]
            front = minOf(front, pointToSegmentMeters(player, a, b))
        }
        val back = green.poly.maxOf { haversineMeters(player, it) }
        return Triple(front, mid, back)
    }
}
