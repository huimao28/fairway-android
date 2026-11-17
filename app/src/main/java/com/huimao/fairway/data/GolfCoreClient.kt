package com.huimao.fairway.data

import com.huimao.fairway.domain.Geo
import com.huimao.fairway.domain.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** GolfCore 搜索结果的一条球场（列表接口返回的字段）。 */
@Serializable
data class GolfCoreCourse(
    val slug: String,
    val name: String,
    val url: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val hole_count: Int = 0,
    val par_total: Int = 0,
    /** 中文原名（中国球场有，如“长安高尔夫球场”；国外为 null） */
    val native_name: String? = null,
    val coverage: GolfCoreCoverage = GolfCoreCoverage(),
)

@Serializable
data class GolfCoreCoverage(
    val contours: Boolean = false,
    val traced: Boolean = false,
    val scorecard: Boolean = false,
    val rated: Boolean = false,
    val wind: Boolean = false,
)

@Serializable
data class GolfCoreSearchResponse(
    val total: Int = 0,
    val count: Int = 0,
    val courses: List<GolfCoreCourse> = emptyList(),
)

/** 详情接口：一条发球台（同一名字男女各一条，rating/slope 可能不同）。 */
@Serializable
data class GolfCoreTee(
    val name: String = "",
    val gender: String = "",
    val yards: Int = 0,
    val par: Int = 0,
    val rating: Double? = null,
    val slope: Int? = null,
    val holes: List<GolfCoreHole> = emptyList(),
)

@Serializable
data class GolfCoreHole(
    val position: Int = 0,
    val par: Int = 0,
    val stroke_index: Int = 0,
    val yards: Int = 0,
)

@Serializable
data class GolfCoreLayout(
    val name: String = "",
    val tees: List<GolfCoreTee> = emptyList(),
)

/** 球场详情（/v1/courses/{slug}）：列表字段 + layouts。 */
@Serializable
data class GolfCoreDetail(
    val slug: String = "",
    val name: String = "",
    val url: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val layouts: List<GolfCoreLayout> = emptyList(),
)

/** GolfCore 搜索的结果（区分限流与普通失败，界面文案不一样）。 */
sealed interface GolfCoreSearch {
    data class Ok(val courses: List<GolfCoreCourse>) : GolfCoreSearch
    /** 联网失败 / 非 200 非 429 */
    data object NetworkError : GolfCoreSearch
    /** 429：免费额度（500 次/月、10 次/分钟）用完了 */
    data object RateLimited : GolfCoreSearch
}

private val golfCoreJson = Json { ignoreUnknownKeys = true }

/** 四色发球台（与 TeeRating.color 一致）。 */
private val TEE_COLORS = setOf("black", "blue", "white", "red")

/**
 * 发球台名 -> 四色之一：名字只能是单个颜色词（或颜色 + tee/tees），
 * 组合台（Gold/White Combo）、Gold、Green 这类一律不映射——颜色对不上就别硬配。
 */
fun teeColorFromName(name: String): String? {
    val tokens = name.lowercase(Locale.ROOT)
        .split(Regex("[^a-z]+"))
        .filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    val color = tokens[0]
    if (color !in TEE_COLORS) return null
    if (tokens.size == 1) return color
    if (tokens.size == 2 && (tokens[1] == "tee" || tokens[1] == "tees")) return color
    return null
}

/**
 * GolfCore 详情 -> 包的 ratings：只取男子（gender M）的发球台，
 * 女子 rating 不能给男球员算 WHS；没有 rating/slope 的台跳过；每色取第一个。
 */
fun mapTeeRatings(detail: GolfCoreDetail): List<TeeRating> {
    val out = mutableMapOf<String, TeeRating>()
    for (layout in detail.layouts) {
        for (tee in layout.tees) {
            if (!tee.gender.equals("M", ignoreCase = true)) continue
            val color = teeColorFromName(tee.name) ?: continue
            if (color in out) continue
            val rating = tee.rating ?: continue
            val slope = tee.slope ?: continue
            out[color] = TeeRating(color, rating, slope)
        }
    }
    return out.values.toList()
}

/** 国家码（ISO alpha-2，如 cn）-> 中文国家名；拿不到就原样返回大写码。 */
fun countryDisplay(code: String): String {
    val c = code.trim().uppercase(Locale.ROOT)
    if (c.length != 2) return c
    return runCatching {
        Locale.Builder().setRegion(c).build()
            .getDisplayCountry(Locale.SIMPLIFIED_CHINESE)
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: c
}

/** 在候选里取离目标点最近的一家（超过 maxKmKm 则不要）。 */
fun nearestWithin(
    courses: List<GolfCoreCourse>,
    lat: Double,
    lon: Double,
    maxKm: Double,
): GolfCoreCourse? {
    val target = LatLng(lat, lon)
    return courses
        .mapNotNull { c ->
            val d = Geo.haversineMeters(target, LatLng(c.lat, c.lng))
            if (d <= maxKm * 1000) c to d else null
        }
        .minByOrNull { it.second }
        ?.first
}

/** 在 Overpass 附近查询的 way 里取中心点离目标最近的一个（下载桥接用）。 */
fun pickNearestCourseWay(elements: List<OverpassElement>, lat: Double, lon: Double): Long? {
    val target = LatLng(lat, lon)
    return elements
        .filter { it.type == "way" && it.center != null }
        .minByOrNull { Geo.haversineMeters(target, LatLng(it.center!!.lat, it.center!!.lon)) }
        ?.id
}

/**
 * GolfCore 客户端：国外球场搜索（按名/按附近）+ 详情（记分卡与 rating/slope）。
 * 免费档 500 次/月、10 次/分钟；2026-12-04 前免 key 可用，之后必须带 key。
 * key 由用户在应用设置里填自己的（SettingsStore），不嵌进 APK、不进仓库、不打日志。
 */
class GolfCoreClient {

    /** 按名搜索（limit 最多 200，默认 10）。 */
    suspend fun searchByName(query: String, apiKey: String? = null): GolfCoreSearch =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            get("https://api.golfcore.org/v1/courses?q=$encoded&limit=10", apiKey)
        }

    /** 按附近搜索：radius 单位是英里（GolfCore 的 near 参数）。 */
    suspend fun searchNearby(
        lat: Double,
        lon: Double,
        radiusMiles: Double = 31.0,
        apiKey: String? = null,
    ): GolfCoreSearch = withContext(Dispatchers.IO) {
        get(
            "https://api.golfcore.org/v1/courses?near=$lat,$lon&radius=$radiusMiles&limit=10",
            apiKey,
        )
    }

    /** 球场详情（含各发球台 rating/slope）；失败返回 null。 */
    suspend fun getCourse(slug: String, apiKey: String? = null): GolfCoreDetail? =
        withContext(Dispatchers.IO) {
            val (_, body) = getRaw("https://api.golfcore.org/v1/courses/$slug", apiKey) ?: return@withContext null
            runCatching { golfCoreJson.decodeFromString<GolfCoreDetail>(body) }.getOrNull()
        }

    /** 按坐标找最近的 GolfCore 球场（5 英里内、2 公里内才算同一家）。 */
    suspend fun findNearest(lat: Double, lon: Double, apiKey: String? = null): GolfCoreCourse? =
        withContext(Dispatchers.IO) {
            val search = searchNearby(lat, lon, radiusMiles = 5.0, apiKey)
            if (search !is GolfCoreSearch.Ok) return@withContext null
            nearestWithin(search.courses, lat, lon, maxKm = 2.0)
        }

    private fun get(url: String, apiKey: String?): GolfCoreSearch {
        val (code, body) = getRaw(url, apiKey) ?: return GolfCoreSearch.NetworkError
        if (code == 429) return GolfCoreSearch.RateLimited
        if (code != 200) return GolfCoreSearch.NetworkError
        return runCatching {
            GolfCoreSearch.Ok(golfCoreJson.decodeFromString<GolfCoreSearchResponse>(body).courses)
        }.getOrElse { GolfCoreSearch.NetworkError }
    }

    /** 返回 (HTTP 状态码, 响应体)；联网失败返回 null。 */
    private fun getRaw(url: String, apiKey: String?): Pair<Int, String>? {
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "HuimaoFairway/1.0 (Android golf app)")
            if (!apiKey.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                code to (stream?.bufferedReader()?.readText() ?: "")
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
