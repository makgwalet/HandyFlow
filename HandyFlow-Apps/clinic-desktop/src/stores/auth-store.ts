import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { User } from '@/types/clinic'

interface AuthState {
  user: User | null
  isAuthenticated: boolean
  isOffline: boolean
  login: (user: User) => void
  logout: () => void
  setOffline: (offline: boolean) => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      isAuthenticated: false,
      isOffline: false,
      login: (user) => set({ user, isAuthenticated: true }),
      logout: () => set({ user: null, isAuthenticated: false }),
      setOffline: (offline) => set({ isOffline: offline }),
    }),
    { name: 'auth-storage' }
  )
)