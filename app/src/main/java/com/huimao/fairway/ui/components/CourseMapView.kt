package com.huimao.fairway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.huimao.fairway.domain.Hole
import com.huimao.fairway.domain.LatLng
import kotlin.math.cos

private fun teeColor(color: String): Color = when (color) {
    "black" -> Color.Black
    "blue" -> Color(0xFF1565C0)
    "white" -> Color.White
    "red" -> Color(0xFFE53935)
    else -> Color.Gray
}

/**
 * 本洞示意图（Canvas 自绘，不接地图 SDK）。
 * 静态路径（洞线、果岭、Tee、沙坑、水障）缓存在 drawWithCache 里，只在尺寸变化时重建；
 * 玩家位置用 lambda 在 Draw 阶段读，不触发重组（GPS 高频更新也不重组整页）。
 */
@Composable
fun CourseMapView(
    hole: Hole,
    bunkers: List<LatLng>,
    waterHazards: List<LatLng>,
    locationProvider: () -> LatLng?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFE8F5E9))
            .drawWithCache {
                val allPoints = hole.poly +
                    (hole.green?.poly.orEmpty()) +
                    hole.tees.values + bunkers + waterHazards
                if (allPoints.isEmpty()) {
                    onDrawBehind { }
                } else {
                    val minLat = allPoints.minOf { it.lat }
                    val maxLat = allPoints.maxOf { it.lat }
                    val minLon = allPoints.minOf { it.lon }
                    val maxLon = allPoints.maxOf { it.lon }
                    val meanLat = Math.toRadians((minLat + maxLat) / 2)
                    val earthR = 6_371_000.0
                    fun toMeters(p: LatLng): Pair<Double, Double> {
                        val x = Math.toRadians(p.lon - minLon) * cos(meanLat) * earthR
                        val y = Math.toRadians(maxLat - p.lat) * earthR
                        return x to y
                    }
                    val spanX = Math.toRadians(maxLon - minLon) * cos(meanLat) * earthR
                    val spanY = Math.toRadians(maxLat - minLat) * earthR
                    val pad = 48f
                    val scale = minOf(
                        (size.width - pad * 2) / spanX.toFloat().coerceAtLeast(1f),
                        (size.height - pad * 2) / spanY.toFloat().coerceAtLeast(1f),
                    )
                    val offsetX = (size.width - spanX.toFloat() * scale) / 2
                    val offsetY = (size.height - spanY.toFloat() * scale) / 2
                    fun toOffset(p: LatLng): Offset {
                        val (x, y) = toMeters(p)
                        return Offset(offsetX + x.toFloat() * scale, offsetY + y.toFloat() * scale)
                    }

                    val holePath = Path().apply {
                        hole.poly.forEachIndexed { i, p ->
                            val o = toOffset(p)
                            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                        }
                    }
                    val greenPath = Path().apply {
                        hole.green?.poly?.forEachIndexed { i, p ->
                            val o = toOffset(p)
                            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                        }
                        close()
                    }
                    val teeOffsets = hole.tees.map { (color, p) -> color to toOffset(p) }
                    val bunkerOffsets = bunkers.map { toOffset(it) }
                    val waterOffsets = waterHazards.map { toOffset(it) }

                    onDrawBehind {
                        if (hole.green != null && hole.green.poly.isNotEmpty()) {
                            drawPath(greenPath, Color(0xFF81C784))
                        } else if (hole.green != null) {
                            // 缺果岭兜底：只画中心点
                            drawCircle(Color(0xFF81C784), radius = 18f, center = toOffset(hole.green.center))
                        }
                        drawPath(
                            holePath,
                            Color(0xFF2E7D32),
                            style = Stroke(width = 10f, cap = StrokeCap.Round),
                        )
                        teeOffsets.forEach { (color, o) ->
                            drawCircle(teeColor(color), radius = 13f, center = o)
                            drawCircle(Color.White, radius = 13f, center = o, style = Stroke(width = 3f))
                        }
                        bunkerOffsets.forEach { drawCircle(Color(0xFFFFD54F), radius = 10f, center = it) }
                        waterOffsets.forEach { drawCircle(Color(0xFF4FC3F7), radius = 10f, center = it) }
                        // 玩家位置：Draw 阶段读 lambda，不触发重组
                        val player = locationProvider()
                        if (player != null) {
                            val o = toOffset(player)
                            drawCircle(Color.White, radius = 22f, center = o)
                            drawCircle(Color(0xFF1565C0), radius = 15f, center = o)
                        }
                    }
                }
            },
    )
}
