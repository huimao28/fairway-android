package com.huimao.fairway.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huimao.fairway.data.GolfExerciseSession
import com.huimao.fairway.data.WatchState
import com.huimao.fairway.data.WorkoutLive
import com.huimao.fairway.data.sendHoleRequest
import com.huimao.fairway.data.sendScore
import com.huimao.fairway.data.sendWorkoutSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 手表打球页状态：手机推来的当前洞 + 本地杆数编辑 + 运动记录。
 * 杆数是本地乐观值：切洞时以手机推来的值为准；改杆后立即发消息给手机，
 * 手机应用后会把新状态推回来，回声与本地值一致，不会跳变。
 */
class WatchViewModel(application: Application) : AndroidViewModel(application) {

    private val exerciseManager = GolfExerciseSession.get(application)

    val round = WatchState.round
    val liveWorkout: StateFlow<WorkoutLive> = exerciseManager.live

    private val _strokes = MutableStateFlow(0)
    val strokes: StateFlow<Int> = _strokes.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var currentHole = -1

    init {
        viewModelScope.launch {
            WatchState.round.collect { r ->
                if (r == null) return@collect
                if (r.hole != currentHole) {
                    currentHole = r.hole
                    _strokes.value = r.strokes
                } else if (r.strokes != _strokes.value) {
                    // 同洞回声（手机已应用）或手机端改了杆数：以手机为准收敛
                    _strokes.value = r.strokes
                }
            }
        }
    }

    fun setStrokes(n: Int) {
        val hole = currentHole
        if (hole < 1) return
        _strokes.value = n.coerceIn(0, 12)
        viewModelScope.launch {
            runCatching { sendScore(getApplication(), hole, _strokes.value) }
        }
    }

    fun nextHole() {
        val hole = currentHole
        if (hole < 1 || hole >= 18) return
        viewModelScope.launch {
            if (!sendHoleRequest(getApplication(), hole + 1)) {
                _message.value = "手表没连上手机，切洞没发过去"
            }
        }
    }

    /** 开始运动：先起前台服务，再起 Health Services 运动；失败则停服务并提示。 */
    fun startWorkout() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, com.huimao.fairway.data.ExerciseService::class.java),
            )
            val result = exerciseManager.start()
            if (result.isFailure) {
                ctx.stopService(Intent(ctx, com.huimao.fairway.data.ExerciseService::class.java))
                _message.value = "开始运动失败：${result.exceptionOrNull()?.message ?: "未知原因"}"
            }
        }
    }

    /** 结束运动：汇总发回手机（发不出去也先提示已记录）。 */
    fun stopWorkout() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val summary = exerciseManager.stop()
            ctx.stopService(Intent(ctx, com.huimao.fairway.data.ExerciseService::class.java))
            if (summary != null) {
                val sent = sendWorkoutSummary(ctx, summary)
                _message.value = if (sent) {
                    "运动已记录并同步到手机"
                } else {
                    "运动已记录，但手表没连上手机，汇总没发过去"
                }
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
