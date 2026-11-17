package com.huimao.fairway.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huimao.fairway.data.CourseDownload
import com.huimao.fairway.data.CourseSearchResult
import com.huimao.fairway.data.DownloadedCourseStore
import com.huimao.fairway.data.OverpassClient
import com.huimao.fairway.domain.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 球场库：附近球场 / 按名搜索 / 下载到本地。 */
class CourseLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val overpass = OverpassClient()
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

    /** 附近 50 公里内的球场（需要定位）。 */
    fun searchNearby(location: LatLng?) {
        if (location == null) {
            _message.value = "需要定位才能找附近球场：先在打球页开启定位权限。"
            return
        }
        viewModelScope.launch {
            _searching.value = true
            _message.value = null
            val list = overpass.searchNearby(location.lat, location.lon)
            _results.value = list
            _searching.value = false
            if (list.isEmpty()) _message.value = "附近 50 公里内没找到球场。"
        }
    }

    /** 按名搜索（Nominatim）。 */
    fun searchByName() {
        val q = _query.value.trim()
        if (q.isEmpty()) {
            _message.value = "先输入球场名。"
            return
        }
        viewModelScope.launch {
            _searching.value = true
            _message.value = null
            val list = overpass.searchByName(q)
            _results.value = list
            _searching.value = false
            if (list.isEmpty()) _message.value = "没搜到叫「$q」的球场，换个名字试试。"
        }
    }

    /**
     * 下载一个球场并存到本地。成功返回文件名（调用方刷新球场列表）；
     * 失败返回 null，原因写在 message 里。
     */
    suspend fun download(result: CourseSearchResult): String? {
        _downloadingId.value = result.osmId
        _message.value = null
        return try {
            when (val r = overpass.downloadCourse(result.osmId)) {
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
        }
    }
}
