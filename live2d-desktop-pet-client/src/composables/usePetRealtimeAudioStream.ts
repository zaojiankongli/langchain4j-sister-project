import { onUnmounted, shallowRef, watch, type ShallowRef } from 'vue'

interface UsePetRealtimeAudioStreamOptions {
  socketStatus: ShallowRef<string>
  startRealtimeSession: () => boolean
  sendAudioChunk: (audioBase64: string) => boolean
  stopRealtimeSession: () => boolean
}

const INPUT_SAMPLE_RATE = 16000
const SCRIPT_PROCESSOR_BUFFER_SIZE = 8192
const TARGET_CHUNK_DURATION_MS = 180

function floatToPcm16(samples: Float32Array): Int16Array {
  const pcm = new Int16Array(samples.length)
  for (let i = 0; i < samples.length; i++) {
    const sample = Math.max(-1, Math.min(1, samples[i]))
    pcm[i] = sample < 0 ? sample * 0x8000 : sample * 0x7fff
  }
  return pcm
}

function resampleTo16k(samples: Float32Array, sourceSampleRate: number): Float32Array {
  if (sourceSampleRate === INPUT_SAMPLE_RATE) {
    return samples
  }

  const ratio = sourceSampleRate / INPUT_SAMPLE_RATE
  const outputLength = Math.max(1, Math.floor(samples.length / ratio))
  const output = new Float32Array(outputLength)

  for (let i = 0; i < outputLength; i++) {
    const sourceIndex = i * ratio
    const leftIndex = Math.floor(sourceIndex)
    const rightIndex = Math.min(leftIndex + 1, samples.length - 1)
    const fraction = sourceIndex - leftIndex
    output[i] = samples[leftIndex] + (samples[rightIndex] - samples[leftIndex]) * fraction
  }

  return output
}

function pcmToBase64(pcm: Int16Array): string {
  const bytes = new Uint8Array(pcm.buffer, pcm.byteOffset, pcm.byteLength)
  let binary = ''
  const chunkSize = 0x8000
  for (let i = 0; i < bytes.length; i += chunkSize) {
    const chunk = bytes.subarray(i, i + chunkSize)
    binary += String.fromCharCode(...chunk)
  }
  return btoa(binary)
}

/**
 * Streams microphone audio to the backend as 16 kHz mono PCM16 Base64 chunks.
 * Qwen semantic_vad owns turn detection; this composable only streams audio.
 */
export function usePetRealtimeAudioStream(options: UsePetRealtimeAudioStreamOptions) {
  const { socketStatus, startRealtimeSession, sendAudioChunk, stopRealtimeSession } = options

  const isStreaming = shallowRef(false)
  const micError = shallowRef<string | null>(null)

  let audioContext: AudioContext | null = null
  let mediaStream: MediaStream | null = null
  let sourceNode: MediaStreamAudioSourceNode | null = null
  let processorNode: ScriptProcessorNode | null = null
  let startPromise: Promise<boolean> | null = null
  let pendingBuffers: Float32Array[] = []
  let pendingSampleCount = 0

  function resetPendingBuffers(): void {
    pendingBuffers = []
    pendingSampleCount = 0
  }

  function flushPendingAudio(sampleRate: number): boolean {
    if (pendingSampleCount === 0) {
      return true
    }

    const merged = new Float32Array(pendingSampleCount)
    let offset = 0
    for (const buffer of pendingBuffers) {
      merged.set(buffer, offset)
      offset += buffer.length
    }
    resetPendingBuffers()

    const resampled = resampleTo16k(merged, sampleRate)
    const pcm = floatToPcm16(resampled)
    return sendAudioChunk(pcmToBase64(pcm))
  }

  function cleanup(sendStop: boolean): void {
    if (processorNode) {
      processorNode.onaudioprocess = null
      processorNode.disconnect()
      processorNode = null
    }
    if (sourceNode) {
      sourceNode.disconnect()
      sourceNode = null
    }
    mediaStream?.getTracks().forEach((track) => track.stop())
    mediaStream = null
    if (audioContext && audioContext.state !== 'closed') {
      audioContext.close().catch(() => {})
    }
    audioContext = null
    resetPendingBuffers()
    if (isStreaming.value && sendStop) {
      stopRealtimeSession()
    }
    isStreaming.value = false
  }

  async function _doStart(): Promise<boolean> {
    if (socketStatus.value !== 'connected') {
      micError.value = 'SocketDisconnected'
      return false
    }
    if (!startRealtimeSession()) {
      micError.value = 'RealtimeStartFailed'
      return false
    }

    micError.value = null

    try {
      mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          sampleRate: INPUT_SAMPLE_RATE,
        },
      })
    } catch (error: unknown) {
      const name = error instanceof DOMException ? error.name : 'MicrophoneUnavailable'
      micError.value = name
      stopRealtimeSession()
      return false
    }

    try {
      audioContext = new AudioContext()
      if (audioContext.state === 'suspended') {
        await audioContext.resume()
      }
      sourceNode = audioContext.createMediaStreamSource(mediaStream)
      processorNode = audioContext.createScriptProcessor(SCRIPT_PROCESSOR_BUFFER_SIZE, 1, 1)
      processorNode.onaudioprocess = (event) => {
        if (!isStreaming.value || socketStatus.value !== 'connected' || !audioContext) return
        const input = event.inputBuffer.getChannelData(0)
        pendingBuffers.push(new Float32Array(input))
        pendingSampleCount += input.length
        const bufferedDurationMs = pendingSampleCount / audioContext.sampleRate * 1000
        if (bufferedDurationMs < TARGET_CHUNK_DURATION_MS) {
          return
        }
        if (!flushPendingAudio(audioContext.sampleRate)) {
          micError.value = 'AudioSendFailed'
          cleanup(true)
        }
      }
      sourceNode.connect(processorNode)
      processorNode.connect(audioContext.destination)
    } catch {
      micError.value = 'AudioContextError'
      cleanup(true)
      return false
    }

    isStreaming.value = true
    return true
  }

  async function start(): Promise<boolean> {
    if (isStreaming.value) return true
    if (startPromise) return startPromise
    startPromise = _doStart()
    try {
      return await startPromise
    } finally {
      startPromise = null
    }
  }

  function stop(): void {
    if (audioContext) {
      flushPendingAudio(audioContext.sampleRate)
    }
    cleanup(true)
  }

  watch(socketStatus, (status) => {
    if (status !== 'connected') {
      cleanup(false)
    }
  })

  onUnmounted(() => {
    cleanup(true)
  })

  return {
    isStreaming,
    micError,
    start,
    stop,
  }
}
