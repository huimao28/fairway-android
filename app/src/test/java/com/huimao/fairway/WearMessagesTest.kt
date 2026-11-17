package com.huimao.fairway

import com.huimao.fairway.data.WorkoutSummary
import com.huimao.fairway.data.parseScoreUpdate
import com.huimao.fairway.data.parseWorkoutSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 手表消息解析（纯函数）：字段映射直出，期望值即输入值本身，无需复算。 */
class WearMessagesTest {

    @Test
    fun parseScoreUpdate_valid() {
        assertEquals(
            com.huimao.fairway.data.WatchScoreUpdate(hole = 3, strokes = 4, putts = 2),
            parseScoreUpdate(hole = 3, strokes = 4, putts = 2),
        )
    }

    @Test
    fun parseScoreUpdate_puttsMissingMeansUnchanged() {
        assertEquals(-1, parseScoreUpdate(hole = 3, strokes = 4, putts = null)?.putts)
    }

    @Test
    fun parseScoreUpdate_puttsBelowMinusOneClamped() {
        assertEquals(-1, parseScoreUpdate(hole = 3, strokes = 4, putts = -5)?.putts)
    }

    @Test
    fun parseScoreUpdate_invalidHoleOrStrokesDropped() {
        assertNull(parseScoreUpdate(hole = 0, strokes = 4, putts = 2))
        assertNull(parseScoreUpdate(hole = 19, strokes = 4, putts = 2))
        assertNull(parseScoreUpdate(hole = null, strokes = 4, putts = 2))
        assertNull(parseScoreUpdate(hole = 3, strokes = -1, putts = 2))
        assertNull(parseScoreUpdate(hole = 3, strokes = null, putts = 2))
    }

    @Test
    fun parseWorkoutSummary_valid() {
        assertEquals(
            WorkoutSummary(
                durationMs = 3_600_000L,
                avgHr = 110.5,
                maxHr = 150,
                calories = 320.0,
                steps = 5_200L,
            ),
            parseWorkoutSummary(
                durationMs = 3_600_000L,
                avgHr = 110.5,
                maxHr = 150,
                calories = 320.0,
                steps = 5_200L,
            ),
        )
    }

    @Test
    fun parseWorkoutSummary_zeroOrMissingDurationDropped() {
        assertNull(parseWorkoutSummary(0L, 110.5, 150, 320.0, 5_200L))
        assertNull(parseWorkoutSummary(null, 110.5, 150, 320.0, 5_200L))
    }

    @Test
    fun parseWorkoutSummary_missingOptionalsDefaultZero() {
        assertEquals(
            WorkoutSummary(durationMs = 1_800_000L, avgHr = 0.0, maxHr = 0, calories = 0.0, steps = 0L),
            parseWorkoutSummary(1_800_000L, null, null, null, null),
        )
    }

    @Test
    fun parseWorkoutSummary_negativeValuesClampedToZero() {
        val parsed = parseWorkoutSummary(1_800_000L, -5.0, -3, -10.0, -100L)
        assertEquals(0.0, parsed?.avgHr)
        assertEquals(0, parsed?.maxHr)
        assertEquals(0.0, parsed?.calories)
        assertEquals(0L, parsed?.steps)
    }
}
