<template>
  <div class="status-panel-container">
    <div class="time-section">
      <div class="time-main">{{ timeStr }}</div>
      <div class="date-row">
        <span class="date-text">{{ dateStr }}</span>
        <span class="week-text">{{ weekStr }}</span>
      </div>
    </div>

    <div class="minimal-divider" />

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
import { ref, onMounted, onUnmounted, onBeforeUnmount, reactive } from 'vue'
import request from '@/utils/request'
import { getUserId } from '@/utils/auth'
import { useUiStore } from '@/stores/ui'

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
const uiStore = useUiStore()

// 情绪映射
const moodConfig = {
  '晴': '是适合出去走走的一天',
  '多云': '云朵厚厚的，适合发呆',
  '阴': '今天有点阴沉呢...',
  '雨': '听，是雨的声音，别淋湿了哦',
  '雪': '想和你一起看初雪',
  'default': '今天也要加油呀'
}

// WMO weather code to icon
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

const getMsUntilNextMinute = () => {
  const now = new Date()
  return (60 - now.getSeconds()) * 1000 - now.getMilliseconds()
}

let timeTimer = null
let timeTimeout = null
let _isMounted = true

onBeforeUnmount(() => { _isMounted = false })

const fetchOpenMeteo = async (lat, lon) => {
  try {
    const res = await request.get('https://api.open-meteo.com/v1/forecast', {
      params: { latitude: lat, longitude: lon, current_weather: true, timezone: 'auto' },
      timeout: 8000
    })
    return res
  } catch (e) {
    console.warn('StatusPanel fetchOpenMeteo:', e)
    return null
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

const CACHE_KEY_BASE = 'zeeva-weather-cache'
const CACHE_TTL = 1000 * 60 * 30 // 30 分钟

function getCacheKey() {
  const uid = getUserId()
  return uid ? `${CACHE_KEY_BASE}-${uid}` : CACHE_KEY_BASE
}

function loadCache() {
  try {
    const key = getCacheKey()
    const raw = localStorage.getItem(key)
    if (!raw) return false
    const cache = JSON.parse(raw)
    if (Date.now() - cache.timestamp > CACHE_TTL) {
      localStorage.removeItem(key)
      return false
    }
    Object.assign(weatherInfo, cache.data)
    return true
  } catch {
    return false
  }
}

function saveCache() {
  try {
    localStorage.setItem(getCacheKey(), JSON.stringify({
      timestamp: Date.now(),
      data: { ...weatherInfo }
    }))
  } catch { /* quota exceeded */ }
}

const fetchWeather = async () => {
  // 缓存命中直接返回
  if (loadCache()) return

  let settled = false
  let completed = 0

  // Atomically claim "first winner" — single-threaded JS guarantees this is safe
  const trySettle = () => {
    if (settled || !_isMounted) return false
    settled = true
    return true
  }

  const finalizeIfNeeded = () => {
    completed += 1
    if (completed < 2 || settled || !_isMounted) return

    weatherInfo.temp = '--'
    weatherInfo.desc = ''
    weatherInfo.city = ''
    weatherInfo.moodText = '断开了和外界的联系呢...'
    uiStore.error('获取天气失败，将使用默认天气信息')
  }

  // 策略 1：浏览器定位 → Open-Meteo（无需 API key）
  const geoTask = (async () => {
    if (!navigator.geolocation) {
      finalizeIfNeeded()
      return
    }

    try {
      const pos = await new Promise((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, { timeout: 5000 })
      })
      if (settled || !_isMounted) return
      const meteoData = await fetchOpenMeteo(pos.coords.latitude, pos.coords.longitude)
      if (settled || !_isMounted) return
      if (applyOpenMeteoWeather(meteoData, '') && trySettle()) {
        saveCache()
      }
    } catch (e) {
      console.warn('StatusPanel geolocation weather failed:', e)
    } finally {
      finalizeIfNeeded()
    }
  })()

  // 策略 2：ip-api.com → Open-Meteo
  const ipTask = (async () => {
    try {
      const res = await request.get('https://ip-api.com/json/', {
        params: { fields: 'status,city,lat,lon' },
        timeout: 5000
      })
      if (settled || !_isMounted) return
      if (res && res.status === 'success' && res.lat && res.lon) {
        const meteoData = await fetchOpenMeteo(res.lat, res.lon)
        if (settled || !_isMounted) return
        if (applyOpenMeteoWeather(meteoData, res.city) && trySettle()) {
          saveCache()
        }
      }
    } catch (e) {
      console.warn('StatusPanel ip weather failed:', e)
    } finally {
      finalizeIfNeeded()
    }
  })()

  await Promise.all([geoTask, ipTask])

  // 全部失败
  if (!_isMounted || settled) return
}

/**
 * 可见性变化时暂停/恢复时间定时器
 */
const handleVisibilityChange = () => {
  if (document.hidden) {
    clearTimeout(timeTimeout)
    clearInterval(timeTimer)
    timeTimeout = null
    timeTimer = null
  } else if (!timeTimeout && !timeTimer) {
    updateTime()
    timeTimeout = setTimeout(() => {
      updateTime()
      timeTimer = setInterval(updateTime, 60 * 1000)
      timeTimeout = null
    }, getMsUntilNextMinute())
  }
}

onMounted(() => {
  updateTime()
  fetchWeather()

  timeTimeout = setTimeout(() => {
    updateTime()
    timeTimer = setInterval(updateTime, 60 * 1000)
    timeTimeout = null
  }, getMsUntilNextMinute())
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  clearTimeout(timeTimeout)
  clearInterval(timeTimer)
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
