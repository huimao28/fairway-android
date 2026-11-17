package com.huimao.fairway.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huimao.fairway.data.SwingLandmark
import com.huimao.fairway.data.SwingSession
import com.huimao.fairway.data.SwingStore
import com.huimao.fairway.domain.SwingMetrics
import com.huimao.fairway.ui.SwingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(dateFormatter)

/** 挥杆分析：列表（选视频/录制 + 已分析的记录）与详情（播放 + 骨架 + 角度/节奏）。 */
@Composable
fun SwingScreen(
    viewModel: SwingViewModel,
    modifier: Modifier = Modifier,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selected by viewModel.selectedSession.collectAsStateWithLifecycle()
    val videoAvailable by viewModel.selectedVideoAvailable.collectAsStateWithLifecycle()
    val progress by viewModel.analysisProgress.collectAsStateWithLifecycle()
    val stage by viewModel.analysisStage.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.analyzeVideo(it) }
    }
    val recordVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let { viewModel.analyzeVideo(it) }
    }

    // 详情页返回先回列表；列表页走 MainActivity 的返回逻辑（回打球页）
    BackHandler(enabled = selected != null) { viewModel.clearSelection() }

    val session = selected
    if (session == null) {
        SwingListScreen(
            sessions = sessions,
            analyzing = progress != null,
            progress = progress,
            stage = stage,
            message = message,
            onConsumeMessage = { viewModel.consumeMessage() },
            onPickVideo = { pickVideo.launch("video/*") },
            onRecordVideo = {
                val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                if (intent.resolveActivity(context.packageManager) != null) {
                    recordVideo.launch(intent)
                } else {
                    viewModel.setMessage("没找到相机应用，先用相册选一段已拍好的视频。")
                }
            },
            onSelect = { viewModel.selectSession(it) },
            onDelete = { viewModel.deleteSession(it) },
            modifier = modifier,
        )
    } else {
        SwingDetailScreen(
            session = session,
            videoAvailable = videoAvailable,
            message = message,
            onConsumeMessage = { viewModel.consumeMessage() },
            onBack = { viewModel.clearSelection() },
            modifier = modifier,
        )
    }
}

@Composable
private fun SwingListScreen(
    sessions: List<SwingSession>,
    analyzing: Boolean,
    progress: Float?,
    stage: String?,
    message: String?,
    onConsumeMessage: () -> Unit,
    onPickVideo: () -> Unit,
    onRecordVideo: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("挥杆分析", style = MaterialTheme.typography.titleLarge)
        }
        item {
            Text(
                "用 ML Kit 在手机本地分析挥杆视频（不用联网、不上传）：抽帧检测身体关键点，" +
                    "标出准备、上杆顶点、击球、收杆 4 个关键时刻，给肩转、髋转、脊柱前倾、膝盖弯曲和节奏。" +
                    "拍摄要求：侧面或正后方拍、拍全身、光线够、不遮挡。ML Kit 看不到球杆，杆头位置和杆面角测不出来，" +
                    "挥杆平面只能拿肩和手腕轨迹近似。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickVideo, enabled = !analyzing) { Text("选视频") }
                OutlinedButton(onClick = onRecordVideo, enabled = !analyzing) { Text("录制") }
            }
        }
        if (analyzing) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = progress ?: 0f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stage ?: "正在分析…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        message?.let { text ->
            item { MessageRow(text = text, onConsume = onConsumeMessage) }
        }
        if (sessions.isNotEmpty()) {
            item {
                Text("已分析", style = MaterialTheme.typography.titleMedium)
            }
            items(
                items = sessions,
                key = { it.id },
                contentType = { "session" },
            ) { session ->
                SwingSessionRow(
                    session = session,
                    onSelect = { onSelect(session.id) },
                    onDelete = { onDelete(session.id) },
                )
            }
        } else if (!analyzing) {
            item {
                Text(
                    "还没有分析过的挥杆视频。选一段视频或现场录一段，分析完就能看关键帧和角度。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SwingSessionRow(
    session: SwingSession,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(formatDate(session.createdAt), style = MaterialTheme.typography.titleMedium)
                Text(
                    "时长 ${"%.1f".format(session.durationMs / 1000.0)} 秒",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val summary = swingSummary(session)
                if (summary.isNotBlank()) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun swingSummary(session: SwingSession): String {
    val m = session.metrics
    val parts = mutableListOf<String>()
    m.shoulderTurnDeg?.let { parts += "肩转 ${it.roundToInt()}°" }
    m.tempoRatio?.let { parts += "节奏 ${"%.1f".format(it)}:1" }
    return parts.joinToString(" · ")
}

@Composable
private fun MessageRow(text: String, onConsume: () -> Unit) {
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
        TextButton(onClick = onConsume) { Text("知道了") }
    }
}

@Composable
private fun SwingDetailScreen(
    session: SwingSession,
    videoAvailable: Boolean,
    message: String?,
    onConsumeMessage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { SwingStore(context) }
    val holder = remember { VideoHolder() }
    var positionMs by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    val videoUri = remember(session.id) { Uri.parse(session.videoUri) }

    // 切 session 时重置播放状态（播放器本身由 key(session.id) 重建）
    LaunchedEffect(session.id) {
        positionMs = 0
        playing = false
    }
    // 播放中每 100ms 轮询一次位置；positionMs 只在 Draw 阶段被骨架读取，不触发重组
    LaunchedEffect(playing, session.id) {
        if (!playing) return@LaunchedEffect
        while (isActive) {
            holder.videoView?.let { positionMs = it.currentPosition }
            delay(100)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("返回列表") }
            Text(formatDate(session.createdAt), style = MaterialTheme.typography.titleLarge)
        }
        message?.let { text ->
            MessageRow(text = text, onConsume = onConsumeMessage)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(
                    if (session.videoWidth > 0 && session.videoHeight > 0) {
                        session.videoWidth.toFloat() / session.videoHeight
                    } else {
                        16f / 9f
                    },
                ),
        ) {
            if (videoAvailable) {
                key(session.id) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(videoUri)
                                setOnCompletionListener {
                                    playing = false
                                    positionMs = session.durationMs.toInt()
                                }
                                holder.videoView = this
                            }
                        },
                        modifier = Modifier.matchParentSize(),
                    )
                }
                // 骨架叠加：按当前播放位置取最近的分析帧；归一化坐标直接映射到 Box
                Canvas(modifier = Modifier.matchParentSize()) {
                    val frame = session.frames.minByOrNull { abs(it.timeMs - positionMs) }
                    val points = frame?.landmarks?.associate {
                        it.type to Offset(it.x * size.width, it.y * size.height)
                    }.orEmpty()
                    val stroke = 2.dp.toPx()
                    val radius = 4.dp.toPx()
                    for ((a, b) in SwingLandmark.CONNECTIONS) {
                        val pa = points[a] ?: continue
                        val pb = points[b] ?: continue
                        drawLine(Color.White, pa, pb, strokeWidth = stroke, cap = StrokeCap.Round)
                    }
                    for ((_, p) in points) {
                        drawCircle(Color.Yellow, radius = radius, center = p)
                    }
                }
            } else {
                Box(
                    Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "原视频找不到了（可能被删了），只能看下面的关键帧和角度。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (videoAvailable) {
            PlayerControls(
                positionMs = positionMs,
                durationMs = session.durationMs,
                playing = playing,
                onTogglePlay = {
                    val vv = holder.videoView ?: return@PlayerControls
                    if (playing) {
                        vv.pause()
                        playing = false
                    } else {
                        vv.start()
                        playing = true
                    }
                },
            )
        }
        KeyframeRow(
            session = session,
            store = store,
            onSeek = { ms ->
                holder.videoView?.seekTo(ms)
                positionMs = ms
            },
        )
        MetricsCards(session = session)
    }
}

/** 播放控制单独抽出来：播放位置每 100ms 变一次，只让这一行重组。 */
@Composable
private fun PlayerControls(
    positionMs: Int,
    durationMs: Long,
    playing: Boolean,
    onTogglePlay: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onTogglePlay) { Text(if (playing) "暂停" else "播放") }
        Text(
            "当前 ${"%.1f".format(positionMs / 1000.0)} 秒 / ${"%.1f".format(durationMs / 1000.0)} 秒",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun KeyframeRow(
    session: SwingSession,
    store: SwingStore,
    onSeek: (Int) -> Unit,
) {
    val moments = listOf(
        Triple("address", "准备", session.keyMoments.addressMs),
        Triple("top", "上杆顶点", session.keyMoments.topMs),
        Triple("impact", "击球", session.keyMoments.impactMs),
        Triple("finish", "收杆", session.keyMoments.finishMs),
    ).filter { it.third != null }
    if (moments.isEmpty()) {
        Text("没找到关键时刻。", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        moments.forEach { (moment, label, ms) ->
            val timeMs = ms ?: return@forEach
            KeyframeThumb(
                file = store.thumbnailFile(session.id, moment),
                label = label,
                onClick = { onSeek(timeMs.toInt()) },
            )
        }
    }
}

@Composable
private fun KeyframeThumb(file: File, label: String, onClick: () -> Unit) {
    val bitmap = rememberThumbnailBitmap(file)
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 96.dp, height = 54.dp),
            )
        } else {
            Box(
                modifier = Modifier.size(width = 96.dp, height = 54.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** 缩略图不在组合里直接解码：IO 线程解完再进状态。 */
@Composable
private fun rememberThumbnailBitmap(file: File): Bitmap? {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        }
    }
    return bitmap
}

@Composable
private fun MetricsCards(session: SwingSession) {
    val m = session.metrics
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("角度", style = MaterialTheme.typography.titleMedium)
            val angleRows = listOfNotNull(
                m.shoulderTurnDeg?.let {
                    Triple(
                        "肩转",
                        "${it.roundToInt()}°",
                        "参考约 ${SwingMetrics.REFERENCE_SHOULDER_TURN_DEG.roundToInt()}°",
                    )
                },
                m.hipTurnDeg?.let {
                    Triple(
                        "髋转",
                        "${it.roundToInt()}°",
                        "参考约 ${SwingMetrics.REFERENCE_HIP_TURN_DEG.roundToInt()}°",
                    )
                },
                m.spineTiltDeg?.let {
                    Triple("脊柱前倾", "${it.roundToInt()}°", "准备姿势")
                },
                m.kneeFlexDeg?.let {
                    Triple("膝盖弯曲", "${it.roundToInt()}°", "准备姿势，前腿")
                },
            )
            if (angleRows.isEmpty()) {
                Text(
                    "这次没算出角度（可能视频里人太小或被遮挡了）。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                angleRows.forEach { (label, value, note) ->
                    MetricRow(label = label, value = value, note = note)
                }
            }
        }
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("节奏", style = MaterialTheme.typography.titleMedium)
            if (m.backswingMs != null && m.downswingMs != null && m.tempoRatio != null) {
                Text(
                    "上杆 ${"%.1f".format(m.backswingMs / 1000.0)} 秒 / " +
                        "下杆 ${"%.1f".format(m.downswingMs / 1000.0)} 秒，" +
                        "比值 ${"%.1f".format(m.tempoRatio)}:1" +
                        "（参考 ${"%.1f".format(SwingMetrics.REFERENCE_TEMPO_RATIO)}:1）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "这次没算出节奏（可能没找全关键时刻）。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, note: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$value（$note）", style = MaterialTheme.typography.bodyMedium)
    }
}

/** VideoView 引用放普通 holder 里，不包 MutableState（只在事件回调与 effect 里读写）。 */
private class VideoHolder {
    var videoView: VideoView? = null
}
