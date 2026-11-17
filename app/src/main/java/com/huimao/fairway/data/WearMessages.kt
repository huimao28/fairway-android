package com.huimao.fairway.data

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Data Layer 路径与 key。
 * 手表端（wear 模块）有同一份拷贝，改这里必须同步改手表端，一字不差。
 */
object WearPaths {
    /** 手机→手表：当前洞状态（DataItem）。 */
    const val ROUND = "/fairway/round"

    /** 手表→手机：记分（Message）。 */
    const val SCORE = "/fairway/score"

    /** 手表→手机：切洞请求（Message）。 */
    const val HOLE = "/fairway/hole"

    /** 手表→手机：运动记录汇总（Message）。 */
    const val WORKOUT = "/fairway/workout"
}

object WearKeys {
    const val COURSE_NAME = "courseName"
    const val HOLE = "hole"
    const val PAR = "par"
    const val DIST_FRONT = "distFront"
    const val DIST_MID = "distMid"
    const val DIST_BACK = "distBack"
    const val STROKES = "strokes"

    /** putts = -1 表示手表没改推杆数，手机保留原值。 */
    const val PUTTS = "putts"
    const val DURATION_MS = "durationMs"
    const val AVG_HR = "avgHr"
    const val MAX_HR = "maxHr"
    const val CALORIES = "calories"
    const val STEPS = "steps"
}

/** 手表发来的记分：putts = -1 表示手表没改推杆数，手机保留原值。 */
data class WatchScoreUpdate(val hole: Int, val strokes: Int, val putts: Int)

/**
 * 解析手表记分（纯函数，可单元测试）。
 * hole 必须 1..18、strokes 必须 ≥ 0，否则整条丢弃；putts 缺失或非法时记 -1（不改）。
 */
fun parseScoreUpdate(hole: Int?, strokes: Int?, putts: Int?): WatchScoreUpdate? {
    if (hole == null || hole !in 1..18) return null
    if (strokes == null || strokes < 0) return null
    val p = when {
        putts == null -> -1
        putts < -1 -> -1
        else -> putts
    }
    return WatchScoreUpdate(hole, strokes, p)
}

/**
 * 解析手表运动汇总（纯函数，可单元测试）。
 * durationMs 必须 > 0，否则整条丢弃；其余缺失时按 0 计，负数钳到 0。
 */
fun parseWorkoutSummary(
    durationMs: Long?,
    avgHr: Double?,
    maxHr: Int?,
    calories: Double?,
    steps: Long?,
): WorkoutSummary? {
    if (durationMs == null || durationMs <= 0) return null
    return WorkoutSummary(
        durationMs = durationMs,
        avgHr = (avgHr ?: 0.0).coerceAtLeast(0.0),
        maxHr = (maxHr ?: 0).coerceAtLeast(0),
        calories = (calories ?: 0.0).coerceAtLeast(0.0),
        steps = (steps ?: 0L).coerceAtLeast(0L),
    )
}

/**
 * 手机内跨组件同步：FairwayWearListenerService 收到手表消息后往这里发，
 * RoundViewModel 消费。SharedFlow 只在进程内，不落盘。
 */
object WearSync {
    val scoreUpdates = MutableSharedFlow<WatchScoreUpdate>(extraBufferCapacity = 16)
    val holeRequests = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val workoutSummaries = MutableSharedFlow<WorkoutSummary>(extraBufferCapacity = 4)
}
