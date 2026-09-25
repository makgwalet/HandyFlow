// src/pages/security/GateAccessTab.tsx
//
// FIX (P1 backlog): the security-module-analysis-report flagged this as
// the largest gap in the whole module — "an entire visitor/contractor/
// delivery/vehicle registry subsystem, plus the 4th security report, has
// no tab in SecurityPage at all." Confirmed directly against the real
// backend (GateAccessController, GateAccessService, the DTOs) before
// building this: staff/admin manages Access Points and REVIEWS what
// guards log (on-site list, gate log, evidence, the site-access report).
// Actually logging an arrival/exit/photo happens through
// GuardGateAccessController — a separate, guard-app-only surface (mobile,
// session-resolved identity) — deliberately not duplicated here.

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  DoorOpen, Plus, Edit2, Ban, RotateCcw, Users, History,
  FileBarChart, Download, X, Paperclip, Truck, User, Building2,
} from "lucide-react"

// ── Types ──────────────────────────────────────────────────────────────────────

interface Site { id: string; name: string }

interface AccessPoint {
  id: string; siteId: string; name: string; description: string | null
  active: boolean; createdAt: string
}

interface GateEntry {
  id: string; siteId: string; accessPointId: string; accessPointName: string
  entryType: "VISITOR" | "CONTRACTOR" | "DELIVERY" | "STAFF_VEHICLE" | "OTHER"
  personName: string; idNumber: string | null; phone: string | null; company: string | null
  hostName: string | null; hostContact: string | null; purpose: string | null
  vehicleRegistration: string | null; vehicleMakeModel: string | null; driverName: string | null
  idScanConfidence: string | null
  loggedInByGuardId: string | null; loggedInByGuardName: string | null; loggedInAt: string
  loggedOutByGuardId: string | null; loggedOutByGuardName: string | null; loggedOutAt: string | null
  status: "ON_SITE" | "DEPARTED" | "OVERSTAYED"
  createdAt: string
}

interface SiteAccessReport {
  siteId: string; siteName: string; month: string
  totalEntries: number; currentlyOnSite: number; departed: number; overstayed: number
  entriesByType: Record<string, number>
  entries: {
    entryType: string; personName: string; company: string | null
    vehicleRegistration: string | null; accessPointName: string
    loggedInAt: string; loggedOutAt: string | null; status: string
  }[]
}

interface Evidence {
  id: string; fileName: string; contentType: string; fileSizeBytes: number
  evidenceType: string; status: string; uploadedByName: string; createdAt: string
}

// ── Config ─────────────────────────────────────────────────────────────────────

const ENTRY_TYPE_CFG: Record<string, { label: string; icon: any; color: string; bg: string }> = {
  VISITOR:       { label: "Visitor",       icon: User,     color: "var(--hf-info-text)", bg: "var(--hf-info-soft)" },
  CONTRACTOR:    { label: "Contractor",    icon: Building2, color: "var(--hf-violet-text)", bg: "var(--hf-violet-soft)" },
  DELIVERY:      { label: "Delivery",      icon: Truck,    color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
  STAFF_VEHICLE: { label: "Staff Vehicle", icon: Truck,    color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  OTHER:         { label: "Other",         icon: User,     color: "var(--hf-text-muted)", bg: "var(--hf-surface-muted)" },
}

const STATUS_CFG: Record<string, { label: string; color: string; bg: string; border: string }> = {
  ON_SITE:    { label: "On Site",   color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", border: "var(--hf-success-border)" },
  DEPARTED:   { label: "Departed",  color: "var(--hf-text-muted)", bg: "var(--hf-surface-muted)", border: "var(--hf-border)" },
  OVERSTAYED: { label: "Overstayed", color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", border: "var(--hf-danger-border)" },
}

const SUB_TABS = [
  { id: "access-points", label: "Access Points", icon: DoorOpen },
  { id: "on-site",       label: "On-Site Now",   icon: Users },
  { id: "gate-log",      label: "Gate Log",      icon: History },
  { id: "report",        label: "Site Access Report", icon: FileBarChart },
] as const

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "var(--hf-surface)", outline: "none" }
const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }
const submitBtn: React.CSSProperties = { padding: "9px 18px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontSize: 14, fontWeight: 600, cursor: "pointer" }

function thisMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`
}
function fmtDateTime(s: string | null) {
  if (!s) return "—"
  return new Date(s).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })
}

// ── Main component ─────────────────────────────────────────────────────────────

export default function GateAccessTab() {
  const qc = useQueryClient()
  const [subTab, setSubTab] = useState<typeof SUB_TABS[number]["id"]>("access-points")
  const [siteId, setSiteId] = useState("")
  const [error, setError] = useState("")
  const [evidenceFor, setEvidenceFor] = useState<GateEntry | null>(null)

  const { data: sites = [] } = useQuery<Site[]>({
    queryKey: ["sites-list"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/sites?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as Site[]
    },
  })

  // ── Access Points ────────────────────────────────────────────────────────────

  const { data: accessPoints = [], isLoading: apLoading } = useQuery<AccessPoint[]>({
    queryKey: ["gate-access-points", siteId],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/security/sites/${siteId}/access-points`)
      return (r.data?.data ?? r.data) as AccessPoint[]
    },
    enabled: !!siteId && subTab === "access-points",
  })

  const [showApForm, setShowApForm] = useState<AccessPoint | "new" | null>(null)
  const [apForm, setApForm] = useState({ name: "", description: "" })

  const createAp = useMutation({
    mutationFn: () => apiClient.post("/api/v1/security/access-points", { siteId, ...apForm }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["gate-access-points", siteId] }); setShowApForm(null); setApForm({ name: "", description: "" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create access point"),
  })
  const updateAp = useMutation({
    mutationFn: ({ id }: { id: string }) => apiClient.put(`/api/v1/security/access-points/${id}`, apForm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["gate-access-points", siteId] }); setShowApForm(null); setApForm({ name: "", description: "" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to update access point"),
  })
  const deactivateAp = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/access-points/${id}/deactivate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["gate-access-points", siteId] }),
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to deactivate access point"),
  })
  const reactivateAp = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/access-points/${id}/reactivate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["gate-access-points", siteId] }),
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to reactivate access point"),
  })

  const openApEdit = (ap: AccessPoint) => { setApForm({ name: ap.name, description: ap.description ?? "" }); setShowApForm(ap); setError("") }
  const openApNew = () => { setApForm({ name: "", description: "" }); setShowApForm("new"); setError("") }

  // ── On-Site Now ──────────────────────────────────────────────────────────────

  const { data: onSite = [], isLoading: onSiteLoading } = useQuery<GateEntry[]>({
    queryKey: ["gate-on-site", siteId],
    queryFn: async () => (await apiClient.get(`/api/v1/security/sites/${siteId}/on-site`)).data?.data ?? [],
    enabled: !!siteId && subTab === "on-site",
    refetchInterval: 30000, // matches DeviceSessionsTab's own live-ish polling convention
  })

  // ── Gate Log ─────────────────────────────────────────────────────────────────

  const [logFrom, setLogFrom] = useState(() => { const d = new Date(); d.setDate(d.getDate() - 7); return d.toISOString().slice(0, 10) })
  const [logTo, setLogTo]     = useState(() => new Date().toISOString().slice(0, 10))
  const { data: gateLog, isLoading: logLoading } = useQuery<{ content: GateEntry[]; totalElements: number }>({
    queryKey: ["gate-log", siteId, logFrom, logTo],
    queryFn: async () => {
      const from = `${logFrom}T00:00:00Z`; const to = `${logTo}T23:59:59Z`
      const r = await apiClient.get(`/api/v1/security/sites/${siteId}/gate-log?from=${from}&to=${to}&size=50`)
      return (r.data?.data ?? r.data) as { content: GateEntry[]; totalElements: number }
    },
    enabled: !!siteId && subTab === "gate-log",
  })

  // ── Evidence ─────────────────────────────────────────────────────────────────

  const { data: evidence = [], isLoading: evidenceLoading } = useQuery<Evidence[]>({
    queryKey: ["gate-entry-evidence", evidenceFor?.id],
    queryFn: async () => (await apiClient.get(`/api/v1/security/gate-entries/${evidenceFor!.id}/attachments`)).data?.data ?? [],
    enabled: !!evidenceFor,
  })
  const downloadEvidence = async (ev: Evidence) => {
    const res = await apiClient.get(`/api/v1/evidence/${ev.id}/download`, { responseType: "blob" })
    const url = URL.createObjectURL(new Blob([res.data], { type: ev.contentType }))
    const a = document.createElement("a"); a.href = url; a.download = ev.fileName; a.click()
  }

  // ── Site Access Report ───────────────────────────────────────────────────────

  const [reportMonth, setReportMonth] = useState(thisMonth())
  const { data: report, isLoading: reportLoading } = useQuery<SiteAccessReport>({
    queryKey: ["site-access-report", siteId, reportMonth],
    queryFn: async () => (await apiClient.get(`/api/v1/security/reports/site-access?siteId=${siteId}&month=${reportMonth}`)).data?.data,
    enabled: !!siteId && !!reportMonth && subTab === "report",
  })
  const downloadReportPdf = async () => {
    const res = await apiClient.get(`/api/v1/security/reports/site-access/pdf?siteId=${siteId}&month=${reportMonth}`, { responseType: "blob" })
    const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
    const a = document.createElement("a"); a.href = url; a.download = `site-access-${reportMonth}.pdf`; a.click()
  }

  // ── Render ───────────────────────────────────────────────────────────────────

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 18, flexWrap: "wrap", gap: 12 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: "var(--hf-text)" }}>Gate Access & Registry</h2>
          <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 2 }}>Access points, visitor/contractor/delivery log, and the site access report</div>
        </div>
        <div style={{ minWidth: 220 }}>
          <select value={siteId} onChange={e => setSiteId(e.target.value)} style={inp}>
            <option value="">Select a site…</option>
            {sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>
      </div>

      {!siteId ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "var(--hf-text-faint)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          <DoorOpen size={32} style={{ marginBottom: 10, opacity: 0.3 }} />
          <div>Select a site to view its gate access configuration and registry.</div>
        </div>
      ) : (
        <>
          {/* Sub-nav */}
          <div style={{ display: "flex", gap: 6, marginBottom: 16, borderBottom: "1px solid var(--hf-border)" }}>
            {SUB_TABS.map(t => {
              const Icon = t.icon; const active = subTab === t.id
              return (
                <button key={t.id} onClick={() => setSubTab(t.id)}
                  style={{ display: "flex", alignItems: "center", gap: 6, padding: "10px 14px", background: "none", border: "none", borderBottom: active ? "2px solid var(--hf-primary)" : "2px solid transparent", color: active ? "var(--hf-primary-text)" : "var(--hf-text-muted)", fontWeight: active ? 700 : 500, fontSize: 13, cursor: "pointer" }}>
                  <Icon size={14} /> {t.label}
                </button>
              )
            })}
          </div>

          {error && <div style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 14 }}>{error}</div>}

          {/* ── Access Points ── */}
          {subTab === "access-points" && (
            <div>
              <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
                <button onClick={openApNew} style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                  <Plus size={14} /> Add Access Point
                </button>
              </div>
              {apLoading ? (
                <div style={{ textAlign: "center", padding: 30, color: "var(--hf-text-faint)" }}>Loading…</div>
              ) : accessPoints.length === 0 ? (
                <div style={{ textAlign: "center", padding: "40px 20px", color: "var(--hf-text-faint)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
                  No access points registered for this site yet.
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  {accessPoints.map(ap => (
                    <div key={ap.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px", opacity: ap.active ? 1 : 0.6 }}>
                      <div>
                        <div style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{ap.name}</div>
                        {ap.description && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{ap.description}</div>}
                      </div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <span style={{ fontSize: 11, fontWeight: 600, padding: "3px 10px", borderRadius: 20, background: ap.active ? "var(--hf-success-soft-strong)" : "var(--hf-surface-muted)", color: ap.active ? "var(--hf-success-text-strong)" : "var(--hf-text-faint)" }}>
                          {ap.active ? "Active" : "Inactive"}
                        </span>
                        <button onClick={() => openApEdit(ap)} title="Edit" style={{ padding: "6px 8px", background: "var(--hf-sky-soft)", border: "1px solid var(--hf-sky-border)", borderRadius: 7, cursor: "pointer", color: "var(--hf-sky-text-strong)" }}><Edit2 size={13} /></button>
                        {ap.active ? (
                          <button onClick={() => deactivateAp.mutate(ap.id)} title="Deactivate" style={{ padding: "6px 8px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, cursor: "pointer", color: "var(--hf-danger-text)" }}><Ban size={13} /></button>
                        ) : (
                          <button onClick={() => reactivateAp.mutate(ap.id)} title="Reactivate" style={{ padding: "6px 8px", background: "var(--hf-success-soft)", border: "1px solid var(--hf-success-border)", borderRadius: 7, cursor: "pointer", color: "var(--hf-success-text-strong)" }}><RotateCcw size={13} /></button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* ── On-Site Now ── */}
          {subTab === "on-site" && (
            <div>
              {onSiteLoading ? (
                <div style={{ textAlign: "center", padding: 30, color: "var(--hf-text-faint)" }}>Loading…</div>
              ) : onSite.length === 0 ? (
                <div style={{ textAlign: "center", padding: "40px 20px", color: "var(--hf-text-faint)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
                  Nobody currently on site.
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  {onSite.map(e => <GateEntryRow key={e.id} entry={e} onEvidence={() => setEvidenceFor(e)} />)}
                </div>
              )}
            </div>
          )}

          {/* ── Gate Log ── */}
          {subTab === "gate-log" && (
            <div>
              <div style={{ display: "flex", gap: 10, marginBottom: 14, alignItems: "flex-end" }}>
                <div>
                  <label style={lbl}>From</label>
                  <input type="date" value={logFrom} onChange={e => setLogFrom(e.target.value)} style={inp} />
                </div>
                <div>
                  <label style={lbl}>To</label>
                  <input type="date" value={logTo} onChange={e => setLogTo(e.target.value)} style={inp} />
                </div>
              </div>
              {logLoading ? (
                <div style={{ textAlign: "center", padding: 30, color: "var(--hf-text-faint)" }}>Loading…</div>
              ) : !gateLog?.content?.length ? (
                <div style={{ textAlign: "center", padding: "40px 20px", color: "var(--hf-text-faint)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
                  No gate entries in this date range.
                </div>
              ) : (
                <>
                  <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                    {gateLog.content.map(e => <GateEntryRow key={e.id} entry={e} onEvidence={() => setEvidenceFor(e)} />)}
                  </div>
                  <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 10 }}>{gateLog.totalElements} total entries in range (showing first 50)</div>
                </>
              )}
            </div>
          )}

          {/* ── Site Access Report ── */}
          {subTab === "report" && (
            <div>
              <div style={{ display: "flex", gap: 10, marginBottom: 16, alignItems: "flex-end" }}>
                <div>
                  <label style={lbl}>Month</label>
                  <input type="month" value={reportMonth} onChange={e => setReportMonth(e.target.value)} style={inp} />
                </div>
                {report && (
                  <button onClick={downloadReportPdf} style={{ display: "flex", alignItems: "center", gap: 6, padding: "9px 16px", background: "var(--hf-sky-soft)", color: "var(--hf-sky-text-strong)", border: "1px solid var(--hf-sky-border)", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                    <Download size={14} /> Download PDF
                  </button>
                )}
              </div>
              {reportLoading ? (
                <div style={{ textAlign: "center", padding: 30, color: "var(--hf-text-faint)" }}>Loading…</div>
              ) : report ? (
                <>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 10, marginBottom: 20 }}>
                    <StatCard label="Total Entries" value={report.totalEntries} color="var(--hf-primary-text)" />
                    <StatCard label="Currently On Site" value={report.currentlyOnSite} color="var(--hf-success-text-strong)" />
                    <StatCard label="Departed" value={report.departed} color="var(--hf-text-muted)" />
                    <StatCard label="Overstayed" value={report.overstayed} color="var(--hf-danger-text)" />
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                    {report.entries.map((e, i) => {
                      const cfg = ENTRY_TYPE_CFG[e.entryType] ?? ENTRY_TYPE_CFG.OTHER
                      const sc = STATUS_CFG[e.status] ?? STATUS_CFG.DEPARTED
                      return (
                        <div key={i} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "10px 14px", fontSize: 13 }}>
                          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                            <span style={{ fontSize: 11, fontWeight: 600, padding: "2px 8px", borderRadius: 20, background: cfg.bg, color: cfg.color }}>{cfg.label}</span>
                            <span style={{ fontWeight: 600, color: "var(--hf-text)" }}>{e.personName}</span>
                            {e.company && <span style={{ color: "var(--hf-text-faint)" }}>· {e.company}</span>}
                            {e.vehicleRegistration && <span style={{ color: "var(--hf-text-faint)" }}>· {e.vehicleRegistration}</span>}
                          </div>
                          <div style={{ display: "flex", alignItems: "center", gap: 10, color: "var(--hf-text-faint)", fontSize: 12 }}>
                            <span>{e.accessPointName}</span>
                            <span>{fmtDateTime(e.loggedInAt)} → {fmtDateTime(e.loggedOutAt)}</span>
                            <span style={{ color: sc.color, fontWeight: 600 }}>{sc.label}</span>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </>
              ) : (
                <div style={{ textAlign: "center", padding: "40px 20px", color: "var(--hf-text-faint)" }}>No data for this period.</div>
              )}
            </div>
          )}
        </>
      )}

      {/* Access Point create/edit modal */}
      {showApForm && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 14, padding: 26, width: 440 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>{showApForm === "new" ? "Add Access Point" : "Edit Access Point"}</h3>
              <button onClick={() => setShowApForm(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}><X size={18} /></button>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Name *</label>
              <input value={apForm.name} onChange={e => setApForm(f => ({ ...f, name: e.target.value }))} placeholder="Main Gate" style={inp} />
            </div>
            <div style={{ marginBottom: 18 }}>
              <label style={lbl}>Description</label>
              <input value={apForm.description} onChange={e => setApForm(f => ({ ...f, description: e.target.value }))} placeholder="Optional" style={inp} />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowApForm(null)} style={cancelBtn}>Cancel</button>
              <button
                onClick={() => showApForm === "new" ? createAp.mutate() : updateAp.mutate({ id: showApForm.id })}
                disabled={!apForm.name.trim() || createAp.isPending || updateAp.isPending}
                style={submitBtn}>
                {createAp.isPending || updateAp.isPending ? "Saving…" : showApForm === "new" ? "Create" : "Save Changes"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Evidence viewer modal */}
      {evidenceFor && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 14, padding: 26, width: 480, maxHeight: "80vh", overflowY: "auto" as const }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Evidence — {evidenceFor.personName}</h3>
              <button onClick={() => setEvidenceFor(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}><X size={18} /></button>
            </div>
            {evidenceLoading ? (
              <div style={{ textAlign: "center", padding: 20, color: "var(--hf-text-faint)" }}>Loading…</div>
            ) : evidence.length === 0 ? (
              <div style={{ textAlign: "center", padding: 20, color: "var(--hf-text-faint)", fontSize: 13 }}>No evidence attached to this entry.</div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {evidence.map(ev => (
                  <div key={ev.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 14px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <Paperclip size={15} style={{ color: "var(--hf-text-faint)" }} />
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)" }}>{ev.fileName}</div>
                        <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{ev.evidenceType} · {ev.uploadedByName}</div>
                      </div>
                    </div>
                    <button onClick={() => downloadEvidence(ev)} style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-sky-soft)", border: "1px solid var(--hf-sky-border)", borderRadius: 7, cursor: "pointer", color: "var(--hf-sky-text-strong)", fontSize: 12, fontWeight: 600 }}>
                      <Download size={13} /> Download
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function GateEntryRow({ entry, onEvidence }: { entry: GateEntry; onEvidence: () => void }) {
  const cfg = ENTRY_TYPE_CFG[entry.entryType] ?? ENTRY_TYPE_CFG.OTHER
  const sc = STATUS_CFG[entry.status] ?? STATUS_CFG.DEPARTED
  const Icon = cfg.icon
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", background: "var(--hf-surface)", border: `1px solid ${entry.status === "OVERSTAYED" ? "var(--hf-danger-border)" : "var(--hf-border)"}`, borderRadius: 10, padding: "12px 16px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <div style={{ width: 36, height: 36, borderRadius: 9, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
          <Icon size={16} style={{ color: cfg.color }} />
        </div>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{entry.personName}</span>
            <span style={{ fontSize: 11, fontWeight: 600, padding: "2px 8px", borderRadius: 20, background: cfg.bg, color: cfg.color }}>{cfg.label}</span>
          </div>
          <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>
            {entry.company && <>{entry.company} · </>}
            {entry.vehicleRegistration && <>{entry.vehicleRegistration} · </>}
            {entry.accessPointName} · {fmtDateTime(entry.loggedInAt)}
            {entry.loggedOutAt && <> → {fmtDateTime(entry.loggedOutAt)}</>}
          </div>
        </div>
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <span style={{ fontSize: 11, fontWeight: 600, padding: "3px 10px", borderRadius: 20, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}` }}>{sc.label}</span>
        <button onClick={onEvidence} title="View evidence" style={{ padding: "6px 8px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 7, cursor: "pointer", color: "var(--hf-text-muted)" }}>
          <Paperclip size={13} />
        </button>
      </div>
    </div>
  )
}

function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "14px 16px" }}>
      <div style={{ fontSize: 22, fontWeight: 800, color }}>{value}</div>
      <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 2 }}>{label}</div>
    </div>
  )
}
