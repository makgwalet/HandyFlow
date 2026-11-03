// src/stores/auth-store.ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { setAccessToken } from '@/api/client'

export interface User {
  userId: string
  tenantId: string
  email: string
  firstName: string
  lastName: string
  permissions: string[]
}

interface AuthState {
  user: User | null
  isAuthenticated: boolean
  isLoading: boolean
  setUser: (user: User | null, token: string | null) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      isAuthenticated: false,
      isLoading: true,
      setUser: (user, token) => {
        setAccessToken(token)
        set({ user, isAuthenticated: !!user, isLoading: false })
      },
      logout: () => {
        setAccessToken(null)
        set({ user: null, isAuthenticated: false, isLoading: false })
      },
    }),
    {
      name: 'hf-auth',
      partialize: (state) => ({ user: state.user, isAuthenticated: state.isAuthenticated }),
    }
  )
)