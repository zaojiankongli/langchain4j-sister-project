/**
 * Image URL helpers.
 *
 * For Alibaba OSS images, request a smaller derivative for thumbnail-sized UI.
 * Non-OSS URLs are left untouched to avoid breaking third-party providers.
 */

const OSS_PROCESS_KEY = 'x-oss-process'
const IMAGE_CACHE_LIMIT = 200
const imageUrlCache = new Map()

function getCacheKey(url, options = {}) {
  const width = options.width || 440
  const quality = options.quality || 80
  return `${url}|${width}|${quality}`
}

function cacheImageUrl(key, value) {
  if (imageUrlCache.has(key)) {
    imageUrlCache.delete(key)
  }
  imageUrlCache.set(key, value)
  if (imageUrlCache.size > IMAGE_CACHE_LIMIT) {
    const oldestKey = imageUrlCache.keys().next().value
    imageUrlCache.delete(oldestKey)
  }
  return value
}

function isOssImageUrl(url) {
  return /aliyuncs\.com|oss-[a-z0-9-]+\./i.test(url)
}

export function getOptimizedImageUrl(url, options = {}) {
  if (!url || typeof url !== 'string') return url
  if (url.startsWith('blob:') || url.startsWith('data:')) return url
  if (!isOssImageUrl(url)) return url

  const cacheKey = getCacheKey(url, options)
  if (imageUrlCache.has(cacheKey)) {
    return imageUrlCache.get(cacheKey)
  }

  try {
    const parsed = new URL(url, window.location.origin)
    if (parsed.searchParams.has(OSS_PROCESS_KEY)) return cacheImageUrl(cacheKey, url)

    const width = options.width || 440
    const quality = options.quality || 80
    parsed.searchParams.set(OSS_PROCESS_KEY, `image/resize,w_${width}/quality,q_${quality}`)
    return cacheImageUrl(cacheKey, parsed.toString())
  } catch {
    const separator = url.includes('?') ? '&' : '?'
    return cacheImageUrl(
      cacheKey,
      `${url}${separator}${OSS_PROCESS_KEY}=image/resize,w_${options.width || 440}/quality,q_${options.quality || 80}`
    )
  }
}
