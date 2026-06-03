// src/pages/fleet/FleetDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Car, Route, Wrench, Fuel, AlertTriangle, CheckCircle, ArrowRight, Clock } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtOdo = (km: number) => `${Number(km).toLocaleString("en-ZA")} km`
const fmtDate = (d: string) => new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
const daysUntil = (date: string) => Math.ceil((new Date(date).getTime() - Date.now()) / 86400000)

const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  AVAILABLE:   { color: "#166534", bg: "#DCFCE7", label: "Available"   },
  ON_TRIP:     { color: "#1D4ED8", bg: "#EFF6FF", label: "On Trip"     },
  MAINTENANCE: { color: "#D97706", bg: "#FFFBEB", label: "Maintenance" },
  BREAKDOWN:   { color: "#DC2626", bg: "#FEF2F2", label: "Breakdown"   },
  RETIRED:     { color: "#94A3B8", bg: "#F8FAFC", label: "Retired"     },
}

const VEHICLE_ICONS: Record<string, string> = {
  SEDAN:"🚗", SUV:"🚙", BAKKIE:"🛻", TRUCK:"🚛", MINIBUS:"🚐", VAN:"🚌", MOTORCYCLE:"🏍️", OTHER:"🚘"
}

export default function FleetDashboard({ onNavigate }: { onNavigate: (t: any) => void }) {
  const { data: vehicles = [] } = useQuery({
    queryKey: ["fleet-vehicles"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fleet/vehicles?size=200")),
  })

  const { data: trips = [] } = useQuery({
    queryKey: ["fleet-trips-all"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fleet/trips?size=200")),
  })

  const vs = vehicles as any[]
  const ts = trips as any[]

  const today = new Date().toISOString().split("T")[0]
  const activeTrips = ts.filter((t: any) => t.status === "ACTIVE")

  const expiring30 = vs.filter((v: any) => {
    const dates = [v.licenceDiscExpiry, v.roadworthyExpiry, v.insuranceExpiry].filter(Boolean)
    return dates.some(d => daysUntil(d) <= 30 && daysUntil(d) >= 0)
  })

  const kpis = [
    { label: "Total fleet",    value: vs.length,                                               color: "#1B3A6B", bg: "#EFF6FF", icon: Car,           tab: "vehicles" },
    { label: "On trip now",    value: activeTrips.length,                                       color: "#1D4ED8", bg: "#EFF6FF", icon: Route,         tab: "trips" },
    { label: "Service due",    value: vs.filter((v: any) => v.dueForService).length,            color: "#D97706", bg: "#FFFBEB", icon: Wrench,        tab: "services" },
    { label: "Expiring soon",  value: expiring30.length,                                        color: expiring30.length > 0 ? "#DC2626" : "#166534", bg: expiring30.length > 0 ? "#FEF2F2" : "#DCFCE7", icon: AlertTriangle, tab: "compliance" },
  ]

  const totalKmThisMonth = ts
    .filter((t: any) => t.startAt?.startsWith(today.slice(0, 7)))
    .reduce((s: number, t: any) => s + (t.distanceKm ?? 0), 0)

  return (
    <div>
      {/* KPIs */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 14, marginBottom: 28 }}>
        {kpis.map(k => (
          <div key={k.label} onClick={() => onNavigate(k.tab)}
            style={{ background: k.bg, borderRadius: 12, padding: "18px 20px", cursor: "pointer" }}
            onMouseEnter={e => (e.currentTarget.style.boxShadow = "0 4px 16px rgba(0,0,0,0.08)")}
            onMouseLeave={e => (e.currentTarget.style.boxShadow = "none")}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: k.color, textTransform: "uppercase" as const }}>{k.label}</div>
              <k.icon size={16} color={k.color} />
            </div>
            <div style={{ fontSize: 28, fontWeight: 800, color: k.color }}>{k.value}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 300px", gap: 18 }}>
        {/* Fleet status list */}
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 14 }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Fleet Overview</span>
            <button onClick={() => onNavigate("vehicles")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#1B3A6B", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              Manage fleet <ArrowRight size={13} />
            </button>
          </div>

          {vs.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed #E2E8F0", borderRadius: 12, color: "#94A3B8" }}>
              <Car size={32} color="#CBD5E1" style={{ marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "#475569" }}>No vehicles registered</div>
              <button onClick={() => onNavigate("vehicles")} style={{ marginTop: 12, padding: "7px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 7, fontSize: 13, cursor: "pointer", fontWeight: 600 }}>
                Register vehicle
              </button>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {vs.slice(0, 8).map((v: any) => {
                const cfg = STATUS_CFG[v.status] ?? STATUS_CFG.AVAILABLE
                const kmUsed = (v.currentOdometer ?? 0) - (v.lastServiceKm ?? 0)
                const svcPct = Math.min(100, (kmUsed / (v.serviceIntervalKm || 10000)) * 100)
                const activeTrip = activeTrips.find((t: any) => t.vehicleId === v.id)
                return (
                  <div key={v.id} style={{ display: "flex", alignItems: "center", gap: 14, padding: "12px 16px", border: `1px solid ${v.status === "BREAKDOWN" ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff" }}>
                    <div style={{ fontSize: 22, width: 36, textAlign: "center" as const, flexShrink: 0 }}>{VEHICLE_ICONS[v.vehicleType] ?? "🚘"}</div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
                        <span style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{v.registration}</span>
                        <span style={{ fontSize: 12, color: "#64748B" }}>{v.make} {v.model}</span>
                        {v.dueForService && <span style={{ fontSize: 10, fontWeight: 700, background: "#FEF3C7", color: "#D97706", padding: "1px 6px", borderRadius: 20, border: "1px solid #FDE68A" }}>SVC DUE</span>}
                        {(v.licenceExpiringSoon || v.roadworthyExpiringSoon) && <span style={{ fontSize: 10, fontWeight: 700, background: "#FEF2F2", color: "#DC2626", padding: "1px 6px", borderRadius: 20 }}>EXPIRING</span>}
                      </div>
                      {activeTrip && (
                        <div style={{ fontSize: 11, color: "#1D4ED8", marginBottom: 2 }}>
                          On trip · {activeTrip.driverName ?? "Unknown driver"} · {activeTrip.purpose ?? ""}
                        </div>
                      )}
                      <div style={{ height: 4, background: "#F1F5F9", borderRadius: 99, overflow: "hidden", marginTop: 4 }}>
                        <div style={{ height: "100%", width: `${svcPct}%`, background: svcPct >= 100 ? "#DC2626" : svcPct >= 80 ? "#D97706" : "#0D9488", borderRadius: 99 }} />
                      </div>
                    </div>
                    <div style={{ flexShrink: 0, textAlign: "right" as const }}>
                      <div style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700, marginBottom: 3 }}>{cfg.label}</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtOdo(v.currentOdometer ?? 0)}</div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {/* Monthly summary */}
          <div style={{ background: "#1B3A6B", borderRadius: 12, padding: 20, color: "#fff" }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "rgba(255,255,255,0.6)", marginBottom: 14, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>This month</div>
            {[
              { label: "Trips completed", value: ts.filter((t: any) => t.status === "COMPLETED" && t.startAt?.startsWith(today.slice(0, 7))).length },
              { label: "Kilometres driven", value: `${totalKmThisMonth.toLocaleString()} km` },
              { label: "Active now", value: activeTrips.length },
            ].map(s => (
              <div key={s.label} style={{ display: "flex", justifyContent: "space-between", padding: "7px 0", borderBottom: "1px solid rgba(255,255,255,0.1)" }}>
                <span style={{ fontSize: 13, color: "rgba(255,255,255,0.8)" }}>{s.label}</span>
                <span style={{ fontSize: 14, fontWeight: 700, color: "#fff" }}>{s.value}</span>
              </div>
            ))}
          </div>

          {/* Upcoming expirations */}
          <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 10 }}>Compliance Alerts</div>
            {expiring30.length === 0 ? (
              <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13, color: "#166534" }}>
                <CheckCircle size={14} color="#166534" /> All documents current
              </div>
            ) : expiring30.slice(0, 4).map((v: any) => {
              const earliest = [
                { label: "Licence", date: v.licenceDiscExpiry },
                { label: "Roadworthy", date: v.roadworthyExpiry },
                { label: "Insurance", date: v.insuranceExpiry },
              ].filter(x => x.date && daysUntil(x.date) <= 30 && daysUntil(x.date) >= 0)
               .sort((a, b) => daysUntil(a.date) - daysUntil(b.date))[0]

              return earliest ? (
                <div key={v.id} style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid #F1F5F9", fontSize: 12 }}>
                  <div>
                    <div style={{ fontWeight: 600, color: "#0F172A" }}>{v.registration}</div>
                    <div style={{ color: "#94A3B8" }}>{earliest.label}</div>
                  </div>
                  <div style={{ textAlign: "right" as const }}>
                    <div style={{ fontWeight: 700, color: daysUntil(earliest.date) <= 7 ? "#DC2626" : "#D97706" }}>
                      {daysUntil(earliest.date)} days
                    </div>
                    <div style={{ color: "#94A3B8" }}>{fmtDate(earliest.date)}</div>
                  </div>
                </div>
              ) : null
            })}
            {expiring30.length > 0 && (
              <button onClick={() => onNavigate("compliance")} style={{ width: "100%", marginTop: 10, padding: "7px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                View all compliance →
              </button>
            )}
          </div>

          {/* Quick actions */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 10 }}>Quick actions</div>
            {[
              { label: "Register vehicle",  tab: "vehicles",   color: "#1B3A6B" },
              { label: "Start trip",        tab: "trips",      color: "#0D9488" },
              { label: "Log fuel fill-up",  tab: "fuel",       color: "#D97706" },
              { label: "Record service",    tab: "services",   color: "#7C3AED" },
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
