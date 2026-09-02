// src/pages/agriculture/AgEvidenceTab.tsx
//
// Shared animal/group evidence (scouting/treatment/harvest photos) —
// confirmed via AgAnimalController/AgGroupController: attach (multipart
// POST), list, download, detach, all mirroring EvidenceFacade's standard
// shape already established across this codebase (e.g.
// TrainingSessionController's own evidence endpoints).
import type React from "react"
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Upload, Download, Trash2, FileImage } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT, fmtDateTime, type AgTargetType } from "./constants"

interface EvidenceResponse {
  id: string; fileName: string; evidenceType: string; contentType: string
  uploadedByName: string | null; uploadedAt: string
}

const EVIDENCE_TYPES = ["PHOTO", "DOCUMENT", "OTHER"]

export default function AgEvidenceTab({ targetType, targetId }: { targetType: AgTargetType; targetId: string }) {
  const qc = useQueryClient()
  const segment = targetType === "animal" ? "animals" : "groups"
  const [evidenceType, setEvidenceType] = useState("PHOTO")
  const [file, setFile] = useState<File | null>(null)

  const queryKey = ["ag-evidence", targetType, targetId]
  const { data, isLoading } = useQuery<EvidenceResponse[]>({
    queryKey,
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/${segment}/${targetId}/evidence`)).data,
  })

  const uploadMut = useMutation({
    mutationFn: () => {
      const form = new FormData()
      form.append("file", file!)
      return apiClient.post(`/api/v1/agriculture/${segment}/${targetId}/evidence`, form, {
        headers: { "Content-Type": "multipart/form-data" }, params: { evidenceType },
      })
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey }); setFile(null) },
  })
  const detachMut = useMutation({
    mutationFn: (evidenceId: string) => apiClient.post(`/api/v1/agriculture/${segment}/evidence/${evidenceId}/detach`),
    onSuccess: () => qc.invalidateQueries({ queryKey }),
  })

  const download = async (evidenceId: string, fileName: string) => {
    const res = await apiClient.get(`/api/v1/agriculture/${segment}/evidence/${evidenceId}/download`, { responseType: "blob" })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement("a"); a.href = url; a.download = fileName; a.click()
    window.URL.revokeObjectURL(url)
  }

  const evidence = data ?? []

  return (
    <div>
      <div style={{ display: "flex", alignItems: "flex-end", gap: 8, marginBottom: 14, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: 14 }}>
        <div>
          <label style={lbl}>Type</label>
          <select value={evidenceType} onChange={e => setEvidenceType(e.target.value)} style={inp}>{EVIDENCE_TYPES.map(t => <option key={t} value={t}>{t}</option>)}</select>
        </div>
        <div style={{ flex: 1 }}>
          <label style={lbl}>File</label>
          <input type="file" onChange={e => setFile(e.target.files?.[0] ?? null)} style={{ fontSize: 12 }} />
        </div>
        <button disabled={!file || uploadMut.isPending} onClick={() => uploadMut.mutate()}
          style={{ ...btnPrimary, opacity: !file || uploadMut.isPending ? 0.6 : 1 }}>
          <Upload size={13} style={{ marginRight: 4, verticalAlign: -2 }} />{uploadMut.isPending ? "Uploading…" : "Upload"}
        </button>
      </div>

      {isLoading ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>Loading…</p> :
        evidence.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 12.5 }}>No evidence attached yet.</p> : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
          {evidence.map((e, i) => (
            <div key={e.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <FileImage size={13} color={AG_ACCENT} />
                <div>
                  <p style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600, margin: 0 }}>{e.fileName}</p>
                  <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{e.evidenceType} · {fmtDateTime(e.uploadedAt)}{e.uploadedByName ? ` · ${e.uploadedByName}` : ""}</p>
                </div>
              </div>
              <div style={{ display: "flex", gap: 6 }}>
                <button onClick={() => download(e.id, e.fileName)} title="Download" style={iconBtn}><Download size={12} /></button>
                <button onClick={() => { if (confirm("Detach this evidence?")) detachMut.mutate(e.id) }} title="Detach" style={{ ...iconBtn, color: "#DC2626" }}><Trash2 size={12} /></button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { fontSize: 10.5, fontWeight: 600, color: "#374151", marginBottom: 3, display: "block" }
const inp: React.CSSProperties = { padding: "7px 9px", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 12, boxSizing: "border-box" }
const btnPrimary: React.CSSProperties = { display: "inline-flex", alignItems: "center", padding: "8px 14px", borderRadius: 7, border: "none", background: AG_ACCENT, color: "#fff", fontSize: 12, fontWeight: 700, cursor: "pointer" }
const iconBtn: React.CSSProperties = { display: "flex", alignItems: "center", justifyContent: "center", width: 24, height: 24, borderRadius: 6, border: "1px solid #E2E8F0", background: "#fff", color: "#64748B", cursor: "pointer" }
