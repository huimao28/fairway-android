package com.huimao.fairway.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 球场包来源：内置（assets）或下载（内部存储）。 */
data class PackRef(val fileName: String, val isDownloaded: Boolean)

/** 球场数据包仓库：内置包在 assets/courses/，下载的包在 files/courses/。IO 一律放 Dispatchers.IO。 */
class CourseRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val downloadedStore = DownloadedCourseStore(context)

    /** 列出全部球场包：内置在前，下载的在后。 */
    suspend fun listPacks(): List<PackRef> = withContext(Dispatchers.IO) {
        val assets = context.assets.list("courses").orEmpty()
            .filter { it.endsWith(".json") }
            .sorted()
            .map { PackRef(it, false) }
        val downloaded = downloadedStore.listFiles().map { PackRef(it, true) }
        assets + downloaded
    }

    suspend fun loadPack(fileName: String, isDownloaded: Boolean = false): CoursePack =
        withContext(Dispatchers.IO) {
            if (isDownloaded) {
                downloadedStore.load(fileName)
                    ?: throw IllegalStateException("下载的球场包不存在：$fileName")
            } else {
                context.assets.open("courses/$fileName").use { stream ->
                    json.decodeFromString<CoursePack>(stream.readBytes().decodeToString())
                }
            }
        }
}
