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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huimao.fairway.data.CourseSearchResult
import com.huimao.fairway.domain.Geo
import com.huimao.fairway.domain.LatLng
import com.huimao.fairway.ui.CourseLibraryViewModel
import com.huimao.fairway.ui.RoundViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 球场库：在线找球场（OpenStreetMap），下载到本地后在打球页切换。 */
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
    val message by libraryViewModel.message.collectAsStateWithLifecycle()
    val packs by roundViewModel.packs.collectAsStateWithLifecycle()
    val location by roundViewModel.location.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

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
                "在线找球场：数据来自 OpenStreetMap，下过的球场存手机里，下次直接用。" +
                    "全球大多数球场只有轮廓、没有洞数据——那样的球场下载时会提示打不了。",
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
                key = { it.osmId },
                contentType = { "result" },
            ) { result ->
                SearchResultRow(
                    result = result,
                    location = location,
                    isDownloaded = result.osmId in downloadedIds,
                    isDownloading = downloadingId == result.osmId,
                    onDownload = {
                        scope.launch {
                            val file = libraryViewModel.download(result)
                            if (file != null) roundViewModel.refreshPacks()
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
    onDownload: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(result.name, style = MaterialTheme.typography.titleMedium)
                val dist = location?.let {
                    val km = Geo.haversineMeters(it, LatLng(result.lat, result.lon)) / 1000.0
                    if (km < 1.0) "${(km * 1000).roundToInt()} 米" else "${km.roundToInt()} 公里"
                }
                Text(
                    dist ?: "距离未知（未定位）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when {
                isDownloading -> CircularProgressIndicator()
                isDownloaded -> Text("已下载", style = MaterialTheme.typography.bodyMedium)
                else -> Button(onClick = onDownload) { Text("下载") }
            }
        }
    }
}
