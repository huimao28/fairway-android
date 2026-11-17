package com.huimao.fairway.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 下载的球场包：存在应用内部存储 files/courses/osm_<wayId>.json，
 * 与 assets/courses/ 里的内置包用同一格式（CoursePack），不用改代码就能用。
 */
class DownloadedCourseStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun dir(): File = File(context.filesDir, "courses").apply { mkdirs() }

    /** 文件名：osm_<wayId>.json（wayId 从 pack.course.osm_id 的 "way/123" 取）。 */
    fun fileNameFor(pack: CoursePack): String {
        val id = pack.course.osm_id.substringAfter("way/", "0")
        return "osm_$id.json"
    }

    suspend fun save(pack: CoursePack): String = withContext(Dispatchers.IO) {
        val name = fileNameFor(pack)
        File(dir(), name).writeText(json.encodeToString(pack))
        name
    }

    suspend fun listFiles(): List<String> = withContext(Dispatchers.IO) {
        dir().listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".json") }
            .map { it.name }
            .sorted()
    }

    suspend fun load(fileName: String): CoursePack? = withContext(Dispatchers.IO) {
        val file = File(dir(), fileName)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<CoursePack>(file.readText()) }.getOrNull()
    }

    suspend fun delete(fileName: String): Boolean = withContext(Dispatchers.IO) {
        File(dir(), fileName).delete()
    }
}
