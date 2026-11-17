package com.huimao.fairway

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.huimao.fairway.CrashReporter
import org.maplibre.android.MapLibre
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huimao.fairway.ui.RoundViewModel
import com.huimao.fairway.ui.screens.HistoryScreen
import com.huimao.fairway.ui.screens.RoundScreen
import com.huimao.fairway.ui.screens.ScorecardScreen
import com.huimao.fairway.ui.theme.FairwayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MapLibre 要求先初始化单例，否则 MapView 构造时直接抛
        // MapLibreConfigurationException 闪退（13.7.1 源码 MapView.initialize 已核实）
        MapLibre.getInstance(this)
        CrashReporter.install(this)
        setContent {
            FairwayTheme {
                FairwayApp()
            }
        }
    }
}

private const val TAB_ROUND = 0
private const val TAB_SCORECARD = 1
private const val TAB_HISTORY = 2

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
private fun FairwayApp() {
    val context = LocalContext.current
    val activity = context as Activity
    val viewModel: RoundViewModel = viewModel()
    var tab by rememberSaveable { mutableIntStateOf(TAB_ROUND) }
    // 授权状态不缓存：每次现查
    var permissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var showRationale by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionGranted = hasLocationPermission(context)
        if (permissionGranted) {
            viewModel.startTracking()
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        ) {
            // 拒绝两次后系统不再弹框，视为永久拒绝
            permanentlyDenied = true
        }
    }

    fun requestLocationPermission() {
        when {
            hasLocationPermission(context) -> {
                permissionGranted = true
                viewModel.startTracking()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) -> showRationale = true
            permanentlyDenied -> showRationale = true
            else -> launcher.launch(LOCATION_PERMISSIONS)
        }
    }

    // 回到前台时现查一次授权状态，同步定位开关
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = hasLocationPermission(context)
                if (permissionGranted) viewModel.startTracking() else viewModel.stopTracking()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val savedMessage by viewModel.savedMessage.collectAsStateWithLifecycle()
    LaunchedEffect(savedMessage) {
        savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSavedMessage()
        }
    }

    // 上次闪退的堆栈（有则弹窗显示，截图或复制发来就能定位）
    val crashText = remember { CrashReporter.readLastCrash(context) }
    var showCrash by remember { mutableStateOf(crashText != null) }
    if (showCrash && crashText != null) {
        AlertDialog(
            onDismissRequest = { showCrash = false },
            title = { Text("上次启动闪退了") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(crashText, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("灰猫球道闪退日志", crashText),
                    )
                    CrashReporter.clear(context)
                    showCrash = false
                }) { Text("复制日志") }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashReporter.clear(context)
                    showCrash = false
                }) { Text("关闭") }
            },
        )
    }

    // 返回手势：不在打球页时先回打球页；打球页直接走系统返回（退出）
    // 注：首版用 navigationevent-compose 1.0.0 的 NavigationBackHandler，真机启动闪退，
    // 改回久经考验的 activity-compose BackHandler（行为一致）
    BackHandler(enabled = tab != TAB_ROUND) { tab = TAB_ROUND }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("需要定位权限") },
            text = {
                Text("测距功能需要你的实时位置，仅在使用时获取，不会后台定位。没有权限也能正常用记分卡。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    if (permanentlyDenied) {
                        openAppSettings(context)
                    } else {
                        launcher.launch(LOCATION_PERMISSIONS)
                    }
                }) { Text(if (permanentlyDenied) "去设置" else "继续") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("暂不开启") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == TAB_ROUND,
                    onClick = { tab = TAB_ROUND },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "打球") },
                    label = { Text("打球") },
                )
                NavigationBarItem(
                    selected = tab == TAB_SCORECARD,
                    onClick = { tab = TAB_SCORECARD },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "记分卡") },
                    label = { Text("记分卡") },
                )
                NavigationBarItem(
                    selected = tab == TAB_HISTORY,
                    onClick = { tab = TAB_HISTORY },
                    icon = { Icon(Icons.Default.Star, contentDescription = "成绩") },
                    label = { Text("成绩") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                TAB_ROUND -> RoundScreen(
                    viewModel = viewModel,
                    locationGranted = permissionGranted,
                    onRequestPermission = ::requestLocationPermission,
                )
                TAB_SCORECARD -> ScorecardScreen(viewModel = viewModel)
                TAB_HISTORY -> HistoryScreen(viewModel = viewModel)
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}
