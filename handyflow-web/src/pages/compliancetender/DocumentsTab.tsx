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
import LookupInput from "../../components/ui/LookupInput"
import { DOCUMENT_TYPES } from "../../lookups/southAfrica"

interface Doc {
  id: string; registrationId: string | null; documentType: string; evidenceId: string
  issueDate: string | null; expiryDate: string | null; verified: boolean; verifiedAt: string | null; createdAt: string
}
interface RegistrationOption { id: string; authority: string; registrationType: string }

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

export default function DocumentsTab() {
  const qc = useQueryClient()
  const canManage = usePermission("COMPLIANCE_MANAGE") || usePermission("COMPLIANCE_ADMIN")
  const canAdmin = usePermission("COMPLIANCE_ADMIN")

  const [documentType, setDocumentType] = useState("")
  const [registrationId, setRegistrationId] = useState("")
  const [issueDate, setIssueDate] = useState("")
  const [expiryDate, setExpiryDate] = useState("")
  const [file, setFile] = useState<File | null>(null)
  const [apiError, setApiError] = useState("")
  const [filterRegistration, setFilterRegistration] = useState("ALL")

  const { data: documents = [], isLoading } = useQuery<Doc[]>({
    queryKey: ["ct-documents"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/compliance/documents")),
  })

  // Reuses the same /registrations/all endpoint RegistrationsTab already
  // calls — just for the picker/filter labels here, no CRUD of its own.
  const { data: registrations = [] } = useQuery<RegistrationOption[]>({
    queryKey: ["ct-registrations", "ALL"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/compliance/registrations/all")),
  })
  const registrationLabel = (id: string | null) => {
    if (!id) return null
    const r = registrations.find(reg => reg.id === id)
    return r ? `${r.authority} — ${r.registrationType}` : "Registration (details unavailable)"
  }

  const invalidate = () => qc.invalidateQueries({ queryKey: ["ct-documents"] })

  const upload = useMutation({
    mutationFn: () => {
      const fd = new FormData()
      fd.append("documentType", documentType)
      if (registrationId) fd.append("registrationId", registrationId)
      if (issueDate) fd.append("issueDate", issueDate)
      if (expiryDate) fd.append("expiryDate", expiryDate)
      fd.append("file", file as File)
      return apiClient.post("/api/v1/compliance/documents", fd, { headers: { "Content-Type": "multipart/form-data" } })
    },
    onSuccess: () => { invalidate(); setDocumentType(""); setRegistrationId(""); setIssueDate(""); setExpiryDate(""); setFile(null); setApiError("") },
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

  const inp: React.CSSProperties = { padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" as const }

  return (
    <div>
      {canManage && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 18, marginBottom: 22 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 12 }}>Upload a document</div>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
            <div>
              <label style={lbl}>Document type *</label>
              <LookupInput value={documentType} options={DOCUMENT_TYPES} onChange={setDocumentType} placeholder="e.g. Tax Clearance Certificate" style={{ ...inp, width: 220 }} />
            </div>
            <div>
              <label style={lbl}>Linked registration</label>
              <select value={registrationId} onChange={e => setRegistrationId(e.target.value)} style={{ ...inp, width: 200, background: "var(--hf-surface)" }}>
                <option value="">— None —</option>
                {registrations.map(r => <option key={r.id} value={r.id}>{r.authority} — {r.registrationType}</option>)}
              </select>
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
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "9px 16px", background: (!file || !documentType.trim()) ? "var(--hf-border-strong)" : "var(--hf-sky-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: (!file || !documentType.trim()) ? "not-allowed" : "pointer" }}>
              <Upload size={14} /> {upload.isPending ? "Uploading..." : "Upload"}
            </button>
          </div>
          {apiError && <div style={{ marginTop: 10, display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "var(--hf-danger-text)" }}><AlertCircle size={13} />{apiError}</div>}
        </div>
      )}

      {registrations.length > 0 && (
        <div style={{ display: "flex", gap: 6, marginBottom: 16, flexWrap: "wrap" }}>
          <button onClick={() => setFilterRegistration("ALL")}
            style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterRegistration === "ALL" ? 600 : 400,
              background: filterRegistration === "ALL" ? "var(--hf-sky-solid-strong)" : "var(--hf-surface-sunken)", color: filterRegistration === "ALL" ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
            All
          </button>
          <button onClick={() => setFilterRegistration("UNLINKED")}
            style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterRegistration === "UNLINKED" ? 600 : 400,
              background: filterRegistration === "UNLINKED" ? "var(--hf-sky-solid-strong)" : "var(--hf-surface-sunken)", color: filterRegistration === "UNLINKED" ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
            Not linked
          </button>
          {registrations.map(r => (
            <button key={r.id} onClick={() => setFilterRegistration(r.id)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterRegistration === r.id ? 600 : 400,
                background: filterRegistration === r.id ? "var(--hf-sky-solid-strong)" : "var(--hf-surface-sunken)", color: filterRegistration === r.id ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
              {r.authority} — {r.registrationType}
            </button>
          ))}
        </div>
      )}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading documents...</div>
      ) : documents.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "var(--hf-text-faint)" }}>
          <FileText size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No documents uploaded yet</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {documents
            .filter(d => filterRegistration === "ALL" || (filterRegistration === "UNLINKED" ? !d.registrationId : d.registrationId === filterRegistration))
            .map(d => (
            <div key={d.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14, border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px", background: "var(--hf-surface)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12, minWidth: 0 }}>
                <div style={{ width: 36, height: 36, borderRadius: 8, background: "var(--hf-info-soft)", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                  <FileText size={16} style={{ color: 'var(--hf-sky-text-strong)' }} />
                </div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 600, fontSize: 13, color: "var(--hf-text)" }}>{d.documentType}</div>
                  <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>
                    {d.issueDate ? `Issued ${fmtDate(d.issueDate)}` : ""}{d.expiryDate ? ` · Expires ${fmtDate(d.expiryDate)}` : ""}
                    {registrationLabel(d.registrationId) ? ` · ${registrationLabel(d.registrationId)}` : ""}
                  </div>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0 }}>
                {d.verified ? (
                  <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: "1px solid var(--hf-success-border)" }}>
                    <BadgeCheck size={12} /> Verified
                  </span>
                ) : (
                  <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: "var(--hf-warning-soft)", color: "var(--hf-warning-text)", padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: "1px solid var(--hf-warning-border)" }}>
                    Not verified
                  </span>
                )}
                {canManage && !d.verified && (
                  <button onClick={() => verify.mutate(d.id)} title="Mark verified" style={{ background: "var(--hf-success-soft-strong)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-success-text-strong)" }}><CheckCircle2 size={13} /></button>
                )}
                {canAdmin && (
                  <button onClick={() => { if (confirm(`Delete "${d.documentType}"?`)) remove.mutate(d.id) }} title="Delete" style={{ background: "var(--hf-danger-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-danger-text)" }}><Trash2 size={13} /></button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }
