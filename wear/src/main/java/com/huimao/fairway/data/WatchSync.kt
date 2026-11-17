package com.huimao.fairway.data

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data Layer 路径与 key：手机端（app 模块）有同一份拷贝，改这里必须同步改手机端，一字不差。
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

/** 手机推来的当前洞状态（距离是手机算好的米数，-1 表示未知）。 */
data class RoundMirror(
    val courseName: String,
    val hole: Int,
    val par: Int,
    val distFront: Int,
    val distMid: Int,
    val distBack: Int,
    val strokes: Int,
)

/** 手表内跨组件同步：ListenerService 收到手机 DataItem 后往这里写，界面消费。 */
object WatchState {
    private val _round = MutableStateFlow<RoundMirror?>(null)
    val round: StateFlow<RoundMirror?> = _round.asStateFlow()

    fun updateRound(mirror: RoundMirror) {
        _round.value = mirror
    }
}

/** 收手机推来的当前洞状态（DataItem /fairway/round），写进 WatchState。 */
class WatchDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path != WearPaths.ROUND) continue
            val map = DataMapItem.fromDataItem(item).dataMap
            WatchState.updateRound(
                RoundMirror(
                    courseName = map.getString(WearKeys.COURSE_NAME, ""),
                    hole = map.getInt(WearKeys.HOLE, 1),
                    par = map.getInt(WearKeys.PAR, 0),
                    distFront = map.getInt(WearKeys.DIST_FRONT, -1),
                    distMid = map.getInt(WearKeys.DIST_MID, -1),
                    distBack = map.getInt(WearKeys.DIST_BACK, -1),
                    strokes = map.getInt(WearKeys.STROKES, 0),
                ),
            )
        }
    }
}
