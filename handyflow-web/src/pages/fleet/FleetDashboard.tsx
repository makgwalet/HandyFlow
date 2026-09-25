// src/pages/fleet/FleetDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Car, Route, Wrench, AlertTriangle, CheckCircle, ArrowRight, TrendingUp } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtOdo = (km: number) => `${Number(km).toLocaleString("en-ZA")} km`
const fmtDate = (d: string) => new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
const fmtR = (n: number | null | undefined) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
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

interface CostSummary {
  vehicleId: string; registration: string; make: string; model: string
  totalServiceCost: number; totalFuelCost: number; totalCost: number
  totalKm: number; costPerKm: number | null
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

  const { data: costSummary = [] } = useQuery<CostSummary[]>({
    queryKey: ["fleet-cost-summary"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fleet/cost-summary")),
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

  const rankedCosts = costSummary.filter(c => c.costPerKm !== null).slice(0, 5)

  return (
    <div>
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
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 14 }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>Fleet Overview</span>
            <button onClick={() => onNavigate("vehicles")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--hf-primary-text)", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              Manage fleet <ArrowRight size={13} />
            </button>
          </div>

          {vs.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed var(--hf-border)", borderRadius: 12, color: "var(--hf-text-faint)" }}>
              <Car size={32} color="#CBD5E1" style={{ marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No vehicles registered</div>
              <button onClick={() => onNavigate("vehicles")} style={{ marginTop: 12, padding: "7px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, fontSize: 13, cursor: "pointer", fontWeight: 600 }}>
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
                  <div key={v.id} style={{ display: "flex", alignItems: "center", gap: 14, padding: "12px 16px", border: `1px solid ${v.status === "BREAKDOWN" ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "var(--hf-surface)" }}>
                    <div style={{ fontSize: 22, width: 36, textAlign: "center" as const, flexShrink: 0 }}>{VEHICLE_ICONS[v.vehicleType] ?? "🚘"}</div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
                        <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{v.registration}</span>
                        <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{v.make} {v.model}</span>
                        {v.assignedDriverName && <span style={{ fontSize: 11, color: "var(--hf-violet-text)" }}>· {v.assignedDriverName}</span>}
                        {v.dueForService && <span style={{ fontSize: 10, fontWeight: 700, background: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text)", padding: "1px 6px", borderRadius: 20, border: "1px solid var(--hf-warning-border)" }}>SVC DUE</span>}
                        {(v.licenceExpiringSoon || v.roadworthyExpiringSoon) && <span style={{ fontSize: 10, fontWeight: 700, background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", padding: "1px 6px", borderRadius: 20 }}>EXPIRING</span>}
                      </div>
                      {activeTrip && (
                        <div style={{ fontSize: 11, color: "var(--hf-info-text)", marginBottom: 2 }}>
                          On trip · {activeTrip.driverName ?? "Unknown driver"} · {activeTrip.purpose ?? ""}
                        </div>
                      )}
                      <div style={{ height: 4, background: "var(--hf-surface-sunken)", borderRadius: 99, overflow: "hidden", marginTop: 4 }}>
                        <div style={{ height: "100%", width: `${svcPct}%`, background: svcPct >= 100 ? "var(--hf-danger)" : svcPct >= 80 ? "var(--hf-warning)" : "var(--hf-accent)", borderRadius: 99 }} />
                      </div>
                    </div>
                    <div style={{ flexShrink: 0, textAlign: "right" as const }}>
                      <div style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700, marginBottom: 3 }}>{cfg.label}</div>
                      <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{fmtOdo(v.currentOdometer ?? 0)}</div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}

          {rankedCosts.length > 0 && (
            <div style={{ marginTop: 24 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                  <TrendingUp size={15} color="#0F172A" />
                  <span style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>Cost per KM</span>
                </div>
                <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Most expensive first · all-time</span>
              </div>
              <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
                  <thead>
                    <tr style={{ background: "var(--hf-surface-muted)", borderBottom: "1px solid var(--hf-border)" }}>
                      {["Vehicle", "Service Cost", "Fuel Cost", "Total Cost", "Total KM", "Cost/KM"].map(h => (
                        <th key={h} style={{ padding: "9px 14px", textAlign: "left", fontWeight: 700, fontSize: 10, color: "var(--hf-text-muted)", letterSpacing: "0.04em" }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {rankedCosts.map((c, i) => (
                      <tr key={c.vehicleId} style={{ borderBottom: i < rankedCosts.length - 1 ? "1px solid var(--hf-border-subtle)" : "none", background: "var(--hf-surface)" }}>
                        <td style={{ padding: "10px 14px" }}>
                          <div style={{ fontWeight: 700, color: "var(--hf-text)" }}>{c.registration}</div>
                          <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{c.make} {c.model}</div>
                        </td>
                        <td style={{ padding: "10px 14px", color: "var(--hf-text-tertiary)" }}>{fmtR(c.totalServiceCost)}</td>
                        <td style={{ padding: "10px 14px", color: "var(--hf-text-tertiary)" }}>{fmtR(c.totalFuelCost)}</td>
                        <td style={{ padding: "10px 14px", fontWeight: 600, color: "var(--hf-text)" }}>{fmtR(c.totalCost)}</td>
                        <td style={{ padding: "10px 14px", color: "var(--hf-text-tertiary)" }}>{fmtOdo(c.totalKm)}</td>
                        <td style={{ padding: "10px 14px", fontWeight: 700, color: i === 0 ? "var(--hf-danger-text)" : "var(--hf-accent-text)" }}>
                          {c.costPerKm != null ? `R ${c.costPerKm.toFixed(2)}/km` : "—"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <div style={{ background: "var(--hf-primary)", borderRadius: 12, padding: 20, color: "var(--hf-text-on-solid)" }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "rgba(255,255,255,0.6)", marginBottom: 14, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>This month</div>
            {[
              { label: "Trips completed", value: ts.filter((t: any) => t.status === "COMPLETED" && t.startAt?.startsWith(today.slice(0, 7))).length },
              { label: "Kilometres driven", value: `${totalKmThisMonth.toLocaleString()} km` },
              { label: "Active now", value: activeTrips.length },
            ].map(s => (
              <div key={s.label} style={{ display: "flex", justifyContent: "space-between", padding: "7px 0", borderBottom: "1px solid rgba(255,255,255,0.1)" }}>
                <span style={{ fontSize: 13, color: "rgba(255,255,255,0.8)" }}>{s.label}</span>
                <span style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text-on-solid)" }}>{s.value}</span>
              </div>
            ))}
          </div>

          <div style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 10 }}>Compliance Alerts</div>
            {expiring30.length === 0 ? (
              <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13, color: "var(--hf-success-text-strong)" }}>
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
                <div key={v.id} style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid var(--hf-border-subtle)", fontSize: 12 }}>
                  <div>
                    <div style={{ fontWeight: 600, color: "var(--hf-text)" }}>{v.registration}</div>
                    <div style={{ color: "var(--hf-text-faint)" }}>{earliest.label}</div>
                  </div>
                  <div style={{ textAlign: "right" as const }}>
                    <div style={{ fontWeight: 700, color: daysUntil(earliest.date) <= 7 ? "var(--hf-danger-text)" : "var(--hf-warning-text)" }}>
                      {daysUntil(earliest.date)} days
                    </div>
                    <div style={{ color: "var(--hf-text-faint)" }}>{fmtDate(earliest.date)}</div>
                  </div>
                </div>
              ) : null
            })}
            {expiring30.length > 0 && (
              <button onClick={() => onNavigate("compliance")} style={{ width: "100%", marginTop: 10, padding: "7px", background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                View all compliance →
              </button>
            )}
          </div>

          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 10 }}>Quick actions</div>
            {[
              { label: "Register vehicle",  tab: "vehicles",   color: "#1B3A6B" },
              { label: "Register driver",   tab: "drivers",    color: "#7C3AED" },
              { label: "Start trip",        tab: "trips",      color: "#0D9488" },
              { label: "Log fuel fill-up",  tab: "fuel",       color: "#D97706" },
              { label: "Record service",    tab: "services",   color: "#1D4ED8" },
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
