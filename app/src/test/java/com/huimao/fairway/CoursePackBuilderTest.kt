package com.huimao.fairway

import com.huimao.fairway.data.CoursePackBuilder
import com.huimao.fairway.data.OverpassElement
import com.huimao.fairway.data.OverpassPoint
import com.huimao.fairway.data.OverpassResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端上配对逻辑回归：用当初 Python 提取桃花源的同一份 Overpass 原始数据（test resources），
 * 要求 Kotlin 版建出的 CoursePack 与 Python 版 v1 数据集一致（洞/Tee/果岭/沙坑/水障数、
 * 缺果岭洞、疑似练习果岭）。
 */
class CoursePackBuilderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun build_taohuayuan_matchesPythonExtraction() {
        val text = javaClass.classLoader
            ?.getResourceAsStream("taohuayuan-raw-geom.json")
            ?.bufferedReader()?.readText()
            ?: error("test resource 缺失")
        val detail = json.decodeFromString<OverpassResponse>(text)

        // 原始文件只有洞级元素，球场轮廓用 v1 数据里的中心点拼一个假 way（只用于取中心与名字）
        val courseWay = OverpassElement(
            type = "way",
            id = 1552111008,
            tags = mapOf(
                "name" to "湖南桃花源国际高尔夫俱乐部",
                "name:en" to "Hunan Taohuayuan (Peach Garden) International Golf Club",
                "leisure" to "golf_course",
            ),
            geometry = listOf(
                OverpassPoint(29.08, 111.65),
                OverpassPoint(29.08, 111.73),
                OverpassPoint(29.15, 111.73),
                OverpassPoint(29.15, 111.65),
            ),
        )

        val pack = CoursePackBuilder.build(courseWay, detail)
        assertNotNull("桃花源应能建成 CoursePack", pack)
        pack!!

        assertEquals("湖南桃花源国际高尔夫俱乐部", pack.course.name)
        assertEquals(72, pack.course.total_par)
        assertEquals(18, pack.holes.size)
        assertEquals((1..18).toList(), pack.holes.map { it.ref })
        assertEquals(72, pack.tees.size)
        assertEquals(15, pack.greens.size)
        assertEquals(95, pack.bunkers.size)
        assertEquals(8, pack.water_hazards.size)

        // 缺果岭的洞：1、4、5、9（与 Python 版一致）
        assertEquals(listOf(1, 4, 5, 9), pack.gaps.missing_greens)
        // 11 号洞多一个疑似练习果岭
        val suspects = pack.greens.filter { it.practice_green_suspect }
        assertEquals(1, suspects.size)
        assertEquals(11, suspects.first().hole)

        // 1 号洞黑 Tee 应在洞线起点附近（Python 版 dist_m 为 0）
        val tee1 = pack.tees.first { it.hole == 1 && it.color == "black" }
        assertTrue(tee1.dist_m <= 2)
        // 每洞每色恰好一个 Tee
        assertEquals(72, pack.tees.distinctBy { it.hole to it.color }.size)
    }

    @Test
    fun build_noHoles_returnsNull() {
        val courseWay = OverpassElement(
            type = "way",
            id = 1,
            tags = mapOf("name" to "空球场"),
            geometry = listOf(OverpassPoint(0.0, 0.0)),
        )
        assertEquals(null, CoursePackBuilder.build(courseWay, OverpassResponse(emptyList())))
    }
}
