// src/pages/security/IncidentsTab.tsx
// Changes from original:
//   - Add incident TYPE selector to create modal (now persisted to DB via fix #16)
//   - Fix data unwrapping: IncidentService now returns proper Page<IncidentResponse>
//   - Add acknowledgedAt/resolvedAt timestamps in incident card footer

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, AlertTriangle, AlertCircle } from "lucide-react"

const SEV_CONFIG: Record<string, { color: string; bg: string; border: string }> = {
  CRITICAL: { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA" },
  HIGH:     { color: "#EA580C", bg: "#FFF7ED", border: "#FED7AA" },
  MEDIUM:   { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A" },
  LOW:      { color: "#64748B", bg: "#F8FAFC", border: "#E2E8F0" },
}

const STATUS_CONFIG: Record<string, { color: string; bg: string; label: string }> = {
  OPEN:         { color: "#DC2626", bg: "#FEF2F2", label: "Open" },
  ACKNOWLEDGED: { color: "#D97706", bg: "#FFFBEB", label: "Acknowledged" },
  RESOLVED:     { color: "#166534", bg: "#DCFCE7", label: "Resolved" },
}

// Incident types — now properly stored in the DB (V102 fix #16)
const INCIDENT_TYPES = [
  { value: "GENERAL",     label: "General" },
  { value: "THEFT",       label: "Theft" },
  { value: "TRESPASS",    label: "Trespass" },
  { value: "MEDICAL",     label: "Medical" },
  { value: "FIRE",        label: "Fire" },
  { value: "VANDALISM",   label: "Vandalism" },
  { value: "ASSAULT",     label: "Assault" },
  { value: "SUSPICIOUS",  label: "Suspicious activity" },
  { value: "OTHER",       label: "Other" },
]

export default function IncidentsTab() {
  const qc = useQueryClient()
  const [showCreate,   setShowCreate]   = useState(false)
  const [statusFilter, setStatusFilter] = useState("ALL")
  const [sevFilter,    setSevFilter]    = useState("ALL")
  const [form, setForm] = useState({
    siteId: "", guardId: "", title: "", description: "",
    severity: "MEDIUM", type: "GENERAL",
    latitude: "", longitude: "",
  })
  const [formError, setFormError] = useState("")

  const { data: incidents = [], isLoading } = useQuery<any[]>({
    queryKey: ["incidents", statusFilter, sevFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "100" })
      if (statusFilter !== "ALL") params.set("status", statusFilter)
      if (sevFilter    !== "ALL") params.set("severity", sevFilter)
      const r = await apiClient.get(`/api/v1/security/incidents?${params}`)
      // IncidentService now returns a proper Page — unwrap .content
      const payload = r.data?.data ?? r.data
      return payload?.content ?? (Array.isArray(payload) ? payload : [])
    },
  })

  const { data: sites = [] }  = useQuery<any[]>({ queryKey: ["sites-list"],  queryFn: async () => { const r = await apiClient.get("/api/v1/security/sites?size=100");  const p = r.data?.data ?? r.data; return p?.content ?? [] } })
  const { data: guards = [] } = useQuery<any[]>({ queryKey: ["guards-list"], queryFn: async () => { const r = await apiClient.get("/api/v1/security/guards?size=100"); const p = r.data?.data ?? r.data; return p?.content ?? [] } })

  const createIncident = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/security/incidents", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["incidents"] }); setShowCreate(false); setFormError(""); setForm(f => ({ ...f, title: "", description: "", siteId: "", guardId: "", latitude: "", longitude: "" })) },
    onError:   (e: any) => setFormError(e.response?.data?.message ?? "Failed to report incident"),
  })

  const acknowledge = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/incidents/${id}/acknowledge`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["incidents"] }),
    onError:   (e: any) => console.error("Acknowledge failed", e.response?.data?.message),
  })

  const resolve = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/incidents/${id}/resolve`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["incidents"] }),
    onError:   (e: any) => console.error("Resolve failed", e.response?.data?.message),
  })

  const fmtDt = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })

  const total    = incidents.length
  const open     = incidents.filter((i: any) => i.status === "OPEN").length
  const ack      = incidents.filter((i: any) => i.status === "ACKNOWLEDGED").length
  const critical = incidents.filter((i: any) => i.severity === "CRITICAL").length

  return (
    <div>
      {/* Stats */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12, marginBottom: 24 }}>
        {[
          { label: "Total",        value: total,    color: "#1B3A6B" },
          { label: "Open",         value: open,     color: open > 0 ? "#DC2626" : "#166534" },
          { label: "Acknowledged", value: ack,      color: "#D97706" },
          { label: "Critical",     value: critical, color: critical > 0 ? "#DC2626" : "#166534" },
        ].map(s => (
          <div key={s.label} style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <div style={{ display: "flex", gap: 4 }}>
            {["ALL", "OPEN", "ACKNOWLEDGED", "RESOLVED"].map(s => (
              <button key={s} onClick={() => setStatusFilter(s)}
                style={{ padding: "6px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none",
                  background: statusFilter === s ? "#1B3A6B" : "#F1F5F9",
                  color: statusFilter === s ? "#fff" : "#64748B", fontWeight: statusFilter === s ? 600 : 400 }}>
                {s === "ALL" ? "All" : STATUS_CONFIG[s]?.label ?? s}
              </button>
            ))}
          </div>
          <div style={{ display: "flex", gap: 4 }}>
            {["ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW"].map(s => (
              <button key={s} onClick={() => setSevFilter(s)}
                style={{ padding: "6px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none",
                  background: sevFilter === s ? (SEV_CONFIG[s]?.color ?? "#1B3A6B") : "#F1F5F9",
                  color: sevFilter === s ? "#fff" : "#64748B", fontWeight: sevFilter === s ? 600 : 400 }}>
                {s === "ALL" ? "All severity" : s}
              </button>
            ))}
          </div>
        </div>
        <button onClick={() => setShowCreate(true)}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#DC2626", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Report Incident
        </button>
      </div>

      {/* Incident list */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading incidents...</div>
      ) : incidents.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <AlertTriangle size={36} color="#CBD5E1" style={{ marginBottom: 12 }} />
          <div style={{ fontWeight: 600, color: "#166534" }}>No incidents — all clear</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {incidents.map((inc: any) => {
            const sev = SEV_CONFIG[inc.severity]  ?? SEV_CONFIG.LOW
            const sts = STATUS_CONFIG[inc.status] ?? STATUS_CONFIG.OPEN
            return (
              <div key={inc.id} style={{ border: `1px solid ${sev.border}`, borderLeft: `4px solid ${sev.color}`, borderRadius: 10, padding: "16px 20px", background: "#fff" }}>
                <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 6, flexWrap: "wrap" }}>
                      <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{inc.title}</span>
                      <span style={{ fontSize: 10, fontWeight: 700, background: sev.bg, color: sev.color, padding: "2px 8px", borderRadius: 20, border: `1px solid ${sev.border}` }}>{inc.severity}</span>
                      <span style={{ fontSize: 11, fontWeight: 600, background: sts.bg, color: sts.color, padding: "2px 8px", borderRadius: 20 }}>{sts.label}</span>
                      {/* Incident type badge — now properly stored */}
                      {inc.type && inc.type !== "GENERAL" && (
                        <span style={{ fontSize: 10, fontWeight: 600, background: "#F0FDF4", color: "#166534", padding: "2px 8px", borderRadius: 20, border: "1px solid #BBF7D0" }}>{inc.type}</span>
                      )}
                    </div>
                    {inc.description && <div style={{ fontSize: 13, color: "#64748B", marginBottom: 6 }}>{inc.description}</div>}
                    <div style={{ fontSize: 11, color: "#94A3B8" }}>
                      Reported {fmtDt(inc.reportedAt)}
                      {inc.siteName  && ` · ${inc.siteName}`}
                      {inc.guardName && ` · By ${inc.guardName}`}
                    </div>
                    {/* Acknowledgement / resolution timestamps */}
                    {inc.acknowledgedAt && (
                      <div style={{ fontSize: 11, color: "#D97706", marginTop: 2 }}>Acknowledged {fmtDt(inc.acknowledgedAt)}</div>
                    )}
                    {inc.resolvedAt && (
                      <div style={{ fontSize: 11, color: "#166534", marginTop: 2 }}>Resolved {fmtDt(inc.resolvedAt)}</div>
                    )}
                  </div>
                  <div style={{ display: "flex", gap: 8, flexShrink: 0, marginLeft: 12 }}>
                    {inc.status === "OPEN" && (
                      <button onClick={() => acknowledge.mutate(inc.id)} disabled={acknowledge.isPending}
                        style={{ padding: "7px 14px", background: "#FFFBEB", color: "#D97706", border: "1px solid #FDE68A", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        Acknowledge
                      </button>
                    )}
                    {inc.status !== "RESOLVED" && (
                      <button onClick={() => resolve.mutate(inc.id)} disabled={resolve.isPending}
                        style={{ padding: "7px 14px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        Resolve
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Report incident modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 520, maxHeight: "88vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 22 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ width: 36, height: 36, borderRadius: 8, background: "#FEF2F2", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <AlertTriangle size={18} color="#DC2626" />
                </div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Report Incident</h3>
              </div>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>

              {/* Severity */}
              <div>
                <label style={lbl}>Severity *</label>
                <div style={{ display: "flex", gap: 8 }}>
                  {["LOW", "MEDIUM", "HIGH", "CRITICAL"].map(s => {
                    const cfg = SEV_CONFIG[s]
                    return (
                      <button key={s} onClick={() => setForm(f => ({ ...f, severity: s }))}
                        style={{ flex: 1, padding: "8px 4px", borderRadius: 8, border: `2px solid ${form.severity === s ? cfg.color : "#E2E8F0"}`, background: form.severity === s ? cfg.bg : "#fff", color: form.severity === s ? cfg.color : "#64748B", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>
                        {s}
                      </button>
                    )
                  })}
                </div>
              </div>

              {/* Incident type — now stored in DB */}
              <div>
                <label style={lbl}>Incident Type</label>
                <select value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value }))} style={inp}>
                  {INCIDENT_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
              </div>

              <div>
                <label style={lbl}>Title *</label>
                <input value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                  placeholder="e.g. Unauthorized access at Gate A" style={inp} autoFocus />
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <div>
                  <label style={lbl}>Site *</label>
                  <select value={form.siteId} onChange={e => setForm(f => ({ ...f, siteId: e.target.value }))} style={inp}>
                    <option value="">Select site...</option>
                    {sites.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                </div>
                <div>
                  <label style={lbl}>Guard <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                  <select value={form.guardId} onChange={e => setForm(f => ({ ...f, guardId: e.target.value }))} style={inp}>
                    <option value="">Select guard...</option>
                    {guards.map((g: any) => <option key={g.id} value={g.id}>{g.fullName}</option>)}
                  </select>
                </div>
              </div>

              <div>
                <label style={lbl}>Description</label>
                <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                  rows={3} placeholder="Describe what happened..." style={{ ...inp, resize: "vertical" as const }} />
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <div>
                  <label style={lbl}>Latitude <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                  <input type="number" value={form.latitude} onChange={e => setForm(f => ({ ...f, latitude: e.target.value }))} placeholder="-26.2041" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Longitude</label>
                  <input type="number" value={form.longitude} onChange={e => setForm(f => ({ ...f, longitude: e.target.value }))} placeholder="28.0473" style={inp} />
                </div>
              </div>
            </div>

            {formError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={14} />{formError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button
                onClick={() => createIncident.mutate({
                  siteId:      form.siteId   || null,
                  guardId:     form.guardId  || null,
                  title:       form.title,
                  description: form.description || null,
                  severity:    form.severity,
                  type:        form.type,
                  latitude:    form.latitude  ? parseFloat(form.latitude)  : null,
                  longitude:   form.longitude ? parseFloat(form.longitude) : null,
                })}
                disabled={!form.title || !form.siteId || createIncident.isPending}
                style={{ padding: "9px 22px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createIncident.isPending ? "Reporting..." : "Report Incident"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff", outline: "none" }
