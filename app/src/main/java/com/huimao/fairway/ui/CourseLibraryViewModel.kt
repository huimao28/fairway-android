package com.huimao.fairway.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huimao.fairway.data.ChinaCoursePoiRepository
import com.huimao.fairway.data.CourseDownload
import com.huimao.fairway.data.CourseSearchResult
import com.huimao.fairway.data.DownloadedCourseStore
import com.huimao.fairway.data.OverpassClient
import com.huimao.fairway.domain.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 球场库：国内球场离线搜（OSM POI 名单，assets/poi/china-courses.json），
 * 名单里没有的自动走在线搜索（Nominatim / Overpass，国外球场用）；下载到本地。
 */
class CourseLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val overpass = OverpassClient()
    private val poiRepo = ChinaCoursePoiRepository(application)
    private val store = DownloadedCourseStore(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _results = MutableStateFlow<List<CourseSearchResult>>(emptyList())
    val results: StateFlow<List<CourseSearchResult>> = _results.asStateFlow()

    /** 正在下载的球场 osmId（null = 没有在下载的）。 */
    private val _downloadingId = MutableStateFlow<Long?>(null)
    val downloadingId: StateFlow<Long?> = _downloadingId.asStateFlow()

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

    /** 附近 50 公里内的球场：先查离线 POI 名单，没有再在线找（Overpass）。 */
    fun searchNearby(location: LatLng?) {
        if (location == null) {
            _message.value = "需要定位才能找附近球场：先在打球页开启定位权限。"
            return
        }
        viewModelScope.launch {
            _searching.value = true
            _message.value = null
            val offline = poiRepo.nearby(location.lat, location.lon)
                .map { it.toSearchResult() }
            if (offline.isNotEmpty()) {
                _results.value = offline
                _searching.value = false
                return@launch
            }
            val online = overpass.searchNearby(location.lat, location.lon)
            _results.value = online
            _searching.value = false
            if (online.isEmpty()) {
                _message.value = "附近 50 公里内没找到球场。"
            } else {
                _message.value = "附近的国内名单里没有，在线搜到 ${online.size} 个球场。"
            }
        }
    }

    /** 按名搜索：先查离线 POI 名单，没有再在线找（Nominatim）。 */
    fun searchByName() {
        val q = _query.value.trim()
        if (q.isEmpty()) {
            _message.value = "先输入球场名。"
            return
        }
        viewModelScope.launch {
            _searching.value = true
            _message.value = null
            val offline = poiRepo.search(q).map { it.toSearchResult() }
            if (offline.isNotEmpty()) {
                _results.value = offline
                _searching.value = false
                return@launch
            }
            val online = overpass.searchByName(q)
            _results.value = online
            _searching.value = false
            if (online.isEmpty()) {
                _message.value = "没搜到叫「$q」的球场，换个名字试试。"
            } else {
                _message.value = "国内名单里没有，在线搜到 ${online.size} 个结果。"
            }
        }
    }

    /** POI 转搜索结果：有已核实的中文别名时优先显示中文名；城市/国家一并带上。 */
    private fun com.huimao.fairway.data.CoursePoi.toSearchResult(): CourseSearchResult =
        CourseSearchResult(osmId, aliases.firstOrNull() ?: name, lat, lon, city, country)

    /**
     * 下载一个球场并存到本地。成功返回文件名（调用方刷新球场列表）；
     * 失败返回 null，原因写在 message 里。
     */
    suspend fun download(result: CourseSearchResult): String? {
        _downloadingId.value = result.osmId
        _downloadProgress.value = 0f
        _downloadStage.value = "正在拉取球场轮廓…"
        _message.value = null
        return try {
            when (val r = overpass.downloadCourse(result.osmId) { progress, stage ->
                _downloadProgress.value = progress
                _downloadStage.value = stage
            }) {
                is CourseDownload.Ok -> {
                    val fileName = store.save(r.pack)
                    _message.value = "已下载「${r.pack.course.name}」，去打球页就能用。"
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
}
