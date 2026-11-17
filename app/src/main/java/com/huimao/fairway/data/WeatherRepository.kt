package com.huimao.fairway.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** Open-Meteo 实时天气（免费、无需 Key）。 */
@Serializable
data class WeatherNow(
    val temperature: Double,
    val windSpeed: Double,
    val windDirection: Double,
    val weatherCode: Int,
)

@Serializable
private data class OpenMeteoResponse(val current: CurrentWeather? = null)

@Serializable
private data class CurrentWeather(
    val temperature_2m: Double = 0.0,
    val wind_speed_10m: Double = 0.0,
    val wind_direction_10m: Double = 0.0,
    val weather_code: Int = 0,
)

private val weatherJson = Json { ignoreUnknownKeys = true }

class WeatherRepository {

    /** 返回 null 表示天气未取到（无网或接口失败），UI 只隐藏天气卡即可。 */
    suspend fun load(lat: Double, lon: Double): WeatherNow? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,wind_speed_10m,wind_direction_10m,weather_code&timezone=auto",
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            try {
                val text = conn.inputStream.bufferedReader().readText()
                weatherJson.decodeFromString<OpenMeteoResponse>(text).current?.let {
                    WeatherNow(it.temperature_2m, it.wind_speed_10m, it.wind_direction_10m, it.weather_code)
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}

/** 方位角度数 -> 十六方位中文（0 = 北）。 */
fun compassText(deg: Double): String {
    val names = listOf(
        "北", "北东北", "东北", "东东北", "东", "东东南", "东南", "南东南",
        "南", "南西南", "西南", "西西南", "西", "西西北", "西北", "北西北",
    )
    val idx = ((deg + 11.25) / 22.5).toInt() % 16
    return names[idx]
}

/** Open-Meteo 天气代码 -> 中文。 */
fun weatherCodeText(code: Int): String = when (code) {
    0 -> "晴"
    1 -> "大致晴"
    2 -> "多云"
    3 -> "阴"
    45, 48 -> "雾"
    51, 53, 55 -> "毛毛雨"
    56, 57 -> "冻毛毛雨"
    61 -> "小雨"
    63 -> "中雨"
    65 -> "大雨"
    66, 67 -> "冻雨"
    71 -> "小雪"
    73 -> "中雪"
    75 -> "大雪"
    77 -> "雪粒"
    80, 81, 82 -> "阵雨"
    85, 86 -> "阵雪"
    95 -> "雷阵雨"
    96, 99 -> "冰雹雷阵雨"
    else -> "未知"
}
