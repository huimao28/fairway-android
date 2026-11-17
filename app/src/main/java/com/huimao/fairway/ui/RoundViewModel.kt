package com.huimao.fairway.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huimao.fairway.data.CourseRepository
import com.huimao.fairway.data.DownloadedCourseStore
import com.huimao.fairway.data.HoleScore
import com.huimao.fairway.data.HoleShot
import com.huimao.fairway.data.RoundPushState
import com.huimao.fairway.data.RoundRecord
import com.huimao.fairway.data.RoundStore
import com.huimao.fairway.data.TeeRating
import com.huimao.fairway.data.WearRoundSync
import com.huimao.fairway.data.WearSync
import com.huimao.fairway.data.WeatherNow
import com.huimao.fairway.data.WeatherRepository
import com.huimao.fairway.data.WorkoutSummary
import com.huimao.fairway.domain.Course
import com.huimao.fairway.domain.Geo
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
import kotlin.math.roundToInt

/** 球场包条目：文件名 + 球场名 + 是否下载的（多球场切换用）。 */
data class PackEntry(val fileName: String, val courseName: String, val isDownloaded: Boolean = false)

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
    private val wearSync = WearRoundSync(application)

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

    /** 手表是否连接（打球页显示“手表已连接/未连接”）。 */
    private val _watchConnected = MutableStateFlow(false)
    val watchConnected: StateFlow<Boolean> = _watchConnected.asStateFlow()

    /** 手表发来的运动汇总（本轮还没保存，先暂存，保存时写进 RoundRecord）。 */
    private val _pendingWorkout = MutableStateFlow<WorkoutSummary?>(null)

    /** 手表推送节流：位置更新最多 5 秒推一次，切洞/改杆立即推。 */
    private var lastWearPushMs = 0L

    init {
        viewModelScope.launch {
            try {
                val refs = repository.listPacks()
                _packs.value = refs.map { ref ->
                    val pack = repository.loadPack(ref.fileName, ref.isDownloaded)
                    PackEntry(ref.fileName, pack.course.name, ref.isDownloaded)
                }
                refs.firstOrNull()?.let { selectPack(it.fileName) }
            } catch (e: Exception) {
                // 数据加载失败只显示错误，不让未捕获异常闪退
                _loadError.value = "球场数据加载失败：${e.message}"
            }
        }
        // 手表发来的记分：写回当轮成绩（putts = -1 表示手表没改，保留原值）
        viewModelScope.launch {
            WearSync.scoreUpdates.collect { u ->
                updateScore(u.hole) {
                    it.copy(strokes = u.strokes, putts = if (u.putts >= 0) u.putts else it.putts)
                }
            }
        }
        // 手表发来的切洞请求
        viewModelScope.launch {
            WearSync.holeRequests.collect { setCurrentHole(it) }
        }
        // 手表发来的运动汇总：先暂存，保存本轮时写进成绩
        viewModelScope.launch {
            WearSync.workoutSummaries.collect { _pendingWorkout.value = it }
        }
        // 位置变化时推手表（节流 5 秒；切洞/改杆走立即推）
        viewModelScope.launch {
            location.collect { pushRoundToWatch() }
        }
    }

    /** 查手表是否连接（回到前台时调一次）。 */
    fun refreshWatchConnection() {
        viewModelScope.launch {
            _watchConnected.value = wearSync.isWatchConnected()
        }
    }

    /** 把当前洞状态推给手表（距离是手机算好的；没球场/没定位时距离记 -1）。 */
    private fun pushRoundToWatch(force: Boolean = false) {
        val course = _course.value ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastWearPushMs < 5_000) return
        lastWearPushMs = now
        val loc = location.value
        val hole = course.holes.find { it.ref == _currentHole.value }
        val (front, mid, back) = if (loc != null && hole?.green != null) {
            Geo.greenDistances(loc, hole.green).let {
                Triple(it.first.roundToInt(), it.second.roundToInt(), it.third.roundToInt())
            }
        } else {
            Triple(-1, -1, -1)
        }
        val state = RoundPushState(
            courseName = course.name,
            hole = _currentHole.value,
            par = hole?.par ?: 0,
            distFront = front,
            distMid = mid,
            distBack = back,
            strokes = _scores.value[_currentHole.value]?.strokes ?: 0,
        )
        viewModelScope.launch {
            runCatching { wearSync.pushRound(state) }
        }
    }

    /** 下载/删除球场后重新列出全部包（当前选中的包被删了就回退到第一个）。 */
    fun refreshPacks() {
        viewModelScope.launch {
            try {
                val refs = repository.listPacks()
                _packs.value = refs.map { ref ->
                    val pack = repository.loadPack(ref.fileName, ref.isDownloaded)
                    PackEntry(ref.fileName, pack.course.name, ref.isDownloaded)
                }
                if (_selectedPack.value !in refs.map { it.fileName }) {
                    refs.firstOrNull()?.let { selectPack(it.fileName) }
                }
            } catch (e: Exception) {
                _loadError.value = "球场数据加载失败：${e.message}"
            }
        }
    }

    /** 删除一个下载的球场包（内置包不能删）。 */
    fun deletePack(fileName: String) {
        viewModelScope.launch {
            DownloadedCourseStore(getApplication()).delete(fileName)
            refreshPacks()
        }
    }

    fun selectPack(fileName: String) {
        viewModelScope.launch {
            try {
                val ref = _packs.value.find { it.fileName == fileName }
                val pack = repository.loadPack(fileName, ref?.isDownloaded ?: false)
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
                // 切球场后手表立即同步新球场第一洞
                pushRoundToWatch(force = true)
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
        pushRoundToWatch(force = true)
    }

    fun nextHole() = setCurrentHole(_currentHole.value + 1)

    fun prevHole() = setCurrentHole(_currentHole.value - 1)

    fun updateScore(hole: Int, transform: (HoleScore) -> HoleScore) {
        val current = _scores.value[hole] ?: HoleScore(hole = hole)
        _scores.value = _scores.value + (hole to transform(current))
        pushRoundToWatch(force = true)
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
                    workout = _pendingWorkout.value,
                ),
            )
            _pendingWorkout.value = null
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
