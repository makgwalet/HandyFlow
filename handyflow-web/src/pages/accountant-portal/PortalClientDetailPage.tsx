// src/pages/accountant-portal/PortalClientDetailPage.tsx
import { useEffect, useState } from "react"
import { useParams } from "react-router-dom"
import { portalApi } from "../../api/portal.api"
import type { DocumentRequest, FeeNote, FicaDocument, PortalClientSummary, TaxDeadline } from "../../types/portal.types"
import { PortalShell } from "./PortalShell"
import { color, radius, shadow, space, statusTone, type } from "./portal-theme"

const fmtR = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD = (d: any) => (d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")

const REQUEST_LABEL: Record<string, string> = {
  PENDING: "Awaiting your response",
  PARTIAL: "Submitted — under review",
  COMPLETE: "Complete",
  CANCELLED: "Cancelled",
}

const DOC_TYPES = ["ID_COPY", "PROOF_OF_ADDRESS", "BENEFICIAL_OWNERSHIP", "COMPANY_DOCUMENTS", "TRUST_DEED", "OTHER"]
const DOC_TYPE_LABELS: Record<string, string> = {
  ID_COPY: "ID Copy", PROOF_OF_ADDRESS: "Proof of Address", BENEFICIAL_OWNERSHIP: "Beneficial Ownership",
  COMPANY_DOCUMENTS: "Company Documents", TRUST_DEED: "Trust Deed", OTHER: "Other",
}
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

type TabId = "fee-notes" | "documents" | "requests" | "deadlines"

export function PortalClientDetailPage() {
  const { clientId } = useParams<{ clientId: string }>()

  const [tab, setTab] = useState<TabId>("fee-notes")
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

  const [requests, setRequests] = useState<DocumentRequest[]>([])
  const [requestsLoading, setRequestsLoading] = useState(true)
  const [submittingRequestId, setSubmittingRequestId] = useState<string | null>(null)

  const [deadlines, setDeadlines] = useState<TaxDeadline[]>([])
  const [deadlinesLoading, setDeadlinesLoading] = useState(true)

  useEffect(() => {
    if (!clientId) return
    portalApi.getMyClients().then((clients: PortalClientSummary[]) => {
      const match = clients.find(c => c.clientId === clientId)
      setClientName(match?.tradingName ?? "")
    })
    portalApi.getMyFeeNotes(clientId).then(setFeeNotes).finally(() => setFeeNotesLoading(false))
    refetchDocs()
    refetchRequests()
    setDeadlinesLoading(true)
    portalApi.getMyTaxDeadlines(clientId).then(setDeadlines).finally(() => setDeadlinesLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId])

  const refetchDocs = () => {
    if (!clientId) return
    setDocsLoading(true)
    portalApi.getMyFicaDocuments(clientId).then(setDocs).finally(() => setDocsLoading(false))
  }

  const refetchRequests = () => {
    if (!clientId) return
    setRequestsLoading(true)
    portalApi.getMyDocumentRequests(clientId).then(setRequests).finally(() => setRequestsLoading(false))
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

  const handleMarkSubmitted = async (req: DocumentRequest) => {
    if (!clientId) return
    setSubmittingRequestId(req.id)
    try {
      await portalApi.submitDocumentRequest(clientId, req.id)
      refetchRequests()
    } catch (e: any) {
      console.error("Failed to mark request as submitted", e)
    } finally {
      setSubmittingRequestId(null)
    }
  }

  const openRequestCount = requests.filter(r => r.status === "PENDING" || r.status === "PARTIAL").length
  const upcomingDeadlineCount = deadlines.filter(d => d.status !== "FILED" && d.daysUntilDue <= 30).length

  const tabs: { id: TabId; label: string; count?: number }[] = [
    { id: "fee-notes", label: "Fee Notes" },
    { id: "documents", label: "Documents" },
    { id: "requests", label: "Requests", count: openRequestCount },
    { id: "deadlines", label: "Deadlines", count: upcomingDeadlineCount },
  ]

  return (
    <PortalShell backTo="/accountant/portal">
      <div style={{ maxWidth: 780, margin: "0 auto", padding: `${space(9)} ${space(5)} ${space(12)}` }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: color.ink, marginBottom: space(6), letterSpacing: "-0.02em" }}>
          {clientName || <span style={{ opacity: 0.3 }}>Loading…</span>}
        </h1>

        <div style={{ display: "flex", gap: space(1), borderBottom: `1px solid ${color.border}`, marginBottom: space(6) }}>
          {tabs.map(t => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              style={{
                padding: `${space(2.5)} ${space(4)}`,
                background: "none",
                border: "none",
                borderBottom: tab === t.id ? `2px solid ${color.navy}` : "2px solid transparent",
                color: tab === t.id ? color.navy : color.muted,
                fontWeight: tab === t.id ? 700 : 500,
                fontSize: 13.5,
                cursor: "pointer",
                marginBottom: -1,
                display: "flex",
                alignItems: "center",
                gap: 6,
                transition: "color 0.15s ease",
              }}
            >
              {t.label}
              {!!t.count && (
                <span
                  style={{
                    fontSize: 11,
                    fontWeight: 700,
                    background: tab === t.id ? color.navy : color.border,
                    color: tab === t.id ? "#fff" : color.slate,
                    borderRadius: radius.pill,
                    padding: "1px 6px",
                    minWidth: 16,
                    textAlign: "center" as const,
                  }}
                >
                  {t.count}
                </span>
              )}
            </button>
          ))}
        </div>

        {tab === "fee-notes" && (
          <TabPanel loading={feeNotesLoading} empty={feeNotes.length === 0}
            emptyIcon="🧾" emptyTitle="No fee notes yet" emptyBody="Invoices from your accountant will appear here as they're issued.">
            <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
              {feeNotes.map(f => {
                const sc = statusTone[f.status] ?? statusTone.DRAFT
                return (
                  <Card key={f.id}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: space(4) }}>
                      <div>
                        <div style={{ display: "flex", alignItems: "center", gap: space(2), marginBottom: space(1) }}>
                          <span style={{ fontWeight: 700, fontSize: 14.5, color: color.ink }}>{f.invoiceNumber}</span>
                          <StatusBadge tone={sc}>{f.status}</StatusBadge>
                        </div>
                        <div style={{ fontSize: 12.5, color: color.faint }}>
                          Issued {fmtD(f.invoiceDate)} · Due {fmtD(f.dueDate)}
                          {f.daysOverdue > 0 && <span style={{ color: color.red, fontWeight: 600 }}> · {f.daysOverdue}d overdue</span>}
                        </div>
                      </div>
                      <div style={{ display: "flex", alignItems: "center", gap: space(4) }}>
                        <div style={{ textAlign: "right" as const }}>
                          <div style={{ fontWeight: 700, fontSize: 15, color: color.ink }}>{fmtR(f.total)}</div>
                          {f.balance > 0 && <div style={{ fontSize: 11.5, color: color.red, fontWeight: 600 }}>{fmtR(f.balance)} owing</div>}
                        </div>
                        <PrimaryButton small onClick={() => handleDownloadPdf(f)} disabled={downloadingPdf === f.id}>
                          {downloadingPdf === f.id ? "…" : "PDF"}
                        </PrimaryButton>
                      </div>
                    </div>
                  </Card>
                )
              })}
            </div>
          </TabPanel>
        )}

        {tab === "documents" && (
          <div>
            <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: space(4) }}>
              <PrimaryButton onClick={() => { setShowUpload(true); setUploadError("") }}>+ Upload Document</PrimaryButton>
            </div>
            <TabPanel loading={docsLoading} empty={docs.length === 0}
              emptyIcon="📎" emptyTitle="No documents uploaded yet" emptyBody="Upload FICA and compliance documents here whenever your accountant needs them.">
              <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
                {docs.map(d => (
                  <Card key={d.id}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <div>
                        <div style={{ display: "flex", alignItems: "center", gap: space(2), marginBottom: space(1) }}>
                          <span style={{ fontWeight: 700, fontSize: 14.5, color: color.ink }}>{d.fileName}</span>
                          {d.verified && <StatusBadge tone={statusTone.PAID}>Verified</StatusBadge>}
                        </div>
                        <div style={{ fontSize: 12.5, color: color.faint }}>
                          {DOC_TYPE_LABELS[d.docType] ?? d.docType} · Uploaded {fmtD(d.createdAt)}
                          {d.expiryDate && ` · Expires ${fmtD(d.expiryDate)}`}
                        </div>
                      </div>
                      <SecondaryButton onClick={() => handleDownloadDoc(d)} disabled={downloadingDoc === d.id}>
                        {downloadingDoc === d.id ? "…" : "Download"}
                      </SecondaryButton>
                    </div>
                  </Card>
                ))}
              </div>
            </TabPanel>
          </div>
        )}

        {tab === "requests" && (
          <TabPanel loading={requestsLoading} empty={requests.length === 0}
            emptyIcon="✅" emptyTitle="Nothing outstanding" emptyBody="You're all caught up — no document requests right now.">
            <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
              {requests.map(r => {
                const sc = statusTone[r.status] ?? statusTone.PENDING
                const actionable = r.status === "PENDING" || r.status === "PARTIAL"
                return (
                  <Card key={r.id}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: space(2) }}>
                      <div style={{ display: "flex", alignItems: "center", gap: space(2) }}>
                        <span style={{ fontWeight: 700, fontSize: 14.5, color: color.ink }}>{r.description}</span>
                        <StatusBadge tone={sc}>{REQUEST_LABEL[r.status] ?? r.status}</StatusBadge>
                      </div>
                      {r.dueDate && <span style={{ fontSize: 12, color: color.faint, whiteSpace: "nowrap" as const }}>Due {fmtD(r.dueDate)}</span>}
                    </div>
                    {r.items.length > 0 && (
                      <ul style={{ margin: `0 0 ${space(3)}`, paddingLeft: 18, fontSize: 13.5, color: color.slate, lineHeight: 1.7 }}>
                        {r.items.map((item, i) => <li key={i}>{item}</li>)}
                      </ul>
                    )}
                    {actionable && (
                      <div style={{ display: "flex", alignItems: "center", gap: space(2.5) }}>
                        <SecondaryButton onClick={() => setTab("documents")}>Upload documents</SecondaryButton>
                        {r.status === "PENDING" && (
                          <PrimaryButton small onClick={() => handleMarkSubmitted(r)} disabled={submittingRequestId === r.id}>
                            {submittingRequestId === r.id ? "…" : "Mark as submitted"}
                          </PrimaryButton>
                        )}
                      </div>
                    )}
                  </Card>
                )
              })}
            </div>
          </TabPanel>
        )}

        {tab === "deadlines" && (
          <TabPanel loading={deadlinesLoading} empty={deadlines.length === 0}
            emptyIcon="📅" emptyTitle="No SARS deadlines on record yet" emptyBody="Filing deadlines your accountant is tracking for you will appear here.">
            <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
              {deadlines.map(d => {
                const sc = statusTone[d.status] ?? statusTone.PENDING
                return (
                  <Card key={d.id}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <div>
                        <div style={{ display: "flex", alignItems: "center", gap: space(2), marginBottom: space(1) }}>
                          <span style={{ fontWeight: 700, fontSize: 14.5, color: color.ink }}>{d.friendlyLabel}</span>
                          <StatusBadge tone={sc}>{d.status}</StatusBadge>
                        </div>
                        <div style={{ fontSize: 12.5, color: color.faint }}>
                          Due {fmtD(d.dueDate)}
                          {d.status === "PENDING" && d.daysUntilDue >= 0 && ` · ${d.daysUntilDue}d remaining`}
                          {d.status === "PENDING" && d.daysUntilDue < 0 && (
                            <span style={{ color: color.red, fontWeight: 600 }}> · {Math.abs(d.daysUntilDue)}d overdue</span>
                          )}
                          {d.status === "FILED" && d.filedDate && ` · Filed ${fmtD(d.filedDate)}`}
                        </div>
                      </div>
                    </div>
                  </Card>
                )
              })}
            </div>
          </TabPanel>
        )}
      </div>

      {showUpload && (
        <div
          style={{
            position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)",
            display: "flex", alignItems: "center", justifyContent: "center",
            zIndex: 1000, backdropFilter: "blur(3px)", padding: space(4),
          }}
          onClick={() => setShowUpload(false)}
        >
          <div
            style={{ background: color.surface, borderRadius: radius.lg, padding: space(7), width: 420, maxWidth: "100%", boxShadow: shadow.modal }}
            onClick={e => e.stopPropagation()}
          >
            <h3 style={{ margin: `0 0 ${space(5)}`, fontSize: 16, fontWeight: 800, color: color.ink }}>Upload Document</h3>
            <div style={{ marginBottom: space(4) }}>
              <label style={fieldLabel}>Document type</label>
              <select
                value={uploadDocType}
                onChange={e => setUploadDocType(e.target.value)}
                style={{ ...fieldInput, background: color.surface, cursor: "pointer" }}
              >
                {DOC_TYPES.map(t => <option key={t} value={t}>{DOC_TYPE_LABELS[t]}</option>)}
              </select>
            </div>
            <div style={{ marginBottom: space(4) }}>
              <label style={fieldLabel}>Expiry date (optional)</label>
              <input type="date" value={uploadExpiry} onChange={e => setUploadExpiry(e.target.value)} style={fieldInput} />
            </div>
            <div style={{ marginBottom: space(1.5) }}>
              <label style={fieldLabel}>File (max 10MB)</label>
              <input
                type="file"
                accept="application/pdf,image/jpeg,image/png,.doc,.docx"
                onChange={e => { const f = e.target.files?.[0]; if (f) handleUpload(f) }}
                disabled={uploading}
                style={{ width: "100%", fontSize: 13, color: color.slate }}
              />
            </div>
            {uploading && <div style={{ fontSize: 12.5, color: color.muted, marginTop: space(2) }}>Uploading…</div>}
            {uploadError && (
              <div style={{ marginTop: space(3), padding: `${space(2)} ${space(3)}`, background: color.redBg, border: "1px solid #FECACA", borderRadius: radius.sm, fontSize: 13, color: color.red }}>
                {uploadError}
              </div>
            )}
            <div style={{ display: "flex", justifyContent: "flex-end", marginTop: space(5) }}>
              <SecondaryButton onClick={() => setShowUpload(false)}>Close</SecondaryButton>
            </div>
          </div>
        </div>
      )}
    </PortalShell>
  )
}

// ── Shared small components ──────────────────────────────────────────────

function TabPanel({ loading, empty, emptyIcon, emptyTitle, emptyBody, children }: {
  loading: boolean; empty: boolean; emptyIcon: string; emptyTitle: string; emptyBody: string; children: React.ReactNode
}) {
  if (loading) {
    return (
      <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
        {[0, 1, 2].map(i => (
          <div key={i} style={{ height: 68, background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md, opacity: 0.5 }} />
        ))}
      </div>
    )
  }
  if (empty) {
    return (
      <div style={{ textAlign: "center" as const, padding: `${space(12)} ${space(6)}`, background: color.surface, border: `1px dashed ${color.border}`, borderRadius: radius.lg }}>
        <div style={{ fontSize: 30, marginBottom: space(3) }}>{emptyIcon}</div>
        <div style={{ fontSize: 14.5, fontWeight: 700, color: color.ink, marginBottom: space(1) }}>{emptyTitle}</div>
        <div style={{ fontSize: 13, color: color.muted, maxWidth: 300, margin: "0 auto", lineHeight: 1.5 }}>{emptyBody}</div>
      </div>
    )
  }
  return <>{children}</>
}

function Card({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ padding: `${space(4)} ${space(5)}`, background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md, boxShadow: shadow.card }}>
      {children}
    </div>
  )
}

function StatusBadge({ tone, children }: { tone: { color: string; bg: string }; children: React.ReactNode }) {
  return (
    <span style={{ background: tone.bg, color: tone.color, padding: "2px 9px", borderRadius: radius.pill, fontSize: 10.5, fontWeight: 700, letterSpacing: "0.02em" }}>
      {children}
    </span>
  )
}

function PrimaryButton({ children, onClick, disabled, small }: { children: React.ReactNode; onClick: () => void; disabled?: boolean; small?: boolean }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      style={{
        padding: small ? "7px 14px" : "9px 18px",
        background: disabled ? color.faint : color.navy,
        color: "#fff",
        border: "none",
        borderRadius: 8,
        fontSize: small ? 12.5 : 13.5,
        fontWeight: 700,
        cursor: disabled ? "default" : "pointer",
        transition: "background 0.15s ease",
        whiteSpace: "nowrap" as const,
      }}
      onMouseEnter={e => { if (!disabled) e.currentTarget.style.background = color.navyDark }}
      onMouseLeave={e => { if (!disabled) e.currentTarget.style.background = color.navy }}
    >
      {children}
    </button>
  )
}

function SecondaryButton({ children, onClick, disabled }: { children: React.ReactNode; onClick: () => void; disabled?: boolean }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      style={{
        padding: "7px 14px",
        background: color.surface,
        color: disabled ? color.faint : color.navy,
        border: `1px solid ${disabled ? color.border : color.navy}`,
        borderRadius: 8,
        fontSize: 12.5,
        fontWeight: 700,
        cursor: disabled ? "default" : "pointer",
        transition: "background 0.15s ease",
        whiteSpace: "nowrap" as const,
      }}
      onMouseEnter={e => { if (!disabled) e.currentTarget.style.background = color.blueBg }}
      onMouseLeave={e => { if (!disabled) e.currentTarget.style.background = color.surface }}
    >
      {children}
    </button>
  )
}

const fieldLabel: React.CSSProperties = {
  display: "block", fontSize: 12.5, fontWeight: 600, color: color.slate, marginBottom: space(1.5),
}
const fieldInput: React.CSSProperties = {
  width: "100%", padding: "9px 12px", border: `1.5px solid ${color.border}`, borderRadius: 8,
  fontSize: 14, boxSizing: "border-box", fontFamily: type.family, color: color.ink,
}
