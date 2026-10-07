// src/pages/security/EvidenceFiles.tsx
//
// A list of evidence files with download, remove and upload. Used for screening records and competencies.
// `baseUrl` is the collection URL (".../evidence"); files download from `${baseUrl}/${id}/download`.
import { useRef, useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { Download, Paperclip, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import type { EvidenceItem } from "./guard360.logic"

const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", boxSizing: "border-box" }
const size = (n: number) => n >= 1048576 ? `${(n / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`

export default function EvidenceFiles({ baseUrl, items, canManage, emptyHint, onChanged, onError }: {
  baseUrl: string; items: EvidenceItem[]; canManage: boolean; emptyHint: string
  onChanged: () => void; onError: (message: string) => void
}) {
  const fileRef = useRef<HTMLInputElement>(null)
  const [label, setLabel] = useState("")
  const fail = (e: any) => onError(e?.response?.data?.message ?? "That did not work. Please try again.")

  const upload = useMutation({
    mutationFn: (file: File) => { const f = new FormData(); f.append("file", file); if (label.trim()) f.append("label", label.trim()); return apiClient.post(baseUrl, f) },
    onSuccess: () => { setLabel(""); if (fileRef.current) fileRef.current.value = ""; onChanged() }, onError: fail,
  })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`${baseUrl}/${id}`), onSuccess: onChanged, onError: fail })
  async function download(ev: EvidenceItem) {
    try {
      const r = await apiClient.get(`${baseUrl}/${ev.id}/download`, { responseType: "blob" })
      const url = URL.createObjectURL(r.data)
      const a = document.createElement("a"); a.href = url; a.download = ev.fileName; a.click(); URL.revokeObjectURL(url)
    } catch (e) { fail(e) }
  }

  return (
    <div>
      {items.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{emptyHint}</div> :
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 6 }}>
          {items.map(e => (
            <li key={e.id} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, flexWrap: "wrap" }}>
              <Paperclip size={13} />
              <span style={{ overflowWrap: "anywhere" }}>{e.fileName}</span>
              <span style={{ color: "var(--hf-text-muted)" }}>{e.label ? `${e.label} · ` : ""}{size(e.sizeBytes)}{e.uploadedByName ? ` · ${e.uploadedByName}` : ""}</span>
              <button aria-label={`Download ${e.fileName}`} onClick={() => download(e)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-accent-text)" }}><Download size={14} /></button>
              {canManage && <button aria-label={`Remove ${e.fileName}`} onClick={() => remove.mutate(e.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-danger-text)" }}><Trash2 size={14} /></button>}
            </li>
          ))}
        </ul>}
      {canManage && (
        <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap", alignItems: "center" }}>
          <input aria-label="Evidence label" placeholder="What is this file? (optional)" value={label} onChange={e => setLabel(e.target.value)} style={{ ...input, width: 220 }} />
          <input ref={fileRef} aria-label="Evidence file" type="file" onChange={e => { const f = e.target.files?.[0]; if (f) upload.mutate(f) }} style={{ fontSize: 12 }} />
        </div>
      )}
    </div>
  )
}
