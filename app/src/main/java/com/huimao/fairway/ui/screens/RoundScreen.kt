package com.huimao.fairway.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huimao.fairway.domain.Geo
import com.huimao.fairway.ui.RoundViewModel
import com.huimao.fairway.ui.components.CourseMapView
import kotlin.math.roundToInt

private fun formatMeters(d: Double?): String =
    if (d == null) "—" else "${d.roundToInt()} 米"

@Composable
fun RoundScreen(
    viewModel: RoundViewModel,
    locationGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val course by viewModel.course.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val currentHole by viewModel.currentHole.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val shotOrigin by viewModel.shotOrigin.collectAsStateWithLifecycle()

    val hole = course?.holes?.find { it.ref == currentHole }
    val greenDist = remember(location, hole) {
        val loc = location
        val g = hole?.green
        if (loc != null && g != null) Geo.greenDistances(loc, g) else null
    }
    val nearestBunker = remember(location, course) {
        val loc = location ?: return@remember null
        course?.bunkers?.minOfOrNull { Geo.haversineMeters(loc, it) }
    }
    val nearestWater = remember(location, course) {
        val loc = location ?: return@remember null
        course?.waterHazards?.minOfOrNull { Geo.haversineMeters(loc, it) }
    }
    val lastShot = remember(location, shotOrigin) {
        val loc = location
        val origin = shotOrigin
        if (loc != null && origin != null) Geo.haversineMeters(origin, loc) else null
    }
    // 地图用的位置提供器：Draw 阶段读，不触发重组
    val locationState = rememberUpdatedState(location)
    val locationProvider = remember { { locationState.value } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            loadError ?: course?.name ?: "加载球场…",
            style = MaterialTheme.typography.titleLarge,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = { viewModel.prevHole() }, enabled = currentHole > 1) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一洞")
            }
            Text(
                text = hole?.let { "第 ${it.ref} 洞 · Par ${it.par} · 差点 ${it.handicap}" } ?: "—",
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { viewModel.nextHole() }, enabled = currentHole < 18) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一洞")
            }
        }

        if (!locationGranted) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("测距需要定位权限", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "仅在使用时获取你的实时位置，不会后台定位。没有权限也能正常用记分卡。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onRequestPermission) { Text("去开启定位") }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("到果岭", style = MaterialTheme.typography.titleMedium)
                DistanceRow("前沿", formatMeters(greenDist?.first))
                DistanceRow("中", formatMeters(greenDist?.second))
                DistanceRow("后沿", formatMeters(greenDist?.third))
                DistanceRow("最近沙坑", formatMeters(nearestBunker))
                DistanceRow("最近水障", formatMeters(nearestWater))
                DistanceRow("上一杆", formatMeters(lastShot))
                if (location == null && locationGranted) {
                    Text("正在定位…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = { viewModel.markShot() },
                enabled = location != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("标记击球点")
            }
            Button(
                onClick = { viewModel.nextHole() },
                enabled = currentHole < 18,
                modifier = Modifier.weight(1f),
            ) {
                Text("下一洞")
            }
        }

        hole?.let {
            CourseMapView(
                hole = it,
                bunkers = course?.bunkers.orEmpty(),
                waterHazards = course?.waterHazards.orEmpty(),
                locationProvider = locationProvider,
            )
        }
    }
}

@Composable
private fun DistanceRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
