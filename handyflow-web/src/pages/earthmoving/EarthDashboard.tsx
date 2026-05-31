// src/pages/earthmoving/EarthDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Truck, Wrench, AlertTriangle, MapPin,
  CheckCircle, Clock, ArrowRight, TrendingUp, Zap,
} from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }

const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  AVAILABLE:   { color: "#166534", bg: "#DCFCE7", label: "Available" },
  DEPLOYED:    { color: "#1D4ED8", bg: "#EFF6FF", label: "Deployed"  },
  MAINTENANCE: { color: "#D97706", bg: "#FFFBEB", label: "Maintenance" },
  BREAKDOWN:   { color: "#DC2626", bg: "#FEF2F2", label: "Breakdown" },
  HIRED_IN:    { color: "#7C3AED", bg: "#F5F3FF", label: "Hired In"  },
  RETIRED:     { color: "#94A3B8", bg: "#F8FAFC", label: "Retired"   },
}

export default function EarthDashboard({ onNavigate }: { onNavigate: (tab: any) => void }) {
  const { data: assets = [] } = useQuery({
    queryKey: ["em-assets"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/earthmoving/assets?size=200")),
  })

  const total      = (assets as any[]).length
  const available  = (assets as any[]).filter((a: any) => a.status === "AVAILABLE").length
  const deployed   = (assets as any[]).filter((a: any) => a.status === "DEPLOYED").length
  const inMaint    = (assets as any[]).filter((a: any) => a.status === "MAINTENANCE").length
  const breakdown  = (assets as any[]).filter((a: any) => a.status === "BREAKDOWN").length
  const dueService = (assets as any[]).filter((a: any) => a.dueForService).length
  const hiredIn    = (assets as any[]).filter((a: any) => a.ownershipType === "HIRED_IN").length

  const kpis = [
    { label: "Total fleet",     value: total,     color: "#1B3A6B", bg: "#EFF6FF", icon: Truck,         tab: "assets" },
    { label: "Deployed",        value: deployed,   color: "#1D4ED8", bg: "#EFF6FF", icon: MapPin,        tab: "deployments" },
    { label: "Service due",     value: dueService, color: "#D97706", bg: "#FFFBEB", icon: Clock,         tab: "maintenance" },
    { label: "Breakdowns",      value: breakdown,  color: breakdown > 0 ? "#DC2626" : "#166534", bg: breakdown > 0 ? "#FEF2F2" : "#DCFCE7", icon: AlertTriangle, tab: "incidents" },
  ]

  // Fleet composition by type
  const byType = (assets as any[]).reduce((acc: Record<string, number>, a: any) => {
    acc[a.assetType] = (acc[a.assetType] ?? 0) + 1; return acc
  }, {})

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
            <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Fleet Status</span>
            <button onClick={() => onNavigate("assets")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#D97706", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
              Manage fleet <ArrowRight size={13} />
            </button>
          </div>

          {(assets as any[]).length === 0 ? (
            <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed #E2E8F0", borderRadius: 12, color: "#94A3B8" }}>
              <Truck size={32} color="#CBD5E1" style={{ marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "#475569" }}>No assets registered</div>
              <button onClick={() => onNavigate("assets")}
                style={{ marginTop: 12, padding: "7px 16px", background: "#D97706", color: "#fff", border: "none", borderRadius: 7, fontSize: 13, cursor: "pointer", fontWeight: 600 }}>
                Register equipment
              </button>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {(assets as any[]).slice(0, 8).map((a: any) => {
                const cfg = STATUS_CFG[a.status] ?? STATUS_CFG.AVAILABLE
                const hoursUsed = (a.currentHours ?? 0) - (a.lastServiceHours ?? 0)
                const svcPct = Math.min(100, (hoursUsed / (a.serviceIntervalHours ?? 250)) * 100)
                return (
                  <div key={a.id} style={{ display: "flex", alignItems: "center", gap: 14, padding: "12px 16px", border: `1px solid ${a.status === "BREAKDOWN" ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff" }}>
                    <div style={{ width: 40, height: 40, borderRadius: 8, background: "#F8FAFC", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 20, flexShrink: 0 }}>
                      {ASSET_EMOJI[a.assetType] ?? "🚧"}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
                        <span style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{a.fleetNumber ?? ""}</span>
                        <span style={{ fontSize: 13, color: "#475569", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" as const }}>{a.name}</span>
                        {a.dueForService && <span style={{ fontSize: 10, fontWeight: 700, background: "#FEF3C7", color: "#D97706", padding: "1px 6px", borderRadius: 20, flexShrink: 0 }}>SVC DUE</span>}
                      </div>
                      <div style={{ height: 4, background: "#F1F5F9", borderRadius: 99, overflow: "hidden", marginTop: 4 }}>
                        <div style={{ height: "100%", width: `${svcPct}%`, background: svcPct >= 100 ? "#DC2626" : svcPct >= 80 ? "#D97706" : "#0D9488", borderRadius: 99 }} />
                      </div>
                    </div>
                    <div style={{ display: "flex", flex: "column", alignItems: "flex-end", gap: 4, flexShrink: 0 }}>
                      <span style={{ background: cfg.bg, color: cfg.color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{cfg.label}</span>
                      <span style={{ fontSize: 11, color: "#94A3B8" }}>{(a.currentHours ?? 0).toLocaleString()} hrs</span>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {/* Fleet composition */}
          <div style={{ background: "#1B3A6B", borderRadius: 12, padding: 20, color: "#fff" }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "rgba(255,255,255,0.6)", marginBottom: 14, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>Fleet Breakdown</div>
            {[
              { label: "Available",   value: available, color: "#4ADE80" },
              { label: "Deployed",    value: deployed,  color: "#60A5FA" },
              { label: "Maintenance", value: inMaint,   color: "#FCD34D" },
              { label: "Breakdown",   value: breakdown, color: "#F87171" },
              { label: "Hired In",    value: hiredIn,   color: "#A78BFA" },
            ].map(s => (
              <div key={s.label} style={{ display: "flex", justifyContent: "space-between", padding: "7px 0", borderBottom: "1px solid rgba(255,255,255,0.1)" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <div style={{ width: 8, height: 8, borderRadius: "50%", background: s.color }} />
                  <span style={{ fontSize: 13, color: "rgba(255,255,255,0.8)" }}>{s.label}</span>
                </div>
                <span style={{ fontSize: 14, fontWeight: 700, color: "#fff" }}>{s.value}</span>
              </div>
            ))}
          </div>

          {/* Fleet by type */}
          {Object.keys(byType).length > 0 && (
            <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 10 }}>By Equipment Type</div>
              {Object.entries(byType).sort((a, b) => b[1] - a[1]).map(([type, count]) => (
                <div key={type} style={{ display: "flex", justifyContent: "space-between", padding: "5px 0", fontSize: 13 }}>
                  <span style={{ color: "#64748B" }}>{ASSET_EMOJI[type] ?? "🚧"} {type}</span>
                  <span style={{ fontWeight: 700, color: "#1B3A6B" }}>{count as number}</span>
                </div>
              ))}
            </div>
          )}

          {/* Quick actions */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 10 }}>Quick actions</div>
            {[
              { label: "Register equipment", tab: "assets",      color: "#1B3A6B" },
              { label: "Log deployment",     tab: "deployments", color: "#D97706" },
              { label: "Schedule service",   tab: "maintenance", color: "#0D9488" },
              { label: "Report breakdown",   tab: "incidents",   color: "#DC2626" },
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

const ASSET_EMOJI: Record<string, string> = {
  DOZER: "🚜", EXCAVATOR: "⛏️", GRADER: "🛣️", LOADER: "🏗️",
  DUMPER: "🚛", CRANE: "🏗️", ROLLER: "🛞", SCRAPER: "🚜", OTHER: "🚧",
}
