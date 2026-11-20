import type { CSSProperties } from 'react'

/** Shared helpers for the Legal Compliance screens. */

export const input: CSSProperties = {
  width: '100%', padding: '9px 12px', boxSizing: 'border-box',
  border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 14,
  background: 'var(--hf-surface)', color: 'var(--hf-text)', outline: 'none',
}

export const label: CSSProperties = {
  display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 4,
}

export const primaryBtn: CSSProperties = {
  padding: '9px 20px', background: 'var(--hf-indigo)', color: 'var(--hf-text-on-solid)',
  border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: 'pointer',
}

export const ghostBtn: CSSProperties = {
  padding: '9px 18px', background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)',
  border: '1px solid var(--hf-border)', borderRadius: 9, fontSize: 14, cursor: 'pointer',
}

interface Envelope { data?: { data?: unknown } & Record<string, unknown> }

/** List responses: a page ({content}), a bare array, or nothing. */
export const unwrap = (r: unknown): never[] => {
  const d = (r as Envelope).data
  const p = (d?.data ?? d) as { content?: unknown } | unknown[] | undefined
  const list = (p && !Array.isArray(p) ? (p as { content?: unknown }).content : undefined) ?? p ?? []
  return (Array.isArray(list) ? list : []) as never[]
}

/** Single-object responses: unwrap the ApiResponse envelope if there is one. */
export const one = (r: unknown): any => { // eslint-disable-line @typescript-eslint/no-explicit-any
  const d = (r as Envelope).data
  return d?.data ?? d
}

export const fmtDate = (d: string | null | undefined) =>
  d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'

export const fmtDateTime = (d: string | null | undefined) =>
  d ? new Date(d).toLocaleString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—'

/** The server's message when it sent one, otherwise the fallback. */
export const errMsg = (e: unknown, fallback: string): string => {
  const d = (e as { response?: { data?: { message?: string } } })?.response?.data
  return d?.message || fallback
}
