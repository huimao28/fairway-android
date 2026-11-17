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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huimao.fairway.data.compassText
import com.huimao.fairway.data.weatherCodeText
import com.huimao.fairway.domain.Geo
import com.huimao.fairway.domain.LatLng
import com.huimao.fairway.ui.RoundViewModel
import com.huimao.fairway.ui.components.CourseMapView
import com.huimao.fairway.ui.teeName
import kotlin.math.roundToInt

private fun formatMeters(d: Double?): String =
    if (d == null) "—" else "${d.roundToInt()} 米"

private val TEE_ORDER = listOf("black", "blue", "white", "red")

/** 风相对当前洞方向：顺风 / 逆风 / 侧风（风向是“风从哪来”的气象 convention）。 */
private fun windHint(windFromDeg: Double, holeBearingDeg: Double): String {
    val towards = (windFromDeg + 180.0) % 360.0
    val diff = ((towards - holeBearingDeg + 540.0) % 360.0) - 180.0
    return when {
        kotlin.math.abs(diff) < 45.0 -> "顺风"
        kotlin.math.abs(diff) > 135.0 -> "逆风"
        else -> "侧风"
    }
}

@Composable
fun RoundScreen(
    viewModel: RoundViewModel,
    locationGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val course by viewModel.course.collectAsStateWithLifecycle()
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    val selectedPack by viewModel.selectedPack.collectAsStateWithLifecycle()
    val selectedTee by viewModel.selectedTee.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val currentHole by viewModel.currentHole.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val shotOrigin by viewModel.shotOrigin.collectAsStateWithLifecycle()
    val shotsMap by viewModel.shots.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()

    val hole = course?.holes?.find { it.ref == currentHole }
    val availableTees = remember(course) {
        course?.holes?.flatMap { it.tees.keys }?.distinct()
            ?.sortedBy { TEE_ORDER.indexOf(it) }.orEmpty()
    }
    val holeShots = shotsMap[currentHole].orEmpty()
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
    // 果岭读线：离洞口 100 米内，画球到洞口的推杆线（OSM 无果岭坡度数据，线为直线参考）
    val puttLine: Pair<LatLng, LatLng>? = remember(location, hole) {
        val loc = location
        val g = hole?.green
        if (loc != null && g != null && Geo.haversineMeters(loc, g.center) <= 100.0) {
            loc to g.center
        } else {
            null
        }
    }
    // 风向相对当前洞：从所选发球台到果岭的方向
    val holeBearing = remember(hole, selectedTee) {
        val tee = hole?.tees?.get(selectedTee) ?: hole?.poly?.firstOrNull()
        val g = hole?.green
        if (tee != null && g != null) Geo.bearing(tee, g.center) else null
    }

    var courseMenuExpanded by remember { mutableStateOf(false) }

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

        // 多球场切换（只有一个球场时也是入口，以后加球场不用改界面）
        if (packs.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { courseMenuExpanded = true }) {
                    Text(course?.name ?: "选择球场")
                }
                DropdownMenu(
                    expanded = courseMenuExpanded,
                    onDismissRequest = { courseMenuExpanded = false },
                ) {
                    packs.forEach { pack ->
                        DropdownMenuItem(
                            text = { Text(pack.courseName) },
                            onClick = {
                                courseMenuExpanded = false
                                if (pack.fileName != selectedPack) viewModel.selectPack(pack.fileName)
                            },
                        )
                    }
                }
            }
        }

        // 发球台选择（WHS rating/slope 按这个台算）
        if (availableTees.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("发球台", style = MaterialTheme.typography.bodyMedium)
                availableTees.forEach { color ->
                    FilterChip(
                        selected = selectedTee == color,
                        onClick = { viewModel.setSelectedTee(color) },
                        label = { Text("${teeName(color)} T") },
                    )
                }
            }
        }

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

        // 天气（Open-Meteo；取不到就不显示）
        weather?.let { w ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("天气", style = MaterialTheme.typography.titleMedium)
                    DistanceRow("天气", weatherCodeText(w.weatherCode))
                    DistanceRow("气温", "${w.temperature.roundToInt()}°C")
                    val windText = "${compassText(w.windDirection)}风 ${w.windSpeed.roundToInt()} 米/秒" +
                        (holeBearing?.let { " · ${windHint(w.windDirection, it)}" } ?: "")
                    DistanceRow("风", windText)
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

        // 果岭读线：离洞口近时显示推杆线（直线参考，OSM 无坡度数据）
        puttLine?.let { (_, center) ->
            val puttDist = location?.let { Geo.haversineMeters(it, center) }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("果岭读线", style = MaterialTheme.typography.titleMedium)
                    DistanceRow("到洞口", formatMeters(puttDist))
                    Text(
                        "推杆线已在地图上标出（绿色虚线）。果岭坡度数据暂无，线为直线参考，实际 break 需现场判断。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // 击球轨迹：本洞每杆距离
        if (holeShots.isNotEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("击球轨迹 · 第 $currentHole 洞", style = MaterialTheme.typography.titleMedium)
                    val teePoint = hole?.tees?.get(selectedTee) ?: hole?.poly?.firstOrNull()
                    var prev = teePoint
                    holeShots.forEachIndexed { idx, shot ->
                        val d = prev?.let { Geo.haversineMeters(it, shot) }
                        DistanceRow("第 ${idx + 1} 杆", formatMeters(d))
                        prev = shot
                    }
                    if (location != null) {
                        val loc = location
                        DistanceRow("正在打的这杆", formatMeters(prev?.let { Geo.haversineMeters(it, loc) }))
                    }
                    OutlinedButton(
                        onClick = { viewModel.undoShot() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("撤销上一杆")
                    }
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
                playerLocation = location,
                shots = holeShots,
                puttLine = puttLine,
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
