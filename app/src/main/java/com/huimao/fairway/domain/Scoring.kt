package com.huimao.fairway.domain

import com.huimao.fairway.data.HoleScore
import com.huimao.fairway.data.RoundRecord

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
