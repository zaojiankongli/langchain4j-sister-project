import { watch, type ShallowRef, type ComputedRef } from 'vue'
import type { PetDisplaySettings } from './useClientSettings'

/* ------------------------------------------------------------------ */
/*  Theme definitions                                                  */
/* ------------------------------------------------------------------ */

interface ThemeVars {
  '--color-accent': string
  '--color-accent-soft': string
  '--color-action-text': string
  '--color-bg': string
  '--color-surface': string
  '--color-surface-raised': string
  '--color-surface-subtle': string
  '--color-heading': string
  '--color-text': string
  '--color-text-muted': string
  '--color-border': string
  '--color-border-strong': string
  '--color-glow-warm': string
  '--color-glow-cool': string
}

const themes: Record<string, ThemeVars> = {
  default: {
    '--color-accent': '#7c3aed',
    '--color-accent-soft': 'rgba(124, 58, 237, 0.12)',
    '--color-action-text': '#ffffff',
    '--color-bg': '#0f0e14',
    '--color-surface': '#1a1825',
    '--color-surface-raised': '#211f2e',
    '--color-surface-subtle': '#161422',
    '--color-heading': '#f0ecf9',
    '--color-text': '#d4cfe6',
    '--color-text-muted': '#8b85a3',
    '--color-border': 'rgba(255, 255, 255, 0.08)',
    '--color-border-strong': 'rgba(255, 255, 255, 0.14)',
    '--color-glow-warm': 'rgba(124, 58, 237, 0.08)',
    '--color-glow-cool': 'rgba(59, 130, 246, 0.06)',
  },
  warm: {
    '--color-accent': '#e67e22',
    '--color-accent-soft': 'rgba(230, 126, 34, 0.12)',
    '--color-action-text': '#ffffff',
    '--color-bg': '#14100c',
    '--color-surface': '#1f1a14',
    '--color-surface-raised': '#2a231a',
    '--color-surface-subtle': '#1a1510',
    '--color-heading': '#f5ece0',
    '--color-text': '#d9cfc0',
    '--color-text-muted': '#9a8e7c',
    '--color-border': 'rgba(255, 255, 255, 0.08)',
    '--color-border-strong': 'rgba(255, 255, 255, 0.14)',
    '--color-glow-warm': 'rgba(230, 126, 34, 0.10)',
    '--color-glow-cool': 'rgba(241, 196, 15, 0.06)',
  },
  cool: {
    '--color-accent': '#3b82f6',
    '--color-accent-soft': 'rgba(59, 130, 246, 0.12)',
    '--color-action-text': '#ffffff',
    '--color-bg': '#0c1017',
    '--color-surface': '#141b28',
    '--color-surface-raised': '#1b2436',
    '--color-surface-subtle': '#101620',
    '--color-heading': '#e0ecf9',
    '--color-text': '#c0d0e6',
    '--color-text-muted': '#7c90ab',
    '--color-border': 'rgba(255, 255, 255, 0.08)',
    '--color-border-strong': 'rgba(255, 255, 255, 0.14)',
    '--color-glow-warm': 'rgba(59, 130, 246, 0.08)',
    '--color-glow-cool': 'rgba(139, 92, 246, 0.06)',
  },
  dark: {
    '--color-accent': '#a855f7',
    '--color-accent-soft': 'rgba(168, 85, 247, 0.12)',
    '--color-action-text': '#ffffff',
    '--color-bg': '#080808',
    '--color-surface': '#121212',
    '--color-surface-raised': '#1a1a1a',
    '--color-surface-subtle': '#0e0e0e',
    '--color-heading': '#f0f0f0',
    '--color-text': '#cccccc',
    '--color-text-muted': '#777777',
    '--color-border': 'rgba(255, 255, 255, 0.06)',
    '--color-border-strong': 'rgba(255, 255, 255, 0.12)',
    '--color-glow-warm': 'rgba(168, 85, 247, 0.06)',
    '--color-glow-cool': 'rgba(99, 102, 241, 0.04)',
  },
  soft: {
    '--color-accent': '#ec4899',
    '--color-accent-soft': 'rgba(236, 72, 153, 0.10)',
    '--color-action-text': '#ffffff',
    '--color-bg': '#140e12',
    '--color-surface': '#1f1820',
    '--color-surface-raised': '#2a202c',
    '--color-surface-subtle': '#18121a',
    '--color-heading': '#f5e8f0',
    '--color-text': '#d9c8d4',
    '--color-text-muted': '#9a8694',
    '--color-border': 'rgba(255, 255, 255, 0.07)',
    '--color-border-strong': 'rgba(255, 255, 255, 0.13)',
    '--color-glow-warm': 'rgba(236, 72, 153, 0.08)',
    '--color-glow-cool': 'rgba(244, 114, 182, 0.05)',
  },
}

/* ------------------------------------------------------------------ */
/*  Theme application                                                  */
/* ------------------------------------------------------------------ */

const themeNameMap: Record<number, string> = {
  1: 'default',
  2: 'warm',
  3: 'cool',
  4: 'dark',
  5: 'soft',
}

function applyTheme(themeId?: number | null): void {
  const name = themeNameMap[themeId ?? 1] ?? 'default'
  const vars = themes[name]
  if (!vars) return

  const root = document.documentElement
  for (const [key, value] of Object.entries(vars)) {
    root.style.setProperty(key, value)
  }
  root.setAttribute('data-theme', name)
}

/* ------------------------------------------------------------------ */
/*  Local pet display preferences (read from unified client settings)  */
/* ------------------------------------------------------------------ */

function applyDisplayPrefs(prefs: PetDisplaySettings): void {
  const root = document.documentElement
  root.style.setProperty('--pet-scale', String(prefs.scale))
  root.style.setProperty('--pet-opacity', String(prefs.opacity))
}

/* ------------------------------------------------------------------ */
/*  Composable                                                         */
/* ------------------------------------------------------------------ */

export function usePetTheme(
  themeIdRef: ShallowRef<{ themeId?: number } | null | undefined>,
  petDisplayRef: ComputedRef<PetDisplaySettings>,
) {
  // Watch settings changes and apply theme
  watch(themeIdRef, (settings) => {
    applyTheme(settings?.themeId)
  }, { immediate: true })

  // Watch display preferences from unified client settings
  watch(petDisplayRef, (prefs) => {
    applyDisplayPrefs(prefs)
  }, { immediate: true })

  return {
    applyTheme,
  }
}
