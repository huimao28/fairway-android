package com.huimao.fairway.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.pose.PoseLandmark
import com.huimao.fairway.domain.SwingMetrics as SwingCalc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/** 需要的关键点类型（ML Kit PoseLandmark 常量；取值与 SwingLandmark 一致）。 */
private val NEEDED_LANDMARK_TYPES = listOf(
    PoseLandmark.NOSE,
    PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
    PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
    PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
    PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
)

/**
 * 挥杆视频分析：MediaMetadataRetriever 每 100ms 抽一帧（超过 30 秒只取前 30 秒），
 * 下采样到最长边 ≤480px 后送 ML Kit 姿态检测（单图模式，端上免费离线）。
 * 检测失败/某帧无人时该帧 landmarks 为空，不中断整体。
 * 全程无网络；返回 null 表示视频读不出来或整段都没检测到人。
 */
class PoseAnalyzer(private val context: Context) {

    suspend fun analyze(
        uri: Uri,
        onProgress: (processed: Int, total: Int, stage: String) -> Unit,
    ): SwingSession? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var detector: PoseDetector? = null
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return@withContext null
            if (durationMs <= 0) return@withContext null
            val cappedMs = min(durationMs, 30_000L)
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            // 旋转元数据：VideoView 播放时会自动转正，宽高与坐标都要按显示方向算
            val videoW = if (rotation == 90 || rotation == 270) rawH else rawW
            val videoH = if (rotation == 90 || rotation == 270) rawW else rawH
            val totalFrames = max(1, (cappedMs / 100).toInt())
            val option = if (Build.VERSION.SDK_INT >= 28) {
                MediaMetadataRetriever.OPTION_CLOSEST
            } else {
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            }
            val options = PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build()
            detector = PoseDetection.getClient(options)
            val poseDetector = detector
            val frames = ArrayList<SwingFrame>(totalFrames)
            for (i in 0 until totalFrames) {
                val timeMs = i * 100L
                val raw = runCatching {
                    retriever.getFrameAtTime(timeMs * 1000, option)
                }.getOrNull()
                val landmarks = if (raw != null) {
                    val bitmap = prepareBitmap(raw, rotation)
                    val found = runCatching {
                        poseDetector.detect(bitmap)?.toLandmarks(bitmap.width, bitmap.height)
                    }.getOrNull().orEmpty()
                    if (bitmap !== raw) bitmap.recycle()
                    raw.recycle()
                    found
                } else {
                    emptyList()
                }
                frames += SwingFrame(timeMs, landmarks)
                onProgress(i + 1, totalFrames, "正在分析第 ${i + 1}/$totalFrames 帧…")
            }
            if (frames.none { it.landmarks.isNotEmpty() }) return@withContext null
            val (moments, metrics) = SwingCalc.analyze(frames)
            val id = UUID.randomUUID().toString()
            saveThumbnails(retriever, moments, SwingStore(context).swingsDir(), id, option, rotation)
            SwingSession(
                id = id,
                videoUri = uri.toString(),
                createdAt = System.currentTimeMillis(),
                durationMs = cappedMs,
                videoWidth = videoW,
                videoHeight = videoH,
                frames = frames,
                keyMoments = moments,
                metrics = metrics,
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { detector?.close() }
            runCatching { retriever.release() }
        }
    }

    /** 下采样到最长边 ≤480px，并按旋转元数据转正（坐标系与播放显示一致）。 */
    private fun prepareBitmap(src: Bitmap, rotation: Int): Bitmap {
        val longest = max(src.width, src.height)
        val scale = if (longest > 480) 480f / longest else 1f
        val matrix = Matrix()
        if (scale != 1f) matrix.postScale(scale, scale)
        if (rotation != 0) matrix.postRotate(rotation.toFloat())
        return if (matrix.isIdentity) src
        else Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /** 4 个关键时刻的缩略图（<id>_<moment>.jpg），供详情页关键帧条用。 */
    private fun saveThumbnails(
        retriever: MediaMetadataRetriever,
        moments: KeyMoments,
        dir: File,
        id: String,
        option: Int,
        rotation: Int,
    ) {
        val jobs = listOf(
            "address" to moments.addressMs,
            "top" to moments.topMs,
            "impact" to moments.impactMs,
            "finish" to moments.finishMs,
        )
        for ((name, ms) in jobs) {
            if (ms == null) continue
            val raw = runCatching { retriever.getFrameAtTime(ms * 1000, option) }.getOrNull()
                ?: continue
            val bitmap = prepareBitmap(raw, rotation)
            runCatching {
                FileOutputStream(File(dir, "${id}_$name.jpg")).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
            }
            if (bitmap !== raw) bitmap.recycle()
            raw.recycle()
        }
    }

    /** Task 转挂起函数（失败返回 null，不抛）。 */
    private suspend fun PoseDetector.detect(bitmap: Bitmap): Pose? =
        suspendCancellableCoroutine { cont ->
            process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { pose ->
                    if (cont.isActive) cont.resume(pose)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }

    /** Pose 转归一化关键点（只取需要的类型；坐标相对帧尺寸 0..1）。 */
    private fun Pose.toLandmarks(width: Int, height: Int): List<LandmarkPoint> {
        if (width <= 0 || height <= 0) return emptyList()
        return NEEDED_LANDMARK_TYPES.mapNotNull { type ->
            val landmark = getPoseLandmark(type) ?: return@mapNotNull null
            val p = landmark.position
            LandmarkPoint(type, p.x / width, p.y / height, landmark.inFrameLikelihood)
        }
    }
}
