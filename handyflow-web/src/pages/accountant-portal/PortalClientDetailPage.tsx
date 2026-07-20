// src/pages/accountant-portal/PortalClientDetailPage.tsx
import { useEffect, useState } from "react"
import { useNavigate, useParams } from "react-router-dom"
import { portalApi } from "../../api/portal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import type { FeeNote, FicaDocument, PortalClientSummary } from "../../types/portal.types"

const fmtR = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const FEE_NOTE_STATUS: Record<string, { color: string; bg: string }> = {
  DRAFT:   { color: "#64748B", bg: "#F1F5F9" },
  BILLED:  { color: "#1D4ED8", bg: "#EFF6FF" },
  PARTIAL: { color: "#D97706", bg: "#FFFBEB" },
  PAID:    { color: "#166534", bg: "#DCFCE7" },
  OVERDUE: { color: "#DC2626", bg: "#FEF2F2" },
}

// Matches ClientsTab.tsx's own DOC_TYPES exactly, for consistency with
// the staff-side upload form.
const DOC_TYPES = ["ID_COPY", "PROOF_OF_ADDRESS", "BENEFICIAL_OWNERSHIP", "COMPANY_DOCUMENTS", "TRUST_DEED", "OTHER"]
const DOC_TYPE_LABELS: Record<string, string> = {
  ID_COPY: "ID Copy", PROOF_OF_ADDRESS: "Proof of Address", BENEFICIAL_OWNERSHIP: "Beneficial Ownership",
  COMPANY_DOCUMENTS: "Company Documents", TRUST_DEED: "Trust Deed", OTHER: "Other",
}
// Matches ClientsTab.tsx's own ALLOWED_DOC_TYPES / MAX_DOC_BYTES exactly
// — confirmed against the real file, not re-derived independently.
const ALLOWED_DOC_TYPES = ["application/pdf", "image/jpeg", "image/jpg", "image/png",
  "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"]
const MAX_DOC_BYTES = 10 * 1024 * 1024

function downloadBlob(blob: Blob, fileName: string) {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.URL.revokeObjectURL(url)
}

export function PortalClientDetailPage() {
  const { clientId } = useParams<{ clientId: string }>()
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user)
  const logout = usePortalAuthStore(s => s.logout)

  const [tab, setTab] = useState<"fee-notes" | "documents">("fee-notes")
  const [clientName, setClientName] = useState<string>("")

  const [feeNotes, setFeeNotes] = useState<FeeNote[]>([])
  const [feeNotesLoading, setFeeNotesLoading] = useState(true)
  const [downloadingPdf, setDownloadingPdf] = useState<string | null>(null)

  const [docs, setDocs] = useState<FicaDocument[]>([])
  const [docsLoading, setDocsLoading] = useState(true)
  const [downloadingDoc, setDownloadingDoc] = useState<string | null>(null)

  const [showUpload, setShowUpload] = useState(false)
  const [uploadDocType, setUploadDocType] = useState("ID_COPY")
  const [uploadExpiry, setUploadExpiry] = useState("")
  const [uploadError, setUploadError] = useState("")
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    if (!clientId) return
    // Client name isn't in the URL — resolved once from the same
    // access-scoped list the home page already uses, not re-fetched
    // per navigation.
    portalApi.getMyClients().then((clients: PortalClientSummary[]) => {
      const match = clients.find(c => c.clientId === clientId)
      setClientName(match?.tradingName ?? "")
    })
    portalApi.getMyFeeNotes(clientId).then(setFeeNotes).finally(() => setFeeNotesLoading(false))
    refetchDocs()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId])

  const refetchDocs = () => {
    if (!clientId) return
    setDocsLoading(true)
    portalApi.getMyFicaDocuments(clientId).then(setDocs).finally(() => setDocsLoading(false))
  }

  const handleDownloadPdf = async (feeNote: FeeNote) => {
    if (!clientId) return
    setDownloadingPdf(feeNote.id)
    try {
      const blob = await portalApi.downloadFeeNotePdf(clientId, feeNote.id)
      downloadBlob(blob, `${feeNote.invoiceNumber}.pdf`)
    } finally {
      setDownloadingPdf(null)
    }
  }

  const handleDownloadDoc = async (doc: FicaDocument) => {
    if (!clientId) return
    setDownloadingDoc(doc.id)
    try {
      const blob = await portalApi.downloadFicaDocument(clientId, doc.id)
      downloadBlob(blob, doc.fileName)
    } finally {
      setDownloadingDoc(null)
    }
  }

  const handleUpload = async (file: File) => {
    setUploadError("")
    if (!ALLOWED_DOC_TYPES.includes(file.type)) {
      setUploadError("Unsupported file type — please upload a PDF, JPG, PNG, or Word document")
      return
    }
    if (file.size > MAX_DOC_BYTES) {
      setUploadError(`File is too large — maximum is ${MAX_DOC_BYTES / (1024 * 1024)}MB`)
      return
    }
    if (!clientId) return
    setUploading(true)
    try {
      const base64 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(((reader.result as string) || "").split(",")[1] ?? "")
        reader.onerror = () => reject(reader.error)
        reader.readAsDataURL(file)
      })
      await portalApi.uploadFicaDocument(clientId, {
        docType: uploadDocType, fileName: file.name, contentType: file.type || "application/octet-stream",
        fileSizeBytes: file.size, fileContentBase64: base64, expiryDate: uploadExpiry || null,
      })
      setShowUpload(false)
      setUploadExpiry("")
      refetchDocs()
    } catch (e: any) {
      setUploadError(e.response?.data?.message ?? "Failed to upload document")
    } finally {
      setUploading(false)
    }
  }

  return (
    <div style={{ minHeight: "100vh", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ background: "#fff", borderBottom: "1px solid #E2E8F0", padding: "16px 24px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
          <button onClick={() => navigate("/accountant/portal")}
            style={{ background: "none", border: "none", cursor: "pointer", color: "#64748B", fontSize: 13 }}>← Back</button>
          <div style={{ fontWeight: 800, fontSize: 16, color: "#0F172A" }}>Client Portal</div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
          <span style={{ fontSize: 13, color: "#64748B" }}>{user?.fullName}</span>
          <button onClick={() => { logout(); navigate("/accountant/portal/login", { replace: true }) }}
            style={{ padding: "6px 12px", border: "1px solid #E2E8F0", borderRadius: 7, background: "#fff", fontSize: 12, cursor: "pointer", color: "#64748B" }}>
            Log out
          </button>
        </div>
      </div>

      <div style={{ maxWidth: 760, margin: "32px auto", padding: "0 20px" }}>
        <h1 style={{ fontSize: 20, fontWeight: 800, color: "#0F172A", marginBottom: 20 }}>{clientName || "..."}</h1>

        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 20 }}>
          {[{ id: "fee-notes" as const, label: "Fee Notes" }, { id: "documents" as const, label: "Documents" }].map(t => (
            <button key={t.id} onClick={() => setTab(t.id)}
              style={{ padding: "10px 18px", background: "none", border: "none", borderBottom: tab === t.id ? "2px solid #1B3A6B" : "2px solid transparent", color: tab === t.id ? "#1B3A6B" : "#64748B", fontWeight: tab === t.id ? 600 : 400, fontSize: 14, cursor: "pointer", marginBottom: -1 }}>
              {t.label}
            </button>
          ))}
        </div>

        {tab === "fee-notes" && (
          feeNotesLoading ? (
            <div style={{ textAlign: "center" as const, padding: 40, color: "#94A3B8" }}>Loading...</div>
          ) : feeNotes.length === 0 ? (
            <div style={{ textAlign: "center" as const, padding: 40, color: "#94A3B8" }}>No fee notes yet.</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {feeNotes.map(f => {
                const sc = FEE_NOTE_STATUS[f.status] ?? FEE_NOTE_STATUS.DRAFT
                return (
                  <div key={f.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 18px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10 }}>
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                        <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{f.invoiceNumber}</span>
                        <span style={{ background: sc.bg, color: sc.color, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{f.status}</span>
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        Issued {fmtD(f.invoiceDate)} · Due {fmtD(f.dueDate)}
                        {f.daysOverdue > 0 && ` · ${f.daysOverdue}d overdue`}
                      </div>
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                      <div style={{ textAlign: "right" as const }}>
                        <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{fmtR(f.total)}</div>
                        {f.balance > 0 && <div style={{ fontSize: 11, color: "#DC2626" }}>{fmtR(f.balance)} owing</div>}
                      </div>
                      <button onClick={() => handleDownloadPdf(f)} disabled={downloadingPdf === f.id}
                        style={{ padding: "7px 14px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        {downloadingPdf === f.id ? "..." : "PDF"}
                      </button>
                    </div>
                  </div>
                )
              })}
            </div>
          )
        )}

        {tab === "documents" && (
          <div>
            <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 14 }}>
              <button onClick={() => { setShowUpload(true); setUploadError("") }}
                style={{ padding: "8px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                + Upload Document
              </button>
            </div>
            {docsLoading ? (
              <div style={{ textAlign: "center" as const, padding: 40, color: "#94A3B8" }}>Loading...</div>
            ) : docs.length === 0 ? (
              <div style={{ textAlign: "center" as const, padding: 40, color: "#94A3B8" }}>No documents uploaded yet.</div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {docs.map(d => (
                  <div key={d.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 18px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10 }}>
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                        <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{d.fileName}</span>
                        {d.verified && <span style={{ background: "#DCFCE7", color: "#166534", padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>Verified</span>}
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        {DOC_TYPE_LABELS[d.docType] ?? d.docType} · Uploaded {fmtD(d.createdAt)}
                        {d.expiryDate && ` · Expires ${fmtD(d.expiryDate)}`}
                      </div>
                    </div>
                    <button onClick={() => handleDownloadDoc(d)} disabled={downloadingDoc === d.id}
                      style={{ padding: "7px 14px", background: "#fff", color: "#1B3A6B", border: "1px solid #1B3A6B", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                      {downloadingDoc === d.id ? "..." : "Download"}
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {showUpload && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <h3 style={{ margin: "0 0 20px", fontSize: 16, fontWeight: 700 }}>Upload Document</h3>
            <div style={{ marginBottom: 14 }}>
              <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Document type</label>
              <select value={uploadDocType} onChange={e => setUploadDocType(e.target.value)}
                style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }}>
                {DOC_TYPES.map(t => <option key={t} value={t}>{DOC_TYPE_LABELS[t]}</option>)}
              </select>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Expiry date (optional)</label>
              <input type="date" value={uploadExpiry} onChange={e => setUploadExpiry(e.target.value)}
                style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
            </div>
            <div style={{ marginBottom: 6 }}>
              <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>File (max 10MB)</label>
              <input type="file" accept="application/pdf,image/jpeg,image/png,.doc,.docx"
                onChange={e => { const f = e.target.files?.[0]; if (f) handleUpload(f) }}
                disabled={uploading}
                style={{ width: "100%", fontSize: 13 }} />
            </div>
            {uploading && <div style={{ fontSize: 12, color: "#64748B", marginTop: 8 }}>Uploading...</div>}
            {uploadError && <div style={{ marginTop: 12, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{uploadError}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 18 }}>
              <button onClick={() => setShowUpload(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Close</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
