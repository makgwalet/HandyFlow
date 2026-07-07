// src/pages/fleet/DriversTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, User, X, AlertCircle, AlertTriangle, CheckCircle, Edit2,
  Phone, Mail, IdCard, ShieldCheck, UserX, UserCheck,
} from "lucide-react"

interface Driver {
  id: string
  firstName: string; lastName: string
  phone: string | null; email: string | null; idNumber: string | null
  licenseNumber: string | null; licenseCode: string | null
  licenseExpiry: string | null; licenseExpiringSoon: boolean
  prdpRequired: boolean; prdpNumber: string | null; prdpCategory: string | null
  prdpExpiry: string | null; prdpExpiringSoon: boolean
  status: string; notes: string | null; createdAt: string
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const daysUntil = (d: string | null) => d ? Math.ceil((new Date(d).getTime() - Date.now()) / 86400000) : 999

const LICENSE_CODES = ["A", "A1", "B", "C1", "C", "EB", "EC1", "EC"]
const PRDP_CATEGORIES = [
  { value: "G", label: "G — Goods" },
  { value: "P", label: "P — Passengers" },
  { value: "D", label: "D — Dangerous goods" },
]

const EMPTY_FORM = {
  firstName: "", lastName: "", phone: "", email: "", idNumber: "",
  licenseNumber: "", licenseCode: "B", licenseExpiry: "",
  prdpRequired: false, prdpNumber: "", prdpCategory: "G", prdpExpiry: "",
  notes: "",
}

function ExpiryBadge({ date, expiringSoon }: { date: string | null; expiringSoon: boolean }) {
  if (!date) return <span style={{ color: "#94A3B8", fontSize: 12 }}>Not set</span>
  const days = daysUntil(date)
  if (days < 0) return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 11, fontWeight: 700, color: "#DC2626" }}><AlertCircle size={11} />Expired {Math.abs(days)}d ago</span>
  if (expiringSoon) return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 11, fontWeight: 700, color: days <= 7 ? "#DC2626" : "#D97706" }}><AlertTriangle size={11} />{days}d left</span>
  return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 11, color: "#166534" }}><CheckCircle size={11} />Valid</span>
}

export default function DriversTab() {
  const qc = useQueryClient()
  const [showAdd, setShowAdd]     = useState(false)
  const [editing, setEditing]     = useState<Driver | null>(null)
  const [filterStatus, setFilterStatus] = useState("ACTIVE")
  const [form, setForm]           = useState(EMPTY_FORM)
  const [apiError, setApiError]   = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const { data: drivers = [], isLoading } = useQuery<Driver[]>({
    queryKey: ["fleet-drivers"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fleet/drivers?size=200")),
  })

  const createDriver = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/fleet/drivers", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["fleet-drivers"] }); closeModal() },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to register driver"),
  })

  const updateDriver = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`/api/v1/fleet/drivers/${id}`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["fleet-drivers"] }); closeModal() },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update driver"),
  })

  const setStatus = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      apiClient.post(`/api/v1/fleet/drivers/${id}/${active ? "reactivate" : "deactivate"}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["fleet-drivers"] }),
  })

  const closeModal = () => {
    setShowAdd(false); setEditing(null); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("")
  }

  const openEdit = (d: Driver) => {
    setEditing(d)
    setForm({
      firstName: d.firstName, lastName: d.lastName, phone: d.phone ?? "", email: d.email ?? "",
      idNumber: d.idNumber ?? "", licenseNumber: d.licenseNumber ?? "", licenseCode: d.licenseCode ?? "B",
      licenseExpiry: d.licenseExpiry ?? "", prdpRequired: d.prdpRequired,
      prdpNumber: d.prdpNumber ?? "", prdpCategory: d.prdpCategory ?? "G", prdpExpiry: d.prdpExpiry ?? "",
      notes: d.notes ?? "",
    })
    setApiError(""); setFieldErrors({})
  }

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.firstName.trim()) errs.firstName = "First name is required"
    if (!form.lastName.trim()) errs.lastName = "Last name is required"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const buildBody = () => ({
    firstName: form.firstName, lastName: form.lastName,
    phone: form.phone || null, email: form.email || null, idNumber: form.idNumber || null,
    licenseNumber: form.licenseNumber || null, licenseCode: form.licenseCode || null,
    licenseExpiry: form.licenseExpiry || null,
    prdpRequired: form.prdpRequired,
    prdpNumber: form.prdpRequired ? (form.prdpNumber || null) : null,
    prdpCategory: form.prdpRequired ? form.prdpCategory : null,
    prdpExpiry: form.prdpRequired ? (form.prdpExpiry || null) : null,
    notes: form.notes || null,
  })

  const filtered = drivers.filter(d => filterStatus === "ALL" || d.status === filterStatus)
  const stats = [
    { label: "Total drivers", value: drivers.length, color: "#1B3A6B" },
    { label: "Active", value: drivers.filter(d => d.status === "ACTIVE").length, color: "#166534" },
    { label: "Licence expiring", value: drivers.filter(d => d.licenseExpiringSoon).length, color: "#D97706" },
    { label: "PrDP expiring", value: drivers.filter(d => d.prdpRequired && d.prdpExpiringSoon).length, color: "#DC2626" },
  ]

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "#FFF5F5" : "#fff", outline: "none",
  })
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

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
        <div style={{ display: "flex", gap: 6 }}>
          {["ACTIVE", "INACTIVE", "ALL"].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                background: filterStatus === s ? "#1B3A6B" : "#F1F5F9", color: filterStatus === s ? "#fff" : "#64748B" }}>
              {s === "ALL" ? "All" : s.charAt(0) + s.slice(1).toLowerCase()}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Register Driver
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading drivers...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <User size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No drivers found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {filtered.map(d => (
            <div key={d.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, padding: "16px 20px", background: "#fff", opacity: d.status === "INACTIVE" ? 0.6 : 1 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 14 }}>
                <div style={{ display: "flex", gap: 14, flex: 1, minWidth: 0 }}>
                  <div style={{ width: 42, height: 42, borderRadius: "50%", background: "#EFF6FF", border: "2px solid #BFDBFE", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 15, color: "#1D4ED8", flexShrink: 0 }}>
                    {d.firstName[0]}{d.lastName[0]}
                  </div>
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" }}>
                      <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{d.firstName} {d.lastName}</span>
                      {d.status === "INACTIVE" && <span style={{ fontSize: 10, fontWeight: 700, background: "#F1F5F9", color: "#64748B", padding: "1px 7px", borderRadius: 20 }}>INACTIVE</span>}
                      {d.prdpRequired && <span style={{ fontSize: 10, fontWeight: 700, background: "#F5F3FF", color: "#7C3AED", padding: "1px 7px", borderRadius: 20 }}>PrDP {d.prdpCategory}</span>}
                    </div>
                    <div style={{ display: "flex", gap: 14, fontSize: 12, color: "#64748B", flexWrap: "wrap", marginBottom: 8 }}>
                      {d.phone && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Phone size={11} />{d.phone}</span>}
                      {d.email && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Mail size={11} />{d.email}</span>}
                      {d.idNumber && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><IdCard size={11} />{d.idNumber}</span>}
                    </div>
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                      <div style={{ padding: "8px 12px", background: "#F8FAFC", borderRadius: 8 }}>
                        <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, marginBottom: 3 }}>
                          Licence {d.licenseCode ? `(${d.licenseCode})` : ""}
                        </div>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                          <span style={{ fontSize: 13, color: "#0F172A" }}>{fmtDate(d.licenseExpiry)}</span>
                          <ExpiryBadge date={d.licenseExpiry} expiringSoon={d.licenseExpiringSoon} />
                        </div>
                      </div>
                      {d.prdpRequired && (
                        <div style={{ padding: "8px 12px", background: "#F8FAFC", borderRadius: 8 }}>
                          <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, marginBottom: 3, display: "flex", alignItems: "center", gap: 4 }}>
                            <ShieldCheck size={10} /> PrDP
                          </div>
                          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                            <span style={{ fontSize: 13, color: "#0F172A" }}>{fmtDate(d.prdpExpiry)}</span>
                            <ExpiryBadge date={d.prdpExpiry} expiringSoon={d.prdpExpiringSoon} />
                          </div>
                        </div>
                      )}
                    </div>
                    {d.notes && <div style={{ marginTop: 8, fontSize: 12, color: "#78350F", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 7, padding: "6px 10px" }}>{d.notes}</div>}
                  </div>
                </div>
                <div style={{ display: "flex", gap: 5, flexShrink: 0 }}>
                  <button onClick={() => openEdit(d)} title="Edit" style={{ background: "#FEF3C7", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#D97706" }}><Edit2 size={13} /></button>
                  {d.status === "ACTIVE" ? (
                    <button onClick={() => setStatus.mutate({ id: d.id, active: false })} title="Deactivate" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><UserX size={13} /></button>
                  ) : (
                    <button onClick={() => setStatus.mutate({ id: d.id, active: true })} title="Reactivate" style={{ background: "#F0FDF4", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#166534" }}><UserCheck size={13} /></button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {(showAdd || editing) && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 600, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{editing ? "Edit Driver" : "Register Driver"}</h3>
              <button onClick={closeModal} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 20 }}>
              <div>
                <label style={lbl}>First Name *</label>
                <input autoFocus value={form.firstName} onChange={e => setForm(f => ({ ...f, firstName: e.target.value }))} placeholder="Sipho" style={inp("firstName")} />
                {fieldErrors.firstName && <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4 }}>{fieldErrors.firstName}</div>}
              </div>
              <div>
                <label style={lbl}>Last Name *</label>
                <input value={form.lastName} onChange={e => setForm(f => ({ ...f, lastName: e.target.value }))} placeholder="Ndlovu" style={inp("lastName")} />
                {fieldErrors.lastName && <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4 }}>{fieldErrors.lastName}</div>}
              </div>
              <div>
                <label style={lbl}>Phone</label>
                <input value={form.phone} onChange={e => setForm(f => ({ ...f, phone: e.target.value }))} placeholder="+27 82 111 0001" style={inp("phone")} />
              </div>
              <div>
                <label style={lbl}>Email</label>
                <input type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} placeholder="sipho@example.co.za" style={inp("email")} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Used to notify the driver directly of expiring compliance documents</div>
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>ID / Passport Number</label>
                <input value={form.idNumber} onChange={e => setForm(f => ({ ...f, idNumber: e.target.value }))} placeholder="8501015800080" style={inp("idNumber")} />
              </div>
            </div>

            <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.07em", textTransform: "uppercase" as const, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid #F1F5F9" }}>
              Driving Licence
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14, marginBottom: 20 }}>
              <div>
                <label style={lbl}>Licence Number</label>
                <input value={form.licenseNumber} onChange={e => setForm(f => ({ ...f, licenseNumber: e.target.value }))} placeholder="SN-2019-00123" style={inp("licenseNumber")} />
              </div>
              <div>
                <label style={lbl}>Code</label>
                <select value={form.licenseCode} onChange={e => setForm(f => ({ ...f, licenseCode: e.target.value }))} style={{ ...inp("licenseCode"), background: "#fff" }}>
                  {LICENSE_CODES.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Expiry Date</label>
                <input type="date" value={form.licenseExpiry} onChange={e => setForm(f => ({ ...f, licenseExpiry: e.target.value }))} style={inp("licenseExpiry")} />
              </div>
            </div>

            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid #F1F5F9" }}>
              <input type="checkbox" id="prdpRequired" checked={form.prdpRequired}
                onChange={e => setForm(f => ({ ...f, prdpRequired: e.target.checked }))}
                style={{ width: 16, height: 16, cursor: "pointer" }} />
              <label htmlFor="prdpRequired" style={{ fontSize: 12, fontWeight: 700, color: "#7C3AED", cursor: "pointer", textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>
                Requires Professional Driving Permit (PrDP)
              </label>
            </div>
            {form.prdpRequired && (
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14, marginBottom: 20, padding: 14, background: "#F5F3FF", borderRadius: 10 }}>
                <div>
                  <label style={lbl}>PrDP Number</label>
                  <input value={form.prdpNumber} onChange={e => setForm(f => ({ ...f, prdpNumber: e.target.value }))} placeholder="PRDP-P-00456" style={inp("prdpNumber")} />
                </div>
                <div>
                  <label style={lbl}>Category</label>
                  <select value={form.prdpCategory} onChange={e => setForm(f => ({ ...f, prdpCategory: e.target.value }))} style={{ ...inp("prdpCategory"), background: "#fff" }}>
                    {PRDP_CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
                  </select>
                </div>
                <div>
                  <label style={lbl}>Expiry Date</label>
                  <input type="date" value={form.prdpExpiry} onChange={e => setForm(f => ({ ...f, prdpExpiry: e.target.value }))} style={inp("prdpExpiry")} />
                </div>
              </div>
            )}

            <div>
              <label style={lbl}>Notes</label>
              <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} rows={2} style={{ ...inp("notes"), resize: "vertical" as const }} placeholder="Any additional notes..." />
            </div>

            {apiError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={14} />{apiError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={closeModal} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button
                onClick={() => {
                  if (!validate()) return
                  const body = buildBody()
                  if (editing) updateDriver.mutate({ id: editing.id, body })
                  else createDriver.mutate(body)
                }}
                disabled={createDriver.isPending || updateDriver.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createDriver.isPending || updateDriver.isPending ? "Saving..." : editing ? "Save Changes" : "Register Driver"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
