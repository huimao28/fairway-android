package com.huimao.fairway.data

import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * 收手表消息：记分 / 切洞 / 运动汇总。
 * 只解析、不写库——解析结果发到 WearSync，由 RoundViewModel 消费后写回当轮成绩。
 * DataMap 字段缺失时按 null 处理，解析失败整条丢弃，不闪退。
 */
class FairwayWearListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val dataMap = runCatching { DataMap.fromByteArray(event.data) }.getOrNull() ?: return
        when (event.path) {
            WearPaths.SCORE -> {
                parseScoreUpdate(
                    hole = dataMap.intOrNull(WearKeys.HOLE),
                    strokes = dataMap.intOrNull(WearKeys.STROKES),
                    putts = dataMap.intOrNull(WearKeys.PUTTS),
                )?.let { WearSync.scoreUpdates.tryEmit(it) }
            }
            WearPaths.HOLE -> {
                dataMap.intOrNull(WearKeys.HOLE)
                    ?.takeIf { it in 1..18 }
                    ?.let { WearSync.holeRequests.tryEmit(it) }
            }
            WearPaths.WORKOUT -> {
                parseWorkoutSummary(
                    durationMs = dataMap.longOrNull(WearKeys.DURATION_MS),
                    avgHr = dataMap.doubleOrNull(WearKeys.AVG_HR),
                    maxHr = dataMap.intOrNull(WearKeys.MAX_HR),
                    calories = dataMap.doubleOrNull(WearKeys.CALORIES),
                    steps = dataMap.longOrNull(WearKeys.STEPS),
                )?.let { WearSync.workoutSummaries.tryEmit(it) }
            }
        }
    }

    private fun DataMap.intOrNull(key: String): Int? =
        if (containsKey(key)) getInt(key) else null

    private fun DataMap.longOrNull(key: String): Long? =
        if (containsKey(key)) getLong(key) else null

    private fun DataMap.doubleOrNull(key: String): Double? =
        if (containsKey(key)) getDouble(key) else null
}
