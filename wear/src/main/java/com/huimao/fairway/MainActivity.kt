package com.huimao.fairway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.MaterialTheme
import com.huimao.fairway.ui.WatchScreen
import com.huimao.fairway.ui.WatchViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // AmbientModeManager 挂在最顶层（skill 样例的最佳实践），子界面读
            // LocalAmbientModeManager.current 区分互动/息屏
            val ambientModeManager = rememberAmbientModeManager()
            CompositionLocalProvider(LocalAmbientModeManager provides ambientModeManager) {
                MaterialTheme {
                    val viewModel: WatchViewModel = viewModel()
                    WatchScreen(viewModel = viewModel)
                }
            }
        }
    }
}
