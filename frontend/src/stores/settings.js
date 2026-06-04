import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import request from '@/utils/request'
import { API } from '@/config/api'
import { getUserId } from '@/utils/auth'

export const useSettingsStore = defineStore('settings', () => {
  const settings = ref(null)
  const presets = ref([])
  const loading = ref(false)
  const saving = ref(false)
  const error = ref('')
  const _fetching = ref(false)
  const _lastFetch = ref(0)
  
   async function fetchSettings() {
     // Prevent concurrent fetches
     if (_fetching.value) return
     // Skip if recently fetched and we have settings
     if (_lastFetch.value > 0 && Date.now() - _lastFetch.value < 30000 && settings.value !== null) return
     
     _fetching.value = true
     loading.value = true
     error.value = ''
     try {
       const res = await request.get(API.SETTINGS_GET(getUserId()))
       if (res.code === 200) {
         settings.value = res.data
         _lastFetch.value = Date.now()
       }
     } catch (e) {
       error.value = e?.message || '获取设置失败'
     } finally {
       loading.value = false
       _fetching.value = false
     }
   }
  
  async function fetchPresets() {
    try {
      const res = await request.get(API.SETTINGS_PRESETS)
      if (res.code === 200) presets.value = res.data
    } catch (e) {
      console.error('获取预设失败:', e)
    }
  }
  
   async function saveSettings(data) {
    // 如果有 fetch 正在进行，轮询等待（无需注册 watcher，避免潜在泄漏）
    while (_fetching.value) {
      await new Promise(resolve => setTimeout(resolve, 100))
    }
    saving.value = true
    error.value = ''
    try {
      const res = await request.put(API.SETTINGS_SAVE(getUserId()), data)
      if (res.code === 200) {
        settings.value = data
        _lastFetch.value = Date.now()
        return true
      }
      error.value = res.message || '保存失败'
      return false
    } catch (e) {
      error.value = e?.message || '保存失败'
      return false
    } finally {
      saving.value = false
    }
  }
  
  // Initialize
  function init() {
    fetchSettings()
    fetchPresets()
  }
  
  return { settings, presets, loading, saving, error, fetchSettings, fetchPresets, saveSettings, init }
})