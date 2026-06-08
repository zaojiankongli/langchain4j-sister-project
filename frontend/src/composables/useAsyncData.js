import { ref, onMounted, onActivated, onBeforeUnmount } from 'vue'

export function useAsyncData(fetcher, options = {}) {
  const data = ref(options.initialValue ?? null)
  const loading = ref(false)
  const error = ref(null)
  const _lastFetch = ref(null)

  let _fetching = false
  let _fetchPromise = null  // 当前进行中的请求 Promise，供并发调用复用
  let _isMounted = false
  let _requestSeq = 0

  onMounted(() => { _isMounted = true })
  onBeforeUnmount(() => { _isMounted = false })

  async function execute(...args) {
    // 并发调用复用同一个 Promise，避免返回过时数据
    if (_fetching && _fetchPromise) return _fetchPromise

    // Check cache: if cacheTTL > 0, data exists, and cache is fresh
    if (options.cacheTTL > 0 && data.value !== null && _lastFetch.value !== null && (Date.now() - _lastFetch.value) < options.cacheTTL) {
      return data.value
    }

    _fetching = true
    loading.value = true
    error.value = null
    const requestSeq = ++_requestSeq

    _fetchPromise = (async () => {
      try {
        const result = await fetcher(...args)
        if (!_isMounted || requestSeq !== _requestSeq) return undefined
        data.value = result
        _lastFetch.value = Date.now()
      } catch (e) {
        if (!_isMounted || requestSeq !== _requestSeq) return undefined
        error.value = e?.response?.data?.message || e?.message || '加载失败'
      } finally {
        if (_isMounted && requestSeq === _requestSeq) {
          loading.value = false
        }
        _fetching = false
        _fetchPromise = null
      }
      return data.value
    })()

    return _fetchPromise
  }

  if (options.immediate !== false) {
    onMounted(() => execute())
    onActivated(() => {
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
