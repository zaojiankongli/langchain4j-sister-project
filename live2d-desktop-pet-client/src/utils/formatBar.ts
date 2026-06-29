/**
 * Clamp a value to [min, max] and return the percentage width as a CSS string.
 * Used for PAD emotion bars and similar visualizations.
 */
export function normalizeBar(value: number, min: number, max: number): string {
  const clamped = Math.max(min, Math.min(max, value))
  const percent = ((clamped - min) / (max - min)) * 100
  return `${Math.round(percent)}%`
}
