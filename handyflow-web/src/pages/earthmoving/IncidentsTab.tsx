// src/pages/earthmoving/IncidentsTab.tsx
import { useState } from "react"
import { Plus, AlertTriangle, CheckCircle2 } from "lucide-react"
import { SEVERITY_CFG, INCIDENT_TYPES, EMOJI } from "./shared/constants"
import { fmtDateTime } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { useAssets, useIncidents, useReportIncident, useResolveIncident } from "./shared/hooks"

const EMPTY_FORM = {
  assetId: "", type: "BREAKDOWN", severity: "MEDIUM", title: "",
  description: "", operatorName: "", siteName: "",
}

export default function IncidentsTab() {
  const [showCreate, setShowCreate] = useState(false)
  const [sevFilter, setSevFilter]   = useState("ALL")
  const [form, setForm]             = useState(EMPTY_FORM)
  const [apiError, setApiError]     = useState("")

  const { data: assets = [] } = useAssets()
  const { data: incidents = [], isLoading } = useIncidents()

  const reportIncident = useReportIncident(() => {
    setShowCreate(false); setForm(EMPTY_FORM); setApiError("")
  })
  const resolveIncident = useResolveIncident()

  // Backend joins nothing extra onto an incident beyond assetId — same
  // client-side join pattern MaintenanceTab and OperatorLogsTab already use
  // against the already-fetched assets list, rather than the backend
  // duplicating asset name/fleetNumber onto every incident row.
  const assetFor = (assetId: string) => assets.find(a => a.id === assetId)

  const filtered = sevFilter === "ALL" ? incidents : incidents.filter(i => i.severity === sevFilter)

  const handleCreate = () => {
    if (!form.assetId || !form.title) { setApiError("Asset and title are required"); return }
    reportIncident.mutate({
      assetId: form.assetId,
      type: form.type,
      severity: form.severity,
      title: form.title,
      description: form.description || null,
      operatorName: form.operatorName || null,
      siteName: form.siteName || null,
    }, {
      // FIX: the previous version fired this same status-changing side effect
      // client-side and swallowed any failure with `.catch(() => {})` — if the
      // backend's state machine rejected the transition (e.g. asset already
      // RETIRED), the user had no idea their incident didn't actually flag the
      // asset. The backend now owns this side effect entirely (see
      // EarthmovingIncidentService.maybeAutoBreakdown) and always succeeds in
      // recording the incident even when the status change isn't legal — so
      // this onError only fires for genuine failures, and the user sees them.
      onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to report incident"),
    })
  }

  const handleResolve = (id: string) => {
    resolveIncident.mutate({ id, resolutionNotes: null }, {
      onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to resolve incident"),
    })
  }

  return (
    <div>
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {[
          { label: "Total incidents", value: incidents.length, color: "#1B3A6B" },
          { label: "Open", value: incidents.filter(i => i.status === "OPEN").length, color: "#DC2626" },
          { label: "Critical", value: incidents.filter(i => i.severity === "CRITICAL").length, color: "#DC2626" },
          { label: "Breakdowns active", value: assets.filter(a => a.status === "BREAKDOWN").length, color: "#D97706" },
        ].map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6 }}>
          {["ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW"].map(s => (
            <button key={s} onClick={() => setSevFilter(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: sevFilter === s ? 600 : 400,
                background: sevFilter === s ? (SEVERITY_CFG[s]?.color ?? "#1B3A6B") : "#F1F5F9",
                color: sevFilter === s ? "#fff" : "#64748B" }}>
              {s === "ALL" ? "All" : s}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowCreate(true); setApiError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#DC2626", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Report Incident
        </button>
      </div>

      {assets.filter(a => a.status === "BREAKDOWN").length > 0 && (
        <div style={{ marginBottom: 16, padding: "12px 16px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <AlertTriangle size={18} color="#DC2626" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13, color: "#DC2626" }}>Active Breakdowns</div>
            <div style={{ fontSize: 12, color: "#B91C1C" }}>
              {assets.filter(a => a.status === "BREAKDOWN").map(a => a.fleetNumber ?? a.name).join(", ")} — currently unserviceable
            </div>
          </div>
        </div>
      )}

      {apiError && <ErrBanner msg={apiError} />}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading incidents...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", border: "1px dashed #E2E8F0", borderRadius: 12, color: "#94A3B8" }}>
          <AlertTriangle size={36} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#166534" }}>No incidents — all clear</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Report breakdowns, accidents or near-misses here.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10, marginTop: 16 }}>
          {filtered.map(inc => {
            const sev = SEVERITY_CFG[inc.severity] ?? SEVERITY_CFG.LOW
            const asset = assetFor(inc.assetId)
            return (
              <div key={inc.id} style={{ border: `1px solid ${sev.border}`, borderLeft: `4px solid ${sev.color}`, borderRadius: 10, padding: "16px 20px", background: "#fff" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6, flexWrap: "wrap" }}>
                      <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{inc.title}</span>
                      <span style={{ fontSize: 10, fontWeight: 700, background: sev.bg, color: sev.color, padding: "1px 7px", borderRadius: 20, border: `1px solid ${sev.border}` }}>{inc.severity}</span>
                      <span style={{ fontSize: 10, fontWeight: 700, background: "#F8FAFC", color: "#64748B", padding: "1px 7px", borderRadius: 20 }}>{inc.type}</span>
                      {inc.status === "RESOLVED" && (
                        <span style={{ fontSize: 10, fontWeight: 700, background: "#DCFCE7", color: "#166534", padding: "1px 7px", borderRadius: 20 }}>RESOLVED</span>
                      )}
                    </div>
                    <div style={{ fontSize: 13, color: "#64748B", marginBottom: 4 }}>
                      {asset ? <>{EMOJI[asset.assetType] ?? "🚧"} {asset.fleetNumber ? `${asset.fleetNumber} — ` : ""}{asset.name}</> : "Unknown asset"}
                      {inc.siteName && ` · 📍 ${inc.siteName}`}
                      {inc.operatorName && ` · 👷 ${inc.operatorName}`}
                    </div>
                    {inc.description && <div style={{ fontSize: 13, color: "#475569" }}>{inc.description}</div>}
                    <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 4 }}>
                      Reported {fmtDateTime(inc.reportedAt)}
                      {inc.resolvedAt && ` · Resolved ${fmtDateTime(inc.resolvedAt)}`}
                    </div>
                  </div>
                  {inc.status === "OPEN" && (
                    <button onClick={() => handleResolve(inc.id)} disabled={resolveIncident.isPending}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 14px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer", flexShrink: 0 }}>
                      <CheckCircle2 size={13} /> Resolve
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {showCreate && (
        <Overlay onClose={() => setShowCreate(false)} width={560}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 22 }}>
            <div style={{ width: 36, height: 36, borderRadius: 8, background: "#FEF2F2", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <AlertTriangle size={18} color="#DC2626" />
            </div>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Report Incident</h3>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div>
              <label style={lbl}>Severity *</label>
              <div style={{ display: "flex", gap: 8 }}>
                {["LOW", "MEDIUM", "HIGH", "CRITICAL"].map(s => {
                  const cfg = SEVERITY_CFG[s]
                  return (
                    <button key={s} onClick={() => setForm(f => ({ ...f, severity: s }))}
                      style={{ flex: 1, padding: "8px 4px", borderRadius: 8, border: `2px solid ${form.severity === s ? cfg.color : "#E2E8F0"}`, background: form.severity === s ? cfg.bg : "#fff", color: form.severity === s ? cfg.color : "#64748B", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>
                      {s}
                    </button>
                  )
                })}
              </div>
              {form.severity === "CRITICAL" && (
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 5 }}>
                  Critical incidents notify fleet managers by SMS and email immediately, not just in-app.
                </div>
              )}
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Equipment *</label>
                <select value={form.assetId} onChange={e => setForm(f => ({ ...f, assetId: e.target.value }))} style={{ ...inputStyle(), background: "#fff" }}>
                  <option value="">Select equipment...</option>
                  {assets.map(a => <option key={a.id} value={a.id}>{a.fleetNumber ? `${a.fleetNumber} — ` : ""}{a.name}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Incident Type</label>
                <select value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value }))} style={{ ...inputStyle(), background: "#fff" }}>
                  {INCIDENT_TYPES.map(t => <option key={t} value={t}>{t.replace("_", " ")}</option>)}
                </select>
              </div>
            </div>

            <div>
              <label style={lbl}>Title *</label>
              <input value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))} placeholder="e.g. Hydraulic failure on left track" style={inputStyle()} autoFocus />
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Operator Name</label>
                <input value={form.operatorName} onChange={e => setForm(f => ({ ...f, operatorName: e.target.value }))} placeholder="James Dlamini" style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Site Name</label>
                <input value={form.siteName} onChange={e => setForm(f => ({ ...f, siteName: e.target.value }))} placeholder="Sandton Site" style={inputStyle()} />
              </div>
            </div>

            <div>
              <label style={lbl}>Description</label>
              <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={3}
                placeholder="Describe what happened, what damage was done, and any immediate actions taken..."
                style={{ ...inputStyle(), resize: "vertical" as const }} />
            </div>

            {(form.type === "BREAKDOWN" || form.type === "ACCIDENT") && (
              <div style={{ padding: "10px 14px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 8, fontSize: 12, color: "#92400E", display: "flex", gap: 8 }}>
                <AlertTriangle size={14} style={{ flexShrink: 0, marginTop: 1 }} />
                This will automatically set the machine status to Breakdown, if that's a valid transition from its current status.
              </div>
            )}
          </div>

          {apiError && <ErrBanner msg={apiError} />}

          <ModalFoot
            onCancel={() => setShowCreate(false)}
            onSubmit={handleCreate}
            loading={reportIncident.isPending}
            label="Report Incident"
            disabled={!form.assetId || !form.title}
          />
        </Overlay>
      )}
    </div>
  )
}
