import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'
import { API } from '@/config/api'
import { getUserId } from '@/utils/auth'

export const useSettingsStore = defineStore('settings', () => {
  const settings = ref(null)
  const presets = ref([])
  const loading = ref(false)
  const saving = ref(false)
  const error = ref('')
  const _lastFetch = ref(0)
  let _fetchPromise = null

   async function fetchSettings() {
      // Skip if recently fetched and we have settings
      if (_lastFetch.value > 0 && Date.now() - _lastFetch.value < 30000 && settings.value !== null) return
      if (_fetchPromise) return _fetchPromise
      const userId = getUserId()
      if (!userId) return

      _fetchPromise = (async () => {
        loading.value = true
        error.value = ''
        try {
          const res = await request.get(API.SETTINGS_GET(userId))
          if (res.code === 200) {
            settings.value = res.data
            _lastFetch.value = Date.now()
          }
       } catch (e) {
         error.value = e?.message || '获取设置失败'
       } finally {
         loading.value = false
         _fetchPromise = null
       }
     })()

     return _fetchPromise
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
     if (_fetchPromise) await _fetchPromise
     const userId = getUserId()
     if (!userId) {
       error.value = '用户未登录或用户信息未就绪'
       return false
     }
     saving.value = true
     error.value = ''
     try {
       const res = await request.put(API.SETTINGS_SAVE(userId), data)
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

  function resetSettings() {
    settings.value = null
    presets.value = []
    loading.value = false
    saving.value = false
    error.value = ''
    _lastFetch.value = 0
    _fetchPromise = null
  }

  return { settings, presets, loading, saving, error, fetchSettings, fetchPresets, saveSettings, init, resetSettings }
})
