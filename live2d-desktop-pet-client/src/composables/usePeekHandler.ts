import { shallowRef, onUnmounted } from 'vue'
import { uploadMultipart } from '../utils/apiClient'

export interface PeekResult {
  code: number
  data?: { success: boolean; peekId: string; message: string }
  message?: string
}

export function usePeekHandler() {
  const isPeeking = shallowRef(false)
  const peekStatus = shallowRef<'idle' | 'capturing' | 'uploading' | 'done' | 'error'>('idle')
  const peekCount = shallowRef(0)
  const lastPeekTimestamp = shallowRef<string | null>(null)
  let peekTimeout: ReturnType<typeof setTimeout> | null = null

  /**
   * Capture a screenshot using the browser getDisplayMedia API.
   * Works in both regular browsers and Tauri webviews.
   * Returns a Blob of the screenshot image, or null if cancelled/failed.
   */
  async function captureScreenshot(): Promise<Blob | null> {
    try {
      const stream = await navigator.mediaDevices.getDisplayMedia({
        video: { displaySurface: 'monitor' },
        audio: false,
      })

      // Grab one frame from the video track
      const track = stream.getVideoTracks()[0]
      const video = document.createElement('video')
      video.srcObject = new MediaStream([track])
      await video.play()

      // Wait for the first frame to be ready
      await new Promise<void>((resolve) => {
        video.onloadedmetadata = () => resolve()
        if (video.readyState >= 2) resolve()
      })

      // Draw the frame to a canvas and export as blob
      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      const ctx = canvas.getContext('2d')!
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height)

      // Clean up the stream immediately
      track.stop()
      video.srcObject = null

      return new Promise<Blob | null>((resolve) => {
        canvas.toBlob((blob) => resolve(blob), 'image/jpeg', 0.85)
      })
    } catch {
      // User denied permission or API not available
      return null
    }
  }

  /**
   * Handle an incoming PEEK_REQUEST from the server.
   * Captures a screenshot and uploads it to the backend for AI analysis.
   */
  async function handlePeekRequest(peekId: string): Promise<void> {
    if (!peekId) return

    peekCount.value++
    lastPeekTimestamp.value = new Date().toISOString()
    isPeeking.value = true
    peekStatus.value = 'capturing'

    // Clear any previous timeout
    if (peekTimeout) {
      clearTimeout(peekTimeout)
    }

    // Visual indicator auto-dismiss after 5 seconds
    peekTimeout = setTimeout(() => {
      isPeeking.value = false
      peekStatus.value = 'idle'
      peekTimeout = null
    }, 5000)

    try {
      // Step 1: Capture screenshot
      const blob = await captureScreenshot()
      if (!blob) {
        peekStatus.value = 'idle'
        return // User cancelled the picker
      }

      peekStatus.value = 'uploading'

      // Step 2: Upload as multipart/form-data
      const formData = new FormData()
      formData.append('peekId', peekId)
      formData.append('screenshot', blob, 'peek-screenshot.jpg')

      await uploadMultipart<PeekResult>('/api/peek/callback', formData)
      peekStatus.value = 'done'
    } catch (err) {
      console.error('Peek callback failed:', err)
      peekStatus.value = 'error'
    }
  }

  function reset(): void {
    if (peekTimeout) {
      clearTimeout(peekTimeout)
      peekTimeout = null
    }
    isPeeking.value = false
    peekStatus.value = 'idle'
    peekCount.value = 0
    lastPeekTimestamp.value = null
  }

  onUnmounted(() => {
    if (peekTimeout) {
      clearTimeout(peekTimeout)
      peekTimeout = null
    }
  })

  return {
    isPeeking,
    peekStatus,
    peekCount,
    lastPeekTimestamp,
    handlePeekRequest,
    reset,
  }
}
