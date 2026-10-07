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
import { Plus, Briefcase, X, AlertCircle, Search, Layers, Timer, Wallet, Hourglass, ChevronRight } from "lucide-react"
import Chip from "../../components/ui/Chip"
import StatTile from "../../components/ui/StatTile"
import LookupInput from "../../components/ui/LookupInput"
import { TENDER_AUTHORITIES, INDUSTRIES, CLASS_OF_WORK } from "../../lookups/southAfrica"
import { tenderKpis, filterAndSort, countdown, edgeTone, type TenderSort } from "../compliancetender/tendersView.logic"

interface ClientTender {
  id: string; tenderNumber: string; name: string; tenderAuthority: string | null
  closingDate: string | null; estimatedValue: number | null; status: string
}

const ACCENT = "var(--hf-success-solid-strong)"
const ACCENT_TEXT = "var(--hf-success-text-strong)";
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtZar = (v: number | null) => v == null ? "—" : `R ${v.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  DRAFT:            { color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)", label: "Draft" },
  IN_PREPARATION:   { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", label: "In Preparation" },
  INTERNAL_REVIEW:  { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", label: "Internal Review" },
  READY_TO_SUBMIT:  { color: "var(--hf-accent-text)", bg: "var(--hf-accent-soft)", label: "Ready to Submit" },
  SUBMITTED:        { color: ACCENT_TEXT, bg: "var(--hf-success-soft)", label: "Submitted" },
  CLARIFICATION:    { color: ACCENT_TEXT, bg: "var(--hf-success-soft)", label: "Clarification" },
  SHORTLISTED:      { color: "var(--hf-accent-text)", bg: "var(--hf-accent-soft)", label: "Shortlisted" },
  NEGOTIATION:      { color: "var(--hf-accent-text)", bg: "var(--hf-accent-soft)", label: "Negotiation" },
  AWARDED:          { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", label: "Awarded" },
  UNSUCCESSFUL:     { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", label: "Unsuccessful" },
  WITHDRAWN:        { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", label: "Withdrawn" },
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
  const [search, setSearch] = useState("")
  const [sort, setSort] = useState<TenderSort>("closing")
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

  const { data: allTenders = [] } = useQuery<ClientTender[]>({
    queryKey: ["cs-tenders", clientId, "ALL"],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/compliance-services/clients/${clientId}/tenders?size=200`)
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })
  const today = new Date()
  const kpis = tenderKpis(allTenders, today)
  const shown = filterAndSort(tenders, search, sort)

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
    border: `1.5px solid ${fieldErrors[k] ? "var(--hf-danger)" : "var(--hf-border)"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "var(--hf-danger-soft)" : "var(--hf-surface)", color: "var(--hf-text)", outline: "none",
  })

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 18 }}>
        <StatTile label="Active tenders" value={kpis.active} icon={<Layers size={18} />} tone="info" />
        <StatTile label="Closing within 14 days" value={kpis.closingSoon} icon={<Timer size={18} />} tone={kpis.closingSoon > 0 ? "warn" : "neutral"} />
        <StatTile label="Pipeline value" value={kpis.pipelineText} icon={<Wallet size={18} />} tone="accent" />
        <StatTile label="Awaiting outcome" value={kpis.awaiting} icon={<Hourglass size={18} />} />
      </div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", flex: "1 1 320px" }}>
          <div style={{ position: "relative", flex: "1 1 220px", maxWidth: 340 }}>
            <Search size={14} style={{ position: "absolute", left: 11, top: 11, color: "var(--hf-text-faint)" }} />
            <input aria-label="Search tenders" value={search} onChange={e => setSearch(e.target.value)} placeholder="Search name, number or authority"
              style={{ width: "100%", boxSizing: "border-box", padding: "8px 12px 8px 32px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", outline: "none" }} />
          </div>
          <select aria-label="Sort tenders" value={sort} onChange={e => setSort(e.target.value as TenderSort)}
            style={{ padding: "8px 10px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-secondary)" }}>
            <option value="closing">Closing soonest</option>
            <option value="value">Highest value</option>
            <option value="name">Name A–Z</option>
          </select>
        </div>
        {canManage && (
          <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
            style={{ display: "flex", alignItems: "center", gap: 7, background: ACCENT, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={15} /> New Tender
          </button>
        )}
      </div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 16 }}>
        {["ALL", ...ALL_STATUSES].map(s => (
          <button key={s} onClick={() => setFilterStatus(s)}
            style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
              background: filterStatus === s ? ACCENT : "var(--hf-surface-sunken)", color: filterStatus === s ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
            {s === "ALL" ? "All" : STATUS_CFG[s]?.label ?? s}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading tenders...</div>
      ) : shown.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "var(--hf-text-faint)" }}>
          <Briefcase size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>{search.trim() ? "No tender matches your search" : "No tenders yet"}</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {shown.map(t => {
            const cfg = STATUS_CFG[t.status] ?? STATUS_CFG.DRAFT
            const cd = countdown(t, today)
            const edge = edgeTone(cd)
            const edgeColor = edge === "bad" ? "var(--hf-danger)" : edge === "warn" ? "var(--hf-warning-text)" : "var(--hf-border)"
            return (
              <div key={t.id} role="link" tabIndex={0} onClick={() => nav(`/complianceservices/tenders/${t.id}`)} onKeyDown={e => { if (e.key === "Enter") nav(`/complianceservices/tenders/${t.id}`) }}
                style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14, flexWrap: "wrap", border: "1px solid var(--hf-border)", borderLeft: `4px solid ${edgeColor}`, borderRadius: 12, padding: "14px 18px", background: "var(--hf-surface)", cursor: "pointer" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 14, minWidth: 0, flex: "1 1 280px" }}>
                  <div style={{ width: 42, height: 42, borderRadius: 11, background: "var(--hf-success-soft)", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Briefcase size={18} style={{ color: ACCENT_TEXT }} />
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 700, fontSize: 14.5, color: "var(--hf-text)" }}>{t.name}</div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 3 }}>
                      {t.tenderNumber}{t.tenderAuthority ? ` · ${t.tenderAuthority}` : ""} · Closing {fmtDate(t.closingDate)}
                    </div>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
                  {cd && <Chip tone={cd.tone}>{cd.text}</Chip>}
                  <div style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text-secondary)", minWidth: 96, textAlign: "right", marginLeft: "auto" }}>{fmtZar(t.estimatedValue)}</div>
                  <span style={{ background: cfg.bg, color: cfg.color, padding: "4px 12px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{cfg.label}</span>
                  <ChevronRight size={16} style={{ color: "var(--hf-text-faint)" }} />
                </div>
              </div>
            )
          })}
        </div>
      )}

      {showAdd && (
        <div onClick={() => { setShowAdd(false); setApiError("") }} style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div onClick={e => e.stopPropagation()} style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 620, maxWidth: "calc(100vw - 32px)", maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>New Tender</h3>
              <button onClick={() => { setShowAdd(false); setApiError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Tender name *</label>
              <input autoFocus value={form.name} onChange={e => { setForm(f => ({ ...f, name: e.target.value })); setFieldErrors(f => { const n = { ...f }; delete n.name; return n }) }} placeholder="Construction of Municipal Offices" style={inp("name")} />
              {fieldErrors.name && <div style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{fieldErrors.name}</div>}
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
              <div>
                <label style={lbl}>Tender authority</label>
                <LookupInput value={form.tenderAuthority} options={TENDER_AUTHORITIES} onChange={v => setForm(f => ({ ...f, tenderAuthority: v }))} placeholder="City of Cape Town" style={inp("tenderAuthority")} />
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
                <LookupInput value={form.industry} options={INDUSTRIES} onChange={v => setForm(f => ({ ...f, industry: v }))} placeholder="Construction" style={inp("industry")} />
              </div>
              <div>
                <label style={lbl}>Required class of work</label>
                <LookupInput value={form.requiredClassOfWork} options={CLASS_OF_WORK} onChange={v => setForm(f => ({ ...f, requiredClassOfWork: v }))} placeholder="cidb Grade 6GB" style={inp("requiredClassOfWork")} />
              </div>
            </div>
            {apiError && <div style={{ marginBottom: 14, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{apiError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 6 }}>
              <button onClick={() => { setShowAdd(false); setApiError("") }} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
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
                style={{ padding: "9px 22px", background: create.isPending ? "var(--hf-text-faint)" : ACCENT, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: create.isPending ? "not-allowed" : "pointer" }}>
                {create.isPending ? "Creating..." : "Create Tender"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }
