// src/pages/legalcompliance/PopiaTab.tsx
//
// Org-wide POPIA processing-activity register — confirmed against the real
// PopiaProcessingActivityController: GET (unpaginated list), GET /{id},
// POST, PUT /{id}, POST /{id}/deactivate, POST /{id}/reactivate,
// DELETE /{id} (ADMIN), GET /export/pdf. No evidence endpoints on this
// controller (confirmed).
//
// The cross-border-transfer/cross-border-details pairing mirrors a real
// backend rule: PopiaProcessingActivity.create()/update() throws
// IllegalArgumentException (citing POPIA s72) if crossBorderTransfer=true
// and crossBorderDetails is blank. The form below enforces the same rule
// client-side so the error surfaces as a field hint instead of a raw 400.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import {
  Plus, Lock, ChevronDown, ChevronUp, X, Edit2, Power, PowerOff,
  Trash2, Download, Globe2, AlertCircle,
} from "lucide-react"

interface PopiaActivity {
  id: string; activityName: string; dataCategory: string; purpose: string | null
  lawfulBasis: string; responsibleDepartment: string | null; responsibleUserId: string | null
  responsibleUserName: string | null; retentionPeriodDescription: string | null
  crossBorderTransfer: boolean; crossBorderDetails: string | null; securityMeasures: string | null
  reviewDate: string | null; active: boolean; createdAt: string; updatedAt: string
}

const DATA_CATEGORIES = ["CUSTOMER", "EMPLOYEE", "SUPPLIER", "MARKETING_CONTACT", "OTHER"]
const LAWFUL_BASES = ["CONSENT", "CONTRACT", "LEGAL_OBLIGATION", "PROTECT_VITAL_INTEREST", "PUBLIC_LAW_DUTY", "LEGITIMATE_INTEREST"]

const LAWFUL_BASIS_LABELS: Record<string, string> = {
  CONSENT: "Consent", CONTRACT: "Contract", LEGAL_OBLIGATION: "Legal obligation",
  PROTECT_VITAL_INTEREST: "Protection of a vital interest", PUBLIC_LAW_DUTY: "Public law duty",
  LEGITIMATE_INTEREST: "Legitimate interest",
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const EMPTY_FORM = {
  activityName: "", dataCategory: "CUSTOMER", purpose: "", lawfulBasis: "CONSENT",
  responsibleDepartment: "", responsibleUserName: "", retentionPeriodDescription: "",
  crossBorderTransfer: false, crossBorderDetails: "", securityMeasures: "", reviewDate: "",
}

async function downloadPdf() {
  const res = await apiClient.get("/api/v1/legalcompliance/popia-activities/export/pdf", { responseType: "blob" })
  const url = URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement("a")
  a.href = url; a.download = "popia-processing-activity-register.pdf"
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
}

export default function PopiaTab() {
  const qc = useQueryClient()
  const canManage = usePermission("LEGALCOMPLIANCE_MANAGE") || usePermission("LEGALCOMPLIANCE_ADMIN")
  const canAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")

  const [filterActive, setFilterActive] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ACTIVE")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showAdd, setShowAdd] = useState(false)
  const [editing, setEditing] = useState<PopiaActivity | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState("")

  const { data: activities = [], isLoading } = useQuery<PopiaActivity[]>({
    queryKey: ["lc-popia"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/popia-activities")),
  })

  const invalidate = () => { qc.invalidateQueries({ queryKey: ["lc-popia"] }); qc.invalidateQueries({ queryKey: ["lc-popia-all"] }) }

  const createActivity = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/legalcompliance/popia-activities", body),
    onSuccess: () => { invalidate(); setShowAdd(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to register activity") },
  })

  const updateActivity = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`/api/v1/legalcompliance/popia-activities/${id}`, body),
    onSuccess: () => { invalidate(); setEditing(null); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to update activity") },
  })

  const toggleActive = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      apiClient.post(`/api/v1/legalcompliance/popia-activities/${id}/${active ? "reactivate" : "deactivate"}`),
    onSuccess: () => invalidate(),
  })

  const deleteActivity = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/legalcompliance/popia-activities/${id}`),
    onSuccess: () => invalidate(),
  })

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.activityName.trim()) errs.activityName = "Activity name is required"
    if (form.crossBorderTransfer && !form.crossBorderDetails.trim())
      errs.crossBorderDetails = "Required when cross-border transfer is enabled (POPIA s72)"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const openEdit = (a: PopiaActivity) => {
    setEditing(a)
    setForm({
      activityName: a.activityName, dataCategory: a.dataCategory, purpose: a.purpose ?? "",
      lawfulBasis: a.lawfulBasis, responsibleDepartment: a.responsibleDepartment ?? "",
      responsibleUserName: a.responsibleUserName ?? "", retentionPeriodDescription: a.retentionPeriodDescription ?? "",
      crossBorderTransfer: a.crossBorderTransfer, crossBorderDetails: a.crossBorderDetails ?? "",
      securityMeasures: a.securityMeasures ?? "", reviewDate: a.reviewDate ?? "",
    })
    setFieldErrors({}); setApiError("")
  }

  const filtered = activities.filter(a => filterActive === "ALL" || (filterActive === "ACTIVE" ? a.active : !a.active))

  const stats = [
    { label: "Total",              value: activities.length,                                    color: "#4338CA" },
    { label: "Active",             value: activities.filter(a => a.active).length,                color: "#166534" },
    { label: "Cross-border",       value: activities.filter(a => a.active && a.crossBorderTransfer).length, color: "#D97706" },
    { label: "Consent-based",      value: activities.filter(a => a.active && a.lawfulBasis === "CONSENT").length, color: "#1D4ED8" },
  ]

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "#FFF5F5" : "#fff", outline: "none",
  })
  const FErr = ({ k }: { k: string }) => fieldErrors[k] ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}><AlertCircle size={12} />{fieldErrors[k]}</div>
  ) : null

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
          {(["ACTIVE", "INACTIVE", "ALL"] as const).map(s => (
            <button key={s} onClick={() => setFilterActive(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterActive === s ? 600 : 400,
                background: filterActive === s ? "#4338CA" : "#F1F5F9", color: filterActive === s ? "#fff" : "#64748B" }}>
              {s === "ALL" ? "All" : s.charAt(0) + s.slice(1).toLowerCase()}
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
              <Plus size={15} /> Register Activity
            </button>
          )}
        </div>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading register...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Lock size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No processing activities found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {filtered.map(a => {
            const isOpen = expanded === a.id
            return (
              <div key={a.id} style={{ border: `1px solid ${!a.active ? "#E2E8F0" : "#E2E8F0"}`, borderRadius: 12, overflow: "hidden", opacity: a.active ? 1 : 0.65 }}>
                <div style={{ padding: "16px 20px", background: "#fff", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14, flex: 1, minWidth: 0 }}>
                    <div style={{ width: 44, height: 44, borderRadius: 10, background: "#EEF2FF", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <Lock size={18} color="#4338CA" />
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3, flexWrap: "wrap" }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{a.activityName}</span>
                        <span style={{ fontSize: 11, color: "#64748B", background: "#F1F5F9", padding: "1px 8px", borderRadius: 20 }}>{a.dataCategory}</span>
                        {a.crossBorderTransfer && <span style={{ display: "flex", alignItems: "center", gap: 3, fontSize: 10, fontWeight: 700, background: "#FFFBEB", color: "#D97706", padding: "1px 7px", borderRadius: 20, border: "1px solid #FDE68A" }}><Globe2 size={10} />CROSS-BORDER</span>}
                        {!a.active && <span style={{ fontSize: 10, fontWeight: 700, background: "#F1F5F9", color: "#64748B", padding: "1px 7px", borderRadius: 20 }}>INACTIVE</span>}
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        {LAWFUL_BASIS_LABELS[a.lawfulBasis] ?? a.lawfulBasis}{a.responsibleDepartment ? ` · ${a.responsibleDepartment}` : ""}{a.reviewDate ? ` · Review ${fmtDate(a.reviewDate)}` : ""}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    {canManage && (
                      <div style={{ display: "flex", gap: 5 }}>
                        <button onClick={() => toggleActive.mutate({ id: a.id, active: !a.active })} title={a.active ? "Deactivate" : "Reactivate"}
                          style={{ background: a.active ? "#F1F5F9" : "#DCFCE7", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: a.active ? "#64748B" : "#166534" }}>
                          {a.active ? <PowerOff size={13} /> : <Power size={13} />}
                        </button>
                        <button onClick={() => openEdit(a)} title="Edit" style={{ background: "#EFF6FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#1D4ED8" }}><Edit2 size={13} /></button>
                        {canAdmin && (
                          <button onClick={() => { if (confirm(`Delete "${a.activityName}"?`)) deleteActivity.mutate(a.id) }} title="Delete" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><Trash2 size={13} /></button>
                        )}
                      </div>
                    )}
                    <button onClick={() => setExpanded(isOpen ? null : a.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                      {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                    </button>
                  </div>
                </div>
                {isOpen && (
                  <div style={{ borderTop: "1px solid #F1F5F9", padding: "16px 20px", background: "#F8FAFC" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(2,1fr)", gap: 14, marginBottom: 14 }}>
                      {[
                        { l: "Purpose",                    v: a.purpose || "—" },
                        { l: "Responsible person",         v: a.responsibleUserName || "—" },
                        { l: "Retention period",           v: a.retentionPeriodDescription || "—" },
                        { l: "Security measures",          v: a.securityMeasures || "—" },
                      ].map(item => (
                        <div key={item.l}>
                          <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{item.v}</div>
                        </div>
                      ))}
                    </div>
                    {a.crossBorderTransfer && a.crossBorderDetails && (
                      <div style={{ padding: "8px 12px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 8, fontSize: 13, color: "#78350F" }}>
                        <strong>Cross-border transfer details:</strong> {a.crossBorderDetails}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {(showAdd || editing) && (
        <Overlay onClose={() => { setShowAdd(false); setEditing(null); setApiError("") }}>
          <MHead title={editing ? `Edit — ${editing.activityName}` : "Register Processing Activity"} onClose={() => { setShowAdd(false); setEditing(null); setApiError("") }} />
          <Sect title="Activity">
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Activity name *</label>
              <input autoFocus value={form.activityName} onChange={e => { setForm(f => ({ ...f, activityName: e.target.value })); setFieldErrors(f => omit(f, "activityName")) }} placeholder="Customer marketing database" style={inp("activityName")} />
              <FErr k="activityName" />
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Data category *</label>
                <select value={form.dataCategory} onChange={e => setForm(f => ({ ...f, dataCategory: e.target.value }))} style={{ ...inp("dataCategory"), background: "#fff" }}>
                  {DATA_CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Lawful basis *</label>
                <select value={form.lawfulBasis} onChange={e => setForm(f => ({ ...f, lawfulBasis: e.target.value }))} style={{ ...inp("lawfulBasis"), background: "#fff" }}>
                  {LAWFUL_BASES.map(b => <option key={b} value={b}>{LAWFUL_BASIS_LABELS[b]}</option>)}
                </select>
              </div>
            </div>
            <div style={{ marginTop: 14 }}>
              <label style={lbl}>Purpose</label>
              <textarea value={form.purpose} onChange={e => setForm(f => ({ ...f, purpose: e.target.value }))} rows={2} style={{ ...inp("purpose"), resize: "vertical" as const }} />
            </div>
          </Sect>

          <Sect title="Ownership & Retention">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Responsible department</label>
                <input value={form.responsibleDepartment} onChange={e => setForm(f => ({ ...f, responsibleDepartment: e.target.value }))} style={inp("responsibleDepartment")} />
              </div>
              <div>
                <label style={lbl}>Responsible person (name)</label>
                <input value={form.responsibleUserName} onChange={e => setForm(f => ({ ...f, responsibleUserName: e.target.value }))} style={inp("responsibleUserName")} />
              </div>
              <div>
                <label style={lbl}>Retention period</label>
                <input value={form.retentionPeriodDescription} onChange={e => setForm(f => ({ ...f, retentionPeriodDescription: e.target.value }))} placeholder="e.g. 5 years after last contact" style={inp("retentionPeriodDescription")} />
              </div>
              <div>
                <label style={lbl}>Review date</label>
                <input type="date" value={form.reviewDate} onChange={e => setForm(f => ({ ...f, reviewDate: e.target.value }))} style={inp("reviewDate")} />
              </div>
            </div>
            <div style={{ marginTop: 14 }}>
              <label style={lbl}>Security measures</label>
              <textarea value={form.securityMeasures} onChange={e => setForm(f => ({ ...f, securityMeasures: e.target.value }))} rows={2} style={{ ...inp("securityMeasures"), resize: "vertical" as const }} />
            </div>
          </Sect>

          <Sect title="Cross-Border Transfer">
            <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer", marginBottom: 12 }}>
              <input type="checkbox" checked={form.crossBorderTransfer} onChange={e => { setForm(f => ({ ...f, crossBorderTransfer: e.target.checked })); if (!e.target.checked) setFieldErrors(f => omit(f, "crossBorderDetails")) }} style={{ width: 16, height: 16 }} />
              <span style={{ fontSize: 13, color: "#374151" }}>This activity involves transferring personal information outside South Africa</span>
            </label>
            {form.crossBorderTransfer && (
              <div>
                <label style={lbl}>Cross-border details *</label>
                <textarea value={form.crossBorderDetails} onChange={e => { setForm(f => ({ ...f, crossBorderDetails: e.target.value })); setFieldErrors(f => omit(f, "crossBorderDetails")) }} rows={2} style={{ ...inp("crossBorderDetails"), resize: "vertical" as const }} placeholder="Destination country, safeguards in place, recipient..." />
                <FErr k="crossBorderDetails" />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 4 }}>Required under POPIA s72 whenever cross-border transfer is enabled.</div>
              </div>
            )}
          </Sect>

          {apiError && <ErrBanner msg={apiError} />}
          <MFoot
            onCancel={() => { setShowAdd(false); setEditing(null); setApiError("") }}
            onSubmit={() => {
              if (!validate()) return
              const body = {
                activityName: form.activityName, dataCategory: form.dataCategory, purpose: form.purpose || null,
                lawfulBasis: form.lawfulBasis, responsibleDepartment: form.responsibleDepartment || null,
                responsibleUserId: null, responsibleUserName: form.responsibleUserName || null,
                retentionPeriodDescription: form.retentionPeriodDescription || null,
                crossBorderTransfer: form.crossBorderTransfer, crossBorderDetails: form.crossBorderDetails || null,
                securityMeasures: form.securityMeasures || null, reviewDate: form.reviewDate || null,
              }
              if (editing) updateActivity.mutate({ id: editing.id, body })
              else createActivity.mutate(body)
            }}
            loading={createActivity.isPending || updateActivity.isPending}
            label={editing ? "Save Changes" : "Register Activity"}
          />
        </Overlay>
      )}
    </div>
  )
}

function Overlay({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 640, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>{children}</div>
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
