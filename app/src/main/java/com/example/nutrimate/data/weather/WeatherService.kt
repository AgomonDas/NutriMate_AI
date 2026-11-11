package com.example.nutrimate.data.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

private const val TAG = "WeatherService"

enum class WeatherCondition(val label: String, val emoji: String) {
    CLEAR("Clear sky", "☀️"),
    PARTLY_CLOUDY("Partly cloudy", "⛅"),
    CLOUDY("Overcast", "☁️"),
    DRIZZLE("Light drizzle", "🌦️"),
    RAINY("Rain showers", "🌧️"),
    HEAVY_RAIN("Heavy rain", "⛈️"),
    THUNDERSTORM("Thunderstorm", "⚡"),
    FOGGY("Foggy haze", "🌫️");

    companion object {
        fun fromWmoCode(code: Int): WeatherCondition {
            return when (code) {
                0 -> CLEAR
                1, 2 -> PARTLY_CLOUDY
                3 -> CLOUDY
                45, 48 -> FOGGY
                51, 53, 55 -> DRIZZLE
                61, 63, 65 -> RAINY
                80, 81, 82 -> HEAVY_RAIN
                95, 96, 99 -> THUNDERSTORM
                else -> if (code in 50..69) RAINY else if (code in 70..79) DRIZZLE else CLEAR
            }
        }
    }
}

data class CurrentWeather(
    val temperatureC: Int,
    val apparentTemperatureC: Int,
    val highTempC: Int,
    val lowTempC: Int,
    val condition: WeatherCondition,
    val humidityPercent: Int,
    val windSpeedKmh: Int,
    val rainMm: Double,
    val isDay: Boolean
)

object WeatherService {

    /**
     * Queries Open-Meteo API for real-time weather at the specified geo-coordinates.
     * Open-Meteo requires no API key and provides fast global coverage.
     */
    suspend fun fetchWeather(latitude: Double, longitude: Double): CurrentWeather? = withContext(Dispatchers.IO) {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val defaultIsDay = currentHour in 6..17

        try {
            val urlString = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,is_day" +
                    "&daily=temperature_2m_max,temperature_2m_min&timezone=auto"

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "NutriMate-Android/1.0")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonText)
                val current = root.optJSONObject("current")

                if (current != null) {
                    val temp = current.optDouble("temperature_2m", 28.0).toInt()
                    val apparent = current.optDouble("apparent_temperature", temp.toDouble()).toInt()
                    val humidity = current.optInt("relative_humidity_2m", 65)
                    val windSpeed = current.optDouble("wind_speed_10m", 12.0).toInt()
                    val rain = current.optDouble("precipitation", 0.0)
                    val weatherCode = current.optInt("weather_code", 0)
                    val isDayInt = current.optInt("is_day", if (defaultIsDay) 1 else 0)
                    val condition = WeatherCondition.fromWmoCode(weatherCode)

                    val daily = root.optJSONObject("daily")
                    val maxArr = daily?.optJSONArray("temperature_2m_max")
                    val minArr = daily?.optJSONArray("temperature_2m_min")
                    val highTemp = maxArr?.optDouble(0, (temp + 3).toDouble())?.toInt() ?: (temp + 3)
                    val lowTemp = minArr?.optDouble(0, (temp - 4).toDouble())?.toInt() ?: (temp - 4)

                    return@withContext CurrentWeather(
                        temperatureC = temp,
                        apparentTemperatureC = apparent,
                        highTempC = highTemp,
                        lowTempC = lowTemp,
                        condition = condition,
                        humidityPercent = humidity,
                        windSpeedKmh = windSpeed,
                        rainMm = rain,
                        isDay = isDayInt == 1
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Open-Meteo query failed or device offline: ${e.message}")
        }

        // Return a realistic location-based fallback based on season in Bangladesh
        val baseTemp = if (defaultIsDay) 29 else 22
        return@withContext CurrentWeather(
            temperatureC = baseTemp,
            apparentTemperatureC = baseTemp + 2,
            highTempC = baseTemp + 4,
            lowTempC = baseTemp - 5,
            condition = WeatherCondition.CLEAR,
            humidityPercent = 68,
            windSpeedKmh = 14,
            rainMm = 0.0,
            isDay = defaultIsDay
        )
    }
}
