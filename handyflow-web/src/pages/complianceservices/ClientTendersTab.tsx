// src/pages/complianceservices/ClientTendersTab.tsx
//
// Client-scoped tender list — mirrors compliancetender's own
// TendersTab.tsx, confirmed against ClientTenderController. Clicking a
// row navigates to /complianceservices/tenders/:id (a routed page).
import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { Plus, Briefcase, X, AlertCircle } from "lucide-react"

interface ClientTender {
  id: string; tenderNumber: string; name: string; tenderAuthority: string | null
  closingDate: string | null; estimatedValue: number | null; status: string
}

const ACCENT = "#065F46"
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtZar = (v: number | null) => v == null ? "—" : `R ${v.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  DRAFT:            { color: "#64748B", bg: "#F1F5F9", label: "Draft" },
  IN_PREPARATION:   { color: "#D97706", bg: "#FFFBEB", label: "In Preparation" },
  INTERNAL_REVIEW:  { color: "#D97706", bg: "#FFFBEB", label: "Internal Review" },
  READY_TO_SUBMIT:  { color: "#0D9488", bg: "#F0FDFA", label: "Ready to Submit" },
  SUBMITTED:        { color: ACCENT, bg: "#ECFDF5", label: "Submitted" },
  CLARIFICATION:    { color: ACCENT, bg: "#ECFDF5", label: "Clarification" },
  SHORTLISTED:      { color: "#0D9488", bg: "#F0FDFA", label: "Shortlisted" },
  NEGOTIATION:      { color: "#0D9488", bg: "#F0FDFA", label: "Negotiation" },
  AWARDED:          { color: "#166534", bg: "#DCFCE7", label: "Awarded" },
  UNSUCCESSFUL:     { color: "#DC2626", bg: "#FEF2F2", label: "Unsuccessful" },
  WITHDRAWN:        { color: "#DC2626", bg: "#FEF2F2", label: "Withdrawn" },
}
const ALL_STATUSES = Object.keys(STATUS_CFG)

const EMPTY_FORM = {
  name: "", tenderAuthority: "", authorityReferenceNumber: "", closingDate: "", briefingDate: "",
  siteInspectionDate: "", estimatedValue: "", industry: "", requiredClassOfWork: "",
}

export default function ClientTendersTab({ clientId }: { clientId: string }) {
  const nav = useNavigate()
  const qc = useQueryClient()
  const canManage = usePermission("COMPLIANCE_SERVICES_MANAGE") || usePermission("COMPLIANCE_SERVICES_ADMIN")

  const [filterStatus, setFilterStatus] = useState("ALL")
  const [showAdd, setShowAdd] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState("")

  const { data: tenders = [], isLoading } = useQuery<ClientTender[]>({
    queryKey: ["cs-tenders", clientId, filterStatus],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/compliance-services/clients/${clientId}/tenders?size=200${filterStatus !== "ALL" ? `&status=${filterStatus}` : ""}`)
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })

  const create = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/compliance-services/clients/${clientId}/tenders`, body),
    onSuccess: (r: any) => {
      qc.invalidateQueries({ queryKey: ["cs-tenders", clientId] })
      setShowAdd(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("")
      const id = r.data?.data?.id ?? r.data?.id
      if (id) nav(`/complianceservices/tenders/${id}`)
    },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to create tender") },
  })

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.name.trim()) errs.name = "Tender name is required"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "#FFF5F5" : "#fff", outline: "none",
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {["ALL", ...ALL_STATUSES].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                background: filterStatus === s ? ACCENT : "#F1F5F9", color: filterStatus === s ? "#fff" : "#64748B" }}>
              {s === "ALL" ? "All" : STATUS_CFG[s]?.label ?? s}
            </button>
          ))}
        </div>
        {canManage && (
          <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
            style={{ display: "flex", alignItems: "center", gap: 7, background: ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={15} /> New Tender
          </button>
        )}
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading tenders...</div>
      ) : tenders.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Briefcase size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No tenders yet</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {tenders.map(t => {
            const cfg = STATUS_CFG[t.status] ?? STATUS_CFG.DRAFT
            return (
              <div key={t.id} onClick={() => nav(`/complianceservices/tenders/${t.id}`)}
                style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14, border: "1px solid #E2E8F0", borderRadius: 10, padding: "14px 18px", background: "#fff", cursor: "pointer" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 14, minWidth: 0 }}>
                  <div style={{ width: 40, height: 40, borderRadius: 10, background: "#ECFDF5", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Briefcase size={17} color={ACCENT} />
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{t.name}</div>
                    <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 2 }}>
                      {t.tenderNumber} {t.tenderAuthority ? `· ${t.tenderAuthority}` : ""} · Closes {fmtDate(t.closingDate)}
                    </div>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 14, flexShrink: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: "#374151" }}>{fmtZar(t.estimatedValue)}</div>
                  <span style={{ background: cfg.bg, color: cfg.color, padding: "4px 12px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{cfg.label}</span>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {showAdd && (
        <div onClick={() => { setShowAdd(false); setApiError("") }} style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div onClick={e => e.stopPropagation()} style={{ background: "#fff", borderRadius: 16, padding: 28, width: 620, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>New Tender</h3>
              <button onClick={() => { setShowAdd(false); setApiError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Tender name *</label>
              <input autoFocus value={form.name} onChange={e => { setForm(f => ({ ...f, name: e.target.value })); setFieldErrors(f => { const n = { ...f }; delete n.name; return n }) }} placeholder="Construction of Municipal Offices" style={inp("name")} />
              {fieldErrors.name && <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4 }}>{fieldErrors.name}</div>}
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
              <div>
                <label style={lbl}>Tender authority</label>
                <input value={form.tenderAuthority} onChange={e => setForm(f => ({ ...f, tenderAuthority: e.target.value }))} placeholder="City of Cape Town" style={inp("tenderAuthority")} />
              </div>
              <div>
                <label style={lbl}>Authority reference</label>
                <input value={form.authorityReferenceNumber} onChange={e => setForm(f => ({ ...f, authorityReferenceNumber: e.target.value }))} style={inp("authorityReferenceNumber")} />
              </div>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14, marginBottom: 14 }}>
              <div>
                <label style={lbl}>Closing date</label>
                <input type="date" value={form.closingDate} onChange={e => setForm(f => ({ ...f, closingDate: e.target.value }))} style={inp("closingDate")} />
              </div>
              <div>
                <label style={lbl}>Briefing date</label>
                <input type="date" value={form.briefingDate} onChange={e => setForm(f => ({ ...f, briefingDate: e.target.value }))} style={inp("briefingDate")} />
              </div>
              <div>
                <label style={lbl}>Site inspection</label>
                <input type="date" value={form.siteInspectionDate} onChange={e => setForm(f => ({ ...f, siteInspectionDate: e.target.value }))} style={inp("siteInspectionDate")} />
              </div>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14, marginBottom: 14 }}>
              <div>
                <label style={lbl}>Estimated value (R)</label>
                <input type="number" value={form.estimatedValue} onChange={e => setForm(f => ({ ...f, estimatedValue: e.target.value }))} style={inp("estimatedValue")} />
              </div>
              <div>
                <label style={lbl}>Industry</label>
                <input value={form.industry} onChange={e => setForm(f => ({ ...f, industry: e.target.value }))} placeholder="Construction" style={inp("industry")} />
              </div>
              <div>
                <label style={lbl}>Required class of work</label>
                <input value={form.requiredClassOfWork} onChange={e => setForm(f => ({ ...f, requiredClassOfWork: e.target.value }))} placeholder="cidb Grade 6GB" style={inp("requiredClassOfWork")} />
              </div>
            </div>
            {apiError && <div style={{ marginBottom: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{apiError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 6 }}>
              <button onClick={() => { setShowAdd(false); setApiError("") }} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button onClick={() => {
                if (!validate()) return
                create.mutate({
                  name: form.name, tenderAuthority: form.tenderAuthority || null,
                  authorityReferenceNumber: form.authorityReferenceNumber || null,
                  closingDate: form.closingDate || null, briefingDate: form.briefingDate || null,
                  siteInspectionDate: form.siteInspectionDate || null,
                  estimatedValue: form.estimatedValue ? Number(form.estimatedValue) : null,
                  industry: form.industry || null, requiredClassOfWork: form.requiredClassOfWork || null,
                })
              }} disabled={create.isPending}
                style={{ padding: "9px 22px", background: create.isPending ? "#94A3B8" : ACCENT, color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: create.isPending ? "not-allowed" : "pointer" }}>
                {create.isPending ? "Creating..." : "Create Tender"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
