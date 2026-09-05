import { useCallback, useEffect, useState } from 'react'
import {
  applyThemeAttribute,
  getStoredThemePreference,
  resolveTheme,
  saveThemePreference,
  watchSystemTheme,
} from '../theme/theme.js'

function initialPreference() {
  if (typeof window === 'undefined') return 'system'
  return getStoredThemePreference(window.localStorage) || 'system'
}

export function useTheme() {
  const [preference, setPreferenceState] = useState(initialPreference)
  const [theme, setTheme] = useState(() => resolveTheme(initialPreference()))

  useEffect(() => {
    applyThemeAttribute(theme)
  }, [theme])

  useEffect(() => {
    if (preference !== 'system') return undefined
    return watchSystemTheme((nextTheme) => setTheme(nextTheme))
  }, [preference])

  const setPreference = useCallback((nextPreference) => {
    setPreferenceState(nextPreference)
    if (typeof window !== 'undefined') {
      saveThemePreference(window.localStorage, nextPreference)
    }
    setTheme(resolveTheme(nextPreference))
  }, [])

  const toggle = useCallback(() => {
    setPreferenceState((current) => {
      const next = current === 'dark' ? 'light' : 'dark'
      if (typeof window !== 'undefined') {
        saveThemePreference(window.localStorage, next)
      }
      setTheme(resolveTheme(next))
      return next
    })
  }, [])

  return { theme, preference, toggle, setPreference }
}
