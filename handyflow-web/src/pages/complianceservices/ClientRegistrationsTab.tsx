// src/pages/complianceservices/ClientRegistrationsTab.tsx
//
// Client-scoped registration register — mirrors compliancetender's own
// RegistrationsTab.tsx closely, confirmed against
// ClientComplianceRegistrationController. Takes a clientId prop since
// every endpoint here is nested under a specific client.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { Plus, ShieldCheck, ChevronDown, ChevronUp, X, Edit2, Trash2, AlertCircle, Clock } from "lucide-react"

interface Registration {
  id: string; authority: string; registrationType: string; registrationNumber: string | null
  status: string; issuedDate: string | null; expiryDate: string | null; notes: string | null
  expiringSoon: boolean; createdAt: string
}

const AUTHORITIES = ["CIPC", "SARS", "UIF", "PSIRA", "CSD", "CIDB", "NHBRC", "OTHER"]
const STATUSES = ["ACTIVE", "EXPIRED", "LAPSED", "PENDING", "NOT_APPLICABLE"]
const ACCENT = "#065F46"

const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string }> = {
  ACTIVE:         { color: "#166534", bg: "#DCFCE7", border: "#86EFAC", label: "Active" },
  EXPIRED:        { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA", label: "Expired" },
  LAPSED:         { color: "#C2410C", bg: "#FFF7ED", border: "#FDBA74", label: "Lapsed" },
  PENDING:        { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A", label: "Pending" },
  NOT_APPLICABLE: { color: "#64748B", bg: "#F1F5F9", border: "#E2E8F0", label: "N/A" },
}

const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const EMPTY_FORM = { authority: "CIPC", registrationType: "", registrationNumber: "", issuedDate: "", expiryDate: "", notes: "", status: "ACTIVE" }

export default function ClientRegistrationsTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("COMPLIANCE_SERVICES_MANAGE") || usePermission("COMPLIANCE_SERVICES_ADMIN")
  const canAdmin = usePermission("COMPLIANCE_SERVICES_ADMIN")

  const [filterAuthority, setFilterAuthority] = useState("ALL")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showAdd, setShowAdd] = useState(false)
  const [editing, setEditing] = useState<Registration | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState("")

  const { data: registrations = [], isLoading } = useQuery<Registration[]>({
    queryKey: ["cs-registrations", clientId],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/compliance-services/clients/${clientId}/registrations`)
      return r.data?.data ?? r.data ?? []
    },
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ["cs-registrations", clientId] })

  const createRegistration = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/compliance-services/clients/${clientId}/registrations`, body),
    onSuccess: () => { invalidate(); setShowAdd(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to create registration") },
  })

  const updateRegistration = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`/api/v1/compliance-services/registrations/${id}`, body),
    onSuccess: () => { invalidate(); setEditing(null); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to update registration") },
  })

  const deleteRegistration = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/compliance-services/registrations/${id}`),
    onSuccess: () => invalidate(),
  })

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.registrationType.trim()) errs.registrationType = "Registration type is required"
    if (form.expiryDate && form.issuedDate && form.expiryDate < form.issuedDate) errs.expiryDate = "Expiry date cannot be before issued date"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const openEdit = (r: Registration) => {
    setEditing(r)
    setForm({
      authority: r.authority, registrationType: r.registrationType, registrationNumber: r.registrationNumber ?? "",
      issuedDate: r.issuedDate ?? "", expiryDate: r.expiryDate ?? "", notes: r.notes ?? "", status: r.status,
    })
    setFieldErrors({}); setApiError("")
  }

  const filtered = filterAuthority === "ALL" ? registrations : registrations.filter(r => r.authority === filterAuthority)

  const stats = [
    { label: "Total",         value: registrations.length,                                    color: ACCENT },
    { label: "Active",        value: registrations.filter(r => r.status === "ACTIVE").length,  color: "#166534" },
    { label: "Expiring Soon", value: registrations.filter(r => r.expiringSoon).length,          color: "#D97706" },
    { label: "Expired",       value: registrations.filter(r => r.status === "EXPIRED").length,  color: "#DC2626" },
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

  const StatusBadge = ({ status, expiringSoon }: { status: string; expiringSoon: boolean }) => {
    if (status === "ACTIVE" && expiringSoon) {
      return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: "#FFFBEB", color: "#D97706", padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: "1px solid #FDE68A" }}><Clock size={11} /> Expiring Soon</span>
    }
    const cfg = STATUS_CFG[status] ?? STATUS_CFG.ACTIVE
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
          {["ALL", ...AUTHORITIES].map(a => (
            <button key={a} onClick={() => setFilterAuthority(a)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterAuthority === a ? 600 : 400,
                background: filterAuthority === a ? ACCENT : "#F1F5F9", color: filterAuthority === a ? "#fff" : "#64748B" }}>
              {a === "ALL" ? "All" : a}
            </button>
          ))}
        </div>
        {canManage && (
          <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
            style={{ display: "flex", alignItems: "center", gap: 7, background: ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={15} /> New Registration
          </button>
        )}
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading registrations...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <ShieldCheck size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No registrations found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {filtered.map(r => {
            const isOpen = expanded === r.id
            return (
              <div key={r.id} style={{ border: `1px solid ${r.status === "EXPIRED" ? "#FECACA" : "#E2E8F0"}`, borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "16px 20px", background: "#fff", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14, flex: 1, minWidth: 0 }}>
                    <div style={{ width: 44, height: 44, borderRadius: 10, background: "#ECFDF5", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <ShieldCheck size={18} color={ACCENT} />
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3, flexWrap: "wrap" }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{r.authority}</span>
                        <span style={{ fontSize: 11, color: "#64748B", background: "#F1F5F9", padding: "1px 8px", borderRadius: 20 }}>{r.registrationType}</span>
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        {r.registrationNumber ? `${r.registrationNumber} · ` : ""}Expires {fmtDate(r.expiryDate)}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    <StatusBadge status={r.status} expiringSoon={r.expiringSoon} />
                    {canManage && (
                      <div style={{ display: "flex", gap: 5 }}>
                        <button onClick={() => openEdit(r)} title="Edit" style={{ background: "#ECFDF5", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: ACCENT }}><Edit2 size={13} /></button>
                        {canAdmin && (
                          <button onClick={() => { if (confirm(`Delete ${r.authority} ${r.registrationType}?`)) deleteRegistration.mutate(r.id) }} title="Delete" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><Trash2 size={13} /></button>
                        )}
                      </div>
                    )}
                    <button onClick={() => setExpanded(isOpen ? null : r.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                      {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                    </button>
                  </div>
                </div>
                {isOpen && (
                  <div style={{ borderTop: "1px solid #F1F5F9", padding: "16px 20px", background: "#F8FAFC" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 14, marginBottom: 14 }}>
                      {[
                        { l: "Issued", v: fmtDate(r.issuedDate) },
                        { l: "Expiry", v: fmtDate(r.expiryDate) },
                        { l: "Registration number", v: r.registrationNumber || "—" },
                      ].map(item => (
                        <div key={item.l}>
                          <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{item.v}</div>
                        </div>
                      ))}
                    </div>
                    {r.notes && <div style={{ fontSize: 13, color: "#374151" }}>{r.notes}</div>}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {(showAdd || editing) && (
        <div onClick={() => { setShowAdd(false); setEditing(null); setApiError("") }} style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div onClick={e => e.stopPropagation()} style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{editing ? `Edit — ${editing.authority} ${editing.registrationType}` : "New Compliance Registration"}</h3>
              <button onClick={() => { setShowAdd(false); setEditing(null); setApiError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
              <div>
                <label style={lbl}>Authority *</label>
                {editing ? (
                  <div style={{ ...inp("_"), background: "#F1F5F9", color: "#64748B" }}>{editing.authority}</div>
                ) : (
                  <select value={form.authority} onChange={e => setForm(f => ({ ...f, authority: e.target.value }))} style={{ ...inp("authority"), background: "#fff" }}>
                    {AUTHORITIES.map(a => <option key={a} value={a}>{a}</option>)}
                  </select>
                )}
              </div>
              <div>
                <label style={lbl}>Registration type *</label>
                <input value={form.registrationType} onChange={e => { setForm(f => ({ ...f, registrationType: e.target.value })); setFieldErrors(f => { const n = { ...f }; delete n.registrationType; return n }) }} placeholder="e.g. Business Registration" style={inp("registrationType")} disabled={!!editing} />
                <FErr k="registrationType" />
              </div>
            </div>
            {editing && (
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Status</label>
                <select value={form.status} onChange={e => setForm(f => ({ ...f, status: e.target.value }))} style={{ ...inp("status"), background: "#fff" }}>
                  {STATUSES.map(s => <option key={s} value={s}>{STATUS_CFG[s]?.label ?? s}</option>)}
                </select>
              </div>
            )}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
              <div>
                <label style={lbl}>Registration number</label>
                <input value={form.registrationNumber} onChange={e => setForm(f => ({ ...f, registrationNumber: e.target.value }))} style={inp("registrationNumber")} />
              </div>
              <div />
              <div>
                <label style={lbl}>Issued date</label>
                <input type="date" value={form.issuedDate} onChange={e => setForm(f => ({ ...f, issuedDate: e.target.value }))} style={inp("issuedDate")} />
              </div>
              <div>
                <label style={lbl}>Expiry date</label>
                <input type="date" value={form.expiryDate} onChange={e => { setForm(f => ({ ...f, expiryDate: e.target.value })); setFieldErrors(f => { const n = { ...f }; delete n.expiryDate; return n }) }} style={inp("expiryDate")} />
                <FErr k="expiryDate" />
              </div>
            </div>
            <div>
              <label style={lbl}>Notes</label>
              <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} rows={2} style={{ ...inp("notes"), resize: "vertical" as const }} />
            </div>
            {apiError && <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{apiError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => { setShowAdd(false); setEditing(null); setApiError("") }} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button onClick={() => {
                if (!validate()) return
                if (editing) {
                  updateRegistration.mutate({ id: editing.id, body: {
                    registrationNumber: form.registrationNumber || null, status: form.status,
                    issuedDate: form.issuedDate || null, expiryDate: form.expiryDate || null, notes: form.notes || null,
                  } })
                } else {
                  createRegistration.mutate({
                    authority: form.authority, registrationType: form.registrationType,
                    registrationNumber: form.registrationNumber || null, issuedDate: form.issuedDate || null,
                    expiryDate: form.expiryDate || null, notes: form.notes || null,
                  })
                }
              }} disabled={createRegistration.isPending || updateRegistration.isPending}
                style={{ padding: "9px 22px", background: (createRegistration.isPending || updateRegistration.isPending) ? "#94A3B8" : ACCENT, color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {editing ? "Save Changes" : "Create Registration"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
