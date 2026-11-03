// src/api/recipientAccess.client.ts
//
// Client for the PUBLIC secure-link pages (/transmittal/:token and /collaborator/:token).
//
// Deliberately NOT the shared apiClient: that one attaches the staff JWT and, on 401/403, refreshes the
// token and then logs the user out. Here a 401 is a NORMAL answer ("enter your code first" or "your
// session ended") and must never touch a staff login. This client sends nothing but the link token in the
// path and, once the code is verified, the short session secret in X-Recipient-Session.
//
// The session secret is kept in memory only (never localStorage): closing the tab ends it.
import axios, { AxiosError } from 'axios'

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'
export const SESSION_HEADER = 'X-Recipient-Session'

export const recipientHttp = axios.create({ baseURL: BASE_URL, headers: { 'Content-Type': 'application/json' } })

export type AccessErrorKind = 'not-found' | 'gone' | 'needs-code' | 'bad-code' | 'rate-limited' | 'other'
export interface AccessError { kind: AccessErrorKind; message: string; status: number | null }

/** Reads a message out of a JSON error body, including one that arrived as a Blob (failed downloads). */
async function messageOf(err: AxiosError): Promise<string | undefined> {
  const d: any = err.response?.data
  if (!d) return undefined
  if (d instanceof Blob) {
    try { return JSON.parse(await d.text())?.message } catch { return undefined }
  }
  return typeof d === 'object' ? d.message : undefined
}

export async function toAccessError(e: unknown): Promise<AccessError> {
  if (!axios.isAxiosError(e)) return { kind: 'other', message: 'Something went wrong. Please try again.', status: null }
  if (!e.response) return { kind: 'other', message: 'Could not reach the server. Check your connection and try again.', status: null }
  const status = e.response.status
  const msg = await messageOf(e)
  switch (status) {
    case 404: return { kind: 'not-found', status, message: 'This link is not valid. Check that you copied the whole address from the email.' }
    case 410: return { kind: 'gone', status, message: msg || 'This link has expired or been withdrawn. Ask the sender for a new one.' }
    case 401: return { kind: 'needs-code', status, message: msg || 'Please confirm your email address with the code we send you.' }
    case 422: return { kind: 'bad-code', status, message: msg || 'That code is not correct.' }
    case 400: return { kind: 'bad-code', status, message: msg || 'Enter the six-digit code.' }
    case 429: return { kind: 'rate-limited', status, message: msg || 'Too many attempts. Please wait a few minutes and try again.' }
    default:  return { kind: 'other', status, message: msg || 'Something went wrong. Please try again.' }
  }
}

/** `base` is e.g. /api/public/projects/transmittals/<token>. The token only ever goes in the path. */
export function accessApi(base: string, getSession: () => string | null) {
  const headers = () => { const s = getSession(); return s ? { [SESSION_HEADER]: s } : {} }
  const unwrap = <T,>(r: { data: any }): T => (r.data && typeof r.data === 'object' && 'data' in r.data ? r.data.data : r.data) as T
  return {
    requestCode: async () => unwrap<{ sentTo: string; validMinutes: number }>(await recipientHttp.post(`${base}/otp`)),
    verify: async (code: string) => unwrap<{ session: string; expiresAt: string }>(await recipientHttp.post(`${base}/verify`, { code })),
    get: async <T,>(path = '') => unwrap<T>(await recipientHttp.get(`${base}${path}`, { headers: headers() })),
    post: async <T,>(path: string, body?: unknown) => unwrap<T>(await recipientHttp.post(`${base}${path}`, body ?? {}, { headers: headers() })),
    /** Fetches the file with the session header, then saves it from a temporary object URL. */
    download: async (path: string, fallbackName: string) => {
      const r = await recipientHttp.get(`${base}${path}`, { headers: headers(), responseType: 'blob' })
      saveBlob(r.data as Blob, filenameFrom(r.headers['content-disposition']) || fallbackName)
    },
  }
}

export function filenameFrom(disposition?: string | null): string | null {
  if (!disposition) return null
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (star) { try { return decodeURIComponent(star[1]) } catch { /* fall through */ } }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain ? plain[1] : null
}

export function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = name; a.rel = 'noopener'
  document.body.appendChild(a); a.click(); a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
