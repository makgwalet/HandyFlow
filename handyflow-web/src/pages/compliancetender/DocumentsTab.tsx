// src/pages/compliancetender/DocumentsTab.tsx
//
// The compliance document vault — a thin wrapper over EvidenceFacade on
// the backend (ComplianceDocumentController). Upload is multipart
// (registrationId optional, documentType, issueDate/expiryDate optional,
// file) — same FormData pattern already used in debtcollection/CasesTab.tsx.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { Upload, FileText, CheckCircle2, Trash2, AlertCircle, BadgeCheck } from "lucide-react"

interface Doc {
  id: string; registrationId: string | null; documentType: string; evidenceId: string
  issueDate: string | null; expiryDate: string | null; verified: boolean; verifiedAt: string | null; createdAt: string
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

export default function DocumentsTab() {
  const qc = useQueryClient()
  const canManage = usePermission("COMPLIANCE_MANAGE") || usePermission("COMPLIANCE_ADMIN")
  const canAdmin = usePermission("COMPLIANCE_ADMIN")

  const [documentType, setDocumentType] = useState("")
  const [issueDate, setIssueDate] = useState("")
  const [expiryDate, setExpiryDate] = useState("")
  const [file, setFile] = useState<File | null>(null)
  const [apiError, setApiError] = useState("")

  const { data: documents = [], isLoading } = useQuery<Doc[]>({
    queryKey: ["ct-documents"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/compliance/documents")),
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ["ct-documents"] })

  const upload = useMutation({
    mutationFn: () => {
      const fd = new FormData()
      fd.append("documentType", documentType)
      if (issueDate) fd.append("issueDate", issueDate)
      if (expiryDate) fd.append("expiryDate", expiryDate)
      fd.append("file", file as File)
      return apiClient.post("/api/v1/compliance/documents", fd, { headers: { "Content-Type": "multipart/form-data" } })
    },
    onSuccess: () => { invalidate(); setDocumentType(""); setIssueDate(""); setExpiryDate(""); setFile(null); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to upload document"),
  })

  const verify = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/compliance/documents/${id}/verify`),
    onSuccess: () => invalidate(),
  })

  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/compliance/documents/${id}`),
    onSuccess: () => invalidate(),
  })

  const inp: React.CSSProperties = { padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box" as const }

  return (
    <div>
      {canManage && (
        <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 18, marginBottom: 22 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 12 }}>Upload a document</div>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
            <div>
              <label style={lbl}>Document type *</label>
              <input value={documentType} onChange={e => setDocumentType(e.target.value)} placeholder="e.g. Tax Clearance Certificate" style={{ ...inp, width: 220 }} />
            </div>
            <div>
              <label style={lbl}>Issue date</label>
              <input type="date" value={issueDate} onChange={e => setIssueDate(e.target.value)} style={inp} />
            </div>
            <div>
              <label style={lbl}>Expiry date</label>
              <input type="date" value={expiryDate} onChange={e => setExpiryDate(e.target.value)} style={inp} />
            </div>
            <div>
              <label style={lbl}>File *</label>
              <input type="file" onChange={e => setFile(e.target.files?.[0] ?? null)} style={{ fontSize: 13 }} />
            </div>
            <button onClick={() => upload.mutate()} disabled={!file || !documentType.trim() || upload.isPending}
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "9px 16px", background: (!file || !documentType.trim()) ? "#CBD5E1" : "#0369A1", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: (!file || !documentType.trim()) ? "not-allowed" : "pointer" }}>
              <Upload size={14} /> {upload.isPending ? "Uploading..." : "Upload"}
            </button>
          </div>
          {apiError && <div style={{ marginTop: 10, display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "#DC2626" }}><AlertCircle size={13} />{apiError}</div>}
        </div>
      )}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading documents...</div>
      ) : documents.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <FileText size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No documents uploaded yet</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {documents.map(d => (
            <div key={d.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14, border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px", background: "#fff" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12, minWidth: 0 }}>
                <div style={{ width: 36, height: 36, borderRadius: 8, background: "#EFF6FF", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                  <FileText size={16} color="#0369A1" />
                </div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{d.documentType}</div>
                  <div style={{ fontSize: 11, color: "#94A3B8" }}>
                    {d.issueDate ? `Issued ${fmtDate(d.issueDate)}` : ""}{d.expiryDate ? ` · Expires ${fmtDate(d.expiryDate)}` : ""}
                  </div>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0 }}>
                {d.verified ? (
                  <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: "#DCFCE7", color: "#166534", padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: "1px solid #86EFAC" }}>
                    <BadgeCheck size={12} /> Verified
                  </span>
                ) : (
                  <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: "#FFFBEB", color: "#D97706", padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: "1px solid #FDE68A" }}>
                    Not verified
                  </span>
                )}
                {canManage && !d.verified && (
                  <button onClick={() => verify.mutate(d.id)} title="Mark verified" style={{ background: "#DCFCE7", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#166534" }}><CheckCircle2 size={13} /></button>
                )}
                {canAdmin && (
                  <button onClick={() => { if (confirm(`Delete "${d.documentType}"?`)) remove.mutate(d.id) }} title="Delete" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><Trash2 size={13} /></button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { display: "block", fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4 }
