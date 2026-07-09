// src/store/auth.store.ts

import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { User } from '../types/auth.types'

interface AuthState {
  token: string | null
  user: User | null
  isAuthenticated: boolean

  setAuth: (token: string, user: User) => void
  logout: () => void
  hasPermission: (permission: string) => boolean
}

// WHY a raw fetch() call here instead of the shared apiClient?
// client.ts imports useAuthStore from this file — importing apiClient
// back into this file would create a circular dependency between the
// two modules. This endpoint doesn't need anything apiClient provides
// anyway (no Authorization header to inject, no {success,data} wrapper
// to unwrap for a fire-and-forget call) — a plain fetch with
// credentials: 'include' is all that's actually required to send the
// httpOnly refresh cookie so the backend can revoke it.
const AUTH_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export const useAuthStore = create<AuthState>()(
  // WHY persist? Survives page refresh — user stays logged in
  persist(
    (set, get) => ({
      token: null,
      user: null,
      isAuthenticated: false,

      setAuth: (token, user) => set({
        token,
        user,
        isAuthenticated: true,
      }),

      // FIX: previously only cleared local Zustand state — the httpOnly
      // refresh_token cookie was never touched, meaning it stayed valid
      // server-side even after a "logout". A "logged out" session wasn't
      // actually terminated at all; the refresh token would just sit
      // there, usable, until it naturally expired on its own (up to 30
      // days later).
      //
      // Deliberately fire-and-forget, not awaited: local logout must
      // always succeed instantly regardless of network conditions —
      // "sign me out" should never hang or fail because a request to the
      // server timed out. Worst case on a failed call here, the refresh
      // token simply sits until it expires naturally, same as before
      // this fix — this is strictly an improvement, never a regression.
      logout: () => {
        fetch(`${AUTH_BASE_URL}/api/v1/auth/logout`, {
          method: 'POST',
          credentials: 'include',
        }).catch(() => {
          // Intentionally swallowed — see comment above.
        })

        set({
          token: null,
          user: null,
          isAuthenticated: false,
        })
      },

      hasPermission: (permission) => {
        const { user } = get()
        return user?.permissions.includes(permission) ?? false
      },
    }),
    { name: 'handyflow-auth' }
  )
)