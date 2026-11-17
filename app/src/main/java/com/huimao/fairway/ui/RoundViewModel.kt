package com.huimao.fairway.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huimao.fairway.data.CourseRepository
import com.huimao.fairway.data.HoleScore
import com.huimao.fairway.data.RoundRecord
import com.huimao.fairway.data.RoundStore
import com.huimao.fairway.domain.Course
import com.huimao.fairway.domain.LatLng
import com.huimao.fairway.domain.toCourse
import com.huimao.fairway.location.LocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 打球页 / 记分卡 / 历史三页共享的状态。定位流用 StateFlow，UI 层 collectAsStateWithLifecycle。 */
class RoundViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)
    private val roundStore = RoundStore(application)
    private val tracker = LocationTracker(application)

    val location: StateFlow<LatLng?> = tracker.location

    val rounds: StateFlow<List<RoundRecord>> = roundStore.rounds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _packNames = MutableStateFlow<List<String>>(emptyList())
    val packNames: StateFlow<List<String>> = _packNames.asStateFlow()

    private val _selectedPack = MutableStateFlow<String?>(null)
    val selectedPack: StateFlow<String?> = _selectedPack.asStateFlow()

    private val _course = MutableStateFlow<Course?>(null)
    val course: StateFlow<Course?> = _course.asStateFlow()

    private val _currentHole = MutableStateFlow(1)
    val currentHole: StateFlow<Int> = _currentHole.asStateFlow()

    private val _scores = MutableStateFlow<Map<Int, HoleScore>>(emptyMap())
    val scores: StateFlow<Map<Int, HoleScore>> = _scores.asStateFlow()

    private val _shotOrigin = MutableStateFlow<LatLng?>(null)
    val shotOrigin: StateFlow<LatLng?> = _shotOrigin.asStateFlow()

    private val _savedMessage = MutableStateFlow<String?>(null)
    val savedMessage: StateFlow<String?> = _savedMessage.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val packs = repository.listPacks()
                _packNames.value = packs
                packs.firstOrNull()?.let { selectPack(it) }
            } catch (e: Exception) {
                // 数据加载失败只显示错误，不让未捕获异常闪退
                _loadError.value = "球场数据加载失败：${e.message}"
            }
        }
    }

    fun selectPack(fileName: String) {
        viewModelScope.launch {
            try {
                val pack = repository.loadPack(fileName)
                _selectedPack.value = fileName
                _course.value = pack.toCourse()
                _currentHole.value = 1
                _scores.value = emptyMap()
                _shotOrigin.value = null
                _loadError.value = null
            } catch (e: Exception) {
                _loadError.value = "球场数据加载失败：${e.message}"
            }
        }
    }

    fun setCurrentHole(n: Int) {
        _currentHole.value = n.coerceIn(1, 18)
    }

    fun nextHole() = setCurrentHole(_currentHole.value + 1)

    fun prevHole() = setCurrentHole(_currentHole.value - 1)

    fun updateScore(hole: Int, transform: (HoleScore) -> HoleScore) {
        val current = _scores.value[hole] ?: HoleScore(hole = hole)
        _scores.value = _scores.value + (hole to transform(current))
    }

    /** 在当前位置标记击球点（上一杆距离从这里算起）。 */
    fun markShot() {
        _shotOrigin.value = location.value
    }

    fun startTracking() = tracker.start()

    fun stopTracking() = tracker.stop()

    override fun onCleared() {
        tracker.stop()
        super.onCleared()
    }

    /** 保存本轮成绩（至少打了一个洞才存）。 */
    fun saveRound() {
        val course = _course.value ?: return
        if (_scores.value.values.none { it.strokes > 0 }) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            roundStore.saveRound(
                RoundRecord(
                    id = now,
                    courseName = course.name,
                    dateEpochMs = now,
                    scores = _scores.value.values.sortedBy { it.hole },
                ),
            )
            _savedMessage.value = "已保存本轮成绩"
        }
    }

    fun consumeSavedMessage() {
        _savedMessage.value = null
    }
}
