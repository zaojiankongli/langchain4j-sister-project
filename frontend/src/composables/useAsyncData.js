import { ref, onMounted, onActivated, onBeforeUnmount } from 'vue'

export function useAsyncData(fetcher, options = {}) {
  const data = ref(options.initialValue ?? null)
  const loading = ref(false)
  const error = ref(null)
  const _lastFetch = ref(null)

  let _fetching = false
  let _isMounted = false

  onMounted(() => { _isMounted = true })
  onBeforeUnmount(() => { _isMounted = false })

  async function execute(...args) {
    // Prevent concurrent execution
    if (_fetching) return data.value

    // Check cache: if cacheTTL > 0, data exists, and cache is fresh
    if (options.cacheTTL > 0 && data.value !== null && _lastFetch.value !== null && (Date.now() - _lastFetch.value) < options.cacheTTL) {
      return data.value
    }

    _fetching = true
    loading.value = true
    error.value = null
    try {
      const result = await fetcher(...args)
      if (!_isMounted) return undefined // 组件已卸载，放弃操作
      data.value = result
      _lastFetch.value = Date.now()
    } catch (e) {
      if (!_isMounted) return undefined
      error.value = e?.response?.data?.message || e?.message || '加载失败'
    } finally {
      if (_isMounted) {
        loading.value = false
      }
      _fetching = false
    }
    return data.value
  }

  if (options.immediate !== false) {
    onMounted(() => execute())
    onActivated(() => {
      // Only execute if cache is expired or no caching
      if (!(options.cacheTTL > 0 && data.value !== null && _lastFetch.value !== null && (Date.now() - _lastFetch.value) < options.cacheTTL)) {
        execute()
      }
    })
  }

  function invalidate() {
    _lastFetch.value = null
  }

  return { data, loading, error, execute, invalidate }
}