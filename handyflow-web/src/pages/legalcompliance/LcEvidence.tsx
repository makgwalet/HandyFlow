// src/pages/legalcompliance/LcEvidence.tsx
//
// Files attached to a record through the shared evidence API (POST /api/v1/evidence, sourceModule=legalcompliance). The list comes from the
// owning module's own endpoint (filesUrl) so it shows only what is attached now. Used for filing proof and third-party item evidence.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { Paperclip, Download, X } from "lucide-react"
import { apiClient } from "../../api/client"
import { ghostBtn, unwrap, errMsg } from "./lcHelpers"

interface EvidenceRef { id: string; fileName: string; fileSizeBytes: number; uploadedByName: string | null }
const kb = (n: number) => (n < 1024 ? `${n} B` : n < 1048576 ? `${(n / 1024).toFixed(0)} KB` : `${(n / 1048576).toFixed(1)} MB`)

export function EvidenceFiles({ filesUrl, entityType, entityId, evidenceType, canEdit, onChange }: {
  filesUrl: string; entityType: string; entityId: string; evidenceType: string; canEdit: boolean; onChange?: () => void
}) {
  const qc = useQueryClient()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const key = ["lc-evidence", filesUrl]
  const { data: files = [] } = useQuery<EvidenceRef[]>({ queryKey: key, queryFn: async () => unwrap(await apiClient.get(filesUrl)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: key }); onChange?.() }

  const upload = async (file: File) => {
    setBusy(true); setError("")
    try {
      const fd = new FormData()
      fd.append("file", file); fd.append("evidenceType", evidenceType); fd.append("sourceModule", "legalcompliance"); fd.append("relatedEntityType", entityType); fd.append("relatedEntityId", entityId)
      await apiClient.post("/api/v1/evidence", fd, { headers: { "Content-Type": "multipart/form-data" } })
      refresh()
    } catch (e) { setError(errMsg(e, "Failed to upload file")) } finally { setBusy(false) }
  }
  const detach = async (id: string) => {
    setError("")
    try { await apiClient.post(`/api/v1/evidence/${id}/detach`); refresh() } catch (e) { setError(errMsg(e, "Failed to remove file")) }
  }
  const download = async (f: EvidenceRef) => {
    const r = await apiClient.get(`/api/v1/evidence/${f.id}/download`, { responseType: "blob" })
    const url = URL.createObjectURL(r.data); const a = document.createElement("a"); a.href = url; a.download = f.fileName; a.click(); URL.revokeObjectURL(url)
  }

  return (
    <div style={{ margin: "6px 0" }}>
      <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 4 }}><Paperclip size={12} style={{ verticalAlign: -1 }} /> Files ({files.length})</div>
      {files.map(f => (
        <div key={f.id} style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 12, padding: "2px 0" }}>
          <button onClick={() => download(f)} style={{ all: "unset", cursor: "pointer", color: "#4338CA", display: "flex", gap: 4, alignItems: "center" }}><Download size={12} /> {f.fileName}</button>
          <span style={{ color: "#94A3B8" }}>{kb(f.fileSizeBytes)}{f.uploadedByName ? ` · ${f.uploadedByName}` : ""}</span>
          {canEdit && <button aria-label="Remove file" onClick={() => { if (confirm("Remove this file from the record? The file itself is preserved.")) detach(f.id) }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={12} /></button>}
        </div>
      ))}
      {canEdit && (
        <label style={{ ...ghostBtn, display: "inline-block", marginTop: 4, fontSize: 12, padding: "5px 12px" }}>{busy ? "Uploading…" : "Attach file"}
          <input type="file" style={{ display: "none" }} disabled={busy} onChange={e => { const f = e.target.files?.[0]; if (f) upload(f); e.target.value = "" }} /></label>
      )}
      {error && <div style={{ color: "#DC2626", fontSize: 12, marginTop: 4 }}>{error}</div>}
    </div>
  )
}
