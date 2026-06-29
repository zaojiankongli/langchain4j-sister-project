import { shallowRef, onUnmounted } from 'vue'

export function usePetAudioPlayer() {
  // State
  const isPlaying = shallowRef(false)
  const audioQueue: string[] = []
  let audioContext: AudioContext | null = null
  let isProcessing = false
  let stopped = false
  // Reusable Float32Array buffer to reduce GC pressure from PCM→Float32 conversion.
  // Reallocated only when chunk size changes (common TTS chunk sizes are stable).
  let pcmConversionBuffer: Float32Array | null = null

  // Initialize AudioContext lazily (must be created after user gesture)
  function ensureAudioContext(): AudioContext {
    if (!audioContext) {
      audioContext = new AudioContext()
    }
    if (audioContext.state === 'suspended') {
      void audioContext.resume()
    }
    return audioContext
  }

  // Decode base64 PCM data and play it
  async function playPcmAudio(base64Data: string, sampleRate = 24000): Promise<void> {
    try {
      const ctx = ensureAudioContext()

      // Decode base64 to raw PCM16 bytes
      const binaryString = atob(base64Data)
      const byteLen = binaryString.length
      const bytes = new Uint8Array(byteLen)
      for (let i = 0; i < byteLen; i++) {
        bytes[i] = binaryString.charCodeAt(i)
      }

      // Convert PCM16 to Float32 (reuse buffer when chunk size is stable)
      const pcm16 = new Int16Array(bytes.buffer, 0, byteLen >> 1)
      if (!pcmConversionBuffer || pcmConversionBuffer.length !== pcm16.length) {
        pcmConversionBuffer = new Float32Array(pcm16.length)
      }
      const float32 = pcmConversionBuffer
      for (let i = 0; i < pcm16.length; i++) {
        float32[i] = pcm16[i] / 32768.0
      }

      // Create AudioBuffer and play
      const audioBuffer = ctx.createBuffer(1, float32.length, sampleRate)
      audioBuffer.getChannelData(0).set(float32)

      const source = ctx.createBufferSource()
      source.buffer = audioBuffer
      source.connect(ctx.destination)
      source.onended = () => {
        if (stopped) return
        isPlaying.value = false
        processQueue()
      }

      isPlaying.value = true
      source.start()
    } catch (error) {
      if (stopped) return
      console.error('Audio playback error:', error)
      isPlaying.value = false
      processQueue()
    }
  }

  // Queue system for sequential playback
  const MAX_QUEUE_SIZE = 10
  function enqueueAudio(data: string): void {
    stopped = false // allow audio to play after programmatic stop
    if (audioQueue.length >= MAX_QUEUE_SIZE) {
      audioQueue.shift() // drop oldest to cap memory
    }
    audioQueue.push(data)
    if (!isProcessing) {
      processQueue()
    }
  }

  async function processQueue(): Promise<void> {
    if (audioQueue.length === 0) {
      isProcessing = false
      return
    }

    isProcessing = true
    const data = audioQueue.shift()!
    await playPcmAudio(data)
  }

  // Stop all playback
  function stop(): void {
    stopped = true
    audioQueue.length = 0
    isProcessing = false
    pcmConversionBuffer = null // release reusable buffer
    if (audioContext) {
      void audioContext.close()
      audioContext = null
    }
    isPlaying.value = false
  }

  // Cleanup on unmount
  onUnmounted(() => {
    stop()
  })

  return {
    isPlaying,
    enqueueAudio,
    stop,
    playPcmAudio,  // for direct use without queue
  }
}
