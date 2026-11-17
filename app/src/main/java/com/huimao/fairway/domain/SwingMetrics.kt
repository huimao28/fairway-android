package com.huimao.fairway.domain

import com.huimao.fairway.data.KeyMoments
import com.huimao.fairway.data.LandmarkPoint
import com.huimao.fairway.data.SwingFrame
import com.huimao.fairway.data.SwingLandmark
import com.huimao.fairway.data.SwingMetrics as MetricValues
import kotlin.math.abs
import kotlin.math.atan2

/**
 * 挥杆指标计算：纯 Kotlin，可单元测试。
 * 坐标系与视频一致：x 向右，y 向下，0..1 归一化。
 */
object SwingMetrics {

    /** 参考区间（只做展示对照，不打分）。 */
    const val REFERENCE_SHOULDER_TURN_DEG = 90.0
    const val REFERENCE_HIP_TURN_DEG = 45.0
    const val REFERENCE_TEMPO_RATIO = 3.0

    /** a-b-c 在 b 点的夹角（0..180 度；退化点返回 null）。 */
    fun angleBetween(a: LandmarkPoint, b: LandmarkPoint, c: LandmarkPoint): Double? {
        val v1x = (a.x - b.x).toDouble()
        val v1y = (a.y - b.y).toDouble()
        val v2x = (c.x - b.x).toDouble()
        val v2y = (c.y - b.y).toDouble()
        if ((v1x == 0.0 && v1y == 0.0) || (v2x == 0.0 && v2y == 0.0)) return null
        val dot = v1x * v2x + v1y * v2y
        val cross = v1x * v2y - v1y * v2x
        return Math.toDegrees(atan2(abs(cross), dot))
    }

    /** p->q 连线与水平线的夹角（-180..180 度）。 */
    fun lineAngleDeg(p: LandmarkPoint, q: LandmarkPoint): Double =
        Math.toDegrees(atan2((q.y - p.y).toDouble(), (q.x - p.x).toDouble()))

    /** 角度差归一化到 (-180, 180]。 */
    fun normalizeAngleDelta(deg: Double): Double {
        var d = deg % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }

    /** 某帧相对准备帧的线转角（肩线/髋线）；缺点的帧返回 null。 */
    fun lineTurnDeg(frame: SwingFrame, address: SwingFrame, leftType: Int, rightType: Int): Double? {
        val fl = frame.landmark(leftType) ?: return null
        val fr = frame.landmark(rightType) ?: return null
        val al = address.landmark(leftType) ?: return null
        val ar = address.landmark(rightType) ?: return null
        return abs(normalizeAngleDelta(lineAngleDeg(fl, fr) - lineAngleDeg(al, ar)))
    }

    /** 脊柱前倾角：肩中点-髋中点连线与竖直线的夹角（0..90 度）。 */
    fun spineTiltDeg(frame: SwingFrame): Double? {
        val ls = frame.landmark(SwingLandmark.LEFT_SHOULDER) ?: return null
        val rs = frame.landmark(SwingLandmark.RIGHT_SHOULDER) ?: return null
        val lh = frame.landmark(SwingLandmark.LEFT_HIP) ?: return null
        val rh = frame.landmark(SwingLandmark.RIGHT_HIP) ?: return null
        val sx = (ls.x + rs.x) / 2.0
        val sy = (ls.y + rs.y) / 2.0
        val hx = (lh.x + rh.x) / 2.0
        val hy = (lh.y + rh.y) / 2.0
        return Math.toDegrees(atan2(abs(sx - hx), abs(sy - hy)))
    }

    /** 膝盖弯曲角（髋-膝-踝夹角）：以前腿（左腿，默认右手球员）为准，左腿缺点的用右腿。 */
    fun kneeFlexDeg(frame: SwingFrame): Double? {
        val left = legTriple(
            frame,
            SwingLandmark.LEFT_HIP,
            SwingLandmark.LEFT_KNEE,
            SwingLandmark.LEFT_ANKLE,
        )
        if (left != null) return angleBetween(left.first, left.second, left.third)
        val right = legTriple(
            frame,
            SwingLandmark.RIGHT_HIP,
            SwingLandmark.RIGHT_KNEE,
            SwingLandmark.RIGHT_ANKLE,
        ) ?: return null
        return angleBetween(right.first, right.second, right.third)
    }

    private fun legTriple(
        frame: SwingFrame,
        hipType: Int,
        kneeType: Int,
        ankleType: Int,
    ): Triple<LandmarkPoint, LandmarkPoint, LandmarkPoint>? {
        val hip = frame.landmark(hipType) ?: return null
        val knee = frame.landmark(kneeType) ?: return null
        val ankle = frame.landmark(ankleType) ?: return null
        return Triple(hip, knee, ankle)
    }

    /**
     * 由逐帧结果算关键时刻与指标。
     * address = 首个有姿态的帧；top = 肩转最大的帧；
     * impact = top 之后髋转最先回到接近 address 的帧（阈值 = max(5°, 最大髋转×0.2)，
     * 没达到就取 top 之后髋转最小的帧；top 之后没有帧则无 impact）；
     * finish = 最后一个有姿态的帧。
     */
    fun analyze(frames: List<SwingFrame>): Pair<KeyMoments, MetricValues> {
        val empty = KeyMoments() to MetricValues()
        val posed = frames.filter { it.landmarks.isNotEmpty() }
        if (posed.isEmpty()) return empty
        val address = posed.first()
        val shoulderTurns = posed.associate {
            it.timeMs to lineTurnDeg(
                it,
                address,
                SwingLandmark.LEFT_SHOULDER,
                SwingLandmark.RIGHT_SHOULDER,
            )
        }
        val hipTurns = posed.associate {
            it.timeMs to lineTurnDeg(
                it,
                address,
                SwingLandmark.LEFT_HIP,
                SwingLandmark.RIGHT_HIP,
            )
        }
        val top = posed.filter { shoulderTurns[it.timeMs] != null }
            .maxByOrNull { shoulderTurns[it.timeMs]!! }
        val maxShoulder = top?.let { shoulderTurns[it.timeMs] }
        val maxHip = hipTurns.values.filterNotNull().maxOrNull()
        var impact: SwingFrame? = null
        if (top != null && maxHip != null) {
            val threshold = maxOf(5.0, 0.2 * maxHip)
            val afterTop = posed.drop(posed.indexOf(top) + 1)
            impact = afterTop.firstOrNull { (hipTurns[it.timeMs] ?: Double.MAX_VALUE) <= threshold }
                ?: afterTop.minByOrNull { hipTurns[it.timeMs] ?: Double.MAX_VALUE }
        }
        val finish = posed.last()
        val backswing = top?.let { it.timeMs - address.timeMs }
        val downswing = if (top != null && impact != null) impact.timeMs - top.timeMs else null
        val tempo = if (backswing != null && downswing != null && downswing > 0) {
            backswing.toDouble() / downswing
        } else {
            null
        }
        return KeyMoments(
            addressMs = address.timeMs,
            topMs = top?.timeMs,
            impactMs = impact?.timeMs,
            finishMs = finish.timeMs,
        ) to MetricValues(
            shoulderTurnDeg = maxShoulder,
            hipTurnDeg = maxHip,
            spineTiltDeg = spineTiltDeg(address),
            kneeFlexDeg = kneeFlexDeg(address),
            backswingMs = backswing,
            downswingMs = downswing,
            tempoRatio = tempo,
        )
    }
}
