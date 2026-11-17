package com.huimao.fairway.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableBehavior
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.StepperDefaults
import androidx.wear.compose.material3.StepperLevelIndicator
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.huimao.fairway.data.RoundMirror
import com.huimao.fairway.data.WorkoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/** 运动记录要的权限（心率 + 步数/卡路里）。Pixel Watch 3（Wear OS 5）上就是这两个。 */
private val WORKOUT_PERMISSIONS = arrayOf(
    Manifest.permission.BODY_SENSORS,
    Manifest.permission.ACTIVITY_RECOGNITION,
)

private fun hasWorkoutPermissions(context: android.content.Context): Boolean =
    WORKOUT_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

private fun formatDist(d: Int): String = if (d < 0) "—" else "$d 米"

private fun formatElapsed(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * 手表打球页（一屏）：距离 + 杆数 Stepper + 运动记录，下一洞放 EdgeButton。
 * 息屏（Ambient）只留距离与洞号。权限走三态流程（现查→rationale→弹框，永久拒绝去设置）。
 */
@Composable
fun WatchScreen(viewModel: WatchViewModel) {
    val context = LocalContext.current
    val activity = context as Activity
    val round by viewModel.round.collectAsStateWithLifecycle()
    val strokes by viewModel.strokes.collectAsStateWithLifecycle()
    val workout by viewModel.liveWorkout.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val ambientMode = LocalAmbientModeManager.current?.currentAmbientMode
    val isAmbient = ambientMode is AmbientMode.Ambient

    var showRationale by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    // 表冠转杆数：1.6.2 已无 onRotaryScrollEvent，用自定义 RotaryScrollableBehavior。
    // 顺时针转＋1、逆时针－1；读 viewModel.strokes.value 实时值，不缓存旧值。
    val stepperFocusRequester = remember { FocusRequester() }
    val strokesRotaryBehavior = remember {
        object : RotaryScrollableBehavior {
            override suspend fun CoroutineScope.performScroll(
                timestampMillis: Long,
                delta: Float,
                inputDeviceId: Int,
                orientation: Orientation,
            ) {
                val current = viewModel.strokes.value
                if (delta > 0) viewModel.setStrokes(current + 1) else viewModel.setStrokes(current - 1)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.startWorkout()
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.BODY_SENSORS,
            )
        ) {
            permanentlyDenied = true
        }
    }

    fun requestWorkoutPermission() {
        when {
            hasWorkoutPermissions(context) -> viewModel.startWorkout()
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.BODY_SENSORS,
            ) -> showRationale = true
            permanentlyDenied -> showRationale = true
            else -> permissionLauncher.launch(WORKOUT_PERMISSIONS)
        }
    }

    // 提示文案 4 秒后自动消失
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            viewModel.consumeMessage()
        }
    }

    AppScaffold {
        val transformationSpec = rememberTransformationSpec()
        val columnState = rememberTransformingLazyColumnState()
        ScreenScaffold(
            scrollState = columnState,
            edgeButtonSpacing = 15.dp,
            edgeButton = {
                EdgeButton(
                    onClick = { viewModel.nextHole() },
                    modifier = Modifier.scrollable(
                        columnState,
                        orientation = Orientation.Vertical,
                        reverseDirection = true,
                        overscrollEffect = rememberOverscrollEffect(),
                    ),
                ) {
                    Text("下一洞")
                }
            },
        ) { contentPadding ->
            TransformingLazyColumn(
                state = columnState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                val r = round
                if (r == null) {
                    item {
                        Text(
                            text = "手机还没推来数据\n先在手机上打开打球页",
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                        )
                    }
                } else if (isAmbient) {
                    // 息屏只留距离与洞号
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                        ) {
                            Text(
                                text = formatDist(r.distMid),
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "第 ${r.hole} 洞",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                } else {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                        ) {
                            Text(
                                text = r.courseName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "第 ${r.hole} 洞 · Par ${r.par}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = formatDist(r.distMid),
                                style = MaterialTheme.typography.displayLarge,
                            )
                            Text(
                                text = "前 ${formatDist(r.distFront)} · 后 ${formatDist(r.distBack)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .requestFocusOnHierarchyActive()
                                .rotaryScrollable(
                                    behavior = strokesRotaryBehavior,
                                    focusRequester = stepperFocusRequester,
                                ),
                        ) {
                            Stepper(
                                value = strokes,
                                onValueChange = { viewModel.setStrokes(it) },
                                valueProgression = 0..12,
                                decreaseIcon = {
                                    // Remove 在 material-icons-extended 里，不引整个扩展包，用文字减号
                                    Box(
                                        modifier = Modifier.size(StepperDefaults.IconSize),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("−", style = MaterialTheme.typography.titleLarge)
                                    }
                                },
                                increaseIcon = {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "加一杆",
                                        modifier = Modifier.size(StepperDefaults.IconSize),
                                    )
                                },
                            ) {
                                Text("$strokes 杆", style = MaterialTheme.typography.titleLarge)
                            }
                            StepperLevelIndicator(
                                value = { strokes },
                                valueProgression = 0..12,
                                modifier = Modifier.align(Alignment.CenterStart),
                            )
                        }
                    }
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                        ) {
                            when (workout.state) {
                                WorkoutState.ACTIVE -> {
                                    Text(
                                        text = "运动中 ${formatElapsed(workout.elapsedMs)}" +
                                            (workout.heartRate?.let { " · 心率 $it" } ?: ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = "${workout.steps} 步 · ${workout.calories.toInt()} 千卡",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Button(
                                        onClick = { viewModel.stopWorkout() },
                                        label = { Text("结束运动") },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                WorkoutState.ENDED -> {
                                    workout.summary?.let { s ->
                                        Text(
                                            text = "已记录 ${formatElapsed(s.durationMs)}" +
                                                " · 平均心率 ${s.avgHr.toInt()}" +
                                                " · 最高 ${s.maxHr}" +
                                                " · ${s.calories.toInt()} 千卡" +
                                                " · ${s.steps} 步",
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                    Button(
                                        onClick = { requestWorkoutPermission() },
                                        label = { Text("再记一场") },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                WorkoutState.IDLE -> {
                                    Button(
                                        onClick = { requestWorkoutPermission() },
                                        label = { Text("开始运动") },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                    if (message != null) {
                        item {
                            Text(
                                text = message!!,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec),
                            )
                        }
                    }
                }
            }
        }
    }

    AlertDialog(
        visible = showRationale,
        onDismissRequest = { showRationale = false },
        title = { Text("需要身体传感器权限") },
        text = {
            Text(
                if (permanentlyDenied) {
                    "权限被永久拒绝了，点确定去手表设置里开身体传感器和活动识别，运动才能记心率和步数。"
                } else {
                    "运动记录要读心率和步数，只在手表本地用，不上传。"
                },
            )
        },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    showRationale = false
                    if (permanentlyDenied) {
                        openAppSettings(context)
                    } else {
                        permissionLauncher.launch(WORKOUT_PERMISSIONS)
                    }
                },
            )
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(onClick = { showRationale = false })
        },
    )
}

@WearPreviewDevices
@Composable
private fun DistanceBlockPreview() {
    AppScaffold {
        val transformationSpec = rememberTransformationSpec()
        val columnState = rememberTransformingLazyColumnState()
        ScreenScaffold(scrollState = columnState) { contentPadding ->
            TransformingLazyColumn(
                state = columnState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    val r = RoundMirror(
                        courseName = "常德桃花源国际高尔夫俱乐部",
                        hole = 7,
                        par = 4,
                        distFront = 132,
                        distMid = 148,
                        distBack = 165,
                        strokes = 3,
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    ) {
                        Text(
                            text = r.courseName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "第 ${r.hole} 洞 · Par ${r.par}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = formatDist(r.distMid),
                            style = MaterialTheme.typography.displayLarge,
                        )
                        Text(
                            text = "前 ${formatDist(r.distFront)} · 后 ${formatDist(r.distBack)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
