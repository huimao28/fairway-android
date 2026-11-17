package com.huimao.fairway.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 推给手表的当前洞状态（距离是手机算好的米数，-1 表示未知）。 */
data class RoundPushState(
    val courseName: String,
    val hole: Int,
    val par: Int,
    val distFront: Int,
    val distMid: Int,
    val distBack: Int,
    val strokes: Int,
)

/** Play Services Task 转挂起函数（不引新协程库）。 */
internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
}

/**
 * 手机→手表同步：DataClient 推当前洞状态（setUrgent 立即同步）；
 * NodeClient 查手表是否连接。没连上手表时 putDataItem 也不会崩，
 * DataItem 会在手表连上后自动同步。
 */
class WearRoundSync(context: Context) {

    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)

    suspend fun pushRound(state: RoundPushState) {
        val request = PutDataMapRequest.create(WearPaths.ROUND).apply {
            dataMap.putString(WearKeys.COURSE_NAME, state.courseName)
            dataMap.putInt(WearKeys.HOLE, state.hole)
            dataMap.putInt(WearKeys.PAR, state.par)
            dataMap.putInt(WearKeys.DIST_FRONT, state.distFront)
            dataMap.putInt(WearKeys.DIST_MID, state.distMid)
            dataMap.putInt(WearKeys.DIST_BACK, state.distBack)
            dataMap.putInt(WearKeys.STROKES, state.strokes)
        }
        dataClient.putDataItem(request.asPutDataRequest().setUrgent()).awaitTask()
    }

    suspend fun isWatchConnected(): Boolean =
        runCatching { nodeClient.connectedNodes.awaitTask().isNotEmpty() }.getOrDefault(false)
}
