package com.huimao.fairway.data

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.startExercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 手表运动汇总（与手机端 RoundStore.WorkoutSummary 同字段，手表端自备一份，不建共享模块）。 */
data class WorkoutSummary(
    val durationMs: Long,
    val avgHr: Double = 0.0,
    val maxHr: Int = 0,
    val calories: Double = 0.0,
    val steps: Long = 0L,
)

enum class WorkoutState { IDLE, ACTIVE, ENDED }

/** 运动实时状态（界面订阅）。 */
data class WorkoutLive(
    val state: WorkoutState = WorkoutState.IDLE,
    val heartRate: Int? = null,
    val elapsedMs: Long = 0L,
    val steps: Long = 0L,
    val calories: Double = 0.0,
    /** 结束后的汇总（state = ENDED 时有值）。 */
    val summary: WorkoutSummary? = null,
)

/**
 * 高尔夫运动记录（Health Services ExerciseClient，ExerciseType.GOLF）。
 * 不开 GPS（isGpsEnabled=false，轨迹等手表独立 GPS 那步）；只订阅手表实际支持的
 * 数据类型（先 getCapabilities 查，Pixel Watch 3 心率肯定有）。
 * 聚合值自己累计（心率平均/最高、步数、卡路里），不依赖 1.1.0 的聚合 API。
 * 注意：Health Services 要求运动期间始终有 ExerciseUpdateCallback 注册，
 * 否则 5 分钟无回调会自动结束运动——回调在开始时注册、结束前不注销。
 */
class GolfExerciseManager(context: Context) {

    private val appContext = context.applicationContext
    private val exerciseClient = HealthServices.getClient(appContext).exerciseClient

    private val _live = MutableStateFlow(WorkoutLive())
    val live: StateFlow<WorkoutLive> = _live.asStateFlow()

    private var callback: ExerciseUpdateCallback? = null
    private var startMs = 0L
    private var hrSum = 0.0
    private var hrCount = 0
    private var maxHr = 0
    private var stepsTotal = 0L
    private var caloriesTotal = 0.0

    private fun onUpdate(update: ExerciseUpdate) {
        val metrics = update.latestMetrics
        metrics.getData(DataType.HEART_RATE_BPM).forEach { dp ->
            val hr = dp.value.toInt()
            hrSum += hr
            hrCount++
            maxHr = maxOf(maxHr, hr)
            _live.value = _live.value.copy(heartRate = hr)
        }
        metrics.getData(DataType.STEPS).forEach { stepsTotal += it.value }
        metrics.getData(DataType.CALORIES).forEach { caloriesTotal += it.value }
        _live.value = _live.value.copy(
            elapsedMs = System.currentTimeMillis() - startMs,
            steps = stepsTotal,
            calories = caloriesTotal,
        )
    }

    /** 开始记录。系统里同时只能有一个运动在跑，被别的运动顶掉时会抛异常，调用方提示用户。 */
    suspend fun start(): Result<Unit> = runCatching {
        val supported = runCatching {
            val caps = exerciseClient.getCapabilities()
            caps.getExerciseTypeCapabilities(ExerciseType.GOLF).supportedDataTypes
        }.getOrDefault(emptySet())
        val requested = setOfNotNull(
            DataType.HEART_RATE_BPM.takeIf { it in supported },
            DataType.STEPS.takeIf { it in supported },
            DataType.CALORIES.takeIf { it in supported },
        )
        val config = ExerciseConfig.builder(ExerciseType.GOLF)
            .setDataTypes(requested)
            .setIsGpsEnabled(false)
            .setIsAutoPauseAndResumeEnabled(false)
            .build()
        startMs = System.currentTimeMillis()
        hrSum = 0.0
        hrCount = 0
        maxHr = 0
        stepsTotal = 0L
        caloriesTotal = 0.0
        callback = object : ExerciseUpdateCallback {
            override fun onExerciseUpdateReceived(update: ExerciseUpdate) = onUpdate(update)
            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
            override fun onAvailabilityChanged(
                dataType: DataType<*, *>,
                availability: Availability,
            ) {
            }
        }
        exerciseClient.setUpdateCallback(callback!!)
        exerciseClient.startExercise(config)
        _live.value = WorkoutLive(state = WorkoutState.ACTIVE)
    }

    /** 结束记录，返回汇总（失败返回 null）。 */
    suspend fun stop(): WorkoutSummary? = runCatching {
        exerciseClient.endExercise()
        val duration = System.currentTimeMillis() - startMs
        val summary = WorkoutSummary(
            durationMs = duration,
            avgHr = if (hrCount > 0) hrSum / hrCount else 0.0,
            maxHr = maxHr,
            calories = caloriesTotal,
            steps = stepsTotal,
        )
        _live.value = WorkoutLive(state = WorkoutState.ENDED, summary = summary)
        summary
    }.getOrNull()
}

/** 进程内单例：前台服务与界面共用同一个 manager（运动中途切界面不丢回调）。 */
object GolfExerciseSession {
    @Volatile
    private var instance: GolfExerciseManager? = null

    fun get(context: Context): GolfExerciseManager =
        instance ?: synchronized(this) {
            instance ?: GolfExerciseManager(context.applicationContext).also { instance = it }
        }
}
