import { create } from 'zustand'

interface UIState {
  sidebarOpen: boolean
  currentPage: string
  toast: { message: string; type: 'success' | 'error' | 'info' } | null
  setSidebarOpen: (open: boolean) => void
  setCurrentPage: (page: string) => void
  showToast: (message: string, type?: 'success' | 'error' | 'info') => void
  clearToast: () => void
}

export const useUIStore = create<UIState>((set) => ({
  sidebarOpen: true,
  currentPage: 'Dashboard',
  toast: null,
  setSidebarOpen: (open) => set({ sidebarOpen: open }),
  setCurrentPage: (page) => set({ currentPage: page }),
  showToast: (message, type = 'info') => set({ toast: { message, type } }),
  clearToast: () => set({ toast: null }),
}))