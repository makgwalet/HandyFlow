// src/pages/fuel/FuelDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Droplets, AlertTriangle, ArrowDownToLine, Fuel, Truck, ArrowRight, TrendingDown } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtR = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short" })

const FUEL_COLORS: Record<string, string> = {
  DIESEL: "#1D4ED8", PETROL: "#DC2626", PARAFFIN: "#D97706", GAS: "#7C3AED", OTHER: "#64748B",
}

export default function FuelDashboard({ onNavigate }: { onNavigate: (t: any) => void }) {
  const { data: tanksRaw } = useQuery({
    queryKey: ["tanks"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/fuel/tanks"); return r.data?.data ?? r.data ?? [] },
  })
  const { data: dispatches = [] } = useQuery({
    queryKey: ["dispatches"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/dispatches?size=200")),
  })
  const { data: deliveries = [] } = useQuery({
    queryKey: ["deliveries"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/deliveries?size=200")),
  })
  const { data: receipts = [] } = useQuery({
    queryKey: ["receipts"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/receipts?size=200")),
  })

  const tanks     = Array.isArray(tanksRaw) ? tanksRaw : []
  const ds        = dispatches as any[]
  const dvs       = deliveries as any[]
  const rcs       = receipts as any[]
  const lowTanks  = tanks.filter((t: any) => t.low)
  const totalStock = tanks.reduce((s: number, t: any) => s + Number(t.currentLitres ?? 0), 0)

  const today = new Date().toISOString().slice(0, 7) // YYYY-MM
  const thisMonthDispatches = ds.filter(d => d.dispatchedAt?.startsWith(today))
  const thisMonthLitres = thisMonthDispatches.reduce((s, d) => s + Number(d.litresDispensed ?? 0), 0)

  const pendingDeliveries = dvs.filter(d => d.status === "SCHEDULED" || d.status === "IN_TRANSIT")

  const kpis = [
    { label: "Total stock",       value: `${totalStock.toLocaleString()} L`,            color: "#1B3A6B", bg: "#EFF6FF",  icon: Droplets,        tab: "tanks"      },
    { label: "Low tanks",         value: lowTanks.length,                                color: lowTanks.length > 0 ? "#DC2626" : "#166534", bg: lowTanks.length > 0 ? "#FEF2F2" : "#DCFCE7", icon: AlertTriangle, tab: "tanks" },
    { label: "Dispatched this mo",value: `${thisMonthLitres.toLocaleString()} L`,        color: "#0D9488", bg: "#F0FDF4",  icon: Fuel,            tab: "dispatches" },
    { label: "Pending deliveries",value: pendingDeliveries.length,                       color: "#D97706", bg: "#FFFBEB",  icon: Truck,           tab: "deliveries" },
  ]

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
            <div style={{ fontSize: 26, fontWeight: 800, color: k.color }}>{k.value}</div>
          </div>
        ))}
      </div>

      {/* Low stock alert */}
      {lowTanks.length > 0 && (
        <div style={{ marginBottom: 22, padding: "14px 18px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 10, display: "flex", alignItems: "center", gap: 12 }}>
          <AlertTriangle size={18} color="#DC2626" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 14, color: "#DC2626" }}>Low Stock Alert</div>
            <div style={{ fontSize: 13, color: "#B91C1C" }}>
              {lowTanks.map((t: any) => `${t.name} (${Number(t.currentLitres).toLocaleString()} L — ${Number(t.fillPercentage).toFixed(0)}%)`).join(" · ")}
            </div>
          </div>
          <button onClick={() => onNavigate("tanks")} style={{ marginLeft: "auto", padding: "6px 14px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer", flexShrink: 0 }}>
            Receive stock
          </button>
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "1fr 280px", gap: 18 }}>
        {/* Tank levels */}
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 14 }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Tank Levels</span>
            <button onClick={() => onNavigate("tanks")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#0D9488", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              Manage tanks <ArrowRight size={13} />
            </button>
          </div>
          {tanks.length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed #E2E8F0", borderRadius: 12, color: "#94A3B8" }}>
              <Droplets size={32} color="#CBD5E1" style={{ marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "#475569" }}>No tanks registered</div>
              <button onClick={() => onNavigate("tanks")} style={{ marginTop: 12, padding: "7px 16px", background: "#0D9488", color: "#fff", border: "none", borderRadius: 7, fontSize: 13, cursor: "pointer", fontWeight: 600 }}>Add tank</button>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {tanks.map((tank: any) => {
                const color = FUEL_COLORS[tank.fuelType] ?? "#64748B"
                const pct   = Math.min(100, Math.max(0, Number(tank.fillPercentage ?? 0)))
                return (
                  <div key={tank.id} style={{ background: "#fff", border: `1px solid ${tank.low ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, padding: "14px 18px" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                        <Droplets size={16} color={color} />
                        <span style={{ fontWeight: 700, color: "#0F172A" }}>{tank.name}</span>
                        <span style={{ fontSize: 11, fontWeight: 700, background: `${color}18`, color, padding: "1px 8px", borderRadius: 20 }}>{tank.fuelType}</span>
                        {tank.low && <span style={{ fontSize: 10, fontWeight: 700, background: "#FEF2F2", color: "#DC2626", padding: "1px 7px", borderRadius: 20, display: "flex", alignItems: "center", gap: 3 }}><AlertTriangle size={9} />LOW</span>}
                      </div>
                      <div style={{ textAlign: "right" as const }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: tank.low ? "#DC2626" : "#0F172A" }}>{Number(tank.currentLitres).toLocaleString()} L</span>
                        <span style={{ fontSize: 12, color: "#94A3B8" }}> / {Number(tank.capacityLitres).toLocaleString()} L</span>
                        <span style={{ fontSize: 13, fontWeight: 700, color: tank.low ? "#DC2626" : color, marginLeft: 10 }}>{pct.toFixed(1)}%</span>
                      </div>
                    </div>
                    <div style={{ height: 8, background: "#F1F5F9", borderRadius: 99, overflow: "hidden" }}>
                      <div style={{ height: "100%", width: `${pct}%`, borderRadius: 99,
                        background: tank.low ? "linear-gradient(90deg,#DC2626,#F87171)" : `linear-gradient(90deg,${color},${color}88)`,
                        transition: "width 0.5s" }} />
                    </div>
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "#94A3B8", marginTop: 4 }}>
                      <span>{tank.location || "No location"}</span>
                      <span>Available: {(Number(tank.capacityLitres) - Number(tank.currentLitres)).toLocaleString()} L</span>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {/* This month summary */}
          <div style={{ background: "#0D9488", borderRadius: 12, padding: 20 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "rgba(255,255,255,0.6)", marginBottom: 14, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>This month</div>
            {[
              { label: "Dispatches",   value: thisMonthDispatches.length },
              { label: "Litres out",   value: `${thisMonthLitres.toLocaleString()} L` },
              { label: "Stock-ins",    value: rcs.filter(r => r.receivedAt?.startsWith(today)).length },
            ].map(s => (
              <div key={s.label} style={{ display: "flex", justifyContent: "space-between", padding: "7px 0", borderBottom: "1px solid rgba(255,255,255,0.15)" }}>
                <span style={{ fontSize: 13, color: "rgba(255,255,255,0.8)" }}>{s.label}</span>
                <span style={{ fontSize: 14, fontWeight: 700, color: "#fff" }}>{s.value}</span>
              </div>
            ))}
          </div>

          {/* Negative variance warning */}
          {(() => {
            const negVariance = ds.filter((d: any) => Number(d.varianceLitres ?? 0) < -5)
            return negVariance.length > 0 ? (
              <div style={{ background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 10, padding: 14 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 6, fontWeight: 700, fontSize: 13, color: "#DC2626", marginBottom: 6 }}>
                  <TrendingDown size={14} /> Variance Alerts
                </div>
                <div style={{ fontSize: 12, color: "#B91C1C" }}>{negVariance.length} dip reading(s) show negative variance — possible leak or theft. Review reconciliation.</div>
              </div>
            ) : null
          })()}

          {/* Pending deliveries */}
          {pendingDeliveries.length > 0 && (
            <div style={{ background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 10, padding: 14 }}>
              <div style={{ fontWeight: 700, fontSize: 13, color: "#D97706", marginBottom: 8 }}>Pending Deliveries</div>
              {pendingDeliveries.slice(0, 3).map((d: any) => (
                <div key={d.id} style={{ display: "flex", justifyContent: "space-between", fontSize: 12, padding: "5px 0", borderBottom: "1px solid #FEF3C7" }}>
                  <span style={{ color: "#78350F" }}>{d.fuelType} · {Number(d.litresOrdered).toLocaleString()} L</span>
                  <span style={{ color: "#D97706", fontWeight: 600 }}>{fmtDate(d.scheduledAt)}</span>
                </div>
              ))}
              <button onClick={() => onNavigate("deliveries")} style={{ marginTop: 8, width: "100%", padding: "6px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 6, fontSize: 12, fontWeight: 600, color: "#D97706", cursor: "pointer" }}>
                View all →
              </button>
            </div>
          )}

          {/* Quick actions */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 10 }}>Quick actions</div>
            {[
              { label: "Receive stock",    tab: "tanks",      color: "#0D9488" },
              { label: "Dispatch fuel",    tab: "dispatches", color: "#1B3A6B" },
              { label: "Record dip",       tab: "tanks",      color: "#D97706" },
              { label: "Schedule delivery",tab: "deliveries", color: "#7C3AED" },
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
