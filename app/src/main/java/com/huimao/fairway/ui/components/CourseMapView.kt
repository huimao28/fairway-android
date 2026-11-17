package com.huimao.fairway.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.huimao.fairway.domain.Hole
import com.huimao.fairway.domain.LatLng
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MLGLatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private const val SRC_HOLE = "hole-line-src"
private const val SRC_GREEN = "green-src"
private const val SRC_TEE = "tee-src"
private const val SRC_BUNKER = "bunker-src"
private const val SRC_WATER = "water-src"
private const val SRC_PLAYER = "player-src"
private const val SRC_SHOTS = "shots-src"
private const val SRC_PUTT = "putt-src"

private fun teeColorHex(color: String): String = when (color) {
    "black" -> "#000000"
    "blue" -> "#1565C0"
    "white" -> "#FFFFFF"
    "red" -> "#E53935"
    else -> "#9E9E9E"
}

/**
 * 本洞地图：MapLibre + OSM 栅格底图（assets/map/osm-raster-style.json），
 * 上叠洞线、果岭、Tee、沙坑、水障与玩家位置。无 API Key、无需 Google 服务，
 * 瓦片走 tile.openstreetmap.org，国内可直连。
 */
@Composable
fun CourseMapView(
    hole: Hole,
    bunkers: List<LatLng>,
    waterHazards: List<LatLng>,
    playerLocation: LatLng?,
    shots: List<LatLng> = emptyList(),
    puttLine: Pair<LatLng, LatLng>? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }

    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var destroyed by remember { mutableStateOf(false) }
    fun destroyOnce() {
        if (!destroyed) {
            destroyed = true
            mapView.onDestroy()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> destroyOnce()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            destroyOnce()
        }
    }

    // 洞/障碍变化 → 更新图层数据并移动镜头（样式加载后才生效）
    LaunchedEffect(hole, bunkers, waterHazards, styleLoaded) {
        val m = map ?: return@LaunchedEffect
        if (!styleLoaded) return@LaunchedEffect
        m.getStyle { style ->
            updateHoleLayers(style, hole, bunkers, waterHazards)
            fitHoleCamera(mapView, m, hole, bunkers, waterHazards)
        }
    }

    // 玩家位置变化 → 只更新玩家图层
    LaunchedEffect(playerLocation, styleLoaded) {
        val m = map ?: return@LaunchedEffect
        if (!styleLoaded) return@LaunchedEffect
        m.getStyle { style ->
            val src = style.getSourceAs<GeoJsonSource>(SRC_PLAYER) ?: return@getStyle
            val loc = playerLocation
            // setGeoJson 的重载按具体类型解析：if/else 两分支类型不同会推成公共父类而解析失败，
            // 所以统一包成 FeatureCollection 再传
            src.setGeoJson(
                FeatureCollection.fromFeatures(
                    if (loc == null) emptyList()
                    else listOf(Feature.fromGeometry(Point.fromLngLat(loc.lon, loc.lat))),
                ),
            )
        }
    }

    // 击球轨迹 / 推杆线变化 → 只更新这两个图层
    LaunchedEffect(shots, puttLine, styleLoaded) {
        val m = map ?: return@LaunchedEffect
        if (!styleLoaded) return@LaunchedEffect
        m.getStyle { style ->
            updateShotLayers(style, shots, puttLine)
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { m ->
                    map = m
                    m.setStyle(
                        Style.Builder().fromUri("asset://map/osm-raster-style.json"),
                        object : Style.OnStyleLoaded {
                            override fun onStyleLoaded(style: Style) {
                                addHoleLayers(style)
                                styleLoaded = true
                            }
                        },
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .clip(RoundedCornerShape(24.dp)),
    )
}

private fun addHoleLayers(style: Style) {
    style.addSource(GeoJsonSource(SRC_HOLE))
    style.addSource(GeoJsonSource(SRC_GREEN))
    style.addSource(GeoJsonSource(SRC_TEE))
    style.addSource(GeoJsonSource(SRC_BUNKER))
    style.addSource(GeoJsonSource(SRC_WATER))
    style.addSource(GeoJsonSource(SRC_PLAYER))
    style.addSource(GeoJsonSource(SRC_SHOTS))
    style.addSource(GeoJsonSource(SRC_PUTT))

    // 图层按加入顺序叠放：果岭底 → 洞线 → 沙坑/水障 → Tee → 玩家
    style.addLayer(
        FillLayer("green-fill", SRC_GREEN).withProperties(
            PropertyFactory.fillColor("#81C784"),
            PropertyFactory.fillOpacity(0.85f),
        ),
    )
    // 缺果岭兜底洞：果岭只是个点，画圆
    style.addLayer(
        CircleLayer("green-point", SRC_GREEN).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor("#81C784"),
        ),
    )
    style.addLayer(
        LineLayer("hole-line", SRC_HOLE).withProperties(
            PropertyFactory.lineColor("#2E7D32"),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer("bunkers", SRC_BUNKER).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor("#FFD54F"),
        ),
    )
    style.addLayer(
        CircleLayer("water", SRC_WATER).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor("#4FC3F7"),
        ),
    )
    style.addLayer(
        CircleLayer("tees", SRC_TEE).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
        ),
    )
    // 击球轨迹：蓝线 + 白点（蓝边）
    style.addLayer(
        LineLayer("shot-trace", SRC_SHOTS).withProperties(
            PropertyFactory.lineColor("#1565C0"),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer("shot-points", SRC_SHOTS).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor("#1565C0"),
        ),
    )
    // 推杆线：绿色虚线（球 -> 洞口）
    style.addLayer(
        LineLayer("putt-line", SRC_PUTT).withProperties(
            PropertyFactory.lineColor("#43A047"),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineDasharray(arrayOf(4f, 3f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer("player-halo", SRC_PLAYER).withProperties(
            PropertyFactory.circleRadius(11f),
            PropertyFactory.circleColor("#FFFFFF"),
        ),
    )
    style.addLayer(
        CircleLayer("player-dot", SRC_PLAYER).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor("#1565C0"),
        ),
    )
}

private fun updateHoleLayers(
    style: Style,
    hole: Hole,
    bunkers: List<LatLng>,
    waterHazards: List<LatLng>,
) {
    style.getSourceAs<GeoJsonSource>(SRC_HOLE)?.setGeoJson(
        FeatureCollection.fromFeatures(
            if (hole.poly.size >= 2) {
                listOf(
                    Feature.fromGeometry(
                        LineString.fromLngLats(hole.poly.map { Point.fromLngLat(it.lon, it.lat) }),
                    ),
                )
            } else {
                emptyList()
            },
        ),
    )
    style.getSourceAs<GeoJsonSource>(SRC_GREEN)?.setGeoJson(
        FeatureCollection.fromFeatures(
            when {
                hole.green?.poly?.isNotEmpty() == true -> {
                    val poly = hole.green.poly
                    val ring = poly.map { Point.fromLngLat(it.lon, it.lat) } +
                        Point.fromLngLat(poly.first().lon, poly.first().lat)
                    listOf(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))))
                }
                hole.green != null -> listOf(
                    Feature.fromGeometry(
                        Point.fromLngLat(hole.green.center.lon, hole.green.center.lat),
                    ),
                )
                else -> emptyList()
            },
        ),
    )
    style.getSourceAs<GeoJsonSource>(SRC_TEE)?.setGeoJson(
        FeatureCollection.fromFeatures(
            hole.tees.map { (color, p) ->
                Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                    addStringProperty("color", teeColorHex(color))
                }
            },
        ),
    )
    style.getSourceAs<GeoJsonSource>(SRC_BUNKER)?.setGeoJson(
        FeatureCollection.fromFeatures(
            bunkers.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) },
        ),
    )
    style.getSourceAs<GeoJsonSource>(SRC_WATER)?.setGeoJson(
        FeatureCollection.fromFeatures(
            waterHazards.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) },
        ),
    )
}

/** 击球轨迹与推杆线：同一 source 里混放折线与点，LineLayer 只画线、CircleLayer 只画点。 */
private fun updateShotLayers(
    style: Style,
    shots: List<LatLng>,
    puttLine: Pair<LatLng, LatLng>?,
) {
    style.getSourceAs<GeoJsonSource>(SRC_SHOTS)?.setGeoJson(
        FeatureCollection.fromFeatures(
            buildList {
                if (shots.size >= 2) {
                    add(
                        Feature.fromGeometry(
                            LineString.fromLngLats(shots.map { Point.fromLngLat(it.lon, it.lat) }),
                        ),
                    )
                }
                shots.forEach { p ->
                    add(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)))
                }
            },
        ),
    )
    style.getSourceAs<GeoJsonSource>(SRC_PUTT)?.setGeoJson(
        FeatureCollection.fromFeatures(
            if (puttLine == null) emptyList()
            else listOf(
                Feature.fromGeometry(
                    LineString.fromLngLats(
                        listOf(
                            Point.fromLngLat(puttLine.first.lon, puttLine.first.lat),
                            Point.fromLngLat(puttLine.second.lon, puttLine.second.lat),
                        ),
                    ),
                ),
            ),
        ),
    )
}

private fun fitHoleCamera(
    mapView: MapView,
    map: MapLibreMap,
    hole: Hole,
    bunkers: List<LatLng>,
    waterHazards: List<LatLng>,
) {
    val points = hole.poly + (hole.green?.poly.orEmpty()) + hole.tees.values + bunkers + waterHazards
    if (points.isEmpty()) return
    val bounds = LatLngBounds.Builder().apply {
        points.forEach { include(MLGLatLng(it.lat, it.lon)) }
    }.build()
    // 等布局完成后再算镜头，否则 newLatLngBounds 会因尺寸为 0 报错
    mapView.post {
        runCatching {
            map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 600)
        }
    }
}
