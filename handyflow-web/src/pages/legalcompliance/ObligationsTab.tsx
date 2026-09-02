// src/pages/legalcompliance/ObligationsTab.tsx
//
// Regulatory obligation register — confirmed against the real
// RegulatoryObligationController: GET (paged, status filter, sort
// reviewDate), GET /due-within, GET /{id}, POST, PUT /{id},
// POST /{id}/mark-reviewed, POST /{id}/mark-non-compliant,
// POST /{id}/link-contract, DELETE /{id} (ADMIN), GET /export/pdf.
//
// status is a COMPUTED field on the backend (RegulatoryObligation.status,
// kept in sync by a daily refreshStatus() sweep) — it is never sent on
// create/update, only read back. markReviewed() rolls reviewDate forward
// by `recurrence` and always resets status to COMPLIANT; markNonCompliant()
// is the ONLY way NON_COMPLIANT gets set, and refreshStatus() never clears
// it automatically — confirmed directly in RegulatoryObligation.java. That
// asymmetry is why "Mark Non-Compliant" requires a note (backend validates
// @NotBlank) but "Mark Reviewed" doesn't.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import {
  Plus, ClipboardList, ChevronDown, ChevronUp, X, Edit2, CheckCircle2,
  AlertCircle, ShieldAlert, Link2, Trash2, Download,
} from "lucide-react"

interface Obligation {
  id: string; title: string; category: string; regulationReference: string | null
  description: string | null; responsibleUserId: string | null; responsibleUserName: string | null
  reviewDate: string; recurrence: string; status: string; linkedContractId: string | null
  notes: string | null; lastReviewedAt: string | null; lastReviewedByName: string | null
  createdAt: string; updatedAt: string
}

const CATEGORIES = ["POPIA", "COMPANIES_ACT", "BCEA", "OHS_ACT", "TAX", "INDUSTRY_SPECIFIC", "OTHER"]
const RECURRENCES = ["ONCE", "MONTHLY", "QUARTERLY", "ANNUALLY"]
const STATUSES = ["COMPLIANT", "DUE_SOON", "OVERDUE", "NON_COMPLIANT"]

const CATEGORY_LABELS: Record<string, string> = {
  POPIA: "POPIA", COMPANIES_ACT: "Companies Act", BCEA: "BCEA", OHS_ACT: "OHS Act",
  TAX: "Tax", INDUSTRY_SPECIFIC: "Industry-specific", OTHER: "Other",
}

const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string }> = {
  COMPLIANT:     { color: "#166534", bg: "#DCFCE7", border: "#86EFAC", label: "Compliant"     },
  DUE_SOON:      { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A", label: "Due Soon"      },
  OVERDUE:       { color: "#C2410C", bg: "#FFF7ED", border: "#FDBA74", label: "Overdue"       },
  NON_COMPLIANT: { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA", label: "Non-Compliant" },
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtDateTime = (d: string | null) => d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }) : "—"

const EMPTY_FORM = {
  title: "", category: "POPIA", regulationReference: "", description: "",
  responsibleUserName: "", reviewDate: "", recurrence: "ANNUALLY", linkedContractId: "",
}

async function downloadPdf() {
  const res = await apiClient.get("/api/v1/legalcompliance/obligations/export/pdf", { responseType: "blob" })
  const url = URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement("a")
  a.href = url; a.download = "regulatory-obligation-register.pdf"
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
}

export default function ObligationsTab() {
  const qc = useQueryClient()
  const canManage = usePermission("LEGALCOMPLIANCE_MANAGE") || usePermission("LEGALCOMPLIANCE_ADMIN")
  const canAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")

  const [filterStatus, setFilterStatus] = useState("ALL")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showAdd, setShowAdd] = useState(false)
  const [editing, setEditing] = useState<Obligation | null>(null)
  const [showReview, setShowReview] = useState<Obligation | null>(null)
  const [showNonCompliant, setShowNonCompliant] = useState<Obligation | null>(null)
  const [showLink, setShowLink] = useState<Obligation | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [noteText, setNoteText] = useState("")
  const [contractId, setContractId] = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState("")

  const { data: obligations = [], isLoading } = useQuery<Obligation[]>({
    queryKey: ["lc-obligations", filterStatus],
    queryFn: async () => unwrap(await apiClient.get(
      `/api/v1/legalcompliance/obligations?size=200${filterStatus !== "ALL" ? `&status=${filterStatus}` : ""}`
    )),
  })

  const invalidate = () => { qc.invalidateQueries({ queryKey: ["lc-obligations"] }); qc.invalidateQueries({ queryKey: ["lc-obligations-all"] }); qc.invalidateQueries({ queryKey: ["lc-calendar-30"] }) }

  const createObligation = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/legalcompliance/obligations", body),
    onSuccess: () => { invalidate(); setShowAdd(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to create obligation") },
  })

  const updateObligation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`/api/v1/legalcompliance/obligations/${id}`, body),
    onSuccess: () => { invalidate(); setEditing(null); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to update obligation") },
  })

  const markReviewed = useMutation({
    mutationFn: ({ id, notes }: { id: string; notes: string }) =>
      apiClient.post(`/api/v1/legalcompliance/obligations/${id}/mark-reviewed`, notes ? { notes } : {}),
    onSuccess: () => { invalidate(); setShowReview(null); setNoteText(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to record review"),
  })

  const markNonCompliant = useMutation({
    mutationFn: ({ id, notes }: { id: string; notes: string }) =>
      apiClient.post(`/api/v1/legalcompliance/obligations/${id}/mark-non-compliant`, { notes }),
    onSuccess: () => { invalidate(); setShowNonCompliant(null); setNoteText(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to record non-compliance"),
  })

  const linkContract = useMutation({
    mutationFn: ({ id, contractId }: { id: string; contractId: string }) =>
      apiClient.post(`/api/v1/legalcompliance/obligations/${id}/link-contract`, { contractId }),
    onSuccess: () => { invalidate(); setShowLink(null); setContractId(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to link contract"),
  })

  const deleteObligation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/legalcompliance/obligations/${id}`),
    onSuccess: () => invalidate(),
  })

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.title.trim()) errs.title = "Title is required"
    if (!form.reviewDate) errs.reviewDate = "Review date is required"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const openEdit = (o: Obligation) => {
    setEditing(o)
    setForm({
      title: o.title, category: o.category, regulationReference: o.regulationReference ?? "",
      description: o.description ?? "", responsibleUserName: o.responsibleUserName ?? "",
      reviewDate: o.reviewDate, recurrence: o.recurrence, linkedContractId: o.linkedContractId ?? "",
    })
    setFieldErrors({}); setApiError("")
  }

  const stats = [
    { label: "Total",         value: obligations.length,                                              color: "#4338CA" },
    { label: "Compliant",     value: obligations.filter(o => o.status === "COMPLIANT").length,        color: "#166534" },
    { label: "Due Soon",      value: obligations.filter(o => o.status === "DUE_SOON").length,          color: "#D97706" },
    { label: "Overdue",       value: obligations.filter(o => o.status === "OVERDUE").length,           color: "#C2410C" },
    { label: "Non-Compliant", value: obligations.filter(o => o.status === "NON_COMPLIANT").length,     color: "#DC2626" },
  ]

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "#FFF5F5" : "#fff", outline: "none",
  })
  const FErr = ({ k }: { k: string }) => fieldErrors[k] ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}>
      <AlertCircle size={12} />{fieldErrors[k]}
    </div>
  ) : null

  const StatusBadge = ({ status }: { status: string }) => {
    const cfg = STATUS_CFG[status] ?? STATUS_CFG.COMPLIANT
    return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: `1px solid ${cfg.border}` }}>{cfg.label}</span>
  }

  return (
    <div>
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {["ALL", ...STATUSES].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                background: filterStatus === s ? (s === "ALL" ? "#4338CA" : STATUS_CFG[s]?.color ?? "#4338CA") : "#F1F5F9",
                color: filterStatus === s ? "#fff" : "#64748B" }}>
              {s === "ALL" ? "All" : STATUS_CFG[s]?.label ?? s}
            </button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={downloadPdf} style={{ display: "flex", alignItems: "center", gap: 6, background: "#fff", color: "#4338CA", border: "1px solid #E2E8F0", borderRadius: 8, padding: "9px 14px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            <Download size={14} /> Export PDF
          </button>
          {canManage && (
            <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
              style={{ display: "flex", alignItems: "center", gap: 7, background: "#4338CA", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
              <Plus size={15} /> New Obligation
            </button>
          )}
        </div>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading obligations...</div>
      ) : obligations.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <ClipboardList size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No obligations found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {obligations.map(o => {
            const isOpen = expanded === o.id
            const cfg = STATUS_CFG[o.status] ?? STATUS_CFG.COMPLIANT
            return (
              <div key={o.id} style={{ border: `1px solid ${o.status === "NON_COMPLIANT" ? "#FECACA" : "#E2E8F0"}`, borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "16px 20px", background: "#fff", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14, flex: 1, minWidth: 0 }}>
                    <div style={{ width: 44, height: 44, borderRadius: 10, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <ClipboardList size={18} color={cfg.color} />
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3, flexWrap: "wrap" }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{o.title}</span>
                        <span style={{ fontSize: 11, color: "#64748B", background: "#F1F5F9", padding: "1px 8px", borderRadius: 20 }}>{CATEGORY_LABELS[o.category] ?? o.category}</span>
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        Review {fmtDate(o.reviewDate)} · {o.recurrence} {o.responsibleUserName ? `· ${o.responsibleUserName}` : ""}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    <StatusBadge status={o.status} />
                    {canManage && (
                      <div style={{ display: "flex", gap: 5 }}>
                        <button onClick={() => { setShowReview(o); setNoteText("") }} title="Mark reviewed" style={{ background: "#DCFCE7", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#166534" }}><CheckCircle2 size={13} /></button>
                        <button onClick={() => { setShowNonCompliant(o); setNoteText("") }} title="Mark non-compliant" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><ShieldAlert size={13} /></button>
                        <button onClick={() => { setShowLink(o); setContractId(o.linkedContractId ?? "") }} title="Link contract" style={{ background: "#F5F3FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#7C3AED" }}><Link2 size={13} /></button>
                        <button onClick={() => openEdit(o)} title="Edit" style={{ background: "#EFF6FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#1D4ED8" }}><Edit2 size={13} /></button>
                        {canAdmin && (
                          <button onClick={() => { if (confirm(`Delete obligation "${o.title}"?`)) deleteObligation.mutate(o.id) }} title="Delete" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><Trash2 size={13} /></button>
                        )}
                      </div>
                    )}
                    <button onClick={() => setExpanded(isOpen ? null : o.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                      {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                    </button>
                  </div>
                </div>
                {isOpen && (
                  <div style={{ borderTop: "1px solid #F1F5F9", padding: "16px 20px", background: "#F8FAFC" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 14, marginBottom: 14 }}>
                      {[
                        { l: "Regulation reference", v: o.regulationReference || "—" },
                        { l: "Linked contract",      v: o.linkedContractId || "None" },
                        { l: "Last reviewed",        v: o.lastReviewedAt ? `${fmtDateTime(o.lastReviewedAt)} by ${o.lastReviewedByName ?? "—"}` : "Never" },
                      ].map(item => (
                        <div key={item.l}>
                          <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{item.v}</div>
                        </div>
                      ))}
                    </div>
                    {o.description && <div style={{ marginBottom: 10, fontSize: 13, color: "#374151" }}>{o.description}</div>}
                    {o.notes && <div style={{ padding: "8px 12px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 8, fontSize: 13, color: "#78350F" }}>{o.notes}</div>}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {(showAdd || editing) && (
        <Overlay onClose={() => { setShowAdd(false); setEditing(null); setApiError("") }}>
          <MHead title={editing ? `Edit — ${editing.title}` : "New Regulatory Obligation"} onClose={() => { setShowAdd(false); setEditing(null); setApiError("") }} />
          <Sect title="Obligation Details">
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Title *</label>
              <input autoFocus value={form.title} onChange={e => { setForm(f => ({ ...f, title: e.target.value })); setFieldErrors(f => omit(f, "title")) }} placeholder="Annual POPIA Information Officer registration" style={inp("title")} />
              <FErr k="title" />
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Category *</label>
                {editing ? (
                  <div style={{ ...inp("_"), background: "#F1F5F9", color: "#64748B" }}>{CATEGORY_LABELS[editing.category]}</div>
                ) : (
                  <select value={form.category} onChange={e => setForm(f => ({ ...f, category: e.target.value }))} style={{ ...inp("category"), background: "#fff" }}>
                    {CATEGORIES.map(c => <option key={c} value={c}>{CATEGORY_LABELS[c]}</option>)}
                  </select>
                )}
                {editing && <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Category can't be changed after creation</div>}
              </div>
              <div>
                <label style={lbl}>Regulation reference</label>
                <input value={form.regulationReference} onChange={e => setForm(f => ({ ...f, regulationReference: e.target.value }))} placeholder="e.g. POPIA s55" style={inp("regulationReference")} />
              </div>
            </div>
            <div style={{ marginTop: 14 }}>
              <label style={lbl}>Description</label>
              <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={2} style={{ ...inp("description"), resize: "vertical" as const }} />
            </div>
          </Sect>

          <Sect title="Review Schedule">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Review date *</label>
                <input type="date" value={form.reviewDate} onChange={e => { setForm(f => ({ ...f, reviewDate: e.target.value })); setFieldErrors(f => omit(f, "reviewDate")) }} style={inp("reviewDate")} />
                <FErr k="reviewDate" />
              </div>
              <div>
                <label style={lbl}>Recurrence *</label>
                <select value={form.recurrence} onChange={e => setForm(f => ({ ...f, recurrence: e.target.value }))} style={{ ...inp("recurrence"), background: "#fff" }}>
                  {RECURRENCES.map(r => <option key={r} value={r}>{r === "ONCE" ? "One-time (does not recur)" : r.charAt(0) + r.slice(1).toLowerCase()}</option>)}
                </select>
              </div>
            </div>
            <div style={{ marginTop: 10, padding: "8px 12px", background: "#EEF2FF", border: "1px solid #C7D2FE", borderRadius: 7, fontSize: 12, color: "#4338CA" }}>
              Marking this reviewed rolls the review date forward by one recurrence interval automatically.
            </div>
          </Sect>

          <Sect title="Ownership & Links">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Responsible person (name)</label>
                <input value={form.responsibleUserName} onChange={e => setForm(f => ({ ...f, responsibleUserName: e.target.value }))} placeholder="Thabang Makgwale" style={inp("responsibleUserName")} />
              </div>
              {!editing && (
                <div>
                  <label style={lbl}>Linked contract ID <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                  <input value={form.linkedContractId} onChange={e => setForm(f => ({ ...f, linkedContractId: e.target.value }))} placeholder="UUID from Contracting" style={inp("linkedContractId")} />
                </div>
              )}
            </div>
          </Sect>

          {apiError && <ErrBanner msg={apiError} />}
          <MFoot
            onCancel={() => { setShowAdd(false); setEditing(null); setApiError("") }}
            onSubmit={() => {
              if (!validate()) return
              const body: any = {
                title: form.title, regulationReference: form.regulationReference || null,
                description: form.description || null, responsibleUserId: null,
                responsibleUserName: form.responsibleUserName || null,
                reviewDate: form.reviewDate, recurrence: form.recurrence,
              }
              if (editing) updateObligation.mutate({ id: editing.id, body })
              else createObligation.mutate({ ...body, category: form.category, linkedContractId: form.linkedContractId || null })
            }}
            loading={createObligation.isPending || updateObligation.isPending}
            label={editing ? "Save Changes" : "Create Obligation"}
          />
        </Overlay>
      )}

      {showReview && (
        <Overlay onClose={() => { setShowReview(null); setApiError("") }}>
          <MHead title={`Mark Reviewed — ${showReview.title}`} onClose={() => { setShowReview(null); setApiError("") }} />
          <div style={{ padding: "10px 12px", background: "#EEF2FF", border: "1px solid #C7D2FE", borderRadius: 8, fontSize: 13, color: "#4338CA", marginBottom: 14 }}>
            This sets status to Compliant and rolls the review date forward by one {showReview.recurrence.toLowerCase()} interval{showReview.recurrence === "ONCE" ? " (unchanged — one-time obligation)" : ""}.
          </div>
          <label style={lbl}>Notes <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
          <textarea value={noteText} onChange={e => setNoteText(e.target.value)} rows={3} style={{ ...inp("_"), resize: "vertical" as const, width: "100%" }} placeholder="What was checked / confirmed..." />
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowReview(null); setApiError("") }} onSubmit={() => markReviewed.mutate({ id: showReview.id, notes: noteText })} loading={markReviewed.isPending} label="Confirm Reviewed" />
        </Overlay>
      )}

      {showNonCompliant && (
        <Overlay onClose={() => { setShowNonCompliant(null); setApiError("") }}>
          <MHead title={`Mark Non-Compliant — ${showNonCompliant.title}`} onClose={() => { setShowNonCompliant(null); setApiError("") }} />
          <div style={{ padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", marginBottom: 14, display: "flex", gap: 8 }}>
            <ShieldAlert size={16} style={{ flexShrink: 0, marginTop: 1 }} />
            <span>Non-Compliant status is never cleared automatically — only a review or manual correction changes it. Record a real finding, not a routine reminder.</span>
          </div>
          <label style={lbl}>Notes *</label>
          <textarea value={noteText} onChange={e => { setNoteText(e.target.value); setFieldErrors(f => omit(f, "notes")) }} rows={3} style={{ ...inp(noteText.trim() ? "_" : "notes"), resize: "vertical" as const, width: "100%" }} placeholder="Describe the non-compliance finding..." />
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowNonCompliant(null); setApiError("") }} onSubmit={() => { if (!noteText.trim()) { setFieldErrors({ notes: "Notes are required" }); return } markNonCompliant.mutate({ id: showNonCompliant.id, notes: noteText }) }} loading={markNonCompliant.isPending} label="Confirm Non-Compliant" disabled={!noteText.trim()} />
        </Overlay>
      )}

      {showLink && (
        <Overlay onClose={() => { setShowLink(null); setApiError("") }}>
          <MHead title={`Link Contract — ${showLink.title}`} onClose={() => { setShowLink(null); setApiError("") }} />
          <label style={lbl}>Contract ID</label>
          <input value={contractId} onChange={e => setContractId(e.target.value)} placeholder="UUID from Contracting module" style={{ ...inp("_"), width: "100%" }} />
          <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 6 }}>Read-only reference — the obligation links to the contract, nothing is written back to Contracting.</div>
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowLink(null); setApiError("") }} onSubmit={() => { if (contractId.trim()) linkContract.mutate({ id: showLink.id, contractId: contractId.trim() }) }} loading={linkContract.isPending} label="Link Contract" disabled={!contractId.trim()} />
        </Overlay>
      )}
    </div>
  )
}

function Overlay({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 620, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>{children}</div>
    </div>
  )
}
function MHead({ title, onClose }: { title: string; onClose: () => void }) {
  return <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}><h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3><button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button></div>
}
function MFoot({ onCancel, onSubmit, loading, label, disabled = false }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string; disabled?: boolean }) {
  return <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}><button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button><button onClick={onSubmit} disabled={loading || disabled} style={{ padding: "9px 22px", background: loading || disabled ? "#94A3B8" : "#4338CA", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading || disabled ? "not-allowed" : "pointer" }}>{loading ? "Saving..." : label}</button></div>
}
function Sect({ title, children }: { title: string; children: React.ReactNode }) {
  return <div style={{ marginBottom: 20 }}><div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.07em", textTransform: "uppercase" as const, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid #F1F5F9" }}>{title}</div>{children}</div>
}
function ErrBanner({ msg }: { msg: string }) {
  return <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{msg}</div>
}
const omit = (obj: Record<string, string>, key: string) => { const n = { ...obj }; delete n[key]; return n }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
