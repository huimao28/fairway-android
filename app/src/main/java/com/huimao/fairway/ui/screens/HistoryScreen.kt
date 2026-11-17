package com.huimao.fairway.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huimao.fairway.data.RoundRecord
import com.huimao.fairway.domain.Scoring
import com.huimao.fairway.ui.RoundViewModel
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
    val coursePar = course?.totalPar ?: 72
    val handicap = Scoring.simplifiedHandicap(rounds, coursePar)
    val allScores = rounds.flatMap { it.scores }
    val avgPutts = Scoring.avgPutts(allScores)
    val girRate = Scoring.girRate(allScores)
    val firRate = Scoring.firRate(allScores)

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
                StatRow("简化差点", handicap?.let { "%.1f".format(it) } ?: "—")
                StatRow("平均推杆", avgPutts?.let { "%.1f".format(it) } ?: "—")
                StatRow("上果岭率", girRate?.let { "%.0f%%".format(it * 100) } ?: "—")
                StatRow("上球道率", firRate?.let { "%.0f%%".format(it * 100) } ?: "—")
            }
        }

        if (rounds.isEmpty()) {
            Text(
                "还没有保存的成绩，打完一轮在记分卡页点「保存本轮成绩」。",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = rounds,
                    key = { it.id },
                    contentType = { "round" },
                ) { round ->
                    RoundRow(round = round, coursePar = coursePar)
                }
            }
        }
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
private fun RoundRow(round: RoundRecord, coursePar: Int) {
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text("总杆 $total", style = MaterialTheme.typography.titleMedium)
                Text(toParText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
