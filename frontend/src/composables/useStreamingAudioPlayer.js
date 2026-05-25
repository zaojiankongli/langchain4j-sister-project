import { ref, onUnmounted } from 'vue'

const SAMPLE_RATE = 44100

export function useStreamingAudioPlayer() {
  const audioContext = ref(null)
  const scriptProcessor = ref(null)
  const gainNode = ref(null)
  
  const isPlaying = ref(false)
  const isPaused = ref(false)
  const volume = ref(1.0)
  
  const decodeQueue = []
  
  const currentTime = ref(0)
  const totalSamples = ref(0)
  
  const stats = ref({
    chunksReceived: 0,
    chunksDecoded: 0,
    decodeErrors: 0
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
  
  function handleAudioProcess(event) {
    const outputBuffer = event.outputBuffer.getChannelData(0)
    
    for (let i = 0; i < outputBuffer.length; i++) {
      if (decodeQueue.length > 0) {
        const chunk = decodeQueue[0]
        if (chunk.position < chunk.data.length) {
          outputBuffer[i] = chunk.data[chunk.position]
          chunk.position++
          currentTime.value = (totalSamples.value + chunk.position) / SAMPLE_RATE
          
          if (chunk.position >= chunk.data.length) {
            decodeQueue.shift()
            totalSamples.value += chunk.data.length
          }
        } else {
          outputBuffer[i] = 0
        }
      } else {
        outputBuffer[i] = 0
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

      if (!isPlaying.value && decodeQueue.length >= 3) {
        startPlayback()
      }
      
    } catch (error) {
      stats.value.decodeErrors++
    }
  }
  
  function startPlayback() {
    if (isPlaying.value) return
    
    if (audioContext.value.state === 'suspended') {
      audioContext.value.resume()
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
    volume,
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
