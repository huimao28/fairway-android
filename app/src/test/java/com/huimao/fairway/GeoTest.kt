package com.huimao.fairway

import com.huimao.fairway.domain.Geo
import com.huimao.fairway.domain.Green
import com.huimao.fairway.domain.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun haversine_equatorOneDegree_isAbout111195m() {
        val d = Geo.haversineMeters(LatLng(0.0, 0.0), LatLng(0.0, 1.0))
        assertTrue("expected ~111195m, got $d", d in 111_000.0..111_400.0)
    }

    @Test
    fun haversine_samePoint_isZero() {
        assertEquals(
            0.0,
            Geo.haversineMeters(LatLng(29.118, 111.693), LatLng(29.118, 111.693)),
            0.001,
        )
    }

    @Test
    fun haversine_taohuayuanHole1TeeToGreen_isReasonable() {
        // 桃花源 1 号洞：黑 Tee(29.118315, 111.690642) 到果岭中心附近约 400 米级
        val d = Geo.haversineMeters(
            LatLng(29.118315, 111.690642),
            LatLng(29.119203, 111.694879),
        )
        assertTrue("expected 300-600m, got $d", d in 300.0..600.0)
    }

    @Test
    fun pointToSegment_onSegment_isZero() {
        val d = Geo.pointToSegmentMeters(
            LatLng(29.118, 111.693),
            LatLng(29.117, 111.693),
            LatLng(29.119, 111.693),
        )
        assertTrue("expected ~0m, got $d", d < 0.5)
    }

    @Test
    fun greenDistances_frontLessThanMidLessThanBack() {
        val center = LatLng(29.118, 111.694)
        val poly = listOf(
            LatLng(29.1182, 111.6938),
            LatLng(29.1182, 111.6942),
            LatLng(29.1178, 111.6942),
            LatLng(29.1178, 111.6938),
        )
        val green = Green(center = center, poly = poly)
        // 玩家在果岭正南约 111 米处
        val (front, mid, back) = Geo.greenDistances(LatLng(29.117, 111.694), green)
        assertTrue("front($front) should be < mid($mid)", front < mid)
        assertTrue("mid($mid) should be < back($back)", mid < back)
        assertTrue("front expected 70-100m, got $front", front in 70.0..100.0)
        assertTrue("back expected 120-150m, got $back", back in 120.0..150.0)
    }

    @Test
    fun greenDistances_emptyPoly_fallsBackToCenter() {
        val green = Green(center = LatLng(29.118, 111.694), poly = emptyList())
        val (front, mid, back) = Geo.greenDistances(LatLng(29.117, 111.694), green)
        assertEquals(front, mid, 0.001)
        assertEquals(mid, back, 0.001)
    }
}
