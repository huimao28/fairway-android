package com.huimao.fairway.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Overpass API 元素（out geom / out center 两种输出都兼容）。 */
@Serializable
data class OverpassPoint(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class OverpassElement(
    val type: String,
    val id: Long,
    val tags: Map<String, String> = emptyMap(),
    val geometry: List<OverpassPoint> = emptyList(),
    val center: OverpassPoint? = null,
)

@Serializable
data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList(),
)

/** Nominatim 按名搜索的一条结果。 */
@Serializable
data class NominatimPlace(
    val osm_type: String = "",
    val osm_id: Long = 0,
    val lat: String = "0",
    val lon: String = "0",
    val display_name: String = "",
    val name: String? = null,
    val `class`: String = "",
    val type: String = "",
)

/** 球场搜索结果：OSM way id + 名 + 中心点。 */
data class CourseSearchResult(
    val osmId: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
)

/** 下载一个球场的结果。 */
sealed interface CourseDownload {
    data class Ok(val pack: CoursePack) : CourseDownload
    /** 联网失败或 Overpass 无响应 */
    data object NetworkError : CourseDownload
    /** 该球场在 OSM 上完全没有洞级数据 */
    data object NoHoleData : CourseDownload
    /** 有洞但用不了（缺 ref/par、或不是标准 18 洞） */
    data object Not18Holes : CourseDownload
}

private val overpassJson = Json { ignoreUnknownKeys = true }

/**
 * 在线球场数据：Overpass（附近球场 + 下载球场明细）与 Nominatim（按名搜索）。
 * 两个都是免费社区接口：Overpass 按球场精确拉（不扫大范围），Nominatim 遵守 1 次/秒。
 */
class OverpassClient {

    /** 附近的球场（way，带名）：按用户位置 radius 米内找。 */
    suspend fun searchNearby(lat: Double, lon: Double, radiusM: Int = 50000): List<CourseSearchResult> =
        withContext(Dispatchers.IO) {
            val query = """
                [out:json][timeout:30];
                way["leisure"="golf_course"](around:$radiusM,$lat,$lon);
                out center;
            """.trimIndent()
            val resp = postOverpass(query, readTimeoutMs = 45_000) ?: return@withContext emptyList()
            resp.elements
                .filter { it.type == "way" && it.center != null }
                .mapNotNull { el ->
                    val name = el.tags["name"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    CourseSearchResult(el.id, name, el.center!!.lat, el.center!!.lon)
                }
                .sortedBy { it.name }
        }

    /** 按名搜索球场（Nominatim；只保留 leisure=golf_course 的 way）。 */
    suspend fun searchByName(query: String): List<CourseSearchResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL(
                "https://nominatim.openstreetmap.org/search?format=json&limit=10" +
                    "&accept-language=zh-CN&q=$encoded",
            )
            runCatching {
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "HuimaoFairway/1.0 (Android golf app)")
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                try {
                    val text = conn.inputStream.bufferedReader().readText()
                    overpassJson.decodeFromString<List<NominatimPlace>>(text)
                        .filter { it.osm_type == "way" && it.`class` == "leisure" && it.type == "golf_course" }
                        .mapNotNull { p ->
                            val name = p.name?.takeIf { it.isNotBlank() }
                                ?: p.display_name.substringBefore(",").takeIf { it.isNotBlank() }
                                ?: return@mapNotNull null
                            CourseSearchResult(
                                p.osm_id,
                                name,
                                p.lat.toDoubleOrNull() ?: return@mapNotNull null,
                                p.lon.toDoubleOrNull() ?: return@mapNotNull null,
                            )
                        }
                } finally {
                    conn.disconnect()
                }
            }.getOrElse { emptyList() }
        }

    /** 下载一个球场：先拉轮廓算 bbox，再拉洞/Tee/果岭/沙坑/水障，端上配对成 CoursePack。 */
    suspend fun downloadCourse(osmId: Long): CourseDownload = withContext(Dispatchers.IO) {
        // 第一步：球场轮廓（算 bbox）
        val outlineQuery = """
            [out:json][timeout:30];
            way($osmId);
            out geom;
        """.trimIndent()
        val outline = postOverpass(outlineQuery, readTimeoutMs = 45_000)
            ?.elements?.firstOrNull { it.type == "way" }
            ?: return@withContext CourseDownload.NetworkError
        if (outline.geometry.isEmpty()) return@withContext CourseDownload.NetworkError
        val minLat = outline.geometry.minOf { it.lat } - 0.005
        val maxLat = outline.geometry.maxOf { it.lat } + 0.005
        val minLon = outline.geometry.minOf { it.lon } - 0.005
        val maxLon = outline.geometry.maxOf { it.lon } + 0.005
        val bbox = "$minLat,$minLon,$maxLat,$maxLon"

        // 第二步：洞级数据（只拉 way；Tee/果岭/沙坑/水障在 OSM 上都是 way）
        val detailQuery = """
            [out:json][timeout:60];
            (
              way["golf"="hole"]["par"]($bbox);
              way["golf"="tee"]($bbox);
              way["golf"="green"]($bbox);
              way["golf"="bunker"]($bbox);
              way["golf"="water_hazard"]($bbox);
              way["golf"="lateral_water_hazard"]($bbox);
            );
            out geom;
        """.trimIndent()
        val detail = postOverpass(detailQuery, readTimeoutMs = 90_000)
            ?: return@withContext CourseDownload.NetworkError
        if (detail.elements.none { it.type == "way" && it.tags["golf"] == "hole" }) {
            return@withContext CourseDownload.NoHoleData
        }
        val pack = CoursePackBuilder.build(outline, detail)
            ?: return@withContext CourseDownload.Not18Holes
        CourseDownload.Ok(pack)
    }

    private fun postOverpass(query: String, readTimeoutMs: Int): OverpassResponse? {
        return runCatching {
            val url = URL("https://overpass-api.de/api/interpreter")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", "HuimaoFairway/1.0 (Android golf app)")
            conn.connectTimeout = 10_000
            conn.readTimeout = readTimeoutMs
            try {
                conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
                val text = conn.inputStream.bufferedReader().readText()
                overpassJson.decodeFromString<OverpassResponse>(text)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
