// src/pages/earthmoving/IncidentsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, AlertTriangle, AlertCircle, CheckCircle } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }

const SEV_CFG: Record<string, { color: string; bg: string; border: string }> = {
  CRITICAL:  { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA" },
  HIGH:      { color: "#EA580C", bg: "#FFF7ED", border: "#FED7AA" },
  MEDIUM:    { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A" },
  LOW:       { color: "#64748B", bg: "#F8FAFC", border: "#E2E8F0" },
}

const INC_TYPES = ["BREAKDOWN", "ACCIDENT", "THEFT", "FIRE", "ROLLOVER", "NEAR_MISS", "FUEL_SPILL", "OTHER"]
const EMPTY_FORM = { assetId: "", type: "BREAKDOWN", severity: "MEDIUM", title: "", description: "", operatorName: "", siteName: "", latitude: "", longitude: "" }

export default function IncidentsTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [sevFilter, setSevFilter]   = useState("ALL")
  const [form, setForm]             = useState(EMPTY_FORM)
  const [apiError, setApiError]     = useState("")

  const { data: assets = [] } = useQuery<any[]>({
    queryKey: ["em-assets"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/earthmoving/assets?size=200")),
  })

  // Local state for incidents (until backend incident endpoint is added)
  const [incidents, setIncidents] = useState<any[]>([])

  const handleCreate = () => {
    if (!form.assetId || !form.title) { setApiError("Asset and title are required"); return }
    const asset = (assets as any[]).find(a => a.id === form.assetId)
    const incident = {
      id: crypto.randomUUID(),
      assetId: form.assetId,
      assetName: asset?.name ?? "Unknown",
      fleetNumber: asset?.fleetNumber,
      assetType: asset?.assetType,
      type: form.type,
      severity: form.severity,
      title: form.title,
      description: form.description,
      operatorName: form.operatorName,
      siteName: form.siteName,
      reportedAt: new Date().toISOString(),
      status: "OPEN",
    }
    setIncidents(prev => [incident, ...prev])
    // Also update asset status if BREAKDOWN
    if (form.type === "BREAKDOWN" || form.type === "ACCIDENT") {
      apiClient.put(`/api/v1/earthmoving/assets/${form.assetId}/status`, { status: "BREAKDOWN", note: form.title })
        .then(() => qc.invalidateQueries({ queryKey: ["em-assets"] }))
        .catch(() => {})
    }
    setShowCreate(false)
    setForm(EMPTY_FORM)
    setApiError("")
  }

  const resolve = (id: string) => {
    setIncidents(prev => prev.map(i => i.id === id ? { ...i, status: "RESOLVED", resolvedAt: new Date().toISOString() } : i))
  }

  const filtered = sevFilter === "ALL" ? incidents : incidents.filter(i => i.severity === sevFilter)

  const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, outline: "none" }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
  const EMOJI: Record<string, string> = { DOZER:"🚜", EXCAVATOR:"⛏️", GRADER:"🛣️", LOADER:"🏗️", DUMPER:"🚛", CRANE:"🏗️", ROLLER:"🛞", OTHER:"🚧" }

  return (
    <div>
      {/* Stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {[
          { label: "Total incidents",  value: incidents.length,                          color: "#1B3A6B" },
          { label: "Open",             value: incidents.filter(i => i.status === "OPEN").length, color: "#DC2626" },
          { label: "Critical",         value: incidents.filter(i => i.severity === "CRITICAL").length, color: "#DC2626" },
          { label: "Breakdowns active",value: (assets as any[]).filter(a => a.status === "BREAKDOWN").length, color: "#D97706" },
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
                background: sevFilter === s ? (SEV_CFG[s]?.color ?? "#1B3A6B") : "#F1F5F9",
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

      {/* Active breakdowns banner */}
      {(assets as any[]).filter(a => a.status === "BREAKDOWN").length > 0 && (
        <div style={{ marginBottom: 16, padding: "12px 16px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <AlertTriangle size={18} color="#DC2626" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13, color: "#DC2626" }}>Active Breakdowns</div>
            <div style={{ fontSize: 12, color: "#B91C1C" }}>
              {(assets as any[]).filter(a => a.status === "BREAKDOWN").map((a: any) => a.fleetNumber ?? a.name).join(", ")} — currently unserviceable
            </div>
          </div>
        </div>
      )}

      {filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", border: "1px dashed #E2E8F0", borderRadius: 12, color: "#94A3B8" }}>
          <AlertTriangle size={36} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#166534" }}>No incidents — all clear</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Report breakdowns, accidents or near-misses here.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {filtered.map(inc => {
            const sev = SEV_CFG[inc.severity] ?? SEV_CFG.LOW
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
                      {EMOJI[inc.assetType] ?? "🚧"} {inc.fleetNumber ? `${inc.fleetNumber} — ` : ""}{inc.assetName}
                      {inc.siteName && ` · 📍 ${inc.siteName}`}
                      {inc.operatorName && ` · 👷 ${inc.operatorName}`}
                    </div>
                    {inc.description && <div style={{ fontSize: 13, color: "#475569" }}>{inc.description}</div>}
                    <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 4 }}>
                      Reported {new Date(inc.reportedAt).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" })}
                    </div>
                  </div>
                  {inc.status === "OPEN" && (
                    <button onClick={() => resolve(inc.id)}
                      style={{ padding: "7px 14px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer", flexShrink: 0 }}>
                      Resolve
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Report incident modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ width: 36, height: 36, borderRadius: 8, background: "#FEF2F2", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <AlertTriangle size={18} color="#DC2626" />
                </div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Report Incident</h3>
              </div>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Severity *</label>
                <div style={{ display: "flex", gap: 8 }}>
                  {["LOW", "MEDIUM", "HIGH", "CRITICAL"].map(s => {
                    const cfg = SEV_CFG[s]
                    return (
                      <button key={s} onClick={() => setForm(f => ({ ...f, severity: s }))}
                        style={{ flex: 1, padding: "8px 4px", borderRadius: 8, border: `2px solid ${form.severity === s ? cfg.color : "#E2E8F0"}`, background: form.severity === s ? cfg.bg : "#fff", color: form.severity === s ? cfg.color : "#64748B", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>
                        {s}
                      </button>
                    )
                  })}
                </div>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Equipment *</label>
                  <select value={form.assetId} onChange={e => setForm(f => ({ ...f, assetId: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                    <option value="">Select equipment...</option>
                    {(assets as any[]).map((a: any) => <option key={a.id} value={a.id}>{a.fleetNumber ? `${a.fleetNumber} — ` : ""}{a.name}</option>)}
                  </select>
                </div>
                <div>
                  <label style={lbl}>Incident Type</label>
                  <select value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                    {INC_TYPES.map(t => <option key={t} value={t}>{t.replace("_", " ")}</option>)}
                  </select>
                </div>
              </div>

              <div>
                <label style={lbl}>Title *</label>
                <input value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))} placeholder="e.g. Hydraulic failure on left track" style={inp} autoFocus />
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Operator Name</label>
                  <input value={form.operatorName} onChange={e => setForm(f => ({ ...f, operatorName: e.target.value }))} placeholder="James Dlamini" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Site Name</label>
                  <input value={form.siteName} onChange={e => setForm(f => ({ ...f, siteName: e.target.value }))} placeholder="Sandton Site" style={inp} />
                </div>
              </div>

              <div>
                <label style={lbl}>Description</label>
                <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={3}
                  placeholder="Describe what happened, what damage was done, and any immediate actions taken..."
                  style={{ ...inp, resize: "vertical" as const }} />
              </div>

              <div style={{ padding: "10px 14px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 8, fontSize: 12, color: "#92400E", display: "flex", gap: 8 }}>
                <AlertTriangle size={14} style={{ flexShrink: 0, marginTop: 1 }} />
                Reporting BREAKDOWN or ACCIDENT will automatically set the machine status to Breakdown.
              </div>
            </div>

            {apiError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={14} />{apiError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button onClick={handleCreate}
                style={{ padding: "9px 22px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                Report Incident
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
