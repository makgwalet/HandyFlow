// src/pages/security/SecurityDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Shield, MapPin, Clock, AlertTriangle, CheckCircle,
  ArrowRight, Radio, TrendingUp, Users,
} from "lucide-react"

const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })
const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short" })

export default function SecurityDashboard({ onNavigate }: { onNavigate: (tab: any) => void }) {
  const { data: guards = [] } = useQuery({
    queryKey: ["guards"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/guards?size=100"); return (r.data?.data ?? r.data).content ?? [] },
  })
  const { data: shifts = [] } = useQuery({
    queryKey: ["shifts"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/shifts?size=100"); return (r.data?.data ?? r.data).content ?? [] },
  })
  const { data: sites = [] } = useQuery({
    queryKey: ["sites"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/sites?size=100"); return (r.data?.data ?? r.data).content ?? [] },
  })
  const { data: incidents = [] } = useQuery({
    queryKey: ["incidents"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/incidents?size=50"); return (r.data?.data ?? r.data).content ?? [] },
  })

  const activeShifts  = (shifts as any[]).filter(s => s.status === "ACTIVE")
  const openIncidents = (incidents as any[]).filter(i => i.status === "OPEN")
  const criticalInc   = (incidents as any[]).filter(i => i.severity === "CRITICAL" && i.status !== "RESOLVED")

  const kpis = [
    { label: "Guards on duty",    value: activeShifts.length, total: (guards as any[]).length, color: "#166534",  bg: "#F0FDF4", icon: Shield,       tab: "shifts" },
    { label: "Active sites",      value: (sites as any[]).length, color: "#1B3A6B",            bg: "#EFF6FF", icon: MapPin,       tab: "sites" },
    { label: "Open incidents",    value: openIncidents.length, color: openIncidents.length > 0 ? "#DC2626" : "#166534", bg: openIncidents.length > 0 ? "#FEF2F2" : "#F0FDF4", icon: AlertTriangle, tab: "incidents" },
    { label: "Critical alerts",   value: criticalInc.length, color: criticalInc.length > 0 ? "#DC2626" : "#166534", bg: criticalInc.length > 0 ? "#FEF2F2" : "#F0FDF4", icon: Radio, tab: "incidents" },
  ]

  return (
    <div>
      {/* KPI grid */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 28 }}>
        {kpis.map(k => (
          <div key={k.label} onClick={() => onNavigate(k.tab)}
            style={{ background: k.bg, border: `1px solid ${k.bg}`, borderRadius: 12, padding: "18px 20px", cursor: "pointer" }}
            onMouseEnter={e => (e.currentTarget.style.boxShadow = "0 4px 16px rgba(0,0,0,0.08)")}
            onMouseLeave={e => (e.currentTarget.style.boxShadow = "none")}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: k.color, textTransform: "uppercase" as const }}>{k.label}</div>
              <k.icon size={16} color={k.color} />
            </div>
            <div style={{ fontSize: 28, fontWeight: 800, color: k.color }}>{k.value}</div>
            {k.total !== undefined && <div style={{ fontSize: 11, color: k.color, opacity: 0.7, marginTop: 2 }}>{k.total} total guards</div>}
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 16 }}>

        {/* Active shifts */}
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 14 }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Active Shifts</span>
            <button onClick={() => onNavigate("shifts")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#0D9488", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              View all <ArrowRight size={13} />
            </button>
          </div>

          {activeShifts.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed #E2E8F0", borderRadius: 12, color: "#94A3B8" }}>
              <Shield size={32} color="#CBD5E1" style={{ marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "#475569" }}>No active shifts</div>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {activeShifts.slice(0, 6).map((shift: any) => (
                <div key={shift.id} style={{ display: "flex", alignItems: "center", gap: 14, padding: "12px 16px", border: "1px solid #E2E8F0", borderRadius: 10, background: "#fff" }}>
                  <div style={{ width: 10, height: 10, borderRadius: "50%", background: "#22C55E", flexShrink: 0, boxShadow: "0 0 0 3px #BBF7D0" }} />
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: 14, color: "#0F172A" }}>{shift.guardId?.slice(0, 8)}...</div>
                    <div style={{ fontSize: 12, color: "#94A3B8" }}>Since {fmtTime(shift.startAt)} · ends {fmtTime(shift.endAt)}</div>
                  </div>
                  <span style={{ fontSize: 11, fontWeight: 600, background: "#DCFCE7", color: "#166534", padding: "2px 8px", borderRadius: 20 }}>ACTIVE</span>
                </div>
              ))}
            </div>
          )}

          {/* Recent incidents */}
          {openIncidents.length > 0 && (
            <div style={{ marginTop: 20 }}>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
                <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Open Incidents</span>
                <button onClick={() => onNavigate("incidents")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
                  View all <ArrowRight size={13} />
                </button>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {openIncidents.slice(0, 3).map((inc: any) => {
                  const sevColor = { CRITICAL: "#DC2626", HIGH: "#EA580C", MEDIUM: "#D97706", LOW: "#64748B" }[inc.severity as string] ?? "#64748B"
                  return (
                    <div key={inc.id} style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", border: `1px solid ${sevColor}30`, borderLeft: `3px solid ${sevColor}`, borderRadius: 10, background: "#fff" }}>
                      <AlertTriangle size={16} color={sevColor} />
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{inc.title}</div>
                        <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtDate(inc.reportedAt)}</div>
                      </div>
                      <span style={{ fontSize: 10, fontWeight: 700, background: `${sevColor}18`, color: sevColor, padding: "2px 8px", borderRadius: 20 }}>{inc.severity}</span>
                    </div>
                  )
                })}
              </div>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {/* Quick stats */}
          <div style={{ background: "#1B3A6B", borderRadius: 12, padding: 20, color: "#fff" }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: "rgba(255,255,255,0.6)", marginBottom: 14, textTransform: "uppercase" as const }}>Operations Summary</div>
            {[
              { label: "Total guards",   value: (guards as any[]).length },
              { label: "Sites secured",  value: (sites as any[]).length },
              { label: "Shifts today",   value: (shifts as any[]).filter((s: any) => s.startAt?.startsWith(new Date().toISOString().split("T")[0])).length },
              { label: "Resolved today", value: (incidents as any[]).filter((i: any) => i.status === "RESOLVED").length },
            ].map(s => (
              <div key={s.label} style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid rgba(255,255,255,0.1)" }}>
                <span style={{ fontSize: 13, color: "rgba(255,255,255,0.7)" }}>{s.label}</span>
                <span style={{ fontSize: 14, fontWeight: 700, color: "#fff" }}>{s.value}</span>
              </div>
            ))}
          </div>

          {/* Quick actions */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 10 }}>Quick actions</div>
            {[
              { label: "Schedule a shift",  tab: "shifts",    color: "#1B3A6B" },
              { label: "Report incident",   tab: "incidents", color: "#DC2626" },
              { label: "Add guard",         tab: "guards",    color: "#0D9488" },
              { label: "View live map",     tab: "live",      color: "#7C3AED" },
            ].map(a => (
              <button key={a.label} onClick={() => onNavigate(a.tab)}
                style={{ width: "100%", marginBottom: 8, padding: "9px 14px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, fontWeight: 600, color: a.color, cursor: "pointer", textAlign: "left" as const, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                {a.label} <ArrowRight size={13} />
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}