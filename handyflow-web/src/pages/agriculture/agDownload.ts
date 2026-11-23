// src/pages/agriculture/agDownload.ts
//
// Download a file from the server (a CSV register or a PDF sale document). A failed download used to arrive as a Blob that nobody
// read, so the person saw nothing; here the server's own message is read out of the Blob and toasted. The file name comes from the
// server's Content-Disposition header.
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { apiErrorMessage } from "./agErrors"

/** filename="x.csv" or filename*=UTF-8''x.csv from a Content-Disposition header; the fallback when there is none. */
export function fileNameFromDisposition(header: string | null | undefined, fallback: string): string {
  if (!header) return fallback
  const star = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/.exec(header)
  if (star) { try { return decodeURIComponent(star[1].trim()) } catch { /* fall through */ } }
  const plain = /filename\s*=\s*"?([^";]+)"?/.exec(header)
  return plain ? plain[1].trim() : fallback
}

/** The message in a JSON error body that arrived as text ({"message": "..."}), or the fallback. */
export function messageFromErrorText(text: string, fallback: string): string {
  try {
    const body = JSON.parse(text) as { message?: unknown; error?: unknown }
    const m = typeof body.message === "string" ? body.message : typeof body.error === "string" ? body.error : ""
    return m.trim() || fallback
  } catch { return fallback }
}

export async function downloadFile(path: string, params: Record<string, string | undefined>, fallbackName: string): Promise<boolean> {
  try {
    const res = await apiClient.get(path, { params, responseType: "blob" })
    const name = fileNameFromDisposition((res.headers as Record<string, string | undefined>)?.["content-disposition"], fallbackName)
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement("a")
    a.href = url; a.download = name; a.click()
    URL.revokeObjectURL(url)
    return true
  } catch (e) {
    const blob = (e as { response?: { data?: unknown } })?.response?.data
    if (blob instanceof Blob) toast.error(messageFromErrorText(await blob.text(), "That download did not work. Please try again."))
    else toast.error(apiErrorMessage(e, "That download did not work. Please try again."))
    return false
  }
}
