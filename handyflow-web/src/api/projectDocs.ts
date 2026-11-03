// src/api/projectDocs.ts — staff-side helpers for stored document files (JWT via apiClient).
import { apiClient } from './client'
import { filenameFrom, saveBlob } from './recipientAccess.client'

/** Message out of an axios error, including a JSON body that arrived as a Blob. */
export async function errorText(e: any, fallback: string): Promise<string> {
  const d = e?.response?.data
  if (d instanceof Blob) { try { const m = JSON.parse(await d.text())?.message; if (m) return m } catch { /* ignore */ } }
  return (d && typeof d === 'object' && d.message) || fallback
}

/** Downloads a stored file with the staff token (a plain <a href> would carry no Authorization header). */
export async function downloadStoredDocument(docId: string, fallbackName: string) {
  const r = await apiClient.get(`/api/v1/projects/documents/${docId}/download`, { responseType: 'blob' })
  saveBlob(r.data as Blob, filenameFrom(r.headers?.['content-disposition']) || fallbackName)
}

/**
 * Uploads a file. The backend reads the "meta" part as JSON, so it MUST be sent as a Blob of type
 * application/json — a plain string part would be sent as text/plain and refused with 415.
 */
export async function uploadStoredDocument(projectId: string, meta: Record<string, unknown>, file: File) {
  const fd = new FormData()
  fd.append('meta', new Blob([JSON.stringify(meta)], { type: 'application/json' }))
  fd.append('file', file, file.name)
  return apiClient.post(`/api/v1/projects/${projectId}/documents/files`, fd)
}
