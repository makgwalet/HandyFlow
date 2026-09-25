// src/theme/ThemeContext.ts
import { createContext, useContext } from 'react'
import type {
  EffectiveUiPreferences, TenantUiDefaults, UpdateTenantUiPreferencesRequest, UserUiOverrides,
} from '../types/uiPreferences.types'
import type { ResolvedTheme } from './applyTheme'

export interface ThemeContextValue {
  /** What is applied right now (includes optimistic changes). */
  prefs: EffectiveUiPreferences
  resolvedTheme: ResolvedTheme
  tenant: TenantUiDefaults
  /** The user's raw overrides; null during a read-only support session. */
  user: UserUiOverrides | null
  canEditTenant: boolean
  /** True during an admin read-only support session: changes are not saved. */
  isSupportSession: boolean
  isLoaded: boolean
  isSaving: boolean
  /** Last save error message, cleared on the next successful save. */
  saveError: string | null
  /** Merge a change into the user's overrides and save. Pass null to inherit. */
  updateMine: (change: Partial<UserUiOverrides>) => void
  updateTenant: (req: UpdateTenantUiPreferencesRequest) => Promise<void>
}

export const ThemeContext = createContext<ThemeContextValue | null>(null)

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be used inside <ThemeProvider>')
  return ctx
}

/**
 * Literal colour values for libraries that cannot use CSS variables
 * (recharts, leaflet, canvas):
 *   const color = useThemeColors()
 *   <Line stroke={color('primary')} />
 * Components using this re-render when theme or brand changes (they consume
 * the theme context), so the values they read are always current.
 */
export function useThemeColors(): (token: string) => string {
  useTheme()
  return readToken
}

function readToken(token: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(`--hf-${token}`).trim()
}
