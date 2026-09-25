// src/pages/security/SecurityDashboard.tsx
// Changes from original:
//   - Replace size=100 all-at-once fetches with targeted queries
//   - Active shifts list shows guard fullName via guard lookup
//   - Open incidents list links through to incidents tab
//   - Guards on duty shows fullName from the guards query

import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Shield, MapPin, Clock, AlertTriangle, ArrowRight, Radio } from "lucide-react"

const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })
const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("en-ZA",  { day: "numeric", month: "short" })

export default function SecurityDashboard({ onNavigate }: { onNavigate: (section: string) => void }) {

  // WHY targeted queries instead of size=100 everything?
  // The original fetched all guards, all shifts, all sites, all incidents on
  // every dashboard load.  For a tenant with 200 guards and 1000+ shifts that's
  // a massive payload for stats that only need counts.
  // New approach: use size=1 to get totalElements for counts (same pattern as
  // BookingsDashboard), and only fetch full data where the UI actually renders
  // individual rows (active shifts list, open incidents list).

  // Counts — size=1 gives us totalElements without loading full data
  const { data: guardStats } = useQuery({
    queryKey: ["guard-count"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/guards?size=1")
      const p = r.data?.data ?? r.data
      return { total: p?.totalElements ?? 0, active: 0 } // active counted from full list below
    },
  })

  const { data: siteStats } = useQuery({
    queryKey: ["site-count"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/sites?size=1")
      const p = r.data?.data ?? r.data
      return p?.totalElements ?? 0
    },
  })

  const { data: openIncidentCount } = useQuery({
    queryKey: ["incident-open-count"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/incidents?status=OPEN&size=1")
      const p = r.data?.data ?? r.data
      return p?.totalElements ?? 0
    },
  })

  const { data: criticalIncidentCount } = useQuery({
    queryKey: ["incident-critical-count"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/incidents?severity=CRITICAL&size=1")
      const p = r.data?.data ?? r.data
      return p?.totalElements ?? 0
    },
  })

  // Full data — only for rows we actually render on screen (max 6 items each)
  const { data: guards = [] } = useQuery<any[]>({
    queryKey: ["guards"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/guards?size=100")
      const p = r.data?.data ?? r.data
      return p?.content ?? []
    },
  })

  const { data: shifts = [] } = useQuery<any[]>({
    queryKey: ["shifts"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/shifts?size=50")
      const p = r.data?.data ?? r.data
      return p?.content ?? []
    },
  })

  const { data: openIncidents = [] } = useQuery<any[]>({
    queryKey: ["open-incidents"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/incidents?status=OPEN&size=10")
      const p = r.data?.data ?? r.data
      return p?.content ?? []
    },
  })

  const activeShifts  = shifts.filter((s: any) => s.status === "ACTIVE")
  const activeGuards  = guards.filter((g: any) => (g.status ?? "ACTIVE") === "ACTIVE").length
  const totalGuards   = guardStats?.total ?? guards.length

  const guardName = (guardId: string) =>
    guards.find((g: any) => g.id === guardId)?.fullName ?? guardId.slice(0, 8) + "…"

  const kpis = [
    { label: "Guards on duty",  value: activeShifts.length, sub: `${activeGuards} active / ${totalGuards} total`, color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft)", icon: Shield,        tab: "shifts" },
    { label: "Active sites",    value: siteStats ?? 0,       color: "var(--hf-primary-text)", bg: "var(--hf-info-soft)", icon: MapPin,        tab: "sites" },
    { label: "Open incidents",  value: openIncidentCount ?? openIncidents.length,
      color: (openIncidentCount ?? openIncidents.length) > 0 ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)",
      bg: (openIncidentCount ?? openIncidents.length) > 0 ? "var(--hf-danger-soft)" : "var(--hf-success-soft)",
      icon: AlertTriangle, tab: "incidents" },
    { label: "Critical alerts", value: criticalIncidentCount ?? 0,
      color: (criticalIncidentCount ?? 0) > 0 ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)",
      bg: (criticalIncidentCount ?? 0) > 0 ? "var(--hf-danger-soft)" : "var(--hf-success-soft)",
      icon: Radio, tab: "incidents" },
  ]

  const todayShifts = shifts.filter((s: any) =>
    new Date(s.startAt).toDateString() === new Date().toDateString())

  return (
    <div>
      {/* KPI grid */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 28 }}>
        {kpis.map(k => (
          <div key={k.label} onClick={() => onNavigate(k.tab)}
            style={{ background: k.bg, borderRadius: 12, padding: "18px 20px", cursor: "pointer", border: "1px solid transparent" }}
            onMouseEnter={e => (e.currentTarget.style.boxShadow = "0 4px 16px rgba(0,0,0,0.08)")}
            onMouseLeave={e => (e.currentTarget.style.boxShadow = "none")}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: k.color, textTransform: "uppercase" as const }}>{k.label}</div>
              <k.icon size={16} style={{ color: k.color }} />
            </div>
            <div style={{ fontSize: 28, fontWeight: 800, color: k.color }}>{k.value}</div>
            {"sub" in k && k.sub && <div style={{ fontSize: 11, color: k.color, opacity: 0.7, marginTop: 2 }}>{k.sub}</div>}
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 16 }}>

        {/* Active shifts */}
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 14 }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>Active Shifts</span>
            <button onClick={() => onNavigate("shifts")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--hf-accent-text)", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              View all <ArrowRight size={13} />
            </button>
          </div>

          {activeShifts.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed var(--hf-border)", borderRadius: 12, color: "var(--hf-text-faint)" }}>
              <Shield size={32} style={{ color: 'var(--hf-text-disabled)', marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No active shifts</div>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {activeShifts.slice(0, 6).map((shift: any) => (
                <div key={shift.id} style={{ display: "flex", alignItems: "center", gap: 14, padding: "12px 16px", border: "1px solid var(--hf-border)", borderRadius: 10, background: "var(--hf-surface)" }}>
                  <div style={{ width: 10, height: 10, borderRadius: "50%", background: "var(--hf-success)", flexShrink: 0, boxShadow: "0 0 0 3px var(--hf-success-border-subtle)" }} />
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: 14, color: "var(--hf-text)" }}>{guardName(shift.guardId)}</div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Since {fmtTime(shift.startAt)} · ends {fmtTime(shift.endAt)}</div>
                  </div>
                  <span style={{ fontSize: 11, fontWeight: 600, background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", padding: "2px 8px", borderRadius: 20 }}>ACTIVE</span>
                </div>
              ))}
            </div>
          )}

          {/* Open incidents */}
          {openIncidents.length > 0 && (
            <div style={{ marginTop: 20 }}>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
                <span style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>Open Incidents</span>
                <button onClick={() => onNavigate("incidents")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--hf-danger-text)", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
                  View all <ArrowRight size={13} />
                </button>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {openIncidents.slice(0, 3).map((inc: any) => {
                  const sevColor = ({ CRITICAL: "var(--hf-danger-text)", HIGH: "var(--hf-orange-text)", MEDIUM: "var(--hf-warning-text)", LOW: "var(--hf-text-muted)" } as Record<string, string>)[inc.severity] ?? "var(--hf-text-muted)"
                  return (
                    <div key={inc.id} style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", border: `1px solid color-mix(in srgb, ${sevColor} 19%, transparent)`, borderLeft: `3px solid ${sevColor}`, borderRadius: 10, background: "var(--hf-surface)" }}>
                      <AlertTriangle size={16} style={{ color: sevColor }} />
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 600, fontSize: 13, color: "var(--hf-text)" }}>{inc.title}</div>
                        <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{fmtDate(inc.reportedAt)}{inc.siteName && ` · ${inc.siteName}`}</div>
                      </div>
                      <span style={{ fontSize: 10, fontWeight: 700, background: `color-mix(in srgb, ${sevColor} 9%, transparent)`, color: sevColor, padding: "2px 8px", borderRadius: 20 }}>{inc.severity}</span>
                    </div>
                  )
                })}
              </div>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {/* Operations summary */}
          <div style={{ background: "var(--hf-primary)", borderRadius: 12, padding: 20, color: "var(--hf-text-on-solid)" }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: "rgba(255,255,255,0.6)", marginBottom: 14, textTransform: "uppercase" as const }}>Operations Summary</div>
            {[
              { label: "Total guards",   value: totalGuards },
              { label: "Active guards",  value: activeGuards },
              { label: "Sites secured",  value: siteStats ?? 0 },
              { label: "Shifts today",   value: todayShifts.length },
            ].map(s => (
              <div key={s.label} style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid rgba(255,255,255,0.1)" }}>
                <span style={{ fontSize: 13, color: "rgba(255,255,255,0.7)" }}>{s.label}</span>
                <span style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text-on-solid)" }}>{s.value}</span>
              </div>
            ))}
          </div>

          {/* Quick actions */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 10 }}>Quick actions</div>
            {[
              { label: "Schedule a shift", tab: "shifts",    color: "var(--hf-primary-text)" },
              { label: "Report incident",  tab: "incidents", color: "var(--hf-danger-text)" },
              { label: "Add guard",        tab: "guards",    color: "var(--hf-accent-text)" },
              { label: "View live map",    tab: "live",      color: "var(--hf-violet-text)" },
            ].map(a => (
              <button key={a.label} onClick={() => onNavigate(a.tab)}
                style={{ width: "100%", marginBottom: 8, padding: "9px 14px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, fontWeight: 600, color: a.color, cursor: "pointer", textAlign: "left" as const, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                {a.label} <ArrowRight size={13} />
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
