// src/pages/earthmoving/AssetDetail.tsx
//
// One machine on one page: overview, maintenance, inspections, hire history and recent activity. It shows what the rest of the
// module already records; nothing here is edited except through the same dialogs the fleet list uses (the buttons on Overview).
import { useState } from "react"
import { ArrowLeft } from "lucide-react"
import type { Asset } from "./shared/types"
import { STATUS_CFG, OWN_TYPE_CFG, EMOJI, INSPECTION_TYPE_LABEL } from "./shared/constants"
import { fmtCurrency, fmtCurrency2dp, fmtDate, fmtDateTime, meterUnitOf } from "./shared/format"
import { useEarthPerms } from "./shared/useEarthPerms"
import { activityFeed } from "./shared/assetActivity"
import { remainingText } from "./shared/dashboard"
import { useMaintenanceHistory, usePlans, useInspections, useAssetDeployments, useHireCharges, useStatusHistory, useIncidents, useOperatorLogs } from "./shared/hooks"

const TABS = [["overview", "Overview"], ["maintenance", "Maintenance"], ["inspections", "Inspections"], ["hire", "Hire history"], ["activity", "Recent activity"]] as const
type TabId = typeof TABS[number][0]

const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px", background: "var(--hf-surface)" }
const outline: React.CSSProperties = { padding: "8px 14px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 13, fontWeight: 600, cursor: "pointer", color: "var(--hf-text-secondary)" }
const muted: React.CSSProperties = { fontSize: 12, color: "var(--hf-text-muted)" }
const RESULT: Record<string, string> = { PASS: "Passed", DEFECTS: "Passed with defects", UNSAFE: "Unsafe" }
const STATE: Record<string, string> = { OK: "In order", DUE_SOON: "Due soon", DUE: "Due now", OVERDUE: "Overdue" }

export interface AssetDetailActions { onBack: () => void; onEdit: () => void; onHours: () => void; onStatus: () => void; onMeter: () => void; onHistory: () => void }

export default function AssetDetail({ asset, actions }: { asset: Asset; actions: AssetDetailActions }) {
  const perms = useEarthPerms()
  const [tab, setTab] = useState<TabId>("overview")
  const unit = meterUnitOf(asset)
  const cfg = STATUS_CFG[asset.status] ?? STATUS_CFG.AVAILABLE
  const Empty = ({ children }: { children: string }) => <div style={{ ...card, textAlign: "center", color: "var(--hf-text-faint)" }}>{children}</div>
  const when = (iso: string | null) => (iso ? fmtDate(iso) : "—")

  const renderOverview = () => {
    const used = Number(asset.currentHours ?? 0) - Number(asset.lastServiceHours ?? 0)
    const interval = asset.serviceIntervalHours || 250
    const pct = Math.min(100, (used / interval) * 100)
    const mine = (plans.data ?? []).filter(p => p.assetId === asset.id && p.active && p.state !== "OK")
    return (
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 10 }}>
          {[
            [asset.meterType === "KILOMETRES" ? "Current kilometres" : "Current hours", `${Number(asset.currentHours ?? 0).toLocaleString()} ${unit}`],
            ["Last service", `${Number(asset.lastServiceHours ?? 0).toLocaleString()} ${unit}`],
            ["Serial number", asset.serialNumber || "—"], ["Daily rate", fmtCurrency(asset.dailyRate)],
            ["Current site", asset.currentSite || "—"], ["Current client", asset.currentClient || "—"],
          ].map(([l, v]) => (
            <div key={l} style={{ padding: "10px 14px", background: "var(--hf-surface-muted)", borderRadius: 8 }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 3 }}>{l}</div>
              <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>{v}</div>
            </div>
          ))}
        </div>
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", ...muted, marginBottom: 6 }}>
            <span>Service interval progress</span><span>{used.toFixed(0)} / {interval} {unit}</span>
          </div>
          <div style={{ height: 8, background: "var(--hf-surface-sunken)", borderRadius: 99, overflow: "hidden" }}>
            <div style={{ height: "100%", width: `${pct}%`, background: pct >= 100 ? "var(--hf-danger)" : pct >= 80 ? "var(--hf-warning)" : "var(--hf-accent)", borderRadius: 99 }} />
          </div>
        </div>
        {mine.length > 0 && (
          <div style={card} aria-label="Service plans needing attention">
            <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 6, color: "var(--hf-text)" }}>Service plans needing attention</div>
            {mine.map(p => <div key={p.id} style={{ fontSize: 13, color: p.state === "DUE_SOON" ? "var(--hf-warning-text)" : "var(--hf-danger-text)" }}>{p.name}: {STATE[p.state] ?? p.state} · {remainingText(p)}</div>)}
          </div>
        )}
        {asset.ownershipType === "HIRED_IN" && asset.hireSupplier && (
          <div style={{ ...card, background: "var(--hf-violet-soft)", borderColor: "var(--hf-violet-border)", color: "var(--hf-violet-text-strong)", fontSize: 13 }}>
            <strong>Hired from:</strong> {asset.hireSupplier}{asset.hireEndDate && ` · Hire ends ${fmtDate(asset.hireEndDate)}`}
          </div>
        )}
        {asset.notes && <div style={{ ...card, background: "var(--hf-warning-soft)", borderColor: "var(--hf-warning-border)", color: "var(--hf-warning-text-deep)", fontSize: 13 }}>{asset.notes}</div>}
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {perms.canWrite && <button style={outline} onClick={actions.onHours}>{asset.meterType === "KILOMETRES" ? "Update odometer" : "Update hours"}</button>}
          {perms.canWrite && <button style={outline} onClick={actions.onEdit}>Edit details</button>}
          {perms.canChangeStatus && <button style={outline} onClick={actions.onStatus}>Change status</button>}
          <button style={outline} onClick={actions.onMeter}>Meter</button>
          <button style={outline} onClick={actions.onHistory}>History</button>
        </div>
      </div>
    )
  }

  const plans = { data: usePlans().data }
  const maintenance = useMaintenanceHistory(tab === "maintenance" ? asset.id : "")
  const inspections = useInspections(tab === "inspections" || tab === "activity" ? asset.id : "__none__")
  const deployments = useAssetDeployments(tab === "hire" ? asset.id : "")
  const charges = useHireCharges("")
  const events = useStatusHistory(tab === "activity" ? asset.id : "")
  const incidents = useIncidents()
  const logs = useOperatorLogs(tab === "activity" ? asset.id : "")

  const renderMaintenance = () => {
    const mine = (plans.data ?? []).filter(p => p.assetId === asset.id && p.active)
    const records = maintenance.data ?? []
    return (
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div>
          <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 6, color: "var(--hf-text)" }}>Service plans</div>
          {mine.length === 0 ? <Empty>No service plans for this machine</Empty> : mine.map(p => (
            <div key={p.id} style={{ ...card, marginBottom: 6, display: "flex", justifyContent: "space-between", gap: 12 }}>
              <span style={{ fontSize: 13, color: "var(--hf-text)" }}>{p.name}</span>
              <span style={{ fontSize: 13, color: p.state === "OK" ? "var(--hf-text-secondary)" : "var(--hf-danger-text)" }}>{STATE[p.state] ?? p.state} · {remainingText(p)}</span>
            </div>))}
        </div>
        <div>
          <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 6, color: "var(--hf-text)" }}>Service history</div>
          {maintenance.isLoading ? <div style={muted}>Loading…</div> : records.length === 0 ? <Empty>No service records yet</Empty> : records.map(r => (
            <div key={r.id} style={{ ...card, marginBottom: 6 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 12, fontSize: 13, color: "var(--hf-text)" }}>
                <strong>{r.type.replace(/_/g, " ")}</strong><span>{r.cost != null ? fmtCurrency2dp(r.cost) : ""}</span>
              </div>
              <div style={muted}>{fmtDate(r.performedAt)}{r.hoursAtService != null ? ` · at ${r.hoursAtService} ${unit}` : ""}{r.supplier ? ` · ${r.supplier}` : ""}</div>
              <div style={{ fontSize: 13, color: "var(--hf-text-secondary)", marginTop: 2 }}>{r.description}</div>
            </div>))}
        </div>
      </div>
    )
  }

  const renderInspections = () => {
    const list = inspections.data ?? []
    if (inspections.isLoading) return <div style={muted}>Loading…</div>
    if (list.length === 0) return <Empty>No inspections yet</Empty>
    return <>{list.map(i => (
      <div key={i.id} style={{ ...card, marginBottom: 6 }}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 12, fontSize: 13 }}>
          <strong style={{ color: "var(--hf-text)" }}>{INSPECTION_TYPE_LABEL[i.inspectionType] ?? "Pre-use"}</strong>
          <span style={{ color: i.result === "UNSAFE" ? "var(--hf-danger-text)" : "var(--hf-text-secondary)", fontWeight: 600 }}>{RESULT[i.result] ?? i.result}</span>
        </div>
        <div style={muted}>{fmtDateTime(i.performedAt)}{i.meterReading != null ? ` · ${i.meterReading} ${unit}` : ""}</div>
        {i.items.filter(x => x.outcome !== "OK").map(x => <div key={x.item} style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>{x.item}: {x.comment}</div>)}
      </div>))}</>
  }

  const renderHire = () => {
    const deps = deployments.data ?? []
    const mine = (charges.data ?? []).filter(c => c.assetId === asset.id && c.status !== "VOIDED")
    return (
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div>
          <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 6, color: "var(--hf-text)" }}>Deployments</div>
          {deployments.isLoading ? <div style={muted}>Loading…</div> : deps.length === 0 ? <Empty>This machine has not been deployed yet</Empty> : deps.map(d => (
            <div key={d.id} style={{ ...card, marginBottom: 6, fontSize: 13 }}>
              <strong style={{ color: "var(--hf-text)" }}>{d.siteName ?? "Unknown site"}</strong>{d.clientName ? ` · ${d.clientName}` : ""}
              <div style={muted}>{when(d.deployedAt)} to {d.returnedAt ? fmtDate(d.returnedAt) : "now (still on site)"}</div>
            </div>))}
        </div>
        <div>
          <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 6, color: "var(--hf-text)" }}>Hire charges</div>
          {mine.length === 0 ? <Empty>No hire charges for this machine</Empty> : mine.map((c, i) => (
            <div key={c.id ?? i} style={{ ...card, marginBottom: 6, display: "flex", justifyContent: "space-between", gap: 12, fontSize: 13 }}>
              <span style={{ color: "var(--hf-text)" }}>{c.customerName ?? "No customer"} · {fmtDate(c.periodFrom)} to {fmtDate(c.periodTo)}</span>
              <span style={{ color: "var(--hf-text-secondary)" }}>{fmtCurrency2dp(c.amount)} · {c.status === "INVOICED" ? `Invoiced ${c.invoiceNumber ?? ""}` : "Not invoiced"}</span>
            </div>))}
        </div>
      </div>
    )
  }

  const renderActivity = () => {
    const feed = activityFeed({ events: events.data ?? [], incidents: (incidents.data ?? []).filter(i => i.assetId === asset.id), logs: logs.data ?? [], inspections: inspections.data ?? [] })
    if (events.isLoading || logs.isLoading) return <div style={muted}>Loading…</div>
    if (feed.length === 0) return <Empty>Nothing recorded yet</Empty>
    return <>{feed.map(a => (
      <div key={a.key} style={{ display: "flex", gap: 12, padding: "8px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
        <span style={{ ...muted, minWidth: 120 }}>{fmtDateTime(a.at)}</span><span style={{ color: "var(--hf-text)" }}>{a.text}</span>
      </div>))}</>
  }

  return (
    <div aria-label="Machine detail">
      <button style={{ ...outline, marginBottom: 12, display: "inline-flex", alignItems: "center", gap: 6 }} onClick={actions.onBack}><ArrowLeft size={14} />Back to fleet</button>
      <div style={{ background: "linear-gradient(135deg, var(--hf-primary) 0%, var(--hf-primary-hover) 100%)", padding: "20px 24px", borderRadius: 14, marginBottom: 14 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
          <div style={{ width: 52, height: 52, borderRadius: 14, background: "rgba(255,255,255,0.15)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 26 }}>{EMOJI[asset.assetType] ?? "🚧"}</div>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
              {asset.fleetNumber && <span style={{ background: "var(--hf-warning)", color: "var(--hf-text-on-solid)", padding: "2px 10px", borderRadius: 6, fontSize: 13, fontWeight: 800 }}>{asset.fleetNumber}</span>}
              <h3 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: "var(--hf-text-on-solid)" }}>{asset.name}</h3>
            </div>
            <div style={{ fontSize: 13, color: "rgba(255,255,255,0.75)" }}>{[asset.make, asset.model, asset.year].filter(Boolean).join(" · ")}{asset.registration && ` · ${asset.registration}`}</div>
          </div>
        </div>
        <div style={{ display: "flex", gap: 8, marginTop: 12, flexWrap: "wrap" }}>
          <span style={{ background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: `1px solid ${cfg.border}` }}>{cfg.label}</span>
          {asset.ownershipType !== "OWN" && <span style={{ background: "rgba(255,255,255,0.2)", color: "var(--hf-text-on-solid)", padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{OWN_TYPE_CFG[asset.ownershipType]?.label}</span>}
          {asset.dueForService && <span style={{ background: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text)", padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: "1px solid var(--hf-warning-border)" }}>⚠ Service Due</span>}
        </div>
      </div>
      <div role="tablist" aria-label="Machine sections" style={{ display: "flex", gap: 4, borderBottom: "1px solid var(--hf-border)", marginBottom: 14, flexWrap: "wrap" }}>
        {TABS.map(([id, label]) => (
          <button key={id} role="tab" aria-selected={tab === id} onClick={() => setTab(id)}
            style={{ padding: "8px 14px", background: "none", border: "none", borderBottom: tab === id ? "2px solid var(--hf-primary)" : "2px solid transparent", fontSize: 13, fontWeight: tab === id ? 700 : 500, color: tab === id ? "var(--hf-text)" : "var(--hf-text-muted)", cursor: "pointer" }}>{label}</button>
        ))}
      </div>
      <div role="tabpanel">
        {tab === "overview" && renderOverview()}
        {tab === "maintenance" && renderMaintenance()}
        {tab === "inspections" && renderInspections()}
        {tab === "hire" && renderHire()}
        {tab === "activity" && renderActivity()}
      </div>
    </div>
  )
}
