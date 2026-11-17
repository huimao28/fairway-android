package com.huimao.fairway.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huimao.fairway.data.ChinaCoursePoiRepository
import com.huimao.fairway.data.CourseDownload
import com.huimao.fairway.data.CoursePack
import com.huimao.fairway.data.CourseSearchResult
import com.huimao.fairway.data.DownloadedCourseStore
import com.huimao.fairway.data.GolfCoreClient
import com.huimao.fairway.data.GolfCoreCourse
import com.huimao.fairway.data.GolfCoreSearch
import com.huimao.fairway.data.OverpassClient
import com.huimao.fairway.data.SettingsStore
import com.huimao.fairway.data.countryDisplay
import com.huimao.fairway.data.mapTeeRatings
import com.huimao.fairway.domain.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 球场库：按名/按附近都先查 GolfCore（国内外都优先），没有再走离线 POI 名单
 * （国内 362 球场），最后 Nominatim / Overpass 兜底；下载到本地。
 * GolfCore 搜到的球场下载时按坐标找最近的 OSM way 桥接，下载完再按
 * GolfCore 的发球台 rating/slope 补全（WHS 用）。
 */
class CourseLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val overpass = OverpassClient()
    private val golfCore = GolfCoreClient()
    private val poiRepo = ChinaCoursePoiRepository(application)
    private val store = DownloadedCourseStore(application)
    private val settingsStore = SettingsStore(application)

    /** 用户填的 GolfCore key（null = 未填；2026-12-04 前免 key 也能用）。 */
    val golfCoreKey: StateFlow<String?> = settingsStore.golfCoreKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _results = MutableStateFlow<List<CourseSearchResult>>(emptyList())
    val results: StateFlow<List<CourseSearchResult>> = _results.asStateFlow()

    /** 正在下载的球场 key（null = 没有在下载的；见 CourseSearchResult.resultKey）。 */
    private val _downloadingId = MutableStateFlow<String?>(null)
    val downloadingId: StateFlow<String?> = _downloadingId.asStateFlow()

    /** 下载进度 0..1（null = 没有在下载的），只在阶段切换时更新。 */
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    /** 下载当前阶段文案（null = 没有在下载的）。 */
    private val _downloadStage = MutableStateFlow<String?>(null)
    val downloadStage: StateFlow<String?> = _downloadStage.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** 保存用户自己的 GolfCore key（存在本机，不进仓库）。 */
    fun saveGolfCoreKey(key: String) {
        viewModelScope.launch {
            settingsStore.saveGolfCoreKey(key)
            _message.value = "GolfCore key 已保存。"
        }
    }

    fun clearGolfCoreKey() {
        viewModelScope.launch {
            settingsStore.clearGolfCoreKey()
            _message.value = "GolfCore key 已清除，改用免 key 访问（12 月 4 日前可用）。"
        }
    }

    /** 附近 50 公里内的球场：GolfCore -> 离线 POI -> Overpass。 */
    fun searchNearby(location: LatLng?) {
        if (location == null) {
            _message.value = "需要定位才能找附近球场：先在打球页开启定位权限。"
            return
        }
        viewModelScope.launch {
            _searching.value = true
            _message.value = null
            var rateLimited = false
            when (val gc = golfCore.searchNearby(location.lat, location.lon, apiKey = golfCoreKey.value)) {
                is GolfCoreSearch.Ok -> if (gc.courses.isNotEmpty()) {
                    _results.value = gc.courses.map { it.toSearchResult() }
                    _message.value = "GolfCore 搜到 ${gc.courses.size} 个附近球场。"
                    _searching.value = false
                    return@launch
                }
                GolfCoreSearch.RateLimited -> rateLimited = true
                GolfCoreSearch.NetworkError -> { /* 没网就静默走离线兜底 */ }
            }
            val offline = poiRepo.nearby(location.lat, location.lon)
                .map { it.toSearchResult() }
            if (offline.isNotEmpty()) {
                _results.value = offline
                _message.value = if (rateLimited) {
                    "GolfCore 免费额度用完了，离线名单里有 ${offline.size} 个附近球场。"
                } else {
                    "GolfCore 附近没搜到，离线名单里有 ${offline.size} 个。"
                }
                _searching.value = false
                return@launch
            }
            val online = overpass.searchNearby(location.lat, location.lon)
            _results.value = online
            _searching.value = false
            if (online.isEmpty()) {
                _message.value = if (rateLimited) {
                    "GolfCore 免费额度用完了，离线名单和 Overpass 附近都没找到球场。"
                } else {
                    "附近 50 公里内没找到球场。"
                }
            } else {
                _message.value = "离线名单里也没有，Overpass 搜到 ${online.size} 个附近球场。"
            }
        }
    }

    /** 按名搜索：GolfCore -> 离线 POI -> Nominatim。 */
    fun searchByName() {
        val q = _query.value.trim()
        if (q.isEmpty()) {
            _message.value = "先输入球场名。"
            return
        }
        viewModelScope.launch {
            _searching.value = true
            _message.value = null
            var rateLimited = false
            when (val gc = golfCore.searchByName(q, golfCoreKey.value)) {
                is GolfCoreSearch.Ok -> if (gc.courses.isNotEmpty()) {
                    _results.value = gc.courses.map { it.toSearchResult() }
                    _message.value = "GolfCore 搜到 ${gc.courses.size} 个结果。"
                    _searching.value = false
                    return@launch
                }
                GolfCoreSearch.RateLimited -> rateLimited = true
                GolfCoreSearch.NetworkError -> { /* 没网就静默走离线兜底 */ }
            }
            val offline = poiRepo.search(q).map { it.toSearchResult() }
            if (offline.isNotEmpty()) {
                _results.value = offline
                _message.value = if (rateLimited) {
                    "GolfCore 免费额度用完了，离线名单里有 ${offline.size} 个。"
                } else {
                    "GolfCore 没搜到，离线名单里有 ${offline.size} 个。"
                }
                _searching.value = false
                return@launch
            }
            val online = overpass.searchByName(q)
            _results.value = online
            _searching.value = false
            if (online.isEmpty()) {
                _message.value = if (rateLimited) {
                    "GolfCore 免费额度用完了，离线名单和 Nominatim 也没搜到「$q」。"
                } else {
                    "没搜到叫「$q」的球场，换个名字试试。"
                }
            } else {
                _message.value = "离线名单里也没有，Nominatim 搜到 ${online.size} 个结果。"
            }
        }
    }

    /** GolfCore 球场转搜索结果：有中文原名优先显示中文名；国家码转中文国家名。 */
    private fun GolfCoreCourse.toSearchResult(): CourseSearchResult =
        CourseSearchResult(
            osmId = 0,
            name = native_name?.takeIf { it.isNotBlank() } ?: name,
            lat = lat,
            lon = lng,
            city = city,
            country = countryDisplay(country),
            golfCoreSlug = slug,
            golfCoreUrl = url.takeIf { it.isNotBlank() },
        )

    /** POI 转搜索结果：有已核实的中文别名时优先显示中文名；城市/国家一并带上。 */
    private fun com.huimao.fairway.data.CoursePoi.toSearchResult(): CourseSearchResult =
        CourseSearchResult(osmId, aliases.firstOrNull() ?: name, lat, lon, city, country)

    /**
     * 下载一个球场并存到本地。成功返回文件名（调用方刷新球场列表）；
     * 失败返回 null，原因写在 message 里。
     * GolfCore 搜到的球场没有 OSM id：先按坐标找最近的 OSM way 再下载。
     */
    suspend fun download(result: CourseSearchResult): String? {
        _downloadingId.value = result.resultKey()
        _downloadProgress.value = 0f
        _downloadStage.value = "正在拉取球场轮廓…"
        _message.value = null
        return try {
            val osmId: Long
            if (result.osmId != 0L) {
                osmId = result.osmId
            } else {
                _downloadProgress.value = 0.02f
                _downloadStage.value = "正在找 OSM 上的球场…"
                val resolved = overpass.resolveCourseWay(result.lat, result.lon)
                if (resolved == null) {
                    _message.value = "「${result.name}」在 OSM 上找不到对应的球场（可能没联网，或 OSM 上没有），下载不了。"
                    return null
                }
                osmId = resolved
                // 让该行在下载后显示“已下载”（GolfCore 结果本来没有 osmId）
                _results.value = _results.value.map {
                    if (it.resultKey() == result.resultKey()) it.copy(osmId = osmId) else it
                }
            }
            when (val r = overpass.downloadCourse(osmId) { progress, stage ->
                _downloadProgress.value = progress
                _downloadStage.value = stage
            }) {
                is CourseDownload.Ok -> {
                    _downloadProgress.value = 0.9f
                    _downloadStage.value = "正在补 rating/slope…"
                    val enriched = enrichRatings(r.pack, result.golfCoreSlug)
                    val fileName = store.save(enriched)
                    _message.value = if (enriched.course.ratings.isNotEmpty() &&
                        r.pack.course.ratings.isEmpty()
                    ) {
                        "已下载「${enriched.course.name}」，已补 rating/slope（GolfCore），去打球页就能用。"
                    } else {
                        "已下载「${enriched.course.name}」，去打球页就能用。"
                    }
                    fileName
                }
                CourseDownload.NetworkError -> {
                    _message.value = "下载失败：连不上 Overpass，检查网络后重试。"
                    null
                }
                CourseDownload.NoHoleData -> {
                    _message.value = "「${result.name}」在 OSM 上没有洞级数据，打不了。"
                    null
                }
                CourseDownload.Not18Holes -> {
                    _message.value = "「${result.name}」不是标准 18 洞，暂不支持。"
                    null
                }
            }
        } finally {
            _downloadingId.value = null
            _downloadProgress.value = null
            _downloadStage.value = null
        }
    }

    /**
     * 下载完补 rating/slope：有 slug 直接取详情，没有就按坐标找最近的 GolfCore 球场。
     * GolfCore 没有这家球场、或没有 rating 数据时原样返回（WHS 照旧不显示）。
     */
    private suspend fun enrichRatings(pack: CoursePack, slugHint: String?): CoursePack {
        if (pack.course.ratings.isNotEmpty()) return pack
        val key = golfCoreKey.value
        val slug = slugHint
            ?: golfCore.findNearest(pack.course.lat, pack.course.lon, key)?.slug
            ?: return pack
        val ratings = golfCore.getCourse(slug, key)?.let(::mapTeeRatings).orEmpty()
        if (ratings.isEmpty()) return pack
        return pack.copy(
            course = pack.course.copy(
                ratings = ratings,
                source = pack.course.source + "；rating/slope: GolfCore",
            ),
        )
    }
}
