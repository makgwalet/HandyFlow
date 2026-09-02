// src/hooks/usePermission.ts
//
// Was uploaded empty — built fresh against the real, confirmed
// src/store/auth.store.ts, which already has a working
// hasPermission(permission) method reading user.permissions (a flat
// string[] on the JWT-derived User, e.g. "LEGALCOMPLIANCE_READ").
//
// Every tab-shell page built for the new modules (Legal/Compliance,
// Debt Collection, Warehousing, etc.) needs to gate action buttons —
// "New Obligation", "Close Matter" — behind the *_MANAGE/*_ADMIN
// permission the backend's @PreAuthorize already enforces server-side.
// This hook is the client-side mirror of that check: it does not
// replace server-side authorization, it just avoids showing a button
// whose click would 403.
//
// Subscribing via a selector (useAuthStore(s => ...)) rather than
// calling get() means a component using usePermission() re-renders
// correctly if the store's `user` changes (e.g. after a token refresh
// that rotates permissions) — get() would silently read a stale
// snapshot outside React's render cycle.
import { useAuthStore } from '../store/auth.store'

/** True if the current user has the given permission (e.g. 'LEGALCOMPLIANCE_MANAGE'). */
export function usePermission(permission: string): boolean {
  return useAuthStore(s => s.user?.permissions.includes(permission) ?? false)
}

/**
 * True if the current user has at least one (mode: 'any', default) or
 * every (mode: 'all') permission in the list. Useful for a tab that's
 * visible if the user can do ANY of several related actions, or a
 * button that requires several permissions at once.
 */
export function usePermissions(permissions: string[], mode: 'any' | 'all' = 'any'): boolean {
  return useAuthStore(s => {
    const userPerms = s.user?.permissions ?? []
    return mode === 'all'
      ? permissions.every(p => userPerms.includes(p))
      : permissions.some(p => userPerms.includes(p))
  })
}