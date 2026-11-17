package com.huimao.fairway.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Play Services Task 转挂起函数（不引新协程库）。 */
internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
}

/** 找第一个已连接的节点（手机）；没连上返回 null，调用方直接丢弃本次发送。 */
private suspend fun firstConnectedNodeId(context: Context): String? =
    runCatching {
        Wearable.getNodeClient(context).connectedNodes.awaitTask().firstOrNull()?.id
    }.getOrNull()

private suspend fun sendMessage(context: Context, path: String, payload: ByteArray): Boolean {
    val nodeId = firstConnectedNodeId(context) ?: return false
    return runCatching {
        Wearable.getMessageClient(context).sendMessage(nodeId, path, payload).awaitTask()
        true
    }.getOrDefault(false)
}

/** 手表→手机：记分（putts 恒为 -1，手表 v1 不改推杆数，手机保留原值）。 */
suspend fun sendScore(context: Context, hole: Int, strokes: Int): Boolean {
    val payload = DataMap().apply {
        putInt(WearKeys.HOLE, hole)
        putInt(WearKeys.STROKES, strokes)
        putInt(WearKeys.PUTTS, -1)
    }.toByteArray()
    return sendMessage(context, WearPaths.SCORE, payload)
}

/** 手表→手机：切洞请求。 */
suspend fun sendHoleRequest(context: Context, hole: Int): Boolean {
    val payload = DataMap().apply {
        putInt(WearKeys.HOLE, hole)
    }.toByteArray()
    return sendMessage(context, WearPaths.HOLE, payload)
}

/** 手表→手机：运动记录汇总。 */
suspend fun sendWorkoutSummary(context: Context, summary: WorkoutSummary): Boolean {
    val payload = DataMap().apply {
        putLong(WearKeys.DURATION_MS, summary.durationMs)
        putDouble(WearKeys.AVG_HR, summary.avgHr)
        putInt(WearKeys.MAX_HR, summary.maxHr)
        putDouble(WearKeys.CALORIES, summary.calories)
        putLong(WearKeys.STEPS, summary.steps)
    }.toByteArray()
    return sendMessage(context, WearPaths.WORKOUT, payload)
}
