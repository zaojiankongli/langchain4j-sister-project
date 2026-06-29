import * as PIXI from 'pixi.js'
import { install as installPixiUnsafeEval } from '@pixi/unsafe-eval'
import { semanticExpressions, semanticMotions } from './semanticLive2dMaps'

installPixiUnsafeEval({ ShaderSystem: PIXI.ShaderSystem })

declare global {
  interface Window {
    PIXI?: typeof PIXI
    Live2DCubismCore?: unknown
  }
}

export interface Live2DRendererHandle {
  dispose: () => void
  setSemanticExpression: (expression: string) => Promise<boolean>
  playSemanticMotion: (motion: string) => Promise<boolean>
  getDebugInfo: () => string
  refreshLayout: () => void
}

export interface MountLive2DOptions {
  canvas: HTMLCanvasElement
  modelPath: string
}

const cubismRuntimeSrc = '/vendor/live2d/live2dcubismcore.min.js'
let cubismRuntimePromise: Promise<void> | null = null

type CubismRuntimeVisibility = {
  windowHas: boolean
  globalHas: boolean
}

function getGlobalCubismRuntime(): unknown {
  const globalObject = globalThis as typeof globalThis & { Live2DCubismCore?: unknown }
  return globalObject.Live2DCubismCore
}

function getCubismRuntimeState(): CubismRuntimeVisibility {
  return {
    windowHas: typeof window.Live2DCubismCore !== 'undefined',
    globalHas: typeof getGlobalCubismRuntime() !== 'undefined',
  }
}

function syncCubismRuntimeToWindow(): void {
  const globalObject = globalThis as typeof globalThis & { Live2DCubismCore?: unknown }
  const runtime = window.Live2DCubismCore ?? globalObject.Live2DCubismCore

  if (runtime) {
    window.Live2DCubismCore = runtime
    globalObject.Live2DCubismCore = runtime
  }
}

function ensureCubismRuntimeVisible(stage: string): void {
  syncCubismRuntimeToWindow()
  const { windowHas, globalHas } = getCubismRuntimeState()

  if (!windowHas) {
    throw new Error(`Live2DCubismCore missing after ${stage} (windowHas=${windowHas} globalHas=${globalHas})`)
  }
}

function hasLoadedCubismRuntime(): boolean {
  syncCubismRuntimeToWindow()
  return typeof window.Live2DCubismCore !== 'undefined'
}

function createInlineCubismRuntimeScript(source: string): HTMLScriptElement {
  const script = document.createElement('script')
  script.dataset.live2dRuntimeInline = 'true'
  script.text = `${source}\n;window.Live2DCubismCore = window.Live2DCubismCore || Live2DCubismCore; globalThis.Live2DCubismCore = globalThis.Live2DCubismCore || Live2DCubismCore;`
  return script
}

function appendFallbackCubismRuntimeScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    const fallbackScript = document.createElement('script')
    fallbackScript.src = `${cubismRuntimeSrc}?fallback=1`
    fallbackScript.async = true
    fallbackScript.onload = () => {
      try {
        ensureCubismRuntimeVisible('fallback-script')
        fallbackScript.dataset.live2dRuntimeLoaded = 'true'
        resolve()
      } catch (error) {
        reject(error instanceof Error ? error : new Error(String(error)))
      }
    }
    fallbackScript.onerror = () => reject(new Error('Failed to load Live2DCubismCore runtime via fallback script'))
    document.head.appendChild(fallbackScript)
  })
}

async function loadCubismRuntimeFromInlineSource(): Promise<void> {
  const response = await fetch(cubismRuntimeSrc)

  if (!response.ok) {
    throw new Error(`Failed to fetch Live2DCubismCore runtime: ${response.status}`)
  }

  const source = await response.text()
  const script = createInlineCubismRuntimeScript(source)
  document.head.appendChild(script)
  ensureCubismRuntimeVisible('inline-script')
}

async function loadCubismRuntime(): Promise<void> {
  if (hasLoadedCubismRuntime()) {
    return
  }

  if (cubismRuntimePromise) {
    return cubismRuntimePromise
  }

  cubismRuntimePromise = (async () => {
    try {
      await loadCubismRuntimeFromInlineSource()
    } catch (error) {
      const inlineError = error instanceof Error ? error : new Error(String(error))

      if (inlineError.message.startsWith('Failed to fetch Live2DCubismCore runtime:')) {
        throw inlineError
      }

      const { windowHas, globalHas } = getCubismRuntimeState()

      if (windowHas) {
        return
      }

      inlineError.message = `${inlineError.message} (windowHas=${windowHas} globalHas=${globalHas})`
      await appendFallbackCubismRuntimeScript()
    }
  })()

  return cubismRuntimePromise
}

export async function mountPixiLive2D({
  canvas,
  modelPath,
}: MountLive2DOptions): Promise<Live2DRendererHandle> {
  await loadCubismRuntime()
  ensureCubismRuntimeVisible('loadCubismRuntime')
  await new Promise<void>((resolve) => setTimeout(resolve, 0))
  ensureCubismRuntimeVisible('pre-cubism4-import')

  const { Live2DModel } = await import('pixi-live2d-display/cubism4')

  window.PIXI = PIXI

  const parent = canvas.parentElement ?? undefined
  const resolution = Math.min(window.devicePixelRatio || 1, 2)
  const app = new PIXI.Application({
    view: canvas,
    resizeTo: parent,
    autoStart: true,
    autoDensity: true,
    antialias: true,
    backgroundAlpha: 0,
    resolution,
  })

  const onVisibilityChange = () => {
    if (document.hidden) {
      app.ticker.stop()
    } else {
      app.ticker.start()
    }
  }
  document.addEventListener('visibilitychange', onVisibilityChange)

  const model = await Live2DModel.from(modelPath)

  app.stage.addChild(model)

  let debugInfo = 'fit=uninitialized'
  const baseBounds = model.getLocalBounds()
  const baseWidth = baseBounds.width
  const baseHeight = baseBounds.height

  const fitModel = () => {
    const rendererWidth = parent?.clientWidth ?? canvas.clientWidth ?? app.view.clientWidth ?? app.renderer.width
    const rendererHeight = parent?.clientHeight ?? canvas.clientHeight ?? app.view.clientHeight ?? app.renderer.height
    const bounds = model.getLocalBounds()
    const measuredWidth = baseWidth > 0 ? baseWidth : bounds.width
    const measuredHeight = baseHeight > 0 ? baseHeight : bounds.height

    if (!(measuredWidth > 0) || !(measuredHeight > 0)) {
      debugInfo = `fit=skipped renderer=${rendererWidth}x${rendererHeight} model=${model.width}x${model.height} bounds=${bounds.width}x${bounds.height} base=${baseWidth}x${baseHeight}`
      return
    }

    const scale = Math.min(rendererWidth / measuredWidth, rendererHeight / measuredHeight) * 0.72

    model.pivot.set(baseBounds.x + baseWidth / 2, baseBounds.y + baseHeight / 2)
    model.scale.set(scale)
    model.x = rendererWidth / 2
    model.y = rendererHeight * 0.47
    debugInfo = `fit=applied renderer=${rendererWidth}x${rendererHeight} model=${model.width}x${model.height} bounds=${bounds.width}x${bounds.height} base=${baseWidth}x${baseHeight} scale=${scale.toFixed(4)} pos=${model.x.toFixed(1)},${model.y.toFixed(1)}`
  }

  fitModel()
  let resizeFrame = 0
  const resizeObserver = parent
    ? new ResizeObserver(() => {
        if (resizeFrame) {
          cancelAnimationFrame(resizeFrame)
        }
        resizeFrame = requestAnimationFrame(() => {
          resizeFrame = 0
          fitModel()
        })
      })
    : null

  if (parent && resizeObserver) {
    resizeObserver.observe(parent)
  }

  return {
    setSemanticExpression: async (expression: string) => {
      const expressionId = semanticExpressions[expression]
      if (!expressionId) {
        return false
      }
      return model.expression(expressionId)
    },
    playSemanticMotion: async (motion: string) => {
      const motionTarget = semanticMotions[motion]
      if (!motionTarget) {
        return false
      }
      return model.motion(motionTarget.group, motionTarget.index)
    },
    getDebugInfo: () => debugInfo,
    refreshLayout: () => fitModel(),
    dispose: () => {
      if (resizeFrame) {
        cancelAnimationFrame(resizeFrame)
      }
      resizeObserver?.disconnect()
      document.removeEventListener('visibilitychange', onVisibilityChange)
      // Stop ticker explicitly before destroy to prevent frame callbacks
      // during teardown and ensure no residual timer refs.
      app.ticker.stop()
      // Destroy textures to prevent GPU memory leak on model swap.
      // Texture/baseTexture: false in app.destroy avoids double-free since
      // model.destroy({ texture: true }) already cleaned them up.
      model.destroy({ texture: true })
      app.destroy(true, { children: false, texture: false, baseTexture: false })
      // Release global PIXI reference to allow GC
      delete window.PIXI
    },
  }
}
