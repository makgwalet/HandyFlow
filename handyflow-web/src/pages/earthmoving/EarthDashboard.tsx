// src/pages/earthmoving/EarthDashboard.tsx
import {
  Truck, AlertTriangle, Wrench, MapPin, ArrowRight, CheckCircle, Clock,
} from "lucide-react"
import { STATUS_CFG, EMOJI, SEVERITY_CFG } from "./shared/constants"
import { fmtCurrency } from "./shared/format"
import { useAssets, useIncidents, usePlans, useUtilisation } from "./shared/hooks"
import { statusCounts, upcomingPlans, remainingText } from "./shared/dashboard"
import { useState } from "react"

type Tab = "dashboard" | "assets" | "maintenance" | "operators" | "deployments" | "incidents"

export default function EarthDashboard({ onNavigate }: { onNavigate: (tab: Tab) => void }) {
  const { data: assets = [], isLoading: assetsLoading } = useAssets()
  const { data: incidents = [] } = useIncidents()
  const { data: plans = [] } = usePlans()
  const [days, setDays] = useState(30)
  const { data: util, isLoading: utilLoading, isError: utilError } = useUtilisation(days)
  const counts = statusCounts(assets)
  const upcoming = upcomingPlans(plans)
  const nameOf = (id: string) => { const a = assets.find(x => x.id === id); return a ? (a.fleetNumber ?? a.name) : "Removed machine" }
  const peak = Math.max(1, ...(util?.perDay ?? [0]))
  const donut = counts.length === 0 ? "var(--hf-border)" : `conic-gradient(${(() => {
    let at = 0
    return counts.map(c => { const from = at; at += (c.count / assets.filter(a => a.status !== "RETIRED").length) * 100; return `${STATUS_CFG[c.status]?.color} ${from}% ${at}%` }).join(", ")
  })()})`

  const breakdowns   = assets.filter(a => a.status === "BREAKDOWN")
  const serviceDue   = assets.filter(a => a.dueForService)
  const deployed     = assets.filter(a => a.status === "DEPLOYED" || a.status === "HIRED_OUT")
  const openIncidents = incidents.filter(i => i.status === "OPEN").slice(0, 5)

  const combinedDailyRate = assets.reduce((sum, a) => sum + (a.dailyRate ?? 0), 0)

  const stats = [
    { label: "Total fleet", value: assets.length, color: "var(--hf-primary-text)", icon: Truck, tab: "assets" as Tab },
    { label: "Deployed", value: deployed.length, color: "var(--hf-info-text)", icon: MapPin, tab: "deployments" as Tab },
    { label: "Service due", value: serviceDue.length, color: "var(--hf-warning-text)", icon: Wrench, tab: "maintenance" as Tab },
    { label: "Breakdowns", value: breakdowns.length, color: "var(--hf-danger-text)", icon: AlertTriangle, tab: "incidents" as Tab },
  ]

  return (
    <div>
      {/* Headline stats — each card doubles as a shortcut into its tab */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12, marginBottom: 22 }}>
        {stats.map(s => {
          const Icon = s.icon
          return (
            <button key={s.label} onClick={() => onNavigate(s.tab)}
              style={{ textAlign: "left" as const, cursor: "pointer", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "16px 18px", display: "flex", flexDirection: "column", gap: 10 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div style={{ width: 34, height: 34, borderRadius: 9, background: `color-mix(in srgb, ${s.color} 9%, transparent)`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <Icon size={16} style={{ color: s.color }} />
                </div>
                <ArrowRight size={14} style={{ color: 'var(--hf-text-disabled)' }} />
              </div>
              <div>
                <div style={{ fontSize: 26, fontWeight: 800, color: s.color }}>{s.value}</div>
                <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 1 }}>{s.label}</div>
              </div>
            </button>
          )
        })}
      </div>

      {/* Urgent attention banner */}
      {(breakdowns.length > 0 || serviceDue.length > 0) && (
        <div style={{ marginBottom: 20, border: "1px solid var(--hf-danger-border)", borderRadius: 12, overflow: "hidden" }}>
          <div style={{ padding: "10px 16px", background: "var(--hf-danger-soft)", borderBottom: breakdowns.length && serviceDue.length ? "1px solid var(--hf-danger-border)" : "none", fontSize: 12, fontWeight: 700, color: "var(--hf-danger-text)", letterSpacing: "0.03em" }}>
            NEEDS ATTENTION
          </div>
          {breakdowns.length > 0 && (
            <button onClick={() => onNavigate("incidents")}
              style={{ width: "100%", textAlign: "left" as const, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", background: "var(--hf-surface)", border: "none", borderBottom: serviceDue.length ? "1px solid var(--hf-border-subtle)" : "none" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <AlertTriangle size={15} style={{ color: 'var(--hf-danger-text)' }} />
                <span style={{ fontSize: 13, color: "var(--hf-text)" }}>
                  <strong>{breakdowns.length}</strong> machine{breakdowns.length !== 1 ? "s" : ""} currently broken down — {breakdowns.map(a => a.fleetNumber ?? a.name).join(", ")}
                </span>
              </div>
              <ArrowRight size={14} style={{ color: 'var(--hf-text-faint)' }} />
            </button>
          )}
          {serviceDue.length > 0 && (
            <button onClick={() => onNavigate("maintenance")}
              style={{ width: "100%", textAlign: "left" as const, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", background: "var(--hf-surface)", border: "none" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <Wrench size={15} style={{ color: 'var(--hf-warning-text)' }} />
                <span style={{ fontSize: 13, color: "var(--hf-text)" }}>
                  <strong>{serviceDue.length}</strong> machine{serviceDue.length !== 1 ? "s" : ""} due for service — {serviceDue.map(a => a.fleetNumber ?? a.name).join(", ")}
                </span>
              </div>
              <ArrowRight size={14} style={{ color: 'var(--hf-text-faint)' }} />
            </button>
          )}
        </div>
      )}

      {/* Status mix, hours per day, upcoming maintenance */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1.4fr 1fr", gap: 16, marginBottom: 16 }}>
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, padding: "12px 16px" }} aria-label="Fleet status">
          <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 12 }}>Fleet status</div>
          {counts.length === 0 ? (
            <div style={{ padding: 16, textAlign: "center", color: "var(--hf-text-faint)", fontSize: 13 }}>No machines yet</div>
          ) : (
            <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
              <div style={{ width: 96, height: 96, borderRadius: "50%", background: donut, position: "relative", flexShrink: 0 }}>
                <div style={{ position: "absolute", inset: 18, borderRadius: "50%", background: "var(--hf-surface)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 18, fontWeight: 800, color: "var(--hf-text)" }}>
                  {counts.reduce((n, c) => n + c.count, 0)}
                </div>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 5, fontSize: 12 }}>
                {counts.map(c => (
                  <div key={c.status} style={{ display: "flex", alignItems: "center", gap: 6, color: "var(--hf-text)" }}>
                    <span style={{ width: 8, height: 8, borderRadius: 2, background: STATUS_CFG[c.status]?.color }} />
                    <span>{STATUS_CFG[c.status]?.label ?? c.status}</span>
                    <strong>{c.count}</strong>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, padding: "12px 16px" }} aria-label="Hours worked">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)" }}>Hours worked per day</span>
            <select aria-label="Chart period" value={days} onChange={e => setDays(Number(e.target.value))}
              style={{ fontSize: 12, border: "1px solid var(--hf-border)", borderRadius: 6, padding: "2px 6px", background: "var(--hf-surface)", color: "var(--hf-text)" }}>
              <option value={7}>Last 7 days</option>
              <option value={30}>Last 30 days</option>
              <option value={90}>Last 90 days</option>
            </select>
          </div>
          {utilLoading ? (
            <div style={{ padding: 24, textAlign: "center", color: "var(--hf-text-faint)", fontSize: 13 }}>Loading...</div>
          ) : utilError || !util ? (
            <div style={{ padding: 24, textAlign: "center", color: "var(--hf-text-faint)", fontSize: 13 }}>Hours worked could not be loaded</div>
          ) : (
            <>
              <div style={{ display: "flex", alignItems: "flex-end", gap: 2, height: 80 }} role="img" aria-label={`${util.totalHours} hours worked in the last ${days} days`}>
                {util.perDay.map((h, i) => (
                  <div key={i} title={`${h} hrs`} style={{ flex: 1, height: `${Math.max(h > 0 ? 4 : 1, (h / peak) * 100)}%`, background: h > 0 ? "var(--hf-info-text)" : "var(--hf-border)", borderRadius: 2 }} />
                ))}
              </div>
              <div style={{ marginTop: 8, fontSize: 12, color: "var(--hf-text-muted)" }}>
                <strong style={{ color: "var(--hf-text)" }}>{util.totalHours} hrs</strong> in {days} days on {util.machines} hour-metered machine{util.machines !== 1 ? "s" : ""} · busiest day {Math.max(0, ...util.perDay)} hrs
              </div>
            </>
          )}
        </div>

        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }} aria-label="Upcoming maintenance">
          <div style={{ padding: "12px 16px", borderBottom: "1px solid var(--hf-border-subtle)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)" }}>Upcoming maintenance</span>
            <button onClick={() => onNavigate("maintenance")} style={{ background: "none", border: "none", cursor: "pointer", fontSize: 12, color: "var(--hf-info-text)", fontWeight: 600 }}>View</button>
          </div>
          {upcoming.length === 0 ? (
            <div style={{ padding: 24, textAlign: "center", color: "var(--hf-text-faint)", fontSize: 13 }}>Nothing due soon</div>
          ) : upcoming.map((p, i) => (
            <div key={p.id} style={{ padding: "9px 16px", borderBottom: i < upcoming.length - 1 ? "1px solid var(--hf-border-subtle)" : "none" }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>{nameOf(p.assetId)} — {p.name}</div>
              <div style={{ fontSize: 11, color: p.state === "DUE_SOON" ? "var(--hf-warning-text)" : "var(--hf-danger-text)" }}>
                {p.state === "OVERDUE" ? "Overdue" : p.state === "DUE" ? "Due now" : "Due soon"} · {remainingText(p)}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1.3fr 1fr", gap: 16 }}>
        {/* Deployed equipment */}
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          <div style={{ padding: "12px 16px", borderBottom: "1px solid var(--hf-border-subtle)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)" }}>Currently Deployed</span>
            <button onClick={() => onNavigate("deployments")} style={{ background: "none", border: "none", cursor: "pointer", fontSize: 12, color: "var(--hf-info-text)", fontWeight: 600, display: "flex", alignItems: "center", gap: 3 }}>
              View all <ArrowRight size={12} />
            </button>
          </div>
          {assetsLoading ? (
            <div style={{ padding: 24, textAlign: "center", color: "var(--hf-text-faint)", fontSize: 13 }}>Loading...</div>
          ) : deployed.length === 0 ? (
            <div style={{ padding: 24, textAlign: "center", color: "var(--hf-text-faint)", fontSize: 13 }}>No equipment deployed right now</div>
          ) : (
            <div>
              {deployed.slice(0, 5).map((a, i) => (
                <div key={a.id} style={{ padding: "11px 16px", display: "flex", alignItems: "center", gap: 12, borderBottom: i < Math.min(deployed.length, 5) - 1 ? "1px solid var(--hf-border-subtle)" : "none" }}>
                  <div style={{ width: 32, height: 32, borderRadius: 8, background: "var(--hf-info-soft)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 16, flexShrink: 0 }}>
                    {EMOJI[a.assetType] ?? "🚧"}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>
                      {a.fleetNumber ? `${a.fleetNumber} — ` : ""}{a.name}
                    </div>
                    <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>
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
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          <div style={{ padding: "12px 16px", borderBottom: "1px solid var(--hf-border-subtle)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)" }}>Open Incidents</span>
            <button onClick={() => onNavigate("incidents")} style={{ background: "none", border: "none", cursor: "pointer", fontSize: 12, color: "var(--hf-info-text)", fontWeight: 600, display: "flex", alignItems: "center", gap: 3 }}>
              View all <ArrowRight size={12} />
            </button>
          </div>
          {openIncidents.length === 0 ? (
            <div style={{ padding: 24, textAlign: "center", color: "var(--hf-text-faint)", fontSize: 13 }}>
              <CheckCircle size={22} style={{ opacity: 0.4, marginBottom: 6 }} />
              <div>No open incidents</div>
            </div>
          ) : (
            <div>
              {openIncidents.map((inc, i) => {
                const sev = SEVERITY_CFG[inc.severity] ?? SEVERITY_CFG.LOW
                return (
                  <div key={inc.id} style={{ padding: "11px 16px", borderBottom: i < openIncidents.length - 1 ? "1px solid var(--hf-border-subtle)" : "none" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 2 }}>
                      <span style={{ width: 6, height: 6, borderRadius: "50%", background: sev.color, flexShrink: 0 }} />
                      <span style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{inc.title}</span>
                    </div>
                    <div style={{ fontSize: 11, color: "var(--hf-text-faint)", paddingLeft: 14 }}>{inc.severity} · {inc.type.replace("_", " ")}</div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>

      {/* Fleet value footer */}
      <div style={{ marginTop: 16, padding: "12px 16px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, display: "flex", alignItems: "center", gap: 8, fontSize: 12, color: "var(--hf-text-muted)" }}>
        <Clock size={13} />
        Combined daily rate across active fleet: <strong style={{ color: "var(--hf-text)" }}>{fmtCurrency(combinedDailyRate)}/day</strong>
      </div>
    </div>
  )
}
