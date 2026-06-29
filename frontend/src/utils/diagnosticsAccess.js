const STORAGE_KEY = 'sister.enableDiagnostics'

export function isDiagnosticsEnabled() {
  if (import.meta.env.DEV) {
    return true
  }

  if (import.meta.env.VITE_ENABLE_DIAGNOSTICS === 'true') {
    return true
  }

  try {
    return localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}
