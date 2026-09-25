// src/theme/resolve.ts
// Client-side mirror of the backend UiPreferencesResolver, used for
// optimistic updates so the UI changes instantly while the save is in flight.
import type {
  EffectiveUiPreferences, TenantUiDefaults, UserUiOverrides,
} from '../types/uiPreferences.types'

export const SYSTEM_DEFAULTS: TenantUiDefaults = {
  brandColor: 'NAVY',
  brandLocked: false,
  defaultTheme: 'LIGHT',
  defaultSidebar: 'FULL',
  defaultContainer: 'BOXED',
}

export const EMPTY_OVERRIDES: UserUiOverrides = {
  theme: null, sidebar: null, container: null, brandColor: null, pinnedModules: [],
}

export function resolveEffective(tenant: TenantUiDefaults, user: UserUiOverrides | null): EffectiveUiPreferences {
  return {
    theme: user?.theme ?? tenant.defaultTheme,
    sidebar: user?.sidebar ?? tenant.defaultSidebar,
    container: user?.container ?? tenant.defaultContainer,
    brandColor: tenant.brandLocked ? tenant.brandColor : (user?.brandColor ?? tenant.brandColor),
    brandLocked: tenant.brandLocked,
    pinnedModules: user?.pinnedModules ?? [],
  }
}
