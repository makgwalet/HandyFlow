// src/store/toast.store.ts
//
// App-wide toasts. Anything that can fail for the user (a save, a move, a load) should say so,
// instead of catching the error and showing nothing. <Toaster /> renders these (mounted in AppShell).
import { create } from 'zustand'

export type ToastKind = 'success' | 'error' | 'info'
export interface Toast { id: number; kind: ToastKind; message: string }

interface ToastState {
  toasts: Toast[]
  push: (kind: ToastKind, message: string) => void
  dismiss: (id: number) => void
}

let nextId = 1
const MAX_VISIBLE = 4

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],
  push: (kind, message) => {
    const id = nextId++
    set(s => ({ toasts: [...s.toasts, { id, kind, message }].slice(-MAX_VISIBLE) }))
    // errors stay a little longer: they are the ones people need time to read
    setTimeout(() => get().dismiss(id), kind === 'error' ? 7000 : 3500)
  },
  dismiss: id => set(s => ({ toasts: s.toasts.filter(t => t.id !== id) })),
}))

/** Call from anywhere, including outside React (e.g. a mutation's onError). */
export const toast = {
  success: (message: string) => useToastStore.getState().push('success', message),
  error:   (message: string) => useToastStore.getState().push('error', message),
  info:    (message: string) => useToastStore.getState().push('info', message),
}

/** A readable message from a failed request (axios error) or anything else that was thrown. */
export function errorMessage(e: unknown, fallback = 'Something went wrong. Please try again.'): string {
  const err = e as { response?: { status?: number; data?: { message?: string } }; message?: string; code?: string }
  const status = err?.response?.status
  if (status === 403) return "You don't have permission to do that."
  if (status === 401) return 'Your session has expired. Please sign in again.'
  const serverMessage = err?.response?.data?.message
  // the API turns unexpected server errors into this generic line; say what it means for the user
  if (status && status >= 500) return serverMessage && !/unexpected error/i.test(serverMessage)
    ? serverMessage : 'The server had a problem with that request. Please try again.'
  if (serverMessage) return serverMessage
  if (!err?.response && (err?.code === 'ERR_NETWORK' || /network/i.test(err?.message ?? ''))) {
    return "Can't reach the server. Check your connection and try again."
  }
  return fallback
}
