package com.huimao.fairway.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huimao.fairway.data.CourseSearchResult
import com.huimao.fairway.domain.Geo
import com.huimao.fairway.domain.LatLng
import com.huimao.fairway.ui.CourseLibraryViewModel
import com.huimao.fairway.ui.RoundViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 球场库：先查 GolfCore（国内外都优先），没有再走离线 POI 名单，最后在线兜底；下载到本地后在打球页切换。 */
@Composable
fun CourseLibraryScreen(
    libraryViewModel: CourseLibraryViewModel,
    roundViewModel: RoundViewModel,
    modifier: Modifier = Modifier,
) {
    val query by libraryViewModel.query.collectAsStateWithLifecycle()
    val searching by libraryViewModel.searching.collectAsStateWithLifecycle()
    val results by libraryViewModel.results.collectAsStateWithLifecycle()
    val downloadingId by libraryViewModel.downloadingId.collectAsStateWithLifecycle()
    val downloadProgress by libraryViewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadStage by libraryViewModel.downloadStage.collectAsStateWithLifecycle()
    val message by libraryViewModel.message.collectAsStateWithLifecycle()
    val golfCoreKey by libraryViewModel.golfCoreKey.collectAsStateWithLifecycle()
    val packs by roundViewModel.packs.collectAsStateWithLifecycle()
    val location by roundViewModel.location.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val downloaded = packs.filter { it.isDownloaded }
    val downloadedIds = downloaded.mapNotNull {
        it.fileName.removePrefix("osm_").removeSuffix(".json").toLongOrNull()
    }.toSet()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("球场库", style = MaterialTheme.typography.titleLarge)
        }
        item {
            Text(
                "搜球场先查 GolfCore（国内外都优先，中国 408 家，带城市、国家和中文原名），" +
                    "没有再走离线名单（OpenStreetMap，2026-11-17，362 个球场，含港澳台；" +
                    "没网时也靠它），最后才走 Nominatim / Overpass 在线搜。下载仍需联网，" +
                    "下过的球场存手机里；GolfCore 的球场下载时按坐标找 OSM 上的球场轮廓，" +
                    "下载完还会补 rating/slope（有的话，WHS 差点就能算）。多数球场只有轮廓、" +
                    "没有洞数据——那样的下载时会提示打不了。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { libraryViewModel.setQuery(it) },
                    label = { Text("球场名") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { libraryViewModel.searchByName() },
                    enabled = !searching,
                ) {
                    Text("搜索")
                }
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { libraryViewModel.searchNearby(location) },
                    enabled = !searching,
                ) {
                    Text("附近球场")
                }
                if (searching) {
                    CircularProgressIndicator()
                }
            }
        }
        message?.let { text ->
            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { libraryViewModel.consumeMessage() }) {
                        Text("知道了")
                    }
                }
            }
        }
        if (results.isNotEmpty()) {
            item {
                Text("搜索结果", style = MaterialTheme.typography.titleMedium)
            }
            items(
                items = results,
                key = { it.resultKey() },
                contentType = { "result" },
            ) { result ->
                SearchResultRow(
                    result = result,
                    location = location,
                    isDownloaded = result.osmId in downloadedIds,
                    isDownloading = downloadingId == result.resultKey(),
                    progress = downloadProgress,
                    stage = downloadStage,
                    onDownload = {
                        scope.launch {
                            val file = libraryViewModel.download(result)
                            if (file != null) roundViewModel.refreshPacks()
                        }
                    },
                    onOpenGolfCore = { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }.onFailure {
                            libraryViewModel.setMessage("打不开 GolfCore 球场页。")
                        }
                    },
                )
            }
        }
        if (downloaded.isNotEmpty()) {
            item {
                Text("已下载", style = MaterialTheme.typography.titleMedium)
            }
            items(
                items = downloaded,
                key = { it.fileName },
                contentType = { "downloaded" },
            ) { pack ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(pack.courseName, style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = {
                            roundViewModel.deletePack(pack.fileName)
                            libraryViewModel.setMessage("已删除「${pack.courseName}」。")
                        }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item {
            GolfCoreKeySection(
                savedKeyTail = golfCoreKey?.takeLast(4),
                onSave = { libraryViewModel.saveGolfCoreKey(it) },
                onClear = { libraryViewModel.clearGolfCoreKey() },
            )
        }
        item {
            Text(
                "国外球场搜索与 rating 数据由 GolfCore 提供（golfcore.org）。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (results.isEmpty() && downloaded.isEmpty()) {
            item {
                Text(
                    "还没有下载的球场。搜一个球场名，或点「附近球场」看看周围有什么球场。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: CourseSearchResult,
    location: LatLng?,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    progress: Float?,
    stage: String?,
    onDownload: () -> Unit,
    onOpenGolfCore: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        if (isDownloading) {
            Column(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ResultTexts(result = result, location = location, onOpenGolfCore = onOpenGolfCore)
                LinearProgressIndicator(
                    progress = progress ?: 0f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stage ?: "正在下载…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Row(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ResultTexts(
                    result = result,
                    location = location,
                    modifier = Modifier.weight(1f),
                    onOpenGolfCore = onOpenGolfCore,
                )
                when {
                    isDownloaded -> Text("已下载", style = MaterialTheme.typography.bodyMedium)
                    else -> Button(onClick = onDownload) { Text("下载") }
                }
            }
        }
    }
}

/** GolfCore key 设置：每人用自己的免费 key（platform.golfcore.app 注册，免卡），只存本机。 */
@Composable
private fun GolfCoreKeySection(
    savedKeyTail: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("GolfCore key", style = MaterialTheme.typography.titleMedium)
            Text(
                "国外搜索与 rating 补全走 GolfCore 免费档（每月 500 次）。2026 年 12 月 4 日前" +
                    "不填 key 也能用，之后必须带 key；key 不能共用，每人在 platform.golfcore.app " +
                    "免费注册一个（不用绑卡），填在这里，只存本机。",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (savedKeyTail != null) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("已保存（尾号 $savedKeyTail）", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onClear) { Text("清除") }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("粘贴 GolfCore key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSave(input)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Text("保存")
                }
            }
        }
    }
}

/** 搜索结果的文字部分：名（GolfCore 的结果带可点的署名）+ “国家 · 城市” + 距离。 */
@Composable
private fun ResultTexts(
    result: CourseSearchResult,
    location: LatLng?,
    modifier: Modifier = Modifier,
    onOpenGolfCore: (String) -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                result.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            val url = result.golfCoreUrl
            if (result.golfCoreSlug != null && url != null) {
                Text(
                    "GolfCore ↗",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onOpenGolfCore(url) },
                )
            }
        }
        val place = listOfNotNull(
            result.country.takeIf { it.isNotBlank() },
            result.city.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (place.isNotBlank()) {
            Text(place, style = MaterialTheme.typography.bodyMedium)
        }
        val dist = location?.let {
            val km = Geo.haversineMeters(it, LatLng(result.lat, result.lon)) / 1000.0
            if (km < 1.0) "${(km * 1000).roundToInt()} 米" else "${km.roundToInt()} 公里"
        }
        Text(
            dist ?: "距离未知（未定位）",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
