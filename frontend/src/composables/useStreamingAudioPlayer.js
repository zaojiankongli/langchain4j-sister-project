import { ref, computed, onUnmounted } from 'vue'

const SAMPLE_RATE = 44100
const DEFAULT_MIN_BUFFER = 8     // 预缓冲块数（原为 3，增大以降低慢连接卡顿）
const BUFFER_LOW_WATERMARK = 4   // 低于此值时视为缓冲不足

export function useStreamingAudioPlayer(minBufferSize = DEFAULT_MIN_BUFFER) {
  const audioContext = ref(null)
  const scriptProcessor = ref(null)
  const gainNode = ref(null)

  const isPlaying = ref(false)
  const isPaused = ref(false)
  const isBuffering = ref(false)    // 缓冲不足，播放卡顿
  const volume = ref(1.0)

  const decodeQueue = []

  const currentTime = ref(0)
  const totalSamples = ref(0)

  const bufferHealth = computed(() => {
    if (minBufferSize === 0) return 1
    return Math.min(decodeQueue.length / minBufferSize, 1)
  })

  const stats = ref({
    chunksReceived: 0,
    chunksDecoded: 0,
    decodeErrors: 0,
    bufferUnderruns: 0,      // 缓冲不足次数
  })

  const init = async () => {
    if (audioContext.value) return

    audioContext.value = new (window.AudioContext || window.webkitAudioContext)({
      sampleRate: SAMPLE_RATE,
      latencyHint: 'interactive'
    })

    gainNode.value = audioContext.value.createGain()
    gainNode.value.gain.value = volume.value
    gainNode.value.connect(audioContext.value.destination)

    // TODO: ScriptProcessorNode is deprecated. Migrate to AudioWorkletNode when possible.
    // AudioWorklet requires a separate processor file loaded via audioContext.audioWorklet.addModule(url).
    scriptProcessor.value = audioContext.value.createScriptProcessor(4096, 0, 1)
    scriptProcessor.value.onaudioprocess = handleAudioProcess
    scriptProcessor.value.connect(gainNode.value)
  }

  // ── 节流 currentTime 更新（音频回调 ~43Hz，UI 只需 ~10Hz） ──
  let _lastTimeUpdate = 0

  function handleAudioProcess(event) {
    const outputBuffer = event.outputBuffer.getChannelData(0)
    let hadData = false

    // 原子化取出所有待处理块
    const queue = decodeQueue.splice(0)

    for (let i = 0; i < outputBuffer.length; i++) {
      if (queue.length > 0) {
        const chunk = queue[0]
        if (chunk.position < chunk.data.length) {
          outputBuffer[i] = chunk.data[chunk.position]
          chunk.position++
          hadData = true

          if (chunk.position >= chunk.data.length) {
            totalSamples.value += chunk.data.length
            queue.shift()
          }
        } else {
          queue.shift()
          outputBuffer[i] = 0
        }
      } else {
        outputBuffer[i] = 0
      }
    }

    // 未消费完的块放回队列（部分消费场景）
    if (queue.length > 0) {
      decodeQueue.unshift(...queue)
    }

    // 节流更新 currentTime（~10Hz 足够 UI 显示）
    const now = performance.now()
    if (now - _lastTimeUpdate > 100) {
      _lastTimeUpdate = now
      if (queue.length > 0 && queue[0].position !== undefined) {
        currentTime.value = (totalSamples.value + queue[0].position) / SAMPLE_RATE
      } else {
        currentTime.value = totalSamples.value / SAMPLE_RATE
      }
    }

    // 检测缓冲不足
    if (!hadData && isPlaying.value) {
      if (!isBuffering.value) {
        isBuffering.value = true
        stats.value.bufferUnderruns++
      }
    }
  }

  const appendAudioChunk = async (arrayBuffer) => {
    stats.value.chunksReceived++

    try {
      const pcmData = new Int16Array(arrayBuffer)
      const floatData = new Float32Array(pcmData.length)

      for (let i = 0; i < pcmData.length; i++) {
        floatData[i] = pcmData[i] / 32768.0
      }

      if (!floatData || floatData.length === 0) {
        stats.value.decodeErrors++
        return
      }

      stats.value.chunksDecoded++

      decodeQueue.push({
        data: floatData,
        position: 0
      })

      if (!isPlaying.value && decodeQueue.length >= minBufferSize) {
        startPlayback()
      }

      // 检测缓冲恢复
      if (isBuffering.value && decodeQueue.length >= BUFFER_LOW_WATERMARK) {
        isBuffering.value = false
      }

    } catch (error) {
      stats.value.decodeErrors++
    }
  }

  async function startPlayback() {
    if (isPlaying.value) return

    if (audioContext.value.state === 'suspended') {
      try {
        await audioContext.value.resume()
      } catch {
        // 浏览器自动播放策略阻止了恢复，不标记为播放中
        return
      }
    }

    // 恢复音频处理回调（stop 中可能被置空）
    if (scriptProcessor.value && !scriptProcessor.value.onaudioprocess) {
      scriptProcessor.value.onaudioprocess = handleAudioProcess
    }

    isPlaying.value = true
    isPaused.value = false
  }

  const pause = () => {
    if (!isPlaying.value || isPaused.value) return
    isPaused.value = true
    audioContext.value?.suspend()
  }

  const resume = () => {
    if (!isPlaying.value || !isPaused.value) return
    isPaused.value = false
    audioContext.value?.resume()
  }

  const stop = () => {
    isPlaying.value = false
    isPaused.value = false
    decodeQueue.length = 0
    currentTime.value = 0
    totalSamples.value = 0
    // 暂停音频处理回调（不 disconnect，startPlayback 会恢复）
    if (scriptProcessor.value) {
      scriptProcessor.value.onaudioprocess = null
    }
  }

  const setVolume = (v) => {
    volume.value = Math.max(0, Math.min(1, v))
    if (gainNode.value) {
      gainNode.value.gain.value = volume.value
    }
  }

  const toggle = () => {
    if (isPaused.value) {
      resume()
    } else {
      pause()
    }
  }

  onUnmounted(() => {
    stop()
    // Close AudioContext to prevent memory leak
    if (audioContext.value) {
      audioContext.value.close().catch(() => {})
      audioContext.value = null
    }
  })

  return {
    isPlaying,
    isPaused,
    isBuffering,
    volume,
    bufferHealth,
    currentTime,
    stats,
    init,
    appendAudioChunk,
    pause,
    resume,
    stop,
    setVolume,
    toggle
  }
}
