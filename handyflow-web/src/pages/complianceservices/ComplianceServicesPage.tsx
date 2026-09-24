// src/pages/complianceservices/ComplianceServicesPage.tsx
//
// The top-level page for the complianceservices module — the client
// list. Confirmed against ComplianceClientController: GET (paged,
// status filter), POST, PUT, deactivate/reactivate, DELETE (ADMIN).
// Clicking a client navigates to /complianceservices/clients/:clientId
// (a routed page, not a tab) since a client's own registrations/
// documents/deadlines/requirements/tenders are too much to fit inside
// this shell alongside the client list itself.
//
// The optional CRM-customer picker on the create form calls the real,
// confirmed GET /api/v1/crm/customers endpoint (CustomerController) —
// checked directly before assuming it, not guessed. It requires
// CUSTOMER_READ, a different module's permission the current user may
// not have; the query is best-effort (retry: false) and the picker
// section simply doesn't render if it comes back empty or errors, so a
// user without CRM access still gets a fully working "add client" form.
import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { Plus, Building2, X, AlertCircle, ChevronRight, Ban, CheckCircle2 } from "lucide-react"

interface Client {
  id: string; name: string; crmCustomerId: string | null; crmCustomerFound: boolean
  crmCustomerName: string | null; contactEmail: string | null; contactPhone: string | null
  mandateNotes: string | null; status: string; createdAt: string
}
interface CrmCustomerOption { id: string; name: string }

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const ACCENT = "#065F46"

const EMPTY_FORM = { name: "", crmCustomerId: "", contactEmail: "", contactPhone: "", mandateNotes: "" }

export default function ComplianceServicesPage() {
  const nav = useNavigate()
  const qc = useQueryClient()
  const canManage = usePermission("COMPLIANCE_SERVICES_MANAGE") || usePermission("COMPLIANCE_SERVICES_ADMIN")
  const canAdmin = usePermission("COMPLIANCE_SERVICES_ADMIN")

  const [filterStatus, setFilterStatus] = useState("ALL")
  const [showAdd, setShowAdd] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState("")

  const { data: clients = [], isLoading } = useQuery<Client[]>({
    queryKey: ["cs-clients", filterStatus],
    queryFn: async () => unwrap(await apiClient.get(
      `/api/v1/compliance-services/clients?size=200${filterStatus !== "ALL" ? `&status=${filterStatus}` : ""}`
    )),
  })

  const { data: crmCustomers = [] } = useQuery<CrmCustomerOption[]>({
    queryKey: ["crm-customers-for-picker"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/crm/customers?size=200")),
    retry: false,
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ["cs-clients"] })

  const create = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/compliance-services/clients", body),
    onSuccess: () => { invalidate(); setShowAdd(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to add client") },
  })

  const deactivate = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/compliance-services/clients/${id}/deactivate`),
    onSuccess: () => invalidate(),
  })
  const reactivate = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/compliance-services/clients/${id}/reactivate`),
    onSuccess: () => invalidate(),
  })
  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/compliance-services/clients/${id}`),
    onSuccess: () => invalidate(),
  })

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.name.trim()) errs.name = "Client name is required"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "#FFF5F5" : "#fff", outline: "none",
  })

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Building2 size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Compliance Services</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Manage compliance and tender work for your client companies
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
          <div style={{ display: "flex", gap: 6 }}>
            {["ALL", "ACTIVE", "INACTIVE"].map(s => (
              <button key={s} onClick={() => setFilterStatus(s)}
                style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                  background: filterStatus === s ? ACCENT : "#F1F5F9", color: filterStatus === s ? "#fff" : "#64748B" }}>
                {s === "ALL" ? "All" : s.charAt(0) + s.slice(1).toLowerCase()}
              </button>
            ))}
          </div>
          {canManage && (
            <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
              style={{ display: "flex", alignItems: "center", gap: 7, background: ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
              <Plus size={15} /> Add Client
            </button>
          )}
        </div>

        {isLoading ? (
          <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading clients...</div>
        ) : clients.length === 0 ? (
          <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
            <Building2 size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
            <div style={{ fontWeight: 600, color: "#475569" }}>No clients yet</div>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {clients.map(c => (
              <div key={c.id} onClick={() => nav(`/complianceservices/clients/${c.id}`)}
                style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14, border: "1px solid #E2E8F0", borderRadius: 10, padding: "14px 18px", background: c.status === "INACTIVE" ? "#F8FAFC" : "#fff", cursor: "pointer" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 14, minWidth: 0 }}>
                  <div style={{ width: 40, height: 40, borderRadius: 10, background: "#ECFDF5", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Building2 size={17} color={ACCENT} />
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{c.name}</div>
                    <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 2 }}>
                      {c.crmCustomerId ? (c.crmCustomerFound ? `Linked to CRM: ${c.crmCustomerName}` : "CRM link no longer available") : "No CRM link"}
                      {c.contactEmail ? ` · ${c.contactEmail}` : ""}
                    </div>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }} onClick={e => e.stopPropagation()}>
                  <span style={{ background: c.status === "ACTIVE" ? "#DCFCE7" : "#F1F5F9", color: c.status === "ACTIVE" ? "#166534" : "#64748B", padding: "4px 12px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                    {c.status === "ACTIVE" ? "Active" : "Inactive"}
                  </span>
                  {canManage && (
                    <>
                      {c.status === "ACTIVE" ? (
                        <button onClick={() => deactivate.mutate(c.id)} title="Deactivate" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><Ban size={13} /></button>
                      ) : (
                        <button onClick={() => reactivate.mutate(c.id)} title="Reactivate" style={{ background: "#DCFCE7", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#166534" }}><CheckCircle2 size={13} /></button>
                      )}
                    </>
                  )}
                  {canAdmin && (
                    <button onClick={() => { if (confirm(`Delete client "${c.name}"? This cannot be undone.`)) remove.mutate(c.id) }} style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626", fontSize: 11, fontWeight: 700 }}>Delete</button>
                  )}
                  <ChevronRight size={16} color="#CBD5E1" />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {showAdd && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}
          onClick={() => { setShowAdd(false); setApiError("") }}>
          <div onClick={e => e.stopPropagation()} style={{ background: "#fff", borderRadius: 16, padding: 28, width: 520, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Add Client</h3>
              <button onClick={() => { setShowAdd(false); setApiError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Client name *</label>
              <input autoFocus value={form.name} onChange={e => { setForm(f => ({ ...f, name: e.target.value })); setFieldErrors(f => { const n = { ...f }; delete n.name; return n }) }} placeholder="Acme Construction (Pty) Ltd" style={inp("name")} />
              {fieldErrors.name && <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4 }}>{fieldErrors.name}</div>}
            </div>
            {crmCustomers.length > 0 && (
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Link to an existing CRM customer (optional)</label>
                <select value={form.crmCustomerId} onChange={e => setForm(f => ({ ...f, crmCustomerId: e.target.value }))} style={{ ...inp("crmCustomerId"), background: "#fff" }}>
                  <option value="">— None —</option>
                  {crmCustomers.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </div>
            )}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
              <div>
                <label style={lbl}>Contact email</label>
                <input value={form.contactEmail} onChange={e => setForm(f => ({ ...f, contactEmail: e.target.value }))} style={inp("contactEmail")} />
              </div>
              <div>
                <label style={lbl}>Contact phone</label>
                <input value={form.contactPhone} onChange={e => setForm(f => ({ ...f, contactPhone: e.target.value }))} style={inp("contactPhone")} />
              </div>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Mandate notes</label>
              <textarea value={form.mandateNotes} onChange={e => setForm(f => ({ ...f, mandateNotes: e.target.value }))} rows={2}
                placeholder="e.g. Full compliance management mandate signed 2026-01-15" style={{ ...inp("mandateNotes"), resize: "vertical" as const }} />
            </div>
            {apiError && <div style={{ marginBottom: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{apiError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => { setShowAdd(false); setApiError("") }} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button onClick={() => {
                if (!validate()) return
                create.mutate({
                  name: form.name, crmCustomerId: form.crmCustomerId || null,
                  contactEmail: form.contactEmail || null, contactPhone: form.contactPhone || null,
                  mandateNotes: form.mandateNotes || null,
                })
              }} disabled={create.isPending}
                style={{ padding: "9px 22px", background: create.isPending ? "#94A3B8" : ACCENT, color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: create.isPending ? "not-allowed" : "pointer" }}>
                {create.isPending ? "Adding..." : "Add Client"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
