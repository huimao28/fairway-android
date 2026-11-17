package com.huimao.fairway.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huimao.fairway.data.HoleScore
import com.huimao.fairway.data.RoundRecord
import com.huimao.fairway.domain.Hole
import com.huimao.fairway.domain.Scoring
import com.huimao.fairway.domain.Whs
import com.huimao.fairway.ui.RoundViewModel
import com.huimao.fairway.ui.components.Stepper
import com.huimao.fairway.ui.components.TriStateChip
import com.huimao.fairway.ui.components.cycle
import com.huimao.fairway.ui.teeName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    viewModel: RoundViewModel,
    modifier: Modifier = Modifier,
) {
    val rounds by viewModel.rounds.collectAsStateWithLifecycle()
    val course by viewModel.course.collectAsStateWithLifecycle()
    val selectedTee by viewModel.selectedTee.collectAsStateWithLifecycle()
    val holes = course?.holes.orEmpty()
    val coursePar = course?.totalPar ?: 72
    val handicap = Scoring.simplifiedHandicap(rounds, coursePar)
    val allScores = rounds.flatMap { it.scores }
    val avgPutts = Scoring.avgPutts(allScores)
    val girRate = Scoring.girRate(allScores)
    val firRate = Scoring.firRate(allScores)
    // WHS 差点指数：由已存微分（按时间升序）算；球场差点按当前球场 + 所选发球台的 rating/slope
    val whsIndex = Whs.handicapIndex(
        rounds.sortedBy { it.dateEpochMs }.mapNotNull { it.differential },
    )
    val teeRating = course?.ratings?.get(selectedTee)
    val courseHandicap = if (whsIndex != null && teeRating != null) {
        Whs.courseHandicap(whsIndex, teeRating.slope, teeRating.rating, coursePar)
    } else {
        null
    }

    // 正在修改的记录（null = 未打开编辑框）
    var editing by remember { mutableStateOf<RoundRecord?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("成绩", style = MaterialTheme.typography.titleLarge)

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatRow("轮次", "${rounds.size}")
                StatRow("WHS 差点指数", whsIndex?.let { "%.1f".format(it) } ?: "—")
                StatRow(
                    "球场差点（${teeName(selectedTee)} T）",
                    courseHandicap?.let { "$it" } ?: "—",
                )
                StatRow("简化差点", handicap?.let { "%.1f".format(it) } ?: "—")
                StatRow("平均推杆", avgPutts?.let { "%.1f".format(it) } ?: "—")
                StatRow("上果岭率", girRate?.let { "%.0f%%".format(it * 100) } ?: "—")
                StatRow("上球道率", firRate?.let { "%.0f%%".format(it * 100) } ?: "—")
                if (whsIndex == null && rounds.isNotEmpty()) {
                    Text(
                        "打满 3 轮（18 洞）后显示 WHS 差点指数；新球场需补 rating/slope。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (rounds.isEmpty()) {
            Text(
                "还没有保存的成绩，打完一轮在记分卡页点「保存本轮成绩」。",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                "点一条记录可以修改或删除。",
                style = MaterialTheme.typography.bodySmall,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = rounds,
                    key = { it.id },
                    contentType = { "round" },
                ) { round ->
                    RoundRow(
                        round = round,
                        coursePar = coursePar,
                        onClick = { editing = round },
                    )
                }
            }
        }
    }

    editing?.let { round ->
        EditRoundDialog(
            round = round,
            holes = holes,
            onDismiss = { editing = null },
            onSave = { updated ->
                viewModel.updateRound(updated)
                editing = null
            },
            onDelete = {
                viewModel.deleteRound(round.id)
                editing = null
            },
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RoundRow(round: RoundRecord, coursePar: Int, onClick: () -> Unit) {
    val date = Instant.ofEpochMilli(round.dateEpochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val total = Scoring.totalStrokes(round.scores)
    val played = round.scores.count { it.strokes > 0 }
    val toParText = if (played >= 18) {
        val diff = total - coursePar
        when {
            diff > 0 -> "+$diff"
            diff < 0 -> "$diff"
            else -> "E"
        }
    } else {
        "未完成"
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(date, style = MaterialTheme.typography.titleMedium)
                Text(round.courseName, style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("总杆 $total", style = MaterialTheme.typography.titleMedium)
                Text(toParText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 修改一条已保存的记录：逐洞改杆数/推杆/罚杆与上道/上果岭，可删除整条。 */
@Composable
private fun EditRoundDialog(
    round: RoundRecord,
    holes: List<Hole>,
    onDismiss: () -> Unit,
    onSave: (RoundRecord) -> Unit,
    onDelete: () -> Unit,
) {
    var scores by remember(round.id) {
        mutableStateOf(
            (1..18).associateWith { ref ->
                round.scores.find { it.hole == ref } ?: HoleScore(hole = ref)
            },
        )
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val total = Scoring.totalStrokes(scores.values.toList())
    val date = Instant.ofEpochMilli(round.dateEpochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("修改记录 · $date", style = MaterialTheme.typography.titleLarge)
                Text(round.courseName, style = MaterialTheme.typography.bodyMedium)

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = (1..18).toList(),
                        key = { it },
                        contentType = { "edit-hole" },
                    ) { ref ->
                        val hole = holes.find { it.ref == ref }
                        val score = scores[ref] ?: HoleScore(hole = ref)
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("第 $ref 洞", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Par ${hole?.par ?: "—"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Stepper("杆数", score.strokes) { v ->
                                        scores = scores + (ref to score.copy(strokes = v))
                                    }
                                    Stepper("推杆", score.putts) { v ->
                                        scores = scores + (ref to score.copy(putts = v))
                                    }
                                    Stepper("罚杆", score.penalties) { v ->
                                        scores = scores + (ref to score.copy(penalties = v))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if ((hole?.par ?: 4) >= 4) {
                                        TriStateChip("开球上道", score.fairwayHit) {
                                            scores = scores + (ref to score.copy(fairwayHit = cycle(score.fairwayHit)))
                                        }
                                    }
                                    TriStateChip("标准杆上果岭", score.gir) {
                                        scores = scores + (ref to score.copy(gir = cycle(score.gir)))
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("总杆 $total", style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = { confirmDelete = true },
                    ) {
                        Text("删除本轮", color = MaterialTheme.colorScheme.error)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = {
                        // 只保留打过的洞（杆数 > 0），与记分卡保存规则一致
                        onSave(
                            round.copy(
                                scores = scores.values
                                    .filter { it.strokes > 0 }
                                    .sortedBy { it.hole },
                            ),
                        )
                    }) { Text("保存") }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这轮记录？") },
            text = { Text("$date · ${round.courseName}，总杆 $total，删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}
