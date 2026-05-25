<template>
  <div class="status-panel-container">
    <div class="time-section">
      <div class="time-main">{{ timeStr }}</div>
      <div class="date-row">
        <span class="date-text">{{ dateStr }}</span>
        <span class="week-text">{{ weekStr }}</span>
      </div>
    </div>

    <div class="minimal-divider"></div>

    <div class="weather-section">
      <div class="weather-row">
        <span class="weather-icon">{{ weatherInfo.icon }}</span>
        <span class="temp-text">{{ weatherInfo.temp }}°</span>
        <span class="city-text">{{ weatherInfo.city || '定位中...' }}</span>
      </div>
      <div class="mood-text-row">
        <span class="weather-desc">{{ weatherInfo.desc }}</span>
        <span class="mood-prompt">{{ weatherInfo.moodText }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, reactive } from 'vue'
import axios from 'axios'

const WEATHER_API_KEY = import.meta.env.VITE_WEATHER_API_KEY || ''

const timeStr = ref('')
const dateStr = ref('')
const weekStr = ref('')

const weatherInfo = reactive({
  temp: '--',
  desc: '',
  city: '',
  icon: '☁️',
  moodText: '正在连接感知...'
})

// 情绪映射（可根据你的"妹相随"风格扩展）
const moodConfig = {
  '晴': '是适合出去走走的一天',
  '多云': '云朵厚厚的，适合发呆',
  '阴': '今天有点阴沉呢...',
  '雨': '听，是雨的声音，别淋湿了哦',
  '雪': '想和你一起看初雪',
  'default': '今天也要加油呀'
}

const weatherCodeMap = {
  thunderstorm: '⛈️', drizzle: '🌦️', rain: '🌧️',
  snow: '🌨️', mist: '🌫️', smoke: '🌫️', haze: '🌫️',
  dust: '🌫️', fog: '🌫️', sand: '🌫️', ash: '🌫️',
  squall: '💨', tornado: '🌪️', clear: '☀️', clouds: '☁️'
}

const getWeatherIcon = (id) => {
  // OpenWeatherMap codes
  if (id >= 200 && id < 300) return weatherCodeMap.thunderstorm
  if (id >= 300 && id < 400) return weatherCodeMap.drizzle
  if (id >= 500 && id < 600) return weatherCodeMap.rain
  if (id >= 600 && id < 700) return weatherCodeMap.snow
  if (id >= 700 && id < 800) return weatherCodeMap.mist
  if (id === 800) return weatherCodeMap.clear
  if (id > 800) return weatherCodeMap.clouds
  return '☁️'
}

// WMO weather code to icon mapping (used by Open-Meteo)
const wmoWeatherIcons = {
  0: '☀️', 1: '☀️', 2: '⛅', 3: '☁️',
  45: '🌫️', 48: '🌫️',
  51: '🌦️', 53: '🌦️', 55: '🌦️',
  56: '🌧️', 57: '🌧️',
  61: '🌧️', 63: '🌧️', 65: '🌧️',
  66: '🌧️', 67: '🌧️',
  71: '🌨️', 73: '🌨️', 75: '🌨️', 77: '🌨️',
  80: '🌦️', 81: '🌦️', 82: '🌧️',
  85: '🌨️', 86: '🌨️',
  95: '⛈️', 96: '⛈️', 99: '⛈️'
}

// WMO weather code to Chinese description
const wmoDescriptions = {
  0: '晴', 1: '晴', 2: '多云', 3: '阴',
  45: '雾', 48: '雾',
  51: '毛毛雨', 53: '毛毛雨', 55: '毛毛雨',
  56: '冻雨', 57: '冻雨',
  61: '小雨', 63: '中雨', 65: '大雨',
  66: '冻雨', 67: '冻雨',
  71: '小雪', 73: '中雪', 75: '大雪', 77: '雪粒',
  80: '阵雨', 81: '阵雨', 82: '大阵雨',
  85: '阵雪', 86: '阵雪',
  95: '雷暴', 96: '雷暴', 99: '雷暴'
}

const getMoodText = (desc) => {
  if (!desc) return moodConfig['default']
  for (const [key, value] of Object.entries(moodConfig)) {
    if (key !== 'default' && desc.includes(key)) return value
  }
  return moodConfig['default']
}

const updateTime = () => {
  const now = new Date()
  timeStr.value = now.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' })
  dateStr.value = `${now.getMonth() + 1}月${now.getDate()}日`
  weekStr.value = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'][now.getDay()]
}

const fetchWeatherByCoords = async (lat, lon) => {
  const res = await axios.get('https://api.openweathermap.org/data/2.5/weather', {
    params: { lat, lon, appid: WEATHER_API_KEY, units: 'metric', lang: 'zh_cn' },
    timeout: 8000
  })
  return res.data
}

const fetchLocationByIP = async () => {
  const res = await axios.get('https://ip-api.com/json/', {
    params: { fields: 'status,city,lat,lon' },
    timeout: 5000
  })
  return res.data
}

const fetchWeatherFromOpenMeteo = async (lat, lon) => {
  const res = await axios.get('https://api.open-meteo.com/v1/forecast', {
    params: { latitude: lat, longitude: lon, current_weather: true, timezone: 'auto' },
    timeout: 8000
  })
  return res.data
}

const applyWeather = (data) => {
  if (data && data.cod === 200) {
    weatherInfo.city = data.name
    weatherInfo.temp = Math.round(data.main.temp)
    weatherInfo.desc = data.weather[0].description
    weatherInfo.icon = getWeatherIcon(data.weather[0].id)
    weatherInfo.moodText = getMoodText(weatherInfo.desc)
  }
}

const applyOpenMeteoWeather = (data, cityName) => {
  if (data && data.current_weather) {
    const wmoCode = data.current_weather.weathercode
    weatherInfo.city = cityName || ''
    weatherInfo.temp = Math.round(data.current_weather.temperature)
    weatherInfo.icon = wmoWeatherIcons[wmoCode] || '☁️'
    weatherInfo.desc = wmoDescriptions[wmoCode] || ''
    weatherInfo.moodText = getMoodText(weatherInfo.desc)
    return true
  }
  return false
}

const fetchWeather = async () => {
  try {
    // 优先：浏览器定位 → OpenWeatherMap (需要 API key)
    if (navigator.geolocation) {
      const data = await new Promise((resolve) => {
        navigator.geolocation.getCurrentPosition(
            ({ coords }) => resolve(fetchWeatherByCoords(coords.latitude, coords.longitude)),
            () => resolve(null),
            { timeout: 5000 }
        )
      })
      if (data && data.cod === 200) { applyWeather(data); return }
    }
  } catch {
    // OpenWeatherMap 失败，走兜底
  }

  // 兜底：ip-api.com 获取位置 → Open-Meteo 获取真实天气
  try {
    const ipData = await fetchLocationByIP()
    if (ipData && ipData.status === 'success' && ipData.lat && ipData.lon) {
      const meteoData = await fetchWeatherFromOpenMeteo(ipData.lat, ipData.lon)
      if (applyOpenMeteoWeather(meteoData, ipData.city)) return
    }
  } catch {
    // 兜底也失败
  }

  weatherInfo.temp = '--'
  weatherInfo.desc = '天气查询失败'
  weatherInfo.city = ''
  weatherInfo.moodText = '断开了和外界的联系呢...'
}

let timeTimer = null
let weatherTimer = null

/**
 * 可见性变化时暂停/恢复定时器，避免后台标签页浪费资源
 */
const handleVisibilityChange = () => {
  if (document.hidden) {
    clearInterval(timeTimer)
    clearInterval(weatherTimer)
    timeTimer = null
    weatherTimer = null
  } else if (!timeTimer) {
    updateTime()
    fetchWeather()
    timeTimer = setInterval(updateTime, 1000)
    weatherTimer = setInterval(fetchWeather, 1000 * 60 * 30)
  }
}

onMounted(() => {
  updateTime()
  fetchWeather()

  timeTimer = setInterval(updateTime, 1000)
  weatherTimer = setInterval(fetchWeather, 1000 * 60 * 30)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  clearInterval(timeTimer)
  clearInterval(weatherTimer)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.status-panel-container {
  position: absolute;
  top: 50px;
  left: 60px;
  z-index: 50;
  background: transparent;
  color: #000;
  pointer-events: none;
}

.time-main {
  font-family: 'Inter', sans-serif;
  font-size: 5.5rem;
  font-weight: 600;
  line-height: 0.9;
  letter-spacing: -4px;
  /* 轻微阴影：增强在浅色背景上的易读性 */
  text-shadow: 0 4px 10px rgba(255, 255, 255, 0.3);
}

.date-row {
  margin-top: 10px;
  display: flex;
  gap: 15px;
  font-size: 1.1rem;
  font-weight: 600;
  opacity: 0.8;
}

.week-text {
  color: #008c7a;
}

.minimal-divider {
  width: 40px;
  height: 3px;
  background: #000;
  margin: 20px 0;
}

/* 天气与文案样式 */
.weather-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.weather-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.temp-text { font-size: 1.8rem; font-weight: 800; }
.city-text { font-size: 1rem; opacity: 0.7; }

.mood-text-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.weather-desc {
  background: #000;
  color: #fff;
  padding: 2px 6px;
  font-size: 0.75rem;
  border-radius: 4px;
}

.mood-prompt {
  font-size: 1rem;
  font-weight: 500;
  color: #444;
  animation: fade-in 1.5s ease-out;
}

@keyframes fade-in {
  from { opacity: 0; transform: translateX(-10px); }
  to { opacity: 1; transform: translateX(0); }
}
</style>
