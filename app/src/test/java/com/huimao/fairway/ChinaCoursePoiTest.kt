package com.huimao.fairway

import com.huimao.fairway.data.CoursePoi
import com.huimao.fairway.data.nearbyPois
import com.huimao.fairway.data.poiMatchScore
import com.huimao.fairway.data.searchPois
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线 POI 搜索回归：匹配分、按名搜索、附近搜索，以及打包进应用的
 * china-courses.json 资产本身（test resources 里是同一份文件的拷贝，刷新 POI 时两处一起换）。
 */
class ChinaCoursePoiTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val taohuayuan = CoursePoi(
        osmId = 1552111008,
        name = "Hunan Taohuayuan (Peach Garden) International Golf Club",
        aliases = listOf("常德桃花源国际高尔夫俱乐部", "桃花源"),
        lat = 29.11807,
        lon = 111.693403,
    )
    private val beijing = CoursePoi(
        osmId = 1,
        name = "北京乡村高尔夫俱乐部",
        lat = 39.9,
        lon = 116.4,
    )
    private val shanghaiLinks = CoursePoi(
        osmId = 2,
        name = "上海林克斯",
        nameEn = "Shanghai Links Golf Course",
        lat = 31.2,
        lon = 121.5,
    )
    private val sample = listOf(taohuayuan, beijing, shanghaiLinks)

    @Test
    fun match_aliasAndEnglishName() {
        // 搜中文别名命中桃花源（别名包含 → 2 分）
        assertEquals(2, poiMatchScore(taohuayuan, "桃花源"))
        // 别名全等 → 0 分
        assertEquals(0, poiMatchScore(taohuayuan, "常德桃花源国际高尔夫俱乐部"))
        // 英文名包含（忽略大小写）→ 2 分
        assertEquals(2, poiMatchScore(taohuayuan, "taohuayuan"))
        // 中文名前缀 → 1 分
        assertEquals(1, poiMatchScore(beijing, "北京"))
        // 只有英文名能命中时走英文通道：英文包含 = 3 + 2 = 5 分
        assertEquals(5, poiMatchScore(shanghaiLinks, "links"))
        // 不匹配与空查询返回 null
        assertNull(poiMatchScore(beijing, "上海"))
        assertNull(poiMatchScore(beijing, "  "))
    }

    @Test
    fun search_ordersByScoreThenName() {
        val byAlias = searchPois(sample, "桃花源")
        assertEquals(listOf(taohuayuan), byAlias)

        val byChinese = searchPois(sample, "高尔夫")
        // 北京中文名含“高尔夫”、桃花源中文别名也含：同为 2 分，按名字排序（拉丁字母在前）
        assertEquals(listOf(taohuayuan, beijing), byChinese)

        assertTrue(searchPois(sample, "").isEmpty())
        assertEquals(1, searchPois(sample, "高尔夫", limit = 1).size)
    }

    @Test
    fun nearby_sortedByDistance() {
        // 站在桃花源门口：50 公里内只有它
        val atTaohuayuan = nearbyPois(sample, 29.118, 111.693, radiusKm = 50.0)
        assertEquals(listOf(taohuayuan), atTaohuayuan)

        // 大半径：按距离排序，桃花源最近
        val wide = nearbyPois(sample, 29.118, 111.693, radiusKm = 2000.0)
        assertEquals(taohuayuan, wide.first())
        assertEquals(3, wide.size)

        // 北京附近 50 公里内只有北京那条
        val atBeijing = nearbyPois(sample, 39.9, 116.4, radiusKm = 50.0)
        assertEquals(listOf(beijing), atBeijing)
    }

    @Test
    fun bundledPoi_parsesAndCoversTaohuayuan() {
        val text = javaClass.classLoader
            ?.getResourceAsStream("poi/china-courses.json")
            ?.bufferedReader()?.readText()
            ?: error("test resources 里没有 poi/china-courses.json")
        val pois = json.decodeFromString<List<CoursePoi>>(text)

        assertTrue("POI 至少 300 条，实际 ${pois.size}", pois.size >= 300)
        val th = pois.singleOrNull { it.osmId == 1552111008L }
            ?: error("POI 里没有桃花源（osm 1552111008）")
        assertTrue("桃花源要有中文别名", th.aliases.any { "桃花源" in it })

        val blocked = listOf(
            "练习", "練習", "训练", "訓練", "迷你", "学院", "學院",
            "槌球", "打擊", "打击", "教練", "教练", "漆彈", "漆弹", "射箭",
        )
        for (poi in pois) {
            assertTrue("名字不能为空：${poi.osmId}", poi.name.isNotBlank())
            assertTrue("纬度越界：${poi.osmId}", poi.lat in 15.0..55.0)
            assertTrue("经度越界：${poi.osmId}", poi.lon in 70.0..135.0)
            assertTrue(
                "练习场/学院等打不了的条目不应进名单：${poi.name}",
                blocked.none { it in poi.name },
            )
        }
    }
}
