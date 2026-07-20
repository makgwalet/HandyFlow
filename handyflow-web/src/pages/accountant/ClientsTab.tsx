// src/pages/accountant/ClientsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, ChevronDown, ChevronUp, AlertTriangle, CheckCircle, Users, Search, Paperclip, Trash2, Download } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtD   = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"
// NEW: closes the "unified client detail page" gap — no money-
// formatting helper existed anywhere in this file until now.
const fmtR   = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

const ENTITY_TYPES = ["PTY_LTD","CC","SOLE_PROP","TRUST","NPO","INDIVIDUAL","PARTNERSHIP","FOREIGN","ARTIST","TRADER","OTHER"]
const RISK_CFG: Record<string, { color: string; bg: string }> = {
  LOW:    { color: "#166534", bg: "#DCFCE7" },
  MEDIUM: { color: "#D97706", bg: "#FFFBEB" },
  HIGH:   { color: "#DC2626", bg: "#FEF2F2" },
}

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none", background: "#fff" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

// NEW: closes the "unified client detail page" gap — onNavigate lets
// the Client Workspace modal's "View all" links switch to the relevant
// full tab (Compliance/Billing/Journals/Time), matching the exact
// pattern AccountantDashboard already uses.
export default function ClientsTab({ onNavigate }: { onNavigate?: (tab: string) => void }) {
  const qc = useQueryClient()
  const [search, setSearch]     = useState("")
  const [expanded, setExpanded] = useState<string | null>(null)
  // NEW: closes the "unified client detail page" gap.
  const [workspaceFor, setWorkspaceFor] = useState<any>(null)
  const { data: clientDetail, isLoading: detailLoading, isError: detailIsError } = useQuery<any>({
    queryKey: ["acc-client-detail", workspaceFor?.id],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/accountant/clients/${workspaceFor.id}/detail`)
      return r.data?.data ?? r.data
    },
    enabled: !!workspaceFor,
  })

  // NEW: closes "where to send the invite on the frontend" — the
  // Client Workspace modal is already this app's one-stop view per
  // client, so portal access management lives here too, not as a
  // separate page. Kept as its own query rather than folded into
  // ClientDetailResponse — a client will realistically have 1-3 portal
  // grants ever, and this stays consistent with how other small,
  // per-section data (e.g. JournalsTab's coaAccounts) is fetched
  // independently rather than bloating one aggregate response.
  const { data: portalGrants = [], isLoading: grantsLoading } = useQuery<any[]>({
    queryKey: ["acc-portal-grants", workspaceFor?.id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accountant/clients/${workspaceFor.id}/portal-invites`)),
    enabled: !!workspaceFor,
  })

  const [showInvite, setShowInvite] = useState(false)
  const [inviteEmail, setInviteEmail] = useState("")
  const [inviteError, setInviteError] = useState("")

  const inviteMut = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/accountant/clients/${workspaceFor.id}/portal-invites`, { email: inviteEmail }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["acc-portal-grants", workspaceFor?.id] })
      setShowInvite(false); setInviteEmail(""); setInviteError("")
    },
    onError: (e: any) => setInviteError(e.response?.data?.message ?? "Failed to send invite"),
  })

  const revokeMut = useMutation({
    mutationFn: (grantId: string) => apiClient.post(`/api/v1/accountant/clients/${workspaceFor.id}/portal-invites/${grantId}/revoke`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["acc-portal-grants", workspaceFor?.id] }),
  })

  const [showCreate, setCreate] = useState(false)
  const [error, setError]       = useState("")

  const INIT = () => ({
    entityType: "PTY_LTD", tradingName: "", registeredName: "", registrationNumber: "",
    taxReferenceNumber: "", vatNumber: "", vatCategory: "", yearEndMonth: 2,
    contactEmail: "", contactPhone: "",
  })
  const [form, setForm] = useState(INIT())
  const f = (k: string, v: any) => setForm(p => ({ ...p, [k]: v }))

  const { data: clients = [], isLoading } = useQuery<any[]>({
    queryKey: ["acc-clients"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accountant/clients?size=200")),
  })

  const createClient = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/accountant/clients", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["acc-clients"] }); setCreate(false); setForm(INIT()); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create client"),
  })

  const markFica = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/accountant/clients/${id}/fica-complete`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["acc-clients"] }),
  })

  const markSars = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/accountant/clients/${id}/sars-agent`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["acc-clients"] }),
  })

  // NEW: closes the audit's "client-facing deadline reminder emails"
  // gap — the toggle behind the per-client opt-out.
  const toggleReminders = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      apiClient.post(`/api/v1/accountant/clients/${id}/deadline-reminders?enabled=${enabled}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["acc-clients"] }),
  })

  const generateDeadlines = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/accountant/clients/${id}/deadlines/generate`, { periodYear: new Date().getFullYear() }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["acc-clients"] }),
  })

  // NEW: closes the accountant module audit's "document/attachment
  // storage on client records" gap. Base64-in-DB — same pattern already
  // proven for SCM's supplier invoice attachments, since there's no S3
  // in this environment yet.
  const [docsOpenFor, setDocsOpenFor] = useState<string | null>(null)
  const [docType, setDocType] = useState("ID_COPY")
  const [docExpiry, setDocExpiry] = useState("")
  const [uploadingDoc, setUploadingDoc] = useState(false)
  const [docError, setDocError] = useState("")
  const MAX_DOC_BYTES = 10 * 1024 * 1024
  // NEW: matches AccountantService.ALLOWED_FICA_DOC_TYPES exactly — see
  // that constant's own comment for why. This is the fast client-side
  // check; the server-side one is the real enforcement.
  const ALLOWED_DOC_TYPES = ["application/pdf", "image/jpeg", "image/jpg", "image/png",
    "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"]

  const { data: ficaDocs = [], refetch: refetchDocs } = useQuery<any[]>({
    queryKey: ["acc-fica-docs", docsOpenFor],
    queryFn: async () => docsOpenFor
      ? unwrap(await apiClient.get(`/api/v1/accountant/clients/${docsOpenFor}/fica-documents`))
      : [],
    enabled: !!docsOpenFor,
  })

  const uploadDoc = async (clientId: string, file: File) => {
    setDocError("")
    if (!ALLOWED_DOC_TYPES.includes(file.type)) {
      setDocError("Unsupported file type — please upload a PDF, JPG, PNG, or Word document")
      return
    }
    if (file.size > MAX_DOC_BYTES) {
      setDocError(`File is too large — maximum is ${MAX_DOC_BYTES / (1024 * 1024)}MB`)
      return
    }
    setUploadingDoc(true)
    try {
      const base64 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(((reader.result as string) || "").split(",")[1] ?? "")
        reader.onerror = () => reject(reader.error)
        reader.readAsDataURL(file)
      })
      await apiClient.post(`/api/v1/accountant/clients/${clientId}/fica-documents`, {
        docType, fileName: file.name, contentType: file.type || "application/octet-stream",
        fileSizeBytes: file.size, fileContentBase64: base64, expiryDate: docExpiry || null,
      })
      refetchDocs()
    } catch (e: any) {
      setDocError(e.response?.data?.message ?? "Failed to upload document")
    } finally {
      setUploadingDoc(false)
    }
  }

  const downloadDoc = async (clientId: string, doc: any) => {
    try {
      const res = await apiClient.get(`/api/v1/accountant/clients/${clientId}/fica-documents/${doc.id}`, { responseType: "blob" })
      const blob = new Blob([res.data], { type: doc.contentType })
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url; a.download = doc.fileName
      document.body.appendChild(a); a.click(); a.remove()
      URL.revokeObjectURL(url)
    } catch (e: any) {
      setDocError(e.response?.data?.message ?? "Failed to download document")
    }
  }

  const verifyDoc = useMutation({
    mutationFn: ({ clientId, docId }: { clientId: string; docId: string }) =>
      apiClient.post(`/api/v1/accountant/clients/${clientId}/fica-documents/${docId}/verify`),
    onSuccess: () => refetchDocs(),
    onError: (e: any) => setDocError(e.response?.data?.message ?? "Failed to verify document"),
  })

  const deleteDoc = useMutation({
    mutationFn: ({ clientId, docId }: { clientId: string; docId: string }) =>
      apiClient.delete(`/api/v1/accountant/clients/${clientId}/fica-documents/${docId}`),
    onSuccess: () => refetchDocs(),
    onError: (e: any) => setDocError(e.response?.data?.message ?? "Failed to delete document"),
  })

  const DOC_TYPES = ["ID_COPY", "PROOF_OF_ADDRESS", "BENEFICIAL_OWNERSHIP", "COMPANY_DOCUMENTS", "TRUST_DEED", "OTHER"]

  const filtered = (clients as any[]).filter(c =>
    !search || c.tradingName?.toLowerCase().includes(search.toLowerCase()) ||
    c.registrationNumber?.includes(search) || c.taxReferenceNumber?.includes(search))

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ position: "relative" as const }}>
          <Search size={13} style={{ position: "absolute" as const, left: 9, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search clients..."
            style={{ paddingLeft: 28, padding: "7px 10px 7px 28px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", width: 240 }} />
        </div>
        <button onClick={() => { setCreate(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add Client
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Users size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No clients yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Add your first client to start managing their compliance.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {filtered.map((c: any) => {
            const risk   = RISK_CFG[c.riskRating] ?? RISK_CFG.LOW
            const isOpen = expanded === c.id
            return (
              <div key={c.id} style={{ border: "1px solid #E2E8F0", borderLeft: `3px solid ${risk.color}`, borderRadius: 10, overflow: "hidden" }}>
                <div onClick={() => setExpanded(isOpen ? null : c.id)}
                  style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "13px 20px", cursor: "pointer", background: isOpen ? "#F8FAFC" : "#fff" }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" }}>
                      <button onClick={e => { e.stopPropagation(); setWorkspaceFor(c) }}
                        style={{ fontWeight: 700, fontSize: 14, color: "#1B3A6B", background: "none", border: "none", padding: 0, cursor: "pointer", textDecoration: "underline", textUnderlineOffset: 2 }}>
                        {c.tradingName}
                      </button>
                      <span style={{ background: "#F8FAFC", color: "#64748B", padding: "1px 7px", borderRadius: 20, fontSize: 11, border: "1px solid #E2E8F0" }}>{c.entityType.replace("_"," ")}</span>
                      <span style={{ background: risk.bg, color: risk.color, padding: "1px 7px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{c.riskRating}</span>
                      {!c.ficaCompleted && <span style={{ background: "#FFFBEB", color: "#D97706", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700, border: "1px solid #FDE68A" }}>FICA pending</span>}
                      {c.overdueDeadlines > 0 && <span style={{ background: "#FEF2F2", color: "#DC2626", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700, border: "1px solid #FECACA" }}>{c.overdueDeadlines} overdue</span>}
                    </div>
                    <div style={{ fontSize: 12, color: "#64748B", display: "flex", gap: 12, flexWrap: "wrap" }}>
                      {c.registrationNumber && <span>{c.registrationNumber}</span>}
                      {c.taxReferenceNumber && <span>TRN: {c.taxReferenceNumber}</span>}
                      {c.vatNumber && <span>VAT: {c.vatNumber}</span>}
                      {c.yearEndMonth && <span>YE: {new Date(0, c.yearEndMonth - 1).toLocaleString("en", { month: "short" })}</span>}
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0 }}>
                    {c.wip > 0 && <span style={{ fontSize: 12, fontWeight: 700, color: "#0D9488" }}>WIP R{Number(c.wip).toLocaleString("en-ZA", { maximumFractionDigits: 0 })}</span>}
                    {isOpen ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                  </div>
                </div>

                {isOpen && (
                  <div style={{ borderTop: "1px solid #E2E8F0", padding: "16px 20px", background: "#FAFAFA" }}>
                    {/* Compliance status */}
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 10, marginBottom: 16 }}>
                      {[
                        { l: "Onboarding",     v: c.onboardingStatus },
                        { l: "FICA",           v: c.ficaCompleted ? "Complete" : "Pending",   ok: c.ficaCompleted },
                        { l: "SARS Agent",     v: c.sarsAgentAppointed ? "Appointed" : "Pending", ok: c.sarsAgentAppointed },
                        { l: "TCS PIN",        v: c.tcsPin ?? "Not on file",                  ok: !!c.tcsPin },
                        { l: "Contact email",  v: c.contactEmail ?? "—" },
                        { l: "Contact phone",  v: c.contactPhone ?? "—" },
                        { l: "Open deadlines", v: c.openDeadlines },
                        { l: "Overdue",        v: c.overdueDeadlines, color: c.overdueDeadlines > 0 ? "#DC2626" : undefined },
                      ].map((item: any) => (
                        <div key={item.l} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, padding: "8px 12px" }}>
                          <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, marginBottom: 2 }}>{item.l}</div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: item.color ?? (item.ok === false ? "#DC2626" : item.ok === true ? "#166534" : "#0F172A") }}>
                            {item.v}
                          </div>
                        </div>
                      ))}
                    </div>

                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      {!c.ficaCompleted && (
                        <button onClick={() => markFica.mutate(c.id)}
                          style={{ padding: "6px 12px", background: "#FFFBEB", color: "#D97706", border: "1px solid #FDE68A", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                          Mark FICA complete
                        </button>
                      )}
                      {!c.sarsAgentAppointed && (
                        <button onClick={() => markSars.mutate(c.id)}
                          style={{ padding: "6px 12px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                          Mark SARS agent appointed
                        </button>
                      )}
                      <button onClick={() => generateDeadlines.mutate(c.id)}
                        style={{ padding: "6px 12px", background: "#F0FDF4", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        Generate {new Date().getFullYear()} deadlines
                      </button>
                      {/* NEW: closes the audit's "document/attachment
                          storage on client records" gap. */}
                      <button onClick={() => { setDocsOpenFor(docsOpenFor === c.id ? null : c.id); setDocError("") }}
                        style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#F8FAFC", color: "#374151", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        <Paperclip size={12} /> {docsOpenFor === c.id ? "Hide documents" : "FICA documents"}
                      </button>
                      {/* NEW: closes the audit's "client-facing deadline
                          reminder emails" gap — the per-client toggle. */}
                      <label style={{ display: "flex", alignItems: "center", gap: 6, padding: "6px 12px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12, color: "#374151", cursor: "pointer" }}>
                        <input type="checkbox" checked={c.clientDeadlineRemindersEnabled}
                          onChange={e => toggleReminders.mutate({ id: c.id, enabled: e.target.checked })}
                          style={{ width: 14, height: 14 }} />
                        Email client deadline reminders
                      </label>
                    </div>

                    {docsOpenFor === c.id && (
                      <div style={{ marginTop: 14, padding: "12px 14px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 9 }}>
                        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 10 }}>
                          <select value={docType} onChange={e => setDocType(e.target.value)}
                            style={{ padding: "6px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12, background: "#fff" }}>
                            {DOC_TYPES.map(t => <option key={t} value={t}>{t.replace(/_/g, " ")}</option>)}
                          </select>
                          <input type="date" value={docExpiry} onChange={e => setDocExpiry(e.target.value)}
                            placeholder="Expiry (optional)"
                            style={{ padding: "6px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12 }} />
                          <label style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: uploadingDoc ? "default" : "pointer", opacity: uploadingDoc ? .6 : 1 }}>
                            <Paperclip size={12} />
                            {uploadingDoc ? "Uploading..." : "Add File"}
                            <input type="file" style={{ display: "none" }} disabled={uploadingDoc}
                              onChange={e => { const f = e.target.files?.[0]; if (f) uploadDoc(c.id, f); e.target.value = "" }} />
                          </label>
                        </div>
                        {docError && <div style={{ marginBottom: 10, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 7, fontSize: 12, color: "#DC2626" }}>{docError}</div>}
                        {ficaDocs.length === 0 ? (
                          <div style={{ fontSize: 12, color: "#94A3B8" }}>No documents uploaded yet.</div>
                        ) : (
                          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                            {ficaDocs.map((d: any) => (
                              <div key={d.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "8px 10px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 7 }}>
                                <div>
                                  <div style={{ display: "flex", alignItems: "center", gap: 7 }}>
                                    <button onClick={() => downloadDoc(c.id, d)} style={{ background: "none", border: "none", cursor: "pointer", fontSize: 12, color: "#1B3A6B", fontWeight: 600, padding: 0 }}>
                                      {d.fileName}
                                    </button>
                                    <span style={{ background: "#F1F5F9", color: "#64748B", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>{d.docType.replace(/_/g, " ")}</span>
                                    {d.verified
                                      ? <span style={{ background: "#DCFCE7", color: "#166534", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700, display: "flex", alignItems: "center", gap: 3 }}><CheckCircle size={9} /> Verified</span>
                                      : <span style={{ background: "#FFFBEB", color: "#D97706", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>Unverified</span>}
                                  </div>
                                  <div style={{ fontSize: 11, color: "#94A3B8" }}>
                                    {(d.fileSizeBytes / 1024).toFixed(0)} KB
                                    {d.expiryDate && ` · Expires ${d.expiryDate}`}
                                  </div>
                                </div>
                                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                  {!d.verified && (
                                    <button onClick={() => verifyDoc.mutate({ clientId: c.id, docId: d.id })}
                                      style={{ padding: "4px 10px", background: "#F0FDF4", color: "#166534", border: "1px solid #86EFAC", borderRadius: 6, fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                                      Verify
                                    </button>
                                  )}
                                  <button onClick={() => deleteDoc.mutate({ clientId: c.id, docId: d.id })}
                                    style={{ background: "none", border: "none", cursor: "pointer", color: "#DC2626", display: "flex" }}>
                                    <Trash2 size={13} />
                                  </button>
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Create client modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 680, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Add Client</h3>
              <button onClick={() => setCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Entity type *</label>
                <select value={form.entityType} onChange={e => f("entityType", e.target.value)} style={{ ...inp, background: "#fff" }}>
                  {ENTITY_TYPES.map(t => <option key={t} value={t}>{t.replace("_"," ")}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Year-end month *</label>
                <select value={form.yearEndMonth} onChange={e => f("yearEndMonth", parseInt(e.target.value))} style={{ ...inp, background: "#fff" }}>
                  {Array.from({ length: 12 }, (_, i) => (
                    <option key={i+1} value={i+1}>{new Date(0, i).toLocaleString("en", { month: "long" })}</option>
                  ))}
                </select>
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Trading name *</label>
                <input autoFocus value={form.tradingName} onChange={e => f("tradingName", e.target.value)} placeholder="Acme Trading (Pty) Ltd" style={inp} />
              </div>
              <div>
                <label style={lbl}>Registered name</label>
                <input value={form.registeredName} onChange={e => f("registeredName", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>CIPC registration number</label>
                <input value={form.registrationNumber} onChange={e => f("registrationNumber", e.target.value)} placeholder="2020/123456/07" style={inp} />
              </div>
              <div>
                <label style={lbl}>SARS tax reference number</label>
                <input value={form.taxReferenceNumber} onChange={e => f("taxReferenceNumber", e.target.value)} placeholder="1234567890" style={inp} />
              </div>
              <div>
                <label style={lbl}>VAT number</label>
                <input value={form.vatNumber} onChange={e => f("vatNumber", e.target.value)} placeholder="4123456789" style={inp} />
              </div>
              <div>
                <label style={lbl}>VAT category</label>
                <select value={form.vatCategory} onChange={e => f("vatCategory", e.target.value)} style={{ ...inp, background: "#fff" }}>
                  <option value="">Not VAT registered</option>
                  <option value="A">A — Bi-monthly (Feb/Apr/Jun/Aug/Oct/Dec)</option>
                  <option value="B">B — Bi-monthly (Jan/Mar/May/Jul/Sep/Nov)</option>
                  <option value="C">C — Monthly</option>
                  <option value="E">E — Annual</option>
                </select>
              </div>
              <div>
                <label style={lbl}>Contact email</label>
                <input type="email" value={form.contactEmail} onChange={e => f("contactEmail", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Contact phone</label>
                <input value={form.contactPhone} onChange={e => f("contactPhone", e.target.value)} style={inp} />
              </div>
            </div>

            {error && <div style={{ marginTop: 14, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setCreate(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button disabled={!form.tradingName || createClient.isPending}
                onClick={() => createClient.mutate({ ...form, vatCategory: form.vatCategory || null, registeredName: form.registeredName || null, registrationNumber: form.registrationNumber || null, taxReferenceNumber: form.taxReferenceNumber || null, vatNumber: form.vatNumber || null, contactEmail: form.contactEmail || null, contactPhone: form.contactPhone || null })}
                style={{ padding: "9px 22px", background: !form.tradingName ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createClient.isPending ? "Adding..." : "Add Client"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* NEW: Client Workspace — closes the "unified client detail
          page" gap. Deliberately a modal, not a routed page — this
          module's tabs are managed by local React state in
          AccountantPage.tsx, not URL routing, and there's no visibility
          into any broader routing infrastructure to build a real page
          against safely. Each section shows the 10 most recent items
          with a "View all" link that switches to the corresponding
          full tab, rather than duplicating each tab's own full
          pagination/filtering inside this modal. */}
      {workspaceFor && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, width: 760, maxHeight: "88vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ position: "sticky", top: 0, background: "#fff", borderBottom: "1px solid #E2E8F0", padding: "20px 28px", display: "flex", justifyContent: "space-between", alignItems: "center", zIndex: 1 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>{workspaceFor.tradingName}</h3>
                <p style={{ margin: "3px 0 0", fontSize: 12, color: "#64748B" }}>{workspaceFor.entityType?.replace("_", " ")} · {workspaceFor.riskRating} risk</p>
              </div>
              <button onClick={() => setWorkspaceFor(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            <div style={{ padding: "20px 28px" }}>
              {detailLoading ? (
                <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading client workspace...</div>
              ) : detailIsError ? (
                <div style={{ textAlign: "center", padding: 40, color: "#DC2626" }}>Couldn't load this client's workspace.</div>
              ) : clientDetail && (
                <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>

                  {/* Deadlines */}
                  <div>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                      <div style={{ fontSize: 13, fontWeight: 700, color: "#374151" }}>Deadlines</div>
                      {onNavigate && <button onClick={() => onNavigate("deadlines")} style={{ fontSize: 12, color: "#1B3A6B", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>View all →</button>}
                    </div>
                    {(clientDetail.recentDeadlines ?? []).length === 0 ? (
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>No deadlines recorded.</div>
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
                        {clientDetail.recentDeadlines.map((d: any) => (
                          <div key={d.id} style={{ display: "flex", justifyContent: "space-between", padding: "7px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12 }}>
                            <span>{d.deadlineType} · {fmtD(d.adjustedDueDate)}</span>
                            <span style={{ fontWeight: 600, color: d.status === "OVERDUE" ? "#DC2626" : d.status === "FILED" ? "#166534" : "#D97706" }}>{d.status}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Fee Notes */}
                  <div>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                      <div style={{ fontSize: 13, fontWeight: 700, color: "#374151" }}>Fee Notes</div>
                      {onNavigate && <button onClick={() => onNavigate("billing")} style={{ fontSize: 12, color: "#1B3A6B", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>View all →</button>}
                    </div>
                    {(clientDetail.recentFeeNotes ?? []).length === 0 ? (
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>No fee notes yet.</div>
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
                        {clientDetail.recentFeeNotes.map((f: any) => (
                          <div key={f.id} style={{ display: "flex", justifyContent: "space-between", padding: "7px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12 }}>
                            <span>{f.invoiceNumber} · {fmtD(f.invoiceDate)}</span>
                            <span style={{ display: "flex", gap: 10 }}>
                              <span style={{ fontWeight: 600 }}>{fmtR(f.total)}</span>
                              <span style={{ color: f.status === "PAID" ? "#166534" : f.status === "OVERDUE" ? "#DC2626" : "#64748B" }}>{f.status}</span>
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Journals */}
                  <div>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                      <div style={{ fontSize: 13, fontWeight: 700, color: "#374151" }}>Journals</div>
                      {onNavigate && <button onClick={() => onNavigate("journals")} style={{ fontSize: 12, color: "#1B3A6B", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>View all →</button>}
                    </div>
                    {(clientDetail.recentJournals ?? []).length === 0 ? (
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>No journals yet.</div>
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
                        {clientDetail.recentJournals.map((j: any) => (
                          <div key={j.id} style={{ display: "flex", justifyContent: "space-between", padding: "7px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12 }}>
                            <span style={{ fontFamily: "monospace" }}>{j.reference} · {fmtD(j.journalDate)}</span>
                            <span style={{ color: j.status === "POSTED" ? "#166534" : "#64748B" }}>{j.status}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Time Entries */}
                  <div>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                      <div style={{ fontSize: 13, fontWeight: 700, color: "#374151" }}>Time</div>
                      {onNavigate && <button onClick={() => onNavigate("time")} style={{ fontSize: 12, color: "#1B3A6B", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>View all →</button>}
                    </div>
                    {(clientDetail.recentTimeEntries ?? []).length === 0 ? (
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>No time logged yet.</div>
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
                        {clientDetail.recentTimeEntries.map((t: any) => (
                          <div key={t.id} style={{ display: "flex", justifyContent: "space-between", padding: "7px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12 }}>
                            <span>{t.activityType?.replace("_", " ")} · {fmtD(t.entryDate)}</span>
                            <span style={{ display: "flex", gap: 10 }}>
                              <span style={{ fontWeight: 600 }}>{fmtR(t.lineTotal)}</span>
                              <span style={{ color: t.status === "BILLED" ? "#166534" : "#64748B" }}>{t.status}</span>
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* NEW: Portal Access — closes "where to send the invite
                      on the frontend". Same section shape as the other
                      four, but with an inline invite form instead of a
                      "View all" link — there's no separate full tab for
                      this to link out to. */}
                  <div>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                      <div style={{ fontSize: 13, fontWeight: 700, color: "#374151" }}>Portal Access</div>
                      <button onClick={() => { setShowInvite(true); setInviteError("") }}
                        style={{ fontSize: 12, color: "#1B3A6B", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
                        + Invite to portal
                      </button>
                    </div>
                    {grantsLoading ? (
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>Loading...</div>
                    ) : portalGrants.length === 0 ? (
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>No portal invites sent yet.</div>
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
                        {portalGrants.map((g: any) => {
                          const cfg: Record<string, { color: string; bg: string }> = {
                            PENDING: { color: "#D97706", bg: "#FFFBEB" },
                            ACTIVE:  { color: "#166534", bg: "#DCFCE7" },
                            REVOKED: { color: "#64748B", bg: "#F1F5F9" },
                          }
                          const c = cfg[g.status] ?? cfg.REVOKED
                          return (
                            <div key={g.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "7px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12 }}>
                              <span>{g.inviteEmail} · {fmtD(g.invitedAt)}</span>
                              <span style={{ display: "flex", alignItems: "center", gap: 10 }}>
                                <span style={{ background: c.bg, color: c.color, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{g.status}</span>
                                {g.status !== "REVOKED" && (
                                  <button onClick={() => revokeMut.mutate(g.id)} disabled={revokeMut.isPending}
                                    style={{ background: "none", border: "none", cursor: "pointer", color: "#DC2626", fontSize: 11, fontWeight: 600 }}>
                                    Revoke
                                  </button>
                                )}
                              </span>
                            </div>
                          )
                        })}
                      </div>
                    )}
                  </div>

                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* NEW: Invite to Portal modal — closes "where to send the
          invite on the frontend". */}
      {showInvite && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <h3 style={{ margin: "0 0 6px", fontSize: 16, fontWeight: 700 }}>Invite to Portal</h3>
            <p style={{ margin: "0 0 20px", fontSize: 13, color: "#64748B" }}>
              {workspaceFor?.tradingName} will receive an email with a link to set up their portal account.
            </p>
            <div>
              <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Client contact email *</label>
              <input type="email" autoFocus value={inviteEmail} onChange={e => setInviteEmail(e.target.value)} placeholder="contact@client.co.za"
                style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
            </div>
            {inviteError && <div style={{ marginTop: 12, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{inviteError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowInvite(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button disabled={!inviteEmail || inviteMut.isPending}
                onClick={() => inviteMut.mutate()}
                style={{ padding: "9px 22px", background: !inviteEmail ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {inviteMut.isPending ? "Sending..." : "Send Invite"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
