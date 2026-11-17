package com.huimao.fairway

import com.huimao.fairway.data.GolfCoreCourse
import com.huimao.fairway.data.GolfCoreDetail
import com.huimao.fairway.data.GolfCoreSearchResponse
import com.huimao.fairway.data.OverpassElement
import com.huimao.fairway.data.OverpassPoint
import com.huimao.fairway.data.countryDisplay
import com.huimao.fairway.data.mapTeeRatings
import com.huimao.fairway.data.nearestWithin
import com.huimao.fairway.data.pickNearestCourseWay
import com.huimao.fairway.data.teeColorFromName
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GolfCore 解析与映射测试：用 2026-11-17 实测的 API 响应做回归。 */
class GolfCoreClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun resource(name: String): String =
        javaClass.classLoader
            ?.getResourceAsStream("golfcore/$name")
            ?.bufferedReader()?.readText()
            ?: error("missing test resource golfcore/$name")

    @Test
    fun search_parsesChinaResults() {
        val resp = json.decodeFromString<GolfCoreSearchResponse>(resource("search-cn.json"))
        assertEquals(408, resp.total)
        assertEquals(2, resp.courses.size)
        val first = resp.courses[0]
        assertEquals("Changan Golf Course", first.name)
        assertEquals("长安高尔夫球场", first.native_name)
        assertEquals("Dongguan", first.city)
        assertEquals("cn", first.country)
        assertTrue(first.coverage.scorecard)
    }

    @Test
    fun detail_mapsToFourColorRatings() {
        val detail = json.decodeFromString<GolfCoreDetail>(resource("detail-pebble-beach.json"))
        assertEquals("Pebble Beach Golf Links", detail.name)
        val ratings = mapTeeRatings(detail).associateBy { it.color }
        // 只取男子、单色名的台：Blue / White / Red；Gold、Green、组合台、女子台全部跳过
        assertEquals(3, ratings.size)
        assertEquals(74.9, ratings["blue"]!!.rating, 0.001)
        assertEquals(144, ratings["blue"]!!.slope)
        assertEquals(71.7, ratings["white"]!!.rating, 0.001)
        assertEquals(135, ratings["white"]!!.slope)
        assertEquals(67.3, ratings["red"]!!.rating, 0.001)
        assertEquals(124, ratings["red"]!!.slope)
        assertNull(ratings["black"])
    }

    @Test
    fun teeColorFromName_rules() {
        assertEquals("blue", teeColorFromName("Blue"))
        assertEquals("black", teeColorFromName("Black Tees"))
        assertEquals("red", teeColorFromName("red"))
        assertNull(teeColorFromName("Gold"))
        assertNull(teeColorFromName("Green"))
        assertNull(teeColorFromName("Gold/White Combo"))
        assertNull(teeColorFromName("White/Green Combo"))
        assertNull(teeColorFromName("Championship"))
        assertNull(teeColorFromName(""))
    }

    @Test
    fun countryDisplay_mapsCodes() {
        assertEquals("中国", countryDisplay("cn"))
        assertEquals("美国", countryDisplay("us"))
        assertEquals("英国", countryDisplay("gb"))
    }

    @Test
    fun nearestWithin_picksClosest() {
        val resp = json.decodeFromString<GolfCoreSearchResponse>(resource("search-cn.json"))
        // 长安球场坐标附近 -> 长安；远处 -> null
        val near = nearestWithin(resp.courses, 22.83, 113.81, maxKm = 2.0)
        assertEquals("Changan Golf Course", near?.name)
        assertNull(nearestWithin(resp.courses, 0.0, 0.0, maxKm = 2.0))
    }

    @Test
    fun pickNearestCourseWay_picksClosest() {
        val elements = listOf(
            OverpassElement("way", 111L, center = OverpassPoint(29.12, 111.69)),
            OverpassElement("way", 222L, center = OverpassPoint(29.13, 111.70)),
            OverpassElement("way", 333L, center = OverpassPoint(28.0, 110.0)),
        )
        assertEquals(111L, pickNearestCourseWay(elements, 29.118, 111.693))
        assertTrue(pickNearestCourseWay(emptyList(), 29.118, 111.693) == null)
    }

    @Test
    fun golfCoreCourse_defaultsAreSafe() {
        // 只有 slug + name 的最小对象也能解析（字段缺失不炸）
        val c = json.decodeFromString<GolfCoreCourse>(
            """{"slug":"x","name":"X"}""",
        )
        assertEquals("x", c.slug)
        assertEquals("", c.city)
        assertNull(c.native_name)
    }
}
