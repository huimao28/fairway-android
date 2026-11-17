package com.huimao.fairway

import com.huimao.fairway.data.HoleScore
import com.huimao.fairway.domain.Hole
import com.huimao.fairway.domain.Whs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhsTest {

    /** 18 个 par 4 的洞，让杆指数 1..18。 */
    private fun holes() = (1..18).map { Hole(it, 4, it, emptyList(), emptyMap(), null) }

    @Test
    fun differential_usesSlopeAndRating() {
        // 调整后总杆 85，rating 70.7，slope 121 -> 113/121 * (85 - 70.7) = 13.36
        val d = Whs.differential(85, 70.7, 121)
        assertEquals(13.4, d, 0.05)
    }

    @Test
    fun handicapIndex_needsThreeRounds() {
        assertNull(Whs.handicapIndex(listOf(10.0, 12.0)))
        // 3 轮取最优 1 个：10.0 * 0.96 = 9.6
        assertEquals(9.6, Whs.handicapIndex(listOf(10.0, 12.0, 14.0))!!, 0.001)
    }

    @Test
    fun handicapIndex_bestEightOfTwenty() {
        // 20 轮微分从 27 递减到 8（最旧在前）：最优 8 个是 8..15，均值 11.5 * 0.96 = 11.04 -> 11.0
        // 指数一路下降，不触发软/硬上限
        val diffs = (27 downTo 8).map { it.toDouble() }
        assertEquals(11.0, Whs.handicapIndex(diffs)!!, 0.001)
    }

    @Test
    fun handicapIndex_softCap_limitsJump() {
        // 前 20 轮微分 10.0（指数 9.6），之后 20 轮微分 15.0：原始指数 14.4，
        // 超历史最低 9.6 共 4.8，软上限：9.6 + 3.0 + (4.8 - 3.0) * 0.5 = 13.5
        val diffs = List(20) { 10.0 } + List(20) { 15.0 }
        assertEquals(13.5, Whs.handicapIndex(diffs)!!, 0.001)
    }

    @Test
    fun handicapIndex_hardCap_limitsJump() {
        // 之后 20 轮微分 20.0：原始指数 19.2，超历史最低 9.6 共 9.6，硬上限 9.6 + 5.0 = 14.6
        val diffs = List(20) { 10.0 } + List(20) { 20.0 }
        assertEquals(14.6, Whs.handicapIndex(diffs)!!, 0.001)
    }

    @Test
    fun courseHandicap_roundsToNearest() {
        // 指数 10.0，白 T slope 121 rating 70.7 par 72 -> 10*121/113 + (70.7-72) = 9.4 -> 9
        assertEquals(9, Whs.courseHandicap(10.0, 121, 70.7, 72))
    }

    @Test
    fun adjustedGross_capsAtNetDoubleBogey() {
        // 球场差点 0：每洞最多 par+2=6。1 号洞打 9 杆 -> 按 6 计；其余 17 洞各 4 杆
        val scores = listOf(HoleScore(1, strokes = 9)) +
            (2..18).map { HoleScore(it, strokes = 4) }
        assertEquals(6 + 17 * 4, Whs.adjustedGross(scores, holes(), 0))
    }

    @Test
    fun adjustedGross_givesStrokesOnHardestHoles() {
        // 球场差点 1：让杆指数 1 的洞（1 号洞）多让 1 杆，上限 7；其他洞上限 6
        val scores = (1..18).map { HoleScore(it, strokes = 9) }
        // 1 号洞按 7 计，其余按 6 计
        assertEquals(7 + 17 * 6, Whs.adjustedGross(scores, holes(), 1))
    }
}
