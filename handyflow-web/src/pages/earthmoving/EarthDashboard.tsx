// src/pages/earthmoving/EarthDashboard.tsx
import {
  Truck, AlertTriangle, Wrench, MapPin, ArrowRight, CheckCircle, Clock,
} from "lucide-react"
import { STATUS_CFG, EMOJI, SEVERITY_CFG } from "./shared/constants"
import { fmtCurrency } from "./shared/format"
import { useAssets, useIncidents } from "./shared/hooks"

type Tab = "dashboard" | "assets" | "maintenance" | "operators" | "deployments" | "incidents"

export default function EarthDashboard({ onNavigate }: { onNavigate: (tab: Tab) => void }) {
  const { data: assets = [], isLoading: assetsLoading } = useAssets()
  const { data: incidents = [] } = useIncidents()

  const breakdowns   = assets.filter(a => a.status === "BREAKDOWN")
  const serviceDue   = assets.filter(a => a.dueForService)
  const deployed     = assets.filter(a => a.status === "DEPLOYED" || a.status === "HIRED_OUT")
  const openIncidents = incidents.filter(i => i.status === "OPEN").slice(0, 5)

  const fleetValue = assets.reduce((sum, a) => sum + (a.dailyRate ?? 0), 0)

  const stats = [
    { label: "Total fleet", value: assets.length, color: "#1B3A6B", icon: Truck, tab: "assets" as Tab },
    { label: "Deployed", value: deployed.length, color: "#1D4ED8", icon: MapPin, tab: "deployments" as Tab },
    { label: "Service due", value: serviceDue.length, color: "#D97706", icon: Wrench, tab: "maintenance" as Tab },
    { label: "Breakdowns", value: breakdowns.length, color: "#DC2626", icon: AlertTriangle, tab: "incidents" as Tab },
  ]

  return (
    <div>
      {/* Headline stats — each card doubles as a shortcut into its tab */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12, marginBottom: 22 }}>
        {stats.map(s => {
          const Icon = s.icon
          return (
            <button key={s.label} onClick={() => onNavigate(s.tab)}
              style={{ textAlign: "left" as const, cursor: "pointer", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: "16px 18px", display: "flex", flexDirection: "column", gap: 10 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div style={{ width: 34, height: 34, borderRadius: 9, background: `${s.color}18`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <Icon size={16} color={s.color} />
                </div>
                <ArrowRight size={14} color="#CBD5E1" />
              </div>
              <div>
                <div style={{ fontSize: 26, fontWeight: 800, color: s.color }}>{s.value}</div>
                <div style={{ fontSize: 12, color: "#64748B", marginTop: 1 }}>{s.label}</div>
              </div>
            </button>
          )
        })}
      </div>

      {/* Urgent attention banner */}
      {(breakdowns.length > 0 || serviceDue.length > 0) && (
        <div style={{ marginBottom: 20, border: "1px solid #FECACA", borderRadius: 12, overflow: "hidden" }}>
          <div style={{ padding: "10px 16px", background: "#FEF2F2", borderBottom: breakdowns.length && serviceDue.length ? "1px solid #FECACA" : "none", fontSize: 12, fontWeight: 700, color: "#DC2626", letterSpacing: "0.03em" }}>
            NEEDS ATTENTION
          </div>
          {breakdowns.length > 0 && (
            <button onClick={() => onNavigate("incidents")}
              style={{ width: "100%", textAlign: "left" as const, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", background: "#fff", border: "none", borderBottom: serviceDue.length ? "1px solid #F1F5F9" : "none" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <AlertTriangle size={15} color="#DC2626" />
                <span style={{ fontSize: 13, color: "#0F172A" }}>
                  <strong>{breakdowns.length}</strong> machine{breakdowns.length !== 1 ? "s" : ""} currently broken down — {breakdowns.map(a => a.fleetNumber ?? a.name).join(", ")}
                </span>
              </div>
              <ArrowRight size={14} color="#94A3B8" />
            </button>
          )}
          {serviceDue.length > 0 && (
            <button onClick={() => onNavigate("maintenance")}
              style={{ width: "100%", textAlign: "left" as const, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", background: "#fff", border: "none" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <Wrench size={15} color="#D97706" />
                <span style={{ fontSize: 13, color: "#0F172A" }}>
                  <strong>{serviceDue.length}</strong> machine{serviceDue.length !== 1 ? "s" : ""} due for service — {serviceDue.map(a => a.fleetNumber ?? a.name).join(", ")}
                </span>
              </div>
              <ArrowRight size={14} color="#94A3B8" />
            </button>
          )}
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "1.3fr 1fr", gap: 16 }}>
        {/* Deployed equipment */}
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <div style={{ padding: "12px 16px", borderBottom: "1px solid #F1F5F9", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>Currently Deployed</span>
            <button onClick={() => onNavigate("deployments")} style={{ background: "none", border: "none", cursor: "pointer", fontSize: 12, color: "#1D4ED8", fontWeight: 600, display: "flex", alignItems: "center", gap: 3 }}>
              View all <ArrowRight size={12} />
            </button>
          </div>
          {assetsLoading ? (
            <div style={{ padding: 24, textAlign: "center", color: "#94A3B8", fontSize: 13 }}>Loading...</div>
          ) : deployed.length === 0 ? (
            <div style={{ padding: 24, textAlign: "center", color: "#94A3B8", fontSize: 13 }}>No equipment deployed right now</div>
          ) : (
            <div>
              {deployed.slice(0, 5).map((a, i) => (
                <div key={a.id} style={{ padding: "11px 16px", display: "flex", alignItems: "center", gap: 12, borderBottom: i < Math.min(deployed.length, 5) - 1 ? "1px solid #F8FAFC" : "none" }}>
                  <div style={{ width: 32, height: 32, borderRadius: 8, background: "#EFF6FF", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 16, flexShrink: 0 }}>
                    {EMOJI[a.assetType] ?? "🚧"}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>
                      {a.fleetNumber ? `${a.fleetNumber} — ` : ""}{a.name}
                    </div>
                    <div style={{ fontSize: 11, color: "#94A3B8" }}>
                      {a.currentSite ?? "Unknown site"}{a.currentClient ? ` · ${a.currentClient}` : ""}
                    </div>
                  </div>
                  <span style={{ fontSize: 11, fontWeight: 700, color: STATUS_CFG[a.status]?.color, background: STATUS_CFG[a.status]?.bg, padding: "2px 8px", borderRadius: 20, flexShrink: 0 }}>
                    {STATUS_CFG[a.status]?.label ?? a.status}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Open incidents */}
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <div style={{ padding: "12px 16px", borderBottom: "1px solid #F1F5F9", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>Open Incidents</span>
            <button onClick={() => onNavigate("incidents")} style={{ background: "none", border: "none", cursor: "pointer", fontSize: 12, color: "#1D4ED8", fontWeight: 600, display: "flex", alignItems: "center", gap: 3 }}>
              View all <ArrowRight size={12} />
            </button>
          </div>
          {openIncidents.length === 0 ? (
            <div style={{ padding: 24, textAlign: "center", color: "#94A3B8", fontSize: 13 }}>
              <CheckCircle size={22} style={{ opacity: 0.4, marginBottom: 6 }} />
              <div>No open incidents</div>
            </div>
          ) : (
            <div>
              {openIncidents.map((inc, i) => {
                const sev = SEVERITY_CFG[inc.severity] ?? SEVERITY_CFG.LOW
                return (
                  <div key={inc.id} style={{ padding: "11px 16px", borderBottom: i < openIncidents.length - 1 ? "1px solid #F8FAFC" : "none" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
                      <span style={{ width: 6, height: 6, borderRadius: "50%", background: sev.color, flexShrink: 0 }} />
                      <span style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{inc.title}</span>
                    </div>
                    <div style={{ fontSize: 11, color: "#94A3B8", paddingLeft: 14 }}>{inc.severity} · {inc.type.replace("_", " ")}</div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>

      {/* Fleet value footer */}
      <div style={{ marginTop: 16, padding: "12px 16px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, display: "flex", alignItems: "center", gap: 8, fontSize: 12, color: "#64748B" }}>
        <Clock size={13} />
        Combined daily rate across active fleet: <strong style={{ color: "#0F172A" }}>{fmtCurrency(fleetValue)}/day</strong>
      </div>
    </div>
  )
}
