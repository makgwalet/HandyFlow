// src/hooks/useAuth.ts
//
// Was uploaded empty — built fresh against the real, confirmed
// src/store/auth.store.ts (Zustand + persist, key 'handyflow-auth').
//
// WHY a thin hook wrapping the store instead of pages importing
// useAuthStore directly? DashboardPage.tsx currently imports
// useAuthStore directly, which works, but every other cross-cutting
// concern in this codebase (queries, mutations) is wrapped in a
// purpose-named hook. This hook exists so new module pages have one
// canonical, documented entry point for "who is logged in" — and so
// that if the store's shape ever changes (e.g. splitting `user` into
// `profile` + `tenant`), only this file needs to change, not every
// page that reads auth state.
//
// Deliberately NOT a selector-per-field hook (e.g. useUser(), useToken())
// — the store is small enough that returning the whole slice is fine,
// and matches how DashboardPage.tsx already destructures
// `{ user, logout } = useAuthStore()`.
import { useAuthStore } from '../store/auth.store'

export function useAuth() {
  const token = useAuthStore(s => s.token)
  const user = useAuthStore(s => s.user)
  const isAuthenticated = useAuthStore(s => s.isAuthenticated)
  const setAuth = useAuthStore(s => s.setAuth)
  const logout = useAuthStore(s => s.logout)

  return {
    token,
    user,
    isAuthenticated,
    setAuth,
    logout,
    // Convenience passthroughs used a lot in header/greeting UI
    // (see DashboardPage.tsx's getGreeting(user?.firstName) pattern).
    firstName: user?.firstName,
    lastName: user?.lastName,
    fullName: user ? `${user.firstName} ${user.lastName}` : undefined,
    email: user?.email,
    tenantId: user?.tenantId,
  }
}