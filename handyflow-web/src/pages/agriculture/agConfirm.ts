// src/pages/agriculture/agConfirm.ts
// A confirm question in the app's own dialog instead of the browser's: `if (await agConfirm({ title: "Delete farm?" })) ...`.
// AgConfirmHost (mounted once, by AgriculturePage) shows it. Without a host it falls back to the browser's confirm, so a call
// can never silently answer yes.

export interface ConfirmOptions { title: string; message?: string; confirmLabel?: string; danger?: boolean }
export interface PendingConfirm extends ConfirmOptions { resolve: (ok: boolean) => void }

let listener: ((p: PendingConfirm | null) => void) | null = null
let pending: PendingConfirm | null = null

/** For the host: called with the open question, or null when it is closed. Returns the unsubscribe function. */
export function subscribeConfirm(fn: (p: PendingConfirm | null) => void): () => void {
  listener = fn
  fn(pending)
  return () => { if (listener === fn) listener = null }
}

export const currentConfirm = () => pending

export function agConfirm(options: ConfirmOptions): Promise<boolean> {
  if (!listener) return Promise.resolve(typeof window !== "undefined" && window.confirm(options.title))
  pending?.resolve(false)   // a new question replaces an unanswered one, which counts as no
  return new Promise<boolean>(resolve => {
    pending = { ...options, resolve: ok => { if (pending?.resolve === resolveRef) pending = null; resolve(ok); listener?.(pending) } }
    const resolveRef = pending.resolve
    listener?.(pending)
  })
}
