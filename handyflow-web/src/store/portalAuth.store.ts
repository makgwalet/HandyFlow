// src/store/portalAuth.store.ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * Deliberately a SEPARATE store and a SEPARATE localStorage key from
 * the staff useAuthStore — a portal session and a staff session in the
 * same browser must never be able to cross-contaminate a request.
 * Matches the backend's own separate-JWT-secret, separate-filter
 * design (PortalJwtService/PortalJwtFilter) for exactly the same
 * reason.
 *
 * Shape is deliberately minimal — portalUserId, email, fullName only.
 * No tenantId, no permissions array: the portal JWT itself carries
 * none of that (see PortalJwtService's own reasoning), and which
 * client/tenant a specific request is about is resolved server-side
 * against the grants table, never assumed client-side.
 */
export interface PortalUser {
  portalUserId: string
  email: string
  fullName: string
}

interface PortalAuthState {
  token: string | null
  user: PortalUser | null
  setAuth: (token: string, user: PortalUser) => void
  logout: () => void
}

export const usePortalAuthStore = create<PortalAuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => set({ token, user }),
      logout: () => set({ token: null, user: null }),
    }),
    { name: 'accountant-portal-auth' }
  )
)
