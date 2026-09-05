export const THEME_STORAGE_KEY = 'linkcute.theme'

export const THEME_PREFERENCES = ['light', 'dark', 'system']

const THEME_COLORS = {
  light: '#faf9f6',
  dark: '#0f1618',
}

export function isThemePreference(value) {
  return THEME_PREFERENCES.includes(value)
}

export function getStoredThemePreference(storage) {
  try {
    const stored = storage?.getItem?.(THEME_STORAGE_KEY)
    return isThemePreference(stored) ? stored : null
  } catch {
    return null
  }
}

export function saveThemePreference(storage, preference) {
  try {
    if (!isThemePreference(preference)) return
    storage?.setItem?.(THEME_STORAGE_KEY, preference)
  } catch {
    // Storage may be unavailable (private mode, sandboxed iframe) — theme still applies this session.
  }
}

export function systemPrefersDark() {
  if (typeof window === 'undefined') return false
  return Boolean(window.matchMedia?.('(prefers-color-scheme: dark)')?.matches)
}

export function resolveTheme(preference) {
  if (preference === 'dark') return 'dark'
  if (preference === 'light') return 'light'
  return systemPrefersDark() ? 'dark' : 'light'
}

export function applyThemeAttribute(theme) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.setAttribute('data-theme', theme)
  root.style.colorScheme = theme
  const meta = document.querySelector('meta[name="theme-color"]')
  meta?.setAttribute('content', THEME_COLORS[theme] || THEME_COLORS.light)
}

export function watchSystemTheme(onChange) {
  if (typeof window === 'undefined' || !window.matchMedia) return () => {}
  const query = window.matchMedia('(prefers-color-scheme: dark)')
  const listener = (event) => onChange(event.matches ? 'dark' : 'light')
  query.addEventListener('change', listener)
  return () => query.removeEventListener('change', listener)
}
