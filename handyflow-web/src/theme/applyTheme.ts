// src/theme/applyTheme.ts
// Applies resolved appearance to <html> and caches it for the pre-paint
// bootstrap script in index.html. localStorage is only a cache here; the
// server (ui-preferences API) is the source of truth.
import { brandVars, type BrandColor } from '../styles/brand'
import type { ContainerMode, SidebarMode, ThemeMode } from '../types/uiPreferences.types'

export type ResolvedTheme = 'light' | 'dark'

export const THEME_CACHE_KEY = 'hf-theme'
export const BRAND_CACHE_KEY = 'hf-brand-vars'

export function systemPrefersDark(): boolean {
  return typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-color-scheme: dark)').matches
}

export function resolveTheme(mode: ThemeMode): ResolvedTheme {
  if (mode === 'SYSTEM') return systemPrefersDark() ? 'dark' : 'light'
  return mode === 'DARK' ? 'dark' : 'light'
}

export function applyAppearance(opts: {
  theme: ResolvedTheme
  themeMode: ThemeMode
  brand: BrandColor
  sidebar: SidebarMode
  container: ContainerMode
}): void {
  const root = document.documentElement
  root.setAttribute('data-theme', opts.theme)
  root.setAttribute('data-sidebar', opts.sidebar.toLowerCase())
  root.setAttribute('data-container', opts.container.toLowerCase())
  for (const [k, v] of Object.entries(brandVars(opts.brand, opts.theme))) root.style.setProperty(k, v)

  try {
    localStorage.setItem(THEME_CACHE_KEY, opts.themeMode.toLowerCase())
    localStorage.setItem(BRAND_CACHE_KEY, JSON.stringify({
      light: brandVars(opts.brand, 'light'),
      dark: brandVars(opts.brand, 'dark'),
    }))
  } catch {
    // storage unavailable: appearance still applies, it just won't be pre-painted next load
  }
}

