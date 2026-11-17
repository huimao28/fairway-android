package com.huimao.fairway.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 球场数据包仓库：从 assets/courses/ 读 JSON。IO 一律放 Dispatchers.IO。 */
class CourseRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 列出内置的球场包文件名（如 taohuayuan.json）。 */
    suspend fun listPacks(): List<String> = withContext(Dispatchers.IO) {
        context.assets.list("courses").orEmpty()
            .filter { it.endsWith(".json") }
            .sorted()
    }

    suspend fun loadPack(fileName: String): CoursePack = withContext(Dispatchers.IO) {
        context.assets.open("courses/$fileName").use { stream ->
            json.decodeFromString<CoursePack>(stream.readBytes().decodeToString())
        }
    }
}
