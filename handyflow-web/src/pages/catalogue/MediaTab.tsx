// src/pages/catalogue/MediaTab.tsx — pictures and documents of one item. Files are fetched with the user's sign-in (never a bare link),
// pictures are shown as small previews and everything can be downloaded.
import { useEffect, useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ErrorBanner } from "./CatalogueKit"
import { linkBtn } from "./catalogue.styles"
import { errorMessage, fileSizeText, mediaProblem, type MediaFile } from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }

function Thumb({ file }: { file: MediaFile }) {
  const [src, setSrc] = useState<string | null>(null)
  useEffect(() => {
    let url: string | null = null
    let live = true
    if (typeof URL === "undefined" || typeof URL.createObjectURL !== "function") return
    apiClient.get(file.url, { responseType: "blob" }).then(r => { if (live) { url = URL.createObjectURL(r.data as Blob); setSrc(url) } }).catch(() => undefined)
    return () => { live = false; if (url) URL.revokeObjectURL(url) }
  }, [file.url])
  return (
    <div style={{ width: 96, height: 96, borderRadius: 8, background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border-subtle)", overflow: "hidden", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
      {src ? <img src={src} alt={file.altText || file.title || file.fileName} style={{ width: "100%", height: "100%", objectFit: "cover" }} /> : <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Picture</span>}
    </div>
  )
}

async function download(file: MediaFile) {
  const r = await apiClient.get(file.url, { responseType: "blob" })
  const url = URL.createObjectURL(r.data as Blob)
  const a = document.createElement("a")
  a.href = url; a.download = file.fileName; a.click()
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

export default function MediaTab({ itemId, canWrite }: { itemId: string; canWrite: boolean }) {
  const qc = useQueryClient()
  const key = ["catalogue", "media", itemId]
  const input = useRef<HTMLInputElement>(null)
  const [error, setError] = useState("")
  const q = useQuery<MediaFile[]>({ queryKey: key, queryFn: async () => inner<MediaFile[]>(await apiClient.get(`/api/v1/catalogue/items/${itemId}/media`)) })
  const refresh = () => qc.invalidateQueries({ queryKey: key })

  const upload = useMutation({
    mutationFn: (file: File) => {
      const body = new FormData(); body.append("file", file)
      return apiClient.post(`/api/v1/catalogue/items/${itemId}/media`, body, { headers: { "Content-Type": "multipart/form-data" } })
    },
    onSuccess: () => { setError(""); refresh() }, onError: e => setError(errorMessage(e, "Couldn't add the file.")),
  })
  const main = useMutation({
    mutationFn: (id: string) => apiClient.put(`/api/v1/catalogue/items/${itemId}/media/${id}`, { primary: true }),
    onSuccess: () => { setError(""); refresh() }, onError: e => setError(errorMessage(e, "Couldn't change the main picture.")),
  })
  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/catalogue/items/${itemId}/media/${id}`),
    onSuccess: () => { setError(""); refresh() }, onError: e => setError(errorMessage(e, "Couldn't remove the file.")),
  })

  const choose = (f: File | undefined) => {
    if (!f) return
    const problem = mediaProblem(f)
    if (problem) { setError(problem); return }
    setError(""); upload.mutate(f)
  }
  const files = q.data ?? []
  const pictures = files.filter(f => f.kind === "IMAGE")
  const documents = files.filter(f => f.kind === "DOCUMENT")

  return (
    <div>
      {q.isLoading && <p role="status">Loading…</p>}
      {q.isError && <ErrorBanner message={errorMessage(q.error, "Couldn't load the files.")} />}
      {error && <ErrorBanner message={error} />}
      {canWrite && (
        <div style={{ marginBottom: 14, display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
          <input ref={input} type="file" accept={"image/jpeg,image/png,image/webp,application/pdf"} aria-label="Choose a picture or PDF" onChange={e => { choose(e.target.files?.[0]); e.target.value = "" }} style={{ fontSize: 13 }} />
          {upload.isPending && <span role="status" style={muted}>Uploading…</span>}
          <span style={muted}>Pictures up to 5 MB (JPEG, PNG, WebP), documents up to 10 MB (PDF).</span>
        </div>
      )}
      {q.data && files.length === 0 && <p style={muted}>No pictures or documents yet.</p>}
      {pictures.length > 0 && (
        <section aria-label="Pictures" style={{ marginBottom: 16 }}>
          <h4 style={{ margin: "0 0 8px", fontSize: 13, color: "var(--hf-text)" }}>Pictures</h4>
          <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))", gap: 10 }}>
            {pictures.map(f => (
              <li key={f.id} style={{ display: "flex", gap: 10, padding: 8, border: "1px solid var(--hf-border-subtle)", borderRadius: 10 }}>
                <Thumb file={f} />
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)", overflowWrap: "anywhere" }}>{f.title || f.fileName}</div>
                  <div style={muted}>{fileSizeText(f.sizeBytes)}{f.primary ? " · Main picture" : ""}</div>
                  <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 6 }}>
                    <button type="button" style={linkBtn} onClick={() => download(f).catch(() => setError("Couldn't download the file."))}>Download</button>
                    {canWrite && !f.primary && <button type="button" style={linkBtn} disabled={main.isPending} onClick={() => main.mutate(f.id)}>Make main</button>}
                    {canWrite && <button type="button" style={linkBtn} disabled={remove.isPending} onClick={() => remove.mutate(f.id)} aria-label={`Remove ${f.fileName}`}>Remove</button>}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}
      {documents.length > 0 && (
        <section aria-label="Documents">
          <h4 style={{ margin: "0 0 8px", fontSize: 13, color: "var(--hf-text)" }}>Documents</h4>
          <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
            {documents.map(f => (
              <li key={f.id} style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "center", flexWrap: "wrap", padding: "8px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
                <span style={{ overflowWrap: "anywhere" }}>{f.title || f.fileName} <span style={muted}>· {fileSizeText(f.sizeBytes)}</span></span>
                <span style={{ display: "flex", gap: 6 }}>
                  <button type="button" style={linkBtn} onClick={() => download(f).catch(() => setError("Couldn't download the file."))}>Download</button>
                  {canWrite && <button type="button" style={linkBtn} disabled={remove.isPending} onClick={() => remove.mutate(f.id)} aria-label={`Remove ${f.fileName}`}>Remove</button>}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
