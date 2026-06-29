import { shallowRef, type ShallowRef } from 'vue'
import { encode } from 'modern-gif'
import { save } from '@tauri-apps/plugin-dialog'
import { writeFile } from '@tauri-apps/plugin-fs'

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

export type CaptureStatus = 'idle' | 'recording'

interface CaptureOptions {
  canvasRef: ShallowRef<HTMLCanvasElement | null>
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

const isTauri = typeof window !== 'undefined' && '__TAURI__' in window

/** Generate a timestamped filename: pet_YYYYMMDD_HHmmss.ext */
function generateFilename(ext: string): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const stamp =
    `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}` +
    `_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  return `pet_${stamp}.${ext}`
}

/** Convert a Blob to a Uint8Array. */
function blobToUint8Array(blob: Blob): Promise<Uint8Array> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer))
    reader.onerror = () => reject(reader.error)
    reader.readAsArrayBuffer(blob)
  })
}

/** Convert a canvas to a PNG Uint8Array. */
async function canvasToPngBytes(canvas: HTMLCanvasElement): Promise<Uint8Array> {
  const blob = await new Promise<Blob | null>((resolve) =>
    canvas.toBlob(resolve, 'image/png'),
  )
  if (!blob) throw new Error('Canvas toBlob returned null')
  return blobToUint8Array(blob)
}

/** Trigger a browser download via a temporary anchor element. */
function downloadInBrowser(url: string, filename: string): void {
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

/** Save binary data via Tauri save dialog. */
async function saveViaTauriDialog(
  data: Uint8Array,
  defaultName: string,
  filters: { name: string; extensions: string[] }[],
): Promise<string | null> {
  const filePath = await save({
    defaultPath: defaultName,
    filters,
  })
  if (filePath) {
    await writeFile(filePath, data)
  }
  return filePath
}

/* ------------------------------------------------------------------ */
/*  Composable                                                         */
/* ------------------------------------------------------------------ */

/**
 * PNG screenshot & GIF recording from the Live2D canvas.
 *
 * - PNG: one-shot capture via `canvas.toBlob('image/png')`.
 * - GIF: frame-by-frame capture at 8 fps, encoded with `modern-gif`.
 *
 * In Tauri, files are saved via the native save dialog.
 * In dev / browser, files are downloaded via a temporary `<a>` element.
 */
export function usePetScreenshot(options: CaptureOptions) {
  const { canvasRef } = options

  const captureStatus = shallowRef<CaptureStatus>('idle')

  // ── GIF recording state ──
  let recordingFrames: HTMLCanvasElement[] = []
  let recordingRafId = 0
  let recordingStartTime = 0

  const GIF_FPS = 8
  const GIF_FRAME_INTERVAL = 1000 / GIF_FPS
  const GIF_MAX_DURATION = 10_000 // 10 s safety cap

  /* ---------------------------------------------------------------- */
  /*  PNG screenshot                                                   */
  /* ---------------------------------------------------------------- */

  async function takeScreenshot(): Promise<void> {
    const canvas = canvasRef.value
    if (!canvas) return

    const bytes = await canvasToPngBytes(canvas)
    const filename = generateFilename('png')

    if (isTauri) {
      await saveViaTauriDialog(bytes, filename, [
        { name: 'PNG Image', extensions: ['png'] },
      ])
    } else {
      const blob = new Blob([bytes.buffer as ArrayBuffer], { type: 'image/png' })
      const url = URL.createObjectURL(blob)
      downloadInBrowser(url, filename)
      setTimeout(() => URL.revokeObjectURL(url), 5_000)
    }
  }

  /* ---------------------------------------------------------------- */
  /*  GIF recording                                                    */
  /* ---------------------------------------------------------------- */

  function captureFrame(): void {
    const canvas = canvasRef.value
    if (!canvas || captureStatus.value !== 'recording') return

    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const frameCanvas = document.createElement('canvas')
    frameCanvas.width = canvas.width
    frameCanvas.height = canvas.height
    const frameCtx = frameCanvas.getContext('2d')
    if (!frameCtx) return

    frameCtx.drawImage(canvas, 0, 0)
    recordingFrames.push(frameCanvas)

    // Check max duration safety cap
    const elapsed = Date.now() - recordingStartTime
    if (elapsed >= GIF_MAX_DURATION) {
      // Auto-stop at max duration
      stopAndEncodeGif()
      return
    }

    recordingRafId = setTimeout(() => {
      requestAnimationFrame(captureFrame)
    }, GIF_FRAME_INTERVAL) as unknown as number
  }

  function startGifRecording(): void {
    if (!canvasRef.value || captureStatus.value === 'recording') return

    recordingFrames = []
    recordingStartTime = Date.now()
    captureStatus.value = 'recording'
    captureFrame()
  }

  /** Stop recording and encode the GIF. Call manually or auto-triggers at max duration. */
  async function stopAndEncodeGif(): Promise<void> {
    if (captureStatus.value !== 'recording') return

    captureStatus.value = 'idle'
    clearTimeout(recordingRafId)

    if (recordingFrames.length < 2) return

    const canvas = canvasRef.value
    if (!canvas) return

    const frames = recordingFrames
    recordingFrames = []

    const gifFrames = frames.map((frame) => ({
      data: frame,
      delay: GIF_FRAME_INTERVAL,
      width: canvas.width,
      height: canvas.height,
    }))

    const gifData = await encode({
      frames: gifFrames,
      width: canvas.width,
      height: canvas.height,
    })

    const filename = generateFilename('gif')
    const bytes = new Uint8Array(gifData)

    if (isTauri) {
      await saveViaTauriDialog(bytes, filename, [
        { name: 'GIF Image', extensions: ['gif'] },
      ])
    } else {
      const blob = new Blob([bytes.buffer as ArrayBuffer], { type: 'image/gif' })
      const url = URL.createObjectURL(blob)
      downloadInBrowser(url, filename)
      setTimeout(() => URL.revokeObjectURL(url), 5_000)
    }
  }

  /** Public stop — returns a promise that resolves when encoding + save is done. */
  function stopGifRecording(): Promise<void> {
    return stopAndEncodeGif()
  }

  return {
    captureStatus: captureStatus as import('vue').Ref<CaptureStatus>,
    takeScreenshot,
    startGifRecording,
    stopGifRecording,
  }
}
