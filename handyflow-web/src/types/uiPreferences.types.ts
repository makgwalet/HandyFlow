// src/types/uiPreferences.types.ts
// Mirrors UiPreferencesResponse and the update requests in the identity module.
import type { BrandColor } from '../styles/brand'

export type ThemeMode = 'LIGHT' | 'DARK' | 'SYSTEM'
export type SidebarMode = 'FULL' | 'MINI'
export type ContainerMode = 'BOXED' | 'FULL'

export interface EffectiveUiPreferences {
  theme: ThemeMode
  sidebar: SidebarMode
  container: ContainerMode
  brandColor: BrandColor
  brandLocked: boolean
  pinnedModules: string[]
}

export interface TenantUiDefaults {
  brandColor: BrandColor
  brandLocked: boolean
  defaultTheme: ThemeMode
  defaultSidebar: SidebarMode
  defaultContainer: ContainerMode
}

/** null in any field = inherit the tenant default */
export interface UserUiOverrides {
  theme: ThemeMode | null
  sidebar: SidebarMode | null
  container: ContainerMode | null
  brandColor: BrandColor | null
  pinnedModules: string[]
}

export interface UiPreferences {
  effective: EffectiveUiPreferences
  tenant: TenantUiDefaults
  /** null during a read-only support session */
  user: UserUiOverrides | null
  canEditTenant: boolean
}

export type UpdateUserUiPreferencesRequest = UserUiOverrides

export interface UpdateTenantUiPreferencesRequest {
  brandColor: BrandColor
  brandLocked: boolean
  defaultTheme: ThemeMode
  defaultSidebar: SidebarMode
  defaultContainer: ContainerMode
}
