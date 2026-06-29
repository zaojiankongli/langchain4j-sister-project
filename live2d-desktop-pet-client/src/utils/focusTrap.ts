/**
 * Focus trap utility for modal dialogs.
 *
 * Use as a keydown event handler on the dialog card element:
 *   <div @keydown.tab="handleTabTrap">
 *
 * Or use the composable for document-level trapping:
 *   const cleanup = useFocusTrap(() => dialogRef.value)
 *   onUnmounted(cleanup)
 */

/** Tab-trap handler to use as @keydown.tab on dialog containers. */
export function handleTabTrap(event: KeyboardEvent): void {
  const container = event.currentTarget as HTMLElement
  const focusable = container.querySelectorAll<HTMLElement>(
    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
  )
  if (focusable.length === 0) return

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement

  if (event.shiftKey && active === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}
