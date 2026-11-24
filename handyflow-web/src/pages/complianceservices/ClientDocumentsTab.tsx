// src/pages/complianceservices/ClientDocumentsTab.tsx
//
// Client-scoped document vault — mirrors compliancetender's own
// DocumentsTab.tsx, confirmed against ClientComplianceDocumentController
// (multipart upload, registrationId optional).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission, usePermissions } from "../../hooks/usePermission"
import { Upload, FileText, CheckCircle2, Trash2, AlertCircle, BadgeCheck } from "lucide-react"
import Chip from "../../components/ui/Chip"
import LookupInput from "../../components/ui/LookupInput"
import { DOCUMENT_TYPES } from "../../lookups/southAfrica"
import { documentState } from "../compliancetender/registration.logic"
import { useAuth } from "../../hooks/useAuth"
import { needsVerifyException, verifyBlocker, statusText, isRetired, canReview, canVerify, canReject, canReplace, canArchive, reasonBlocker } from "./documentRules"

interface Doc {
  id: string; registrationId: string | null; documentType: string; evidenceId: string
  issueDate: string | null; expiryDate: string | null; verified: boolean; verifiedAt: string | null; createdAt: string
  uploadedBy?: string | null; verifiedBy?: string | null; verifiedByUploader?: boolean
  status: string; rejectionReason?: string | null; archiveReason?: string | null; supersededById?: string | null; canDelete?: boolean
}
interface DocEvent { event: string; fromStatus: string | null; toStatus: string; reason: string | null; occurredAt: string }
interface RegistrationOption { id: string; authority: string; registrationType: string }

const ACCENT = "var(--hf-success-solid-strong)"
const ACCENT_TEXT = "var(--hf-success-text-strong)";
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

export default function ClientDocumentsTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const canAdmin = usePermission("COMPLIANCE_SERVICES_ADMIN")
  const { user } = useAuth()
  const [verifyingId, setVerifyingId] = useState<string | null>(null)
  const [exceptionReason, setExceptionReason] = useState("")
  const [verifyError, setVerifyError] = useState("")

  const [documentType, setDocumentType] = useState("")
  const [registrationId, setRegistrationId] = useState("")
  const [issueDate, setIssueDate] = useState("")
  const [expiryDate, setExpiryDate] = useState("")
  const [file, setFile] = useState<File | null>(null)
  const [apiError, setApiError] = useState("")
  const [filterRegistration, setFilterRegistration] = useState("ALL")
  const [replacing, setReplacing] = useState<Doc | null>(null)
  const [reasonFor, setReasonFor] = useState<{ id: string; kind: "reject" | "archive" } | null>(null)
  const [reasonText, setReasonText] = useState("")
  const [showRetired, setShowRetired] = useState(false)
  const [historyId, setHistoryId] = useState<string | null>(null)
  const [stepError, setStepError] = useState("")

  const { data: documents = [], isLoading } = useQuery<Doc[]>({
    queryKey: ["cs-documents", clientId],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/compliance-services/clients/${clientId}/documents`)
      return r.data?.data ?? r.data ?? []
    },
  })

  const { data: registrations = [] } = useQuery<RegistrationOption[]>({
    queryKey: ["cs-registrations", clientId],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/compliance-services/clients/${clientId}/registrations`)
      return r.data?.data ?? r.data ?? []
    },
  })
  const registrationLabel = (id: string | null) => {
    if (!id) return null
    const r = registrations.find(reg => reg.id === id)
    return r ? `${r.authority} — ${r.registrationType}` : "Registration (details unavailable)"
  }

  const invalidate = () => qc.invalidateQueries({ queryKey: ["cs-documents", clientId] })
  const { data: events = [] } = useQuery<DocEvent[]>({
    queryKey: ["cs-document-events", historyId], enabled: !!historyId,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/compliance-services/documents/${historyId}/events`); return r.data?.data ?? r.data ?? [] },
  })
  const stepFail = (e: { response?: { data?: { message?: string } } }) => setStepError(e.response?.data?.message ?? "That did not work")
  const stepDone = () => { setStepError(""); setReasonFor(null); setReasonText(""); invalidate(); qc.invalidateQueries({ queryKey: ["cs-document-events"] }) }
  const review = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/compliance-services/documents/${id}/review`, {}), onSuccess: stepDone, onError: stepFail })
  const withReason = useMutation({
    mutationFn: (v: { id: string; kind: "reject" | "archive"; reason: string }) => apiClient.post(`/api/v1/compliance-services/documents/${v.id}/${v.kind}`, { reason: v.reason }),
    onSuccess: stepDone, onError: stepFail,
  })

  const upload = useMutation({
    mutationFn: () => {
      const fd = new FormData()
      fd.append("documentType", documentType)
      if (registrationId) fd.append("registrationId", registrationId)
      if (replacing) fd.append("replacesId", replacing.id)
      if (issueDate) fd.append("issueDate", issueDate)
      if (expiryDate) fd.append("expiryDate", expiryDate)
      fd.append("file", file as File)
      return apiClient.post(`/api/v1/compliance-services/clients/${clientId}/documents`, fd, { headers: { "Content-Type": "multipart/form-data" } })
    },
    onSuccess: () => { invalidate(); setReplacing(null); setDocumentType(""); setRegistrationId(""); setIssueDate(""); setExpiryDate(""); setFile(null); setApiError("") },
    onError: (e: { response?: { data?: { message?: string } } }) => setApiError(e.response?.data?.message ?? "Failed to upload document"),
  })

  const verify = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string | null }) =>
      apiClient.post(`/api/v1/compliance-services/documents/${id}/verify`, { exceptionReason: reason }),
    onSuccess: () => { setVerifyingId(null); setExceptionReason(""); setVerifyError(""); invalidate() },
    onError: (e: { response?: { data?: { message?: string } } }) => setVerifyError(e.response?.data?.message ?? "Failed to verify the document"),
  })

  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/compliance-services/documents/${id}`),
    onSuccess: () => invalidate(),
  })

  const inp: React.CSSProperties = { padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" as const, background: "var(--hf-surface)", color: "var(--hf-text)" }

  return (
    <div>
      {canManage && (
        <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 18, marginBottom: 22 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 12 }}>{replacing ? `Upload a replacement for ${replacing.documentType}` : "Upload a document"}</div>
          {replacing && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 10 }}>The earlier document stays on record but stops counting as evidence. <button onClick={() => setReplacing(null)} style={{ border: "none", background: "transparent", color: "var(--hf-primary-text)", cursor: "pointer", fontSize: 12 }}>Cancel replacement</button></div>}
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
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "9px 16px", background: (!file || !documentType.trim()) ? "var(--hf-border-strong)" : ACCENT, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: (!file || !documentType.trim()) ? "not-allowed" : "pointer" }}>
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
              background: filterRegistration === "ALL" ? ACCENT : "var(--hf-surface-sunken)", color: filterRegistration === "ALL" ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
            All
          </button>
          <button onClick={() => setFilterRegistration("UNLINKED")}
            style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterRegistration === "UNLINKED" ? 600 : 400,
              background: filterRegistration === "UNLINKED" ? ACCENT : "var(--hf-surface-sunken)", color: filterRegistration === "UNLINKED" ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
            Not linked
          </button>
          {registrations.map(r => (
            <button key={r.id} onClick={() => setFilterRegistration(r.id)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterRegistration === r.id ? 600 : 400,
                background: filterRegistration === r.id ? ACCENT : "var(--hf-surface-sunken)", color: filterRegistration === r.id ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
              {r.authority} — {r.registrationType}
            </button>
          ))}
        </div>
      )}

      {stepError && <div role="alert" style={{ marginBottom: 10, fontSize: 12, color: "var(--hf-danger-text)" }}>{stepError}</div>}
      {documents.some(d => isRetired(d.status)) && (
        <label style={{ display: "inline-flex", gap: 6, alignItems: "center", fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 12 }}>
          <input type="checkbox" checked={showRetired} onChange={e => setShowRetired(e.target.checked)} /> Show replaced and archived documents
        </label>
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
            .filter(d => showRetired || !isRetired(d.status))
            .filter(d => filterRegistration === "ALL" || (filterRegistration === "UNLINKED" ? !d.registrationId : d.registrationId === filterRegistration))
            .map(d => (
            <div key={d.id} style={{ display: "flex", flexWrap: "wrap", alignItems: "center", justifyContent: "space-between", gap: 14, border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px", background: "var(--hf-surface)" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12, minWidth: 0 }}>
                <div style={{ width: 36, height: 36, borderRadius: 8, background: "var(--hf-success-soft)", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                  <FileText size={16} style={{ color: ACCENT_TEXT }} />
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
                {d.status === "VERIFIED" || d.status === "UPLOADED" || d.status === "UNDER_REVIEW"
                  ? (() => { const st = documentState(d, new Date()); return <Chip tone={st.tone} icon={d.verified && st.text.startsWith("Verified") ? <BadgeCheck size={12} /> : undefined}>{st.text}</Chip> })()
                  : <Chip tone={d.status === "REJECTED" ? "bad" : "neutral"}>{statusText(d.status)}</Chip>}
                {d.status === "UNDER_REVIEW" && <Chip tone="neutral">Under review</Chip>}
                {canManage && canReview(d.status) && <button onClick={() => review.mutate(d.id)} style={miniBtn}>Send for review</button>}
                {canManage && canReject(d.status) && <button onClick={() => { setStepError(""); setReasonText(""); setReasonFor({ id: d.id, kind: "reject" }) }} style={miniBtn}>Reject</button>}
                {canManage && canReplace(d.status) && <button onClick={() => { setReplacing(d); setDocumentType(d.documentType); setRegistrationId(d.registrationId ?? ""); window.scrollTo?.({ top: 0 }) }} style={miniBtn}>Replace</button>}
                {canManage && canArchive(d.status) && <button onClick={() => { setStepError(""); setReasonText(""); setReasonFor({ id: d.id, kind: "archive" }) }} style={miniBtn}>Archive</button>}
                <button onClick={() => setHistoryId(historyId === d.id ? null : d.id)} style={miniBtn}>History</button>
                {canManage && canVerify(d.status) && !d.verified && (
                  <button onClick={() => { setVerifyError(""); if (needsVerifyException(d, user?.userId)) { setVerifyingId(verifyingId === d.id ? null : d.id); setExceptionReason("") } else verify.mutate({ id: d.id, reason: null }) }} title="Mark verified" aria-label="Mark verified" style={{ display: "inline-flex", alignItems: "center", background: "var(--hf-success-soft-strong)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-success-text-strong)" }}><CheckCircle2 size={13} /> <span style={{ marginLeft: 4, fontSize: 12, fontWeight: 700 }}>Verify</span></button>
                )}
                {canAdmin && d.canDelete && (
                  <button onClick={() => { if (confirm(`Delete "${d.documentType}"?`)) remove.mutate(d.id) }} title="Delete" style={{ background: "var(--hf-danger-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-danger-text)" }}><Trash2 size={13} /></button>
                )}
              </div>
              {(d.rejectionReason || d.archiveReason) && (
                <div style={{ flexBasis: "100%", fontSize: 12, color: "var(--hf-text-muted)" }}>{d.rejectionReason ? `Rejected: ${d.rejectionReason}` : `Archived: ${d.archiveReason}`}</div>
              )}
              {reasonFor?.id === d.id && (
                <div style={{ flexBasis: "100%", paddingTop: 10, borderTop: "1px solid var(--hf-border)" }}>
                  <textarea rows={2} value={reasonText} onChange={e => setReasonText(e.target.value)} placeholder={reasonFor.kind === "reject" ? "Why is this rejected?" : "Why is this archived?"}
                    style={{ width: "100%", padding: "8px 10px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} />
                  {reasonText.length > 0 && reasonBlocker(reasonFor.kind, reasonText) && <div style={{ marginTop: 4, fontSize: 12, color: "var(--hf-text-faint)" }}>{reasonBlocker(reasonFor.kind, reasonText)}</div>}
                  <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
                    <button disabled={!!reasonBlocker(reasonFor.kind, reasonText) || withReason.isPending} onClick={() => withReason.mutate({ id: d.id, kind: reasonFor.kind, reason: reasonText.trim() })}
                      style={{ padding: "6px 12px", borderRadius: 8, fontSize: 12, fontWeight: 700, border: "none", cursor: "pointer", background: "var(--hf-primary-text)", color: "#fff" }}>{reasonFor.kind === "reject" ? "Reject document" : "Archive document"}</button>
                    <button onClick={() => setReasonFor(null)} style={{ padding: "6px 12px", borderRadius: 8, fontSize: 12, fontWeight: 600, border: "1.5px solid var(--hf-border)", background: "var(--hf-surface)", cursor: "pointer" }}>Cancel</button>
                  </div>
                </div>
              )}
              {historyId === d.id && (
                <div style={{ flexBasis: "100%", fontSize: 12, color: "var(--hf-text-muted)", display: "grid", gap: 3 }}>
                  {events.length === 0 && <div>No history yet.</div>}
                  {events.map((e, i) => <div key={i}>{e.occurredAt.slice(0, 10)} · {e.event.toLowerCase().replace("_", " ")}{e.reason ? ` · ${e.reason}` : ""}</div>)}
                </div>
              )}
              {verifyingId === d.id && (
                <div style={{ flexBasis: "100%", marginTop: 10, paddingTop: 10, borderTop: "1px solid var(--hf-border)" }}>
                  <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 6 }}>
                    You uploaded this document, so someone else should verify it. If no one else can, say why. This is recorded with the document.
                  </div>
                  <textarea rows={2} value={exceptionReason} onChange={e => setExceptionReason(e.target.value)} placeholder="Why can no one else verify this?"
                    style={{ width: "100%", padding: "8px 10px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} />
                  {(verifyBlocker(true, exceptionReason) || verifyError) && (
                    <div role="alert" style={{ marginTop: 6, fontSize: 12, color: "var(--hf-danger-text)" }}>{verifyError || verifyBlocker(true, exceptionReason)}</div>
                  )}
                  <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
                    <button disabled={!!verifyBlocker(true, exceptionReason) || verify.isPending} onClick={() => verify.mutate({ id: d.id, reason: exceptionReason.trim() })}
                      style={{ padding: "6px 12px", borderRadius: 8, fontSize: 12, fontWeight: 700, border: "none", cursor: "pointer", background: "var(--hf-primary-text)", color: "#fff" }}>Verify as exception</button>
                    <button onClick={() => setVerifyingId(null)} style={{ padding: "6px 12px", borderRadius: 8, fontSize: 12, fontWeight: 600, border: "1.5px solid var(--hf-border)", background: "var(--hf-surface)", cursor: "pointer" }}>Cancel</button>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const miniBtn: React.CSSProperties = { padding: "5px 9px", border: "1px solid var(--hf-border)", borderRadius: 6, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const lbl: React.CSSProperties = { display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }
