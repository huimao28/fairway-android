package com.huimao.fairway

import com.huimao.fairway.data.LandmarkPoint
import com.huimao.fairway.data.SwingFrame
import com.huimao.fairway.data.SwingLandmark
import com.huimao.fairway.domain.SwingMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwingMetricsTest {

    private fun pt(type: Int, x: Double, y: Double) =
        LandmarkPoint(type, x.toFloat(), y.toFloat(), 1f)

    @Test
    fun angleBetween_rightTriangle_is90() {
        // (0,1)-(0,0)-(1,0)：直角
        val a = pt(SwingLandmark.NOSE, 0.0, 1.0)
        val b = pt(SwingLandmark.NOSE, 0.0, 0.0)
        val c = pt(SwingLandmark.NOSE, 1.0, 0.0)
        assertEquals(90.0, SwingMetrics.angleBetween(a, b, c)!!, 0.001)
    }

    @Test
    fun angleBetween_60deg_is60() {
        // (1,0)-(0,0)-(0.5,√3/2)：60°
        val a = pt(SwingLandmark.NOSE, 1.0, 0.0)
        val b = pt(SwingLandmark.NOSE, 0.0, 0.0)
        val c = pt(SwingLandmark.NOSE, 0.5, Math.sqrt(3.0) / 2)
        assertEquals(60.0, SwingMetrics.angleBetween(a, b, c)!!, 0.001)
    }

    @Test
    fun angleBetween_straightLine_is180() {
        val a = pt(SwingLandmark.NOSE, -1.0, 0.0)
        val b = pt(SwingLandmark.NOSE, 0.0, 0.0)
        val c = pt(SwingLandmark.NOSE, 1.0, 0.0)
        assertEquals(180.0, SwingMetrics.angleBetween(a, b, c)!!, 0.001)
    }

    /**
     * 合成挥杆序列（11 帧，t=0..1000ms）：肩转 0→90°（t=600 到顶）→回 0，
     * 髋转 0→45°→回 0。坐标与期望值先用 Python 按同一数学算过
     * （~/workspace/tmp/swing_expected.py），Kotlin 里只填算好的数。
     */
    private fun syntheticFrames(): List<SwingFrame> {
        // 每行：t, 肩左, 肩右, 髋左, 髋右；其余点全帧固定
        val rows = listOf(
            arrayOf(0L, 0.420000, 0.300000, 0.620000, 0.300000, 0.450000, 0.600000, 0.550000, 0.600000),
            arrayOf(100L, 0.421519, 0.282635, 0.618481, 0.317365, 0.450190, 0.595642, 0.549810, 0.604358),
            arrayOf(200L, 0.429369, 0.257738, 0.610631, 0.342262, 0.451093, 0.589604, 0.548907, 0.610396),
            arrayOf(300L, 0.449289, 0.229289, 0.590711, 0.370711, 0.453015, 0.582899, 0.546985, 0.617101),
            arrayOf(400L, 0.477738, 0.209369, 0.562262, 0.390631, 0.456699, 0.575000, 0.543301, 0.625000),
            arrayOf(500L, 0.502635, 0.201519, 0.537365, 0.398481, 0.460599, 0.569217, 0.539401, 0.630783),
            arrayOf(600L, 0.520000, 0.200000, 0.520000, 0.400000, 0.464645, 0.564645, 0.535355, 0.635355),
            arrayOf(700L, 0.485798, 0.206031, 0.554202, 0.393969, 0.456699, 0.575000, 0.543301, 0.625000),
            arrayOf(800L, 0.443396, 0.235721, 0.596604, 0.364279, 0.451093, 0.589604, 0.548907, 0.610396),
            arrayOf(900L, 0.423407, 0.274118, 0.616593, 0.325882, 0.450122, 0.596512, 0.549878, 0.603488),
            arrayOf(1000L, 0.420000, 0.300000, 0.620000, 0.300000, 0.450000, 0.600000, 0.550000, 0.600000),
        )
        return rows.map { r ->
            val t = r[0] as Long
            val sl = pt(SwingLandmark.LEFT_SHOULDER, r[1] as Double, r[2] as Double)
            val sr = pt(SwingLandmark.RIGHT_SHOULDER, r[3] as Double, r[4] as Double)
            val hl = pt(SwingLandmark.LEFT_HIP, r[5] as Double, r[6] as Double)
            val hr = pt(SwingLandmark.RIGHT_HIP, r[7] as Double, r[8] as Double)
            SwingFrame(
                timeMs = t,
                landmarks = listOf(
                    sl, sr, hl, hr,
                    pt(SwingLandmark.NOSE, 0.5, 0.15),
                    pt(SwingLandmark.LEFT_ELBOW, 0.35, 0.45),
                    pt(SwingLandmark.RIGHT_ELBOW, 0.65, 0.45),
                    pt(SwingLandmark.LEFT_WRIST, 0.3, 0.55),
                    pt(SwingLandmark.RIGHT_WRIST, 0.7, 0.55),
                    pt(SwingLandmark.LEFT_KNEE, 0.47, 0.8),
                    pt(SwingLandmark.RIGHT_KNEE, 0.55, 0.8),
                    pt(SwingLandmark.LEFT_ANKLE, 0.45, 0.95),
                    pt(SwingLandmark.RIGHT_ANKLE, 0.55, 0.95),
                ),
            )
        }
    }

    @Test
    fun analyze_syntheticSwing_findsKeyMomentsAndMetrics() {
        val (moments, metrics) = SwingMetrics.analyze(syntheticFrames())
        // Python 复算：top 在 t=600（肩转 90°），impact 在 t=900（髋转先回到 ≤9°）
        assertEquals(0L, moments.addressMs!!)
        assertEquals(600L, moments.topMs!!)
        assertEquals(900L, moments.impactMs!!)
        assertEquals(1000L, moments.finishMs!!)
        assertEquals(90.0, metrics.shoulderTurnDeg!!, 0.5)
        assertEquals(45.0, metrics.hipTurnDeg!!, 0.5)
        // 准备姿势：肩中点 (0.52, 0.3)、髋中点 (0.5, 0.6) -> 脊柱角 3.814°
        assertEquals(3.814, metrics.spineTiltDeg!!, 0.05)
        // 左腿髋 (0.45,0.6)、膝 (0.47,0.8)、踝 (0.45,0.95) -> 膝弯 166.695°
        assertEquals(166.695, metrics.kneeFlexDeg!!, 0.5)
        assertEquals(600L, metrics.backswingMs!!)
        assertEquals(300L, metrics.downswingMs!!)
        assertEquals(2.0, metrics.tempoRatio!!, 0.01)
    }

    @Test
    fun analyze_noPoses_returnsNulls() {
        val frames = listOf(SwingFrame(0, emptyList()), SwingFrame(100, emptyList()))
        val (moments, metrics) = SwingMetrics.analyze(frames)
        assertNull(moments.addressMs)
        assertNull(moments.topMs)
        assertNull(moments.impactMs)
        assertNull(moments.finishMs)
        assertNull(metrics.shoulderTurnDeg)
        assertNull(metrics.tempoRatio)
    }

    @Test
    fun analyze_truncatedBeforeImpact_tempoIsNull() {
        // 只到上杆顶点就断了：有 top 没 impact，节奏算不出
        val frames = syntheticFrames().take(7) // t=0..600
        val (moments, metrics) = SwingMetrics.analyze(frames)
        assertEquals(600L, moments.topMs!!)
        assertNull(moments.impactMs)
        assertNull(metrics.tempoRatio)
        assertEquals(90.0, metrics.shoulderTurnDeg!!, 0.5)
    }
}
