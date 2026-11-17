package com.huimao.fairway

import com.huimao.fairway.data.HoleScore
import com.huimao.fairway.data.RoundRecord
import com.huimao.fairway.domain.Hole
import com.huimao.fairway.domain.Scoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoringTest {

    private fun holes() = (1..18).map { Hole(it, 4, it, emptyList(), emptyMap(), null) }

    @Test
    fun totalStrokes_ignoresUnplayedHoles() {
        val scores = listOf(
            HoleScore(1, strokes = 4, putts = 2),
            HoleScore(2, strokes = 0, putts = 0),
            HoleScore(3, strokes = 5, putts = 2),
        )
        assertEquals(9, Scoring.totalStrokes(scores))
    }

    @Test
    fun toPar_par72Round_isCorrect() {
        val scores = (1..18).map { HoleScore(it, strokes = 4, putts = 2) }
        assertEquals(0, Scoring.toPar(scores, holes()))
        val over = (1..18).map { HoleScore(it, strokes = 5, putts = 2) }
        assertEquals(18, Scoring.toPar(over, holes()))
    }

    @Test
    fun simplifiedHandicap_noFinishedRound_isNull() {
        assertNull(Scoring.simplifiedHandicap(emptyList(), 72))
        val unfinished = listOf(
            RoundRecord(1, "桃花源", 1L, (1..9).map { HoleScore(it, strokes = 4) }),
        )
        assertNull(Scoring.simplifiedHandicap(unfinished, 72))
    }

    @Test
    fun simplifiedHandicap_twoFinishedRounds_averages() {
        val rounds = listOf(
            RoundRecord(1, "桃花源", 1L, (1..18).map { HoleScore(it, strokes = 5) }),
            RoundRecord(2, "桃花源", 2L, (1..18).map { HoleScore(it, strokes = 4) }),
        )
        // (90-72 + 72-72) / 2 = 9.0
        assertEquals(9.0, Scoring.simplifiedHandicap(rounds, 72)!!, 0.001)
    }

    @Test
    fun girAndFir_rates() {
        val scores = listOf(
            HoleScore(1, strokes = 4, gir = true, fairwayHit = true),
            HoleScore(2, strokes = 4, gir = false, fairwayHit = false),
            HoleScore(3, strokes = 4, gir = null, fairwayHit = null),
        )
        assertEquals(0.5, Scoring.girRate(scores)!!, 0.001)
        assertEquals(0.5, Scoring.firRate(scores)!!, 0.001)
        assertNull(Scoring.girRate(listOf(HoleScore(1, strokes = 4))))
    }
}
