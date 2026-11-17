package com.huimao.fairway.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huimao.fairway.data.PoseAnalyzer
import com.huimao.fairway.data.SwingSession
import com.huimao.fairway.data.SwingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 挥杆视频分析：ML Kit 姿态检测（端上免费离线），结果存本机 files/swings/。 */
class SwingViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SwingStore(application)
    private val analyzer = PoseAnalyzer(application)

    private val _sessions = MutableStateFlow<List<SwingSession>>(emptyList())
    val sessions: StateFlow<List<SwingSession>> = _sessions.asStateFlow()

    private val _selectedSession = MutableStateFlow<SwingSession?>(null)
    val selectedSession: StateFlow<SwingSession?> = _selectedSession.asStateFlow()

    /** 选中 session 的原视频是否还能打开（打不开时只看指标与关键帧，不播视频）。 */
    private val _selectedVideoAvailable = MutableStateFlow(true)
    val selectedVideoAvailable: StateFlow<Boolean> = _selectedVideoAvailable.asStateFlow()

    /** 分析进度 0..1（null = 没有在分析的），只在 5% 档位或阶段切换时更新。 */
    private val _analysisProgress = MutableStateFlow<Float?>(null)
    val analysisProgress: StateFlow<Float?> = _analysisProgress.asStateFlow()

    /** 分析当前阶段文案（null = 没有在分析的）。 */
    private val _analysisStage = MutableStateFlow<String?>(null)
    val analysisStage: StateFlow<String?> = _analysisStage.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshSessions()
    }

    fun refreshSessions() {
        viewModelScope.launch {
            _sessions.value = store.listSessions()
        }
    }

    fun setMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** 分析一个视频（相册选的或刚录的）：先拿持久读权限，拿不到也不中断（用当次授权继续）。 */
    fun analyzeVideo(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModelScope.launch {
            _analysisProgress.value = 0f
            _analysisStage.value = "正在读取视频…"
            _message.value = null
            var lastBucket = -1
            var lastStage = ""
            try {
                val session = analyzer.analyze(uri) { processed, total, stage ->
                    val bucket = if (total > 0) processed * 20 / total else 0
                    if (stage != lastStage || bucket != lastBucket) {
                        lastStage = stage
                        lastBucket = bucket
                        _analysisStage.value = stage
                        _analysisProgress.value = if (total > 0) processed.toFloat() / total else 0f
                    }
                }
                if (session == null) {
                    _message.value = "分析失败：视频读不出来，或整段都没拍到人。换个光线好、全身入镜的视频再试。"
                } else {
                    store.save(session)
                    _sessions.value = store.listSessions()
                    _selectedSession.value = session
                    _selectedVideoAvailable.value = true
                    val moments = session.keyMoments
                    val found = listOfNotNull(
                        moments.addressMs,
                        moments.topMs,
                        moments.impactMs,
                        moments.finishMs,
                    ).size
                    _message.value = if (moments.topMs == null || moments.impactMs == null) {
                        "分析完成，但没找全关键时刻（只找到 $found/4 个），角度与节奏可能不准。"
                    } else {
                        "分析完成。"
                    }
                }
            } catch (_: Exception) {
                _message.value = "分析失败：视频读不出来，换个视频再试。"
            } finally {
                _analysisProgress.value = null
                _analysisStage.value = null
            }
        }
    }

    fun selectSession(id: String) {
        viewModelScope.launch {
            val session = store.load(id)
            _selectedSession.value = session
            _selectedVideoAvailable.value = session?.let { isVideoAvailable(it.videoUri) } ?: false
        }
    }

    fun clearSelection() {
        _selectedSession.value = null
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            store.delete(id)
            if (_selectedSession.value?.id == id) _selectedSession.value = null
            _sessions.value = store.listSessions()
        }
    }

    private suspend fun isVideoAvailable(videoUri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver
                .openInputStream(Uri.parse(videoUri))?.close()
            true
        }.getOrDefault(false)
    }
}
