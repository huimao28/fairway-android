package com.huimao.fairway.data

import kotlinx.serialization.Serializable

/**
 * ML Kit 姿态检测关键点类型：取值与 ML Kit PoseLandmark 常量一致
 * （NOSE=0, LEFT_SHOULDER=11, RIGHT_SHOULDER=12, LEFT_ELBOW=13,
 * RIGHT_ELBOW=14, LEFT_WRIST=15, RIGHT_WRIST=16, LEFT_HIP=23,
 * RIGHT_HIP=24, LEFT_KNEE=25, RIGHT_KNEE=26, LEFT_ANKLE=27, RIGHT_ANKLE=28）。
 * 只存挥杆分析用得到的点。
 */
object SwingLandmark {
    const val NOSE = 0
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28

    /** 骨架连线：画在视频上方的线段（按类型连）。 */
    val CONNECTIONS: List<Pair<Int, Int>> = listOf(
        LEFT_SHOULDER to RIGHT_SHOULDER,
        LEFT_SHOULDER to LEFT_ELBOW,
        LEFT_ELBOW to LEFT_WRIST,
        RIGHT_SHOULDER to RIGHT_ELBOW,
        RIGHT_ELBOW to RIGHT_WRIST,
        LEFT_SHOULDER to LEFT_HIP,
        RIGHT_SHOULDER to RIGHT_HIP,
        LEFT_HIP to RIGHT_HIP,
        LEFT_HIP to LEFT_KNEE,
        LEFT_KNEE to LEFT_ANKLE,
        RIGHT_HIP to RIGHT_KNEE,
        RIGHT_KNEE to RIGHT_ANKLE,
    )
}

/** 一帧里的一个关键点：x/y 是相对帧尺寸的 0..1 归一化坐标。 */
@Serializable
data class LandmarkPoint(
    val type: Int,
    val x: Float,
    val y: Float,
    val likelihood: Float,
)

/** 一帧的分析结果（timeMs 相对视频开头；无人的帧 landmarks 为空）。 */
@Serializable
data class SwingFrame(
    val timeMs: Long,
    val landmarks: List<LandmarkPoint> = emptyList(),
) {
    fun landmark(type: Int): LandmarkPoint? = landmarks.firstOrNull { it.type == type }
}

/** 4 个关键时刻（毫秒，相对视频开头；没找到为 null）。 */
@Serializable
data class KeyMoments(
    val addressMs: Long? = null,
    val topMs: Long? = null,
    val impactMs: Long? = null,
    val finishMs: Long? = null,
)

/** 挥杆指标（都没算出来时为 null；只做展示对照，不打分）。 */
@Serializable
data class SwingMetrics(
    /** 上杆顶点时的肩转角（最大肩转）。 */
    val shoulderTurnDeg: Double? = null,
    /** 上杆顶点时的髋转角（最大髋转）。 */
    val hipTurnDeg: Double? = null,
    /** 准备姿势的脊柱前倾角（肩中点-髋中点连线与竖直线的夹角）。 */
    val spineTiltDeg: Double? = null,
    /** 准备姿势的前腿（左腿，默认右手球员）膝盖弯曲角。 */
    val kneeFlexDeg: Double? = null,
    /** 上杆时长（address -> top）。 */
    val backswingMs: Long? = null,
    /** 下杆时长（top -> impact）。 */
    val downswingMs: Long? = null,
    /** 节奏比值 = 上杆时长 / 下杆时长。 */
    val tempoRatio: Double? = null,
)

/** 一次挥杆分析的完整结果（视频本身不复制，只存 Uri）。 */
@Serializable
data class SwingSession(
    val id: String,
    val videoUri: String,
    val createdAt: Long,
    val durationMs: Long,
    /** 显示尺寸（已按旋转元数据换算过，与 VideoView 显示一致）。 */
    val videoWidth: Int,
    val videoHeight: Int,
    val frames: List<SwingFrame> = emptyList(),
    val keyMoments: KeyMoments = KeyMoments(),
    val metrics: SwingMetrics = SwingMetrics(),
)
