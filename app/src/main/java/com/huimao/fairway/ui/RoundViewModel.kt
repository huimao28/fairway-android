package com.huimao.fairway.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huimao.fairway.data.CourseRepository
import com.huimao.fairway.data.HoleScore
import com.huimao.fairway.data.HoleShot
import com.huimao.fairway.data.RoundRecord
import com.huimao.fairway.data.RoundStore
import com.huimao.fairway.data.TeeRating
import com.huimao.fairway.data.WeatherNow
import com.huimao.fairway.data.WeatherRepository
import com.huimao.fairway.domain.Course
import com.huimao.fairway.domain.Hole
import com.huimao.fairway.domain.LatLng
import com.huimao.fairway.domain.Whs
import com.huimao.fairway.domain.toCourse
import com.huimao.fairway.location.LocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 球场包条目：文件名 + 球场名（多球场切换用）。 */
data class PackEntry(val fileName: String, val courseName: String)

/** 发球台颜色中文名。 */
fun teeName(color: String): String = when (color) {
    "black" -> "黑"
    "blue" -> "蓝"
    "white" -> "白"
    "red" -> "红"
    else -> color
}

/** 打球页 / 记分卡 / 历史三页共享的状态。定位流用 StateFlow，UI 层 collectAsStateWithLifecycle。 */
class RoundViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)
    private val roundStore = RoundStore(application)
    private val tracker = LocationTracker(application)
    private val weatherRepository = WeatherRepository()

    val location: StateFlow<LatLng?> = tracker.location

    val rounds: StateFlow<List<RoundRecord>> = roundStore.rounds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _packs = MutableStateFlow<List<PackEntry>>(emptyList())
    val packs: StateFlow<List<PackEntry>> = _packs.asStateFlow()

    private val _selectedPack = MutableStateFlow<String?>(null)
    val selectedPack: StateFlow<String?> = _selectedPack.asStateFlow()

    private val _course = MutableStateFlow<Course?>(null)
    val course: StateFlow<Course?> = _course.asStateFlow()

    private val _selectedTee = MutableStateFlow("white")
    val selectedTee: StateFlow<String> = _selectedTee.asStateFlow()

    private val _currentHole = MutableStateFlow(1)
    val currentHole: StateFlow<Int> = _currentHole.asStateFlow()

    private val _scores = MutableStateFlow<Map<Int, HoleScore>>(emptyMap())
    val scores: StateFlow<Map<Int, HoleScore>> = _scores.asStateFlow()

    /** 击球轨迹：洞号 -> 该洞的击球点（按顺序）。 */
    private val _shots = MutableStateFlow<Map<Int, List<LatLng>>>(emptyMap())
    val shots: StateFlow<Map<Int, List<LatLng>>> = _shots.asStateFlow()

    private val _shotOrigin = MutableStateFlow<LatLng?>(null)
    val shotOrigin: StateFlow<LatLng?> = _shotOrigin.asStateFlow()

    private val _weather = MutableStateFlow<WeatherNow?>(null)
    val weather: StateFlow<WeatherNow?> = _weather.asStateFlow()

    private val _savedMessage = MutableStateFlow<String?>(null)
    val savedMessage: StateFlow<String?> = _savedMessage.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val files = repository.listPacks()
                _packs.value = files.map { file ->
                    val pack = repository.loadPack(file)
                    PackEntry(file, pack.course.name)
                }
                files.firstOrNull()?.let { selectPack(it) }
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
                _shots.value = emptyMap()
                _shotOrigin.value = null
                _loadError.value = null
                // 默认发球台：白 T 有则用白 T，否则用该球场第一个
                val tees = pack.toCourse().holes
                    .flatMap { it.tees.keys }.distinct()
                    .sortedBy { listOf("black", "blue", "white", "red").indexOf(it) }
                _selectedTee.value = if ("white" in tees) "white" else tees.firstOrNull() ?: "white"
                // 天气（失败就留空，UI 不显示天气卡）
                _weather.value = weatherRepository.load(pack.course.lat, pack.course.lon)
            } catch (e: Exception) {
                _loadError.value = "球场数据加载失败：${e.message}"
            }
        }
    }

    fun setSelectedTee(color: String) {
        _selectedTee.value = color
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

    /** 在当前位置标记击球点：追加到本洞轨迹，上一杆距离从这里算起。 */
    fun markShot() {
        val loc = location.value ?: return
        val hole = _currentHole.value
        _shots.value = _shots.value + (hole to (_shots.value[hole].orEmpty() + loc))
        _shotOrigin.value = loc
    }

    /** 撤销本洞最后一个击球点。 */
    fun undoShot() {
        val hole = _currentHole.value
        val list = _shots.value[hole].orEmpty()
        if (list.isEmpty()) return
        val updated = list.dropLast(1)
        _shots.value = if (updated.isEmpty()) _shots.value - hole
        else _shots.value + (hole to updated)
        _shotOrigin.value = updated.lastOrNull()
    }

    fun startTracking() = tracker.start()

    fun stopTracking() = tracker.stop()

    override fun onCleared() {
        tracker.stop()
        super.onCleared()
    }

    /** 按时间升序的已存微分（WHS 指数用）。 */
    private fun differentialsOldestFirst(excludeId: Long? = null): List<Double> =
        rounds.value
            .filter { it.id != excludeId }
            .sortedBy { it.dateEpochMs }
            .mapNotNull { it.differential }

    /**
     * 算本轮的 WHS 微分：需打满 18 洞且该发球台有 rating/slope。
     * 用保存时的差点指数算球场差点（净双柏忌调整用）。
     */
    private fun computeDifferential(
        scores: List<HoleScore>,
        holes: List<Hole>,
        tee: String?,
        rating: TeeRating?,
        par: Int,
        excludeId: Long? = null,
    ): Double? {
        if (rating == null) return null
        if (scores.count { it.strokes > 0 } < 18) return null
        val index = Whs.handicapIndex(differentialsOldestFirst(excludeId))
        val ch = if (index != null) Whs.courseHandicap(index, rating.slope, rating.rating, par) else 0
        val adjusted = Whs.adjustedGross(scores, holes, ch)
        return Whs.differential(adjusted, rating.rating, rating.slope)
    }

    /** 保存本轮成绩（至少打了一个洞才存）。 */
    fun saveRound() {
        val course = _course.value ?: return
        if (_scores.value.values.none { it.strokes > 0 }) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val tee = _selectedTee.value
            val rating = course.ratings[tee]
            val shotList = _shots.value.flatMap { (hole, pts) ->
                pts.map { HoleShot(hole, it.lat, it.lon) }
            }
            roundStore.saveRound(
                RoundRecord(
                    id = now,
                    courseName = course.name,
                    dateEpochMs = now,
                    scores = _scores.value.values.sortedBy { it.hole },
                    tee = tee,
                    rating = rating?.rating,
                    slope = rating?.slope,
                    differential = computeDifferential(
                        _scores.value.values.toList(),
                        course.holes,
                        tee,
                        rating,
                        course.totalPar,
                    ),
                    shots = shotList,
                ),
            )
            _savedMessage.value = "已保存本轮成绩"
        }
    }

    fun consumeSavedMessage() {
        _savedMessage.value = null
    }

    /** 修改一条已保存的记录（同球场时重算微分）。 */
    fun updateRound(record: RoundRecord) {
        viewModelScope.launch {
            val course = _course.value
            val updated = if (course != null && record.courseName == course.name) {
                val rating = record.tee?.let { course.ratings[it] }
                record.copy(
                    differential = computeDifferential(
                        record.scores,
                        course.holes,
                        record.tee,
                        rating,
                        course.totalPar,
                        excludeId = record.id,
                    ),
                )
            } else {
                record
            }
            roundStore.updateRound(updated)
        }
    }

    /** 删除一条已保存的记录。 */
    fun deleteRound(id: Long) {
        viewModelScope.launch { roundStore.deleteRound(id) }
    }
}
