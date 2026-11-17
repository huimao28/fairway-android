package com.huimao.fairway.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huimao.fairway.data.HoleScore
import com.huimao.fairway.domain.Hole
import com.huimao.fairway.domain.Scoring
import com.huimao.fairway.ui.RoundViewModel
import com.huimao.fairway.ui.components.Stepper
import com.huimao.fairway.ui.components.TriStateChip
import com.huimao.fairway.ui.components.cycle

@Composable
fun ScorecardScreen(
    viewModel: RoundViewModel,
    modifier: Modifier = Modifier,
) {
    val course by viewModel.course.collectAsStateWithLifecycle()
    val scores by viewModel.scores.collectAsStateWithLifecycle()
    val holes = course?.holes.orEmpty()
    val scoreList = scores.values.toList()
    val total = Scoring.totalStrokes(scoreList)
    val toPar = course?.let { Scoring.toPar(scoreList, it.holes) }
    val played = scoreList.count { it.strokes > 0 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("记分卡", style = MaterialTheme.typography.titleLarge)

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("总杆 $total", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        toPar == null -> "±0"
                        toPar > 0 -> "+$toPar"
                        else -> "$toPar"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("已打 $played/18", style = MaterialTheme.typography.titleMedium)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = holes,
                key = { it.ref },
                contentType = { "hole" },
            ) { hole ->
                HoleScoreRow(
                    hole = hole,
                    score = scores[hole.ref] ?: HoleScore(hole = hole.ref),
                    onUpdate = { transform -> viewModel.updateScore(hole.ref, transform) },
                )
            }
            item(key = "save", contentType = "save") {
                Button(
                    onClick = { viewModel.saveRound() },
                    enabled = played > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存本轮成绩")
                }
            }
        }
    }
}

@Composable
private fun HoleScoreRow(
    hole: Hole,
    score: HoleScore,
    onUpdate: ((HoleScore) -> HoleScore) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("第 ${hole.ref} 洞", style = MaterialTheme.typography.titleMedium)
                Text("Par ${hole.par} · 差点 ${hole.handicap}", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Stepper("杆数", score.strokes) { v -> onUpdate { it.copy(strokes = v) } }
                Stepper("推杆", score.putts) { v -> onUpdate { it.copy(putts = v) } }
                Stepper("罚杆", score.penalties) { v -> onUpdate { it.copy(penalties = v) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hole.par >= 4) {
                    TriStateChip("开球上道", score.fairwayHit) {
                        onUpdate { it.copy(fairwayHit = cycle(it.fairwayHit)) }
                    }
                }
                TriStateChip("标准杆上果岭", score.gir) {
                    onUpdate { it.copy(gir = cycle(it.gir)) }
                }
            }
        }
    }
}
