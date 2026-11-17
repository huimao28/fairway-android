package com.huimao.fairway.domain

import com.huimao.fairway.data.HoleScore
import com.huimao.fairway.data.RoundRecord
import kotlin.math.roundToInt

/** 记分与简化差点：纯 Kotlin，可单元测试。 */
object Scoring {

    /** 已打洞的总杆数（strokes 为 0 的洞视为未打，不计）。 */
    fun totalStrokes(scores: List<HoleScore>): Int =
        scores.filter { it.strokes > 0 }.sumOf { it.strokes }

    /** 相对标准杆（只算已打的洞）。 */
    fun toPar(scores: List<HoleScore>, holes: List<Hole>): Int {
        val parByHole = holes.associate { it.ref to it.par }
        return scores.filter { it.strokes > 0 }
            .sumOf { it.strokes - (parByHole[it.hole] ?: 0) }
    }

    /**
     * 简化差点：最近 5 轮（不足 5 轮有多少算多少）每轮（总杆 - 球场总 par）的平均值。
     * v1 不算 WHS，只给趋势参考；没有完整轮次返回 null。
     */
    fun simplifiedHandicap(rounds: List<RoundRecord>, coursePar: Int): Double? {
        val finished = rounds
            .filter { it.scores.count { s -> s.strokes > 0 } >= 18 }
            .sortedByDescending { it.dateEpochMs }
            .take(5)
        if (finished.isEmpty()) return null
        return finished.sumOf { totalStrokes(it.scores) - coursePar }.toDouble() / finished.size
    }

    /** 标准杆上果岭率（只算已记录 GIR 的洞）。 */
    fun girRate(scores: List<HoleScore>): Double? {
        val recorded = scores.filter { it.gir != null }
        if (recorded.isEmpty()) return null
        return recorded.count { it.gir == true }.toDouble() / recorded.size
    }

    /** 开球上球道率（只算已记录 FIR 的洞）。 */
    fun firRate(scores: List<HoleScore>): Double? {
        val recorded = scores.filter { it.fairwayHit != null }
        if (recorded.isEmpty()) return null
        return recorded.count { it.fairwayHit == true }.toDouble() / recorded.size
    }

    /** 平均推杆数（只算已打且记了推杆的洞）。 */
    fun avgPutts(scores: List<HoleScore>): Double? {
        val played = scores.filter { it.strokes > 0 }
        if (played.isEmpty()) return null
        return played.sumOf { it.putts }.toDouble() / played.size
    }
}

/**
 * WHS（世界差点系统）差点指数与球场差点。
 * 规则要点：取最近 20 轮差点微分中的最优 N 个（WHS 表），均值 × 0.96；
 * 相对历史最低指数设软上限（超 3.0 的部分只算一半）与硬上限（最多 +5.0）。
 * PCC（打球条件调整）暂按 0 计；历史最低指数按全部已存轮次算（v2 简化，未按 12 个月截断）；
 * 不足 3 轮没有指数；9 洞轮次不计入微分。
 */
object Whs {

    /** 微分个数 -> 取最优个数（WHS 表）。 */
    private fun bestCount(n: Int): Int = when {
        n < 3 -> 0
        n <= 5 -> 1
        n <= 8 -> 2
        n <= 10 -> 3
        n <= 12 -> 4
        n <= 14 -> 5
        n <= 16 -> 6
        n == 17 -> 7
        else -> 8
    }

    private fun Double.roundTo1(): Double = (this * 10).roundToInt() / 10.0

    /** 球场差点 H 时，在让杆指数（stroke index）为 si 的洞上让几杆。 */
    fun strokesReceived(courseHandicap: Int, strokeIndex: Int): Int {
        if (courseHandicap <= 0 || strokeIndex <= 0) return 0
        val full = courseHandicap / 18
        val rem = courseHandicap % 18
        return full + if (strokeIndex <= rem) 1 else 0
    }

    /** 净双柏忌调整后的总杆：每洞最多记 par + 2 + 该洞让杆数。 */
    fun adjustedGross(scores: List<HoleScore>, holes: List<Hole>, courseHandicap: Int): Int {
        val holeByRef = holes.associateBy { it.ref }
        var total = 0
        for (s in scores) {
            if (s.strokes <= 0) continue
            val hole = holeByRef[s.hole] ?: continue
            val max = hole.par + 2 + strokesReceived(courseHandicap, hole.handicap)
            total += minOf(s.strokes, max)
        }
        return total
    }

    /** 差点微分 = (113 / slope) × (调整后总杆 - rating)。 */
    fun differential(adjustedGross: Int, rating: Double, slope: Int): Double =
        113.0 / slope * (adjustedGross - rating)

    /** 球场差点 = 差点指数 × (slope / 113) + (rating - par)，四舍五入取整。 */
    fun courseHandicap(index: Double, slope: Int, rating: Double, par: Int): Int =
        (index * slope / 113.0 + (rating - par)).roundToInt()

    /**
     * 由按时间升序（最旧在前）的微分列表算差点指数（含软/硬上限）。
     * 微分不足 3 个返回 null。
     */
    fun handicapIndex(differentialsOldestFirst: List<Double>): Double? {
        val idx = rawIndex(differentialsOldestFirst) ?: return null
        val low = lowestIndexSoFar(differentialsOldestFirst.dropLast(1)) ?: return idx
        val over = idx - low
        return if (over <= 3.0) idx
        else minOf(low + 3.0 + (over - 3.0) * 0.5, low + 5.0).roundTo1()
    }

    /** 不含上限的原始指数（最优 N 个均值 × 0.96）。 */
    private fun rawIndex(differentialsOldestFirst: List<Double>): Double? {
        val recent = differentialsOldestFirst.takeLast(20)
        val n = bestCount(recent.size)
        if (n == 0) return null
        return (recent.sorted().take(n).average() * 0.96).roundTo1()
    }

    /** 逐轮推进算出的指数序列里的最小值（历史最低指数）。 */
    private fun lowestIndexSoFar(differentialsOldestFirst: List<Double>): Double? {
        var low: Double? = null
        for (i in differentialsOldestFirst.indices) {
            val v = rawIndex(differentialsOldestFirst.take(i + 1)) ?: continue
            low = minOf(low ?: v, v)
        }
        return low
    }
}
