// src/pages/fleet/PhotoBox.tsx
// A vehicle's or driver's picture. It is stored as a PHOTO document, so it comes from, and goes to, that record's document list.
// The file needs the sign-in header, so it is fetched as a blob and shown from a local address rather than linked directly.
import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Camera } from "lucide-react"
import { apiClient } from "../../api/client"
import { PHOTO_TYPES, photoProblem } from "./photo.logic"

interface DocRow { id: string }
const rowsOf = (r: { data: unknown }): DocRow[] => { const o = r.data as { data?: unknown } | null; const p = (o && typeof o === "object" && !Array.isArray(o) && "data" in o ? o.data : r.data) as DocRow[] | { content?: DocRow[] } | null; return (Array.isArray(p) ? p : p?.content) ?? [] }
/** A data address needs no clean-up, unlike an object address that must be revoked. */
const toDataUrl = (b: Blob) => new Promise<string>((ok, no) => { const r = new FileReader(); r.onload = () => ok(String(r.result)); r.onerror = () => no(r.error); r.readAsDataURL(b) })
const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "We couldn't save the picture. Try again."

export default function PhotoBox({ base, canEdit, name, size = 88, fallback }: { base: string; canEdit: boolean; name: string; size?: number; fallback?: React.ReactNode }) {
  const qc = useQueryClient(), input = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const key = ["fleet", "photo", base]
  const { data: src } = useQuery<string | null>({
    queryKey: key,
    queryFn: async () => {
      const first = rowsOf(await apiClient.get(`${base}/documents?type=PHOTO`))[0]
      if (!first) return null
      const r = await apiClient.get(`${base}/documents/${first.id}/download`, { responseType: "blob" })
      return r.data instanceof Blob ? toDataUrl(r.data) : null
    },
  })
  const upload = useMutation({
    mutationFn: (file: File) => { const f = new FormData(); f.append("file", file); f.append("type", "PHOTO"); return apiClient.post(`${base}/documents`, f) },
    onSuccess: () => { setError(null); qc.invalidateQueries({ queryKey: key }) },
    onError: e => setError(errorOf(e)),
  })
  const choose = (file: File | undefined) => {
    if (!file) return
    const p = photoProblem(file); if (p) { setError(p); return }
    setError(null); upload.mutate(file)
  }
  return (
    <div style={{ width: size, flex: "none" }}>
      <div style={{ width: size, height: size, borderRadius: 14, overflow: "hidden", background: "var(--hf-surface-sunken)", border: "1px solid var(--hf-border)", display: "grid", placeItems: "center", color: "var(--hf-text-faint)" }}>
        {src ? <img src={src} alt={`Photo of ${name}`} style={{ width: "100%", height: "100%", objectFit: "cover" }} /> : fallback ?? <Camera size={Math.round(size / 3)} aria-label="No photo yet" role="img" />}
      </div>
      {canEdit && <>
        <input ref={input} type="file" accept={PHOTO_TYPES.join(",")} aria-label={`Photo file for ${name}`} hidden onChange={e => { choose(e.target.files?.[0]); e.target.value = "" }} />
        <button type="button" disabled={upload.isPending} onClick={() => input.current?.click()} style={{ marginTop: 6, width: "100%", padding: "4px 0", fontSize: 11.5, border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", cursor: "pointer" }}>
          {upload.isPending ? "Saving…" : src ? "Change photo" : "Add photo"}
        </button>
      </>}
      {error && <div role="alert" style={{ fontSize: 11, color: "var(--hf-danger-text)", marginTop: 4 }}>{error}</div>}
    </div>)
}
