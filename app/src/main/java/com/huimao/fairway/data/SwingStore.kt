package com.huimao.fairway.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 挥杆分析结果：存在应用内部存储 files/swings/，一个 session 一个 JSON
 * （<id>.json）+ 4 张关键帧缩略图（<id>_address.jpg 等）；视频本身不复制，只存 Uri。
 */
class SwingStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun dir(): File = File(context.filesDir, "swings").apply { mkdirs() }

    /** 关键帧缩略图文件（moment 取 address/top/impact/finish）。 */
    fun thumbnailFile(id: String, moment: String): File = File(dir(), "${id}_$moment.jpg")

    /** 供 PoseAnalyzer 存缩略图用（同目录）。 */
    fun swingsDir(): File = dir()

    suspend fun save(session: SwingSession) = withContext(Dispatchers.IO) {
        File(dir(), "${session.id}.json").writeText(json.encodeToString(session))
    }

    suspend fun listSessions(): List<SwingSession> = withContext(Dispatchers.IO) {
        dir().listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".json") }
            .mapNotNull {
                runCatching { json.decodeFromString<SwingSession>(it.readText()) }.getOrNull()
            }
            .sortedByDescending { it.createdAt }
    }

    suspend fun load(id: String): SwingSession? = withContext(Dispatchers.IO) {
        val file = File(dir(), "$id.json")
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<SwingSession>(file.readText()) }.getOrNull()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir(), "$id.json").delete()
        for (moment in listOf("address", "top", "impact", "finish")) {
            thumbnailFile(id, moment).delete()
        }
    }
}
