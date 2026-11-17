// src/pages/fuel/FuelDashboard.tsx
//
// Fuel Operations Command Centre: headline numbers, tank levels, what needs attention, ongoing deliveries
// and quick actions. Tank levels come from the tanks list; the month and open-delivery figures come from the server's dashboard summary,
// which covers the whole month and every open delivery for the tanks the user may see.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Droplets, AlertTriangle, Fuel, Truck, TrendingDown, PackagePlus, Gauge, CalendarPlus, Activity, ClipboardList } from "lucide-react"
import { ActionTile, EmptyState, KpiGrid, KpiTile, Panel, PanelLink, ProgressBar, RingGauge, StatusPill, StepTracker, type Tone } from "./fuelUi"
import { asSummary, buildDashboard, DELIVERY_STEPS, deliveryStep, FUEL_TONE, fmtShortDate, fmtLitres } from "./dashboardOps"

export default function FuelDashboard({ onNavigate }: { onNavigate: (section: string) => void }) {
  const tanksQ = useQuery({ queryKey: ["tanks"], queryFn: async () => { const r = await apiClient.get("/api/v1/fuel/tanks"); return r.data?.data ?? r.data ?? [] } })
  const summaryQ = useQuery({ queryKey: ["fuel-dashboard-summary"], queryFn: async () => { const r = await apiClient.get("/api/v1/fuel/dashboard-summary"); return asSummary(r.data?.data ?? r.data) } })

  const d = buildDashboard({ tanks: Array.isArray(tanksQ.data) ? tanksQ.data : [], summary: summaryQ.data ?? null })
  const loading = tanksQ.isLoading || summaryQ.isLoading
  const failed = tanksQ.isError || summaryQ.isError || (summaryQ.isSuccess && summaryQ.data == null)

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      {failed && <p role="alert" style={{ margin: 0, padding: "10px 14px", borderRadius: 10, background: "var(--hf-warning-soft)", color: "var(--hf-warning-text-strong)", fontSize: 13 }}>Some figures could not be loaded, so the numbers below may be incomplete.</p>}

      <KpiGrid>
        <KpiTile label="Total stock" icon={Droplets} tone="blue" value={loading ? "…" : fmtLitres(d.totalStock)} hint={d.capacity > 0 ? `${d.stockPercent.toFixed(0)}% of ${fmtLitres(d.capacity)} capacity` : "No tanks yet"} onClick={() => onNavigate("tanks")} />
        <KpiTile label="Dispatched this month" icon={Fuel} tone="green" value={loading ? "…" : fmtLitres(d.monthLitres)} hint={`${d.monthDispatchCount} dispatch${d.monthDispatchCount === 1 ? "" : "es"}`} trend={d.dailyLitres} onClick={() => onNavigate("dispatches")} />
        <KpiTile label="Pending deliveries" icon={Truck} tone="violet" value={loading ? "…" : d.openDeliveries} hint={d.inTransit > 0 ? `${d.inTransit} in transit` : "None on the road"} onClick={() => onNavigate("deliveries")} />
        <KpiTile label="Low tanks" icon={AlertTriangle} tone={d.lowTanks.length > 0 ? "red" : "slate"} value={loading ? "…" : d.lowTanks.length} hint={d.lowTanks.length > 0 ? "Needs a stock receipt" : "All tanks above their threshold"} onClick={() => onNavigate("tanks")} />
      </KpiGrid>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(340px, 1fr))", gap: 18, alignItems: "start" }}>
        <div style={{ gridColumn: "span 2", minWidth: 0 }} className="hf-fuel-span">
          <Panel title="Tank levels" icon={Gauge} action={<PanelLink onClick={() => onNavigate("tanks")}>Manage tanks →</PanelLink>}>
            {d.tanks.length === 0 ? (
              <EmptyState icon={Droplets} title="No tanks registered" hint="Add a tank to start tracking stock." action={<button onClick={() => onNavigate("tanks")} style={{ padding: "8px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Add tank</button>} />
            ) : (
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(230px, 1fr))", gap: 14 }}>
                {d.tanks.map(t => {
                  const tone: Tone = t.low ? "red" : t.percent < 40 ? "amber" : FUEL_TONE[t.fuelType] ?? "blue"
                  return (
                    <div key={t.id} style={{ display: "flex", gap: 14, alignItems: "center", padding: 14, border: `1px solid ${t.low ? "var(--hf-danger-border)" : "var(--hf-border)"}`, borderRadius: 12, background: t.low ? "var(--hf-danger-soft)" : "var(--hf-surface)" }}>
                      <RingGauge percent={t.percent} size={84} stroke={9} tone={tone} />
                      <div style={{ minWidth: 0, flex: 1 }}>
                        <div style={{ fontWeight: 700, color: "var(--hf-text)", fontSize: 14, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{t.name}</div>
                        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 6 }}>{t.fuelType}{t.location ? ` · ${t.location}` : ""}</div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: t.low ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{fmtLitres(t.litres)}</div>
                        <div style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>of {fmtLitres(t.capacity)} · {fmtLitres(t.available)} free</div>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </Panel>
        </div>

        <Panel title="Alerts & exceptions" icon={AlertTriangle} pad={false}>
          {d.alerts.length === 0 ? <EmptyState icon={Activity} title="All clear" hint="No low tanks, variances or overdue deliveries." /> : (
            <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
              {d.alerts.map(a => (
                <li key={a.key} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <button type="button" onClick={() => onNavigate(a.section)} style={{ display: "flex", gap: 12, width: "100%", padding: "12px 16px", background: "none", border: "none", textAlign: "left", cursor: "pointer", font: "inherit", alignItems: "flex-start" }}>
                    <span aria-hidden="true" style={{ width: 4, alignSelf: "stretch", borderRadius: 4, background: a.severity === "high" ? "var(--hf-danger)" : "var(--hf-warning)" }} />
                    <span style={{ flex: 1, minWidth: 0 }}>
                      <span style={{ display: "flex", alignItems: "center", gap: 6, fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>
                        {a.key.startsWith("variance") && <TrendingDown size={14} aria-hidden="true" />}{a.title}
                      </span>
                      <span style={{ display: "block", fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>{a.detail}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(340px, 1fr))", gap: 18, alignItems: "start" }}>
        <div style={{ gridColumn: "span 2", minWidth: 0 }} className="hf-fuel-span">
          <Panel title="Ongoing deliveries" icon={Truck} pad={false} action={<PanelLink onClick={() => onNavigate("deliveries")}>View all →</PanelLink>}>
            {d.pendingDeliveries.length === 0 ? <EmptyState icon={Truck} title="No deliveries on the way" hint="Scheduled and in-transit deliveries show here." /> : (
              <div style={{ overflowX: "auto" }}>
                <table>
                  <thead><tr><th style={{ textAlign: "left", padding: "9px 16px" }}>Fuel</th><th style={{ textAlign: "right", padding: "9px 12px" }}>Ordered</th><th style={{ textAlign: "left", padding: "9px 12px" }}>Scheduled</th><th style={{ textAlign: "left", padding: "9px 16px" }}>Status</th></tr></thead>
                  <tbody>{d.pendingDeliveries.slice(0, 6).map((v: any) => (
                    <tr key={v.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                      <td style={{ padding: "10px 16px", fontSize: 13, color: "var(--hf-text)", fontWeight: 600 }}>{v.fuelType}</td>
                      <td style={{ padding: "10px 12px", fontSize: 13, textAlign: "right", color: "var(--hf-text)" }}>{fmtLitres(Number(v.litresOrdered ?? 0))}</td>
                      <td style={{ padding: "10px 12px", fontSize: 13, color: "var(--hf-text-muted)" }}>{fmtShortDate(v.scheduledAt)}</td>
                      <td style={{ padding: "10px 16px" }}><StatusPill status={v.status} /></td>
                    </tr>))}</tbody>
                </table>
              </div>
            )}
            {d.pendingDeliveries.length > 0 && (
              <div style={{ padding: "16px 20px", borderTop: "1px solid var(--hf-border)" }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-muted)", marginBottom: 10, textTransform: "uppercase", letterSpacing: "0.04em" }}>Next delivery · {d.pendingDeliveries[0].fuelType}</div>
                <StepTracker steps={DELIVERY_STEPS} current={deliveryStep(d.pendingDeliveries[0].status)} />
              </div>
            )}
          </Panel>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 18, minWidth: 0 }}>
          <Panel title="This month" icon={ClipboardList}>
            {[
              { label: "Dispatches", value: String(d.monthDispatchCount) },
              { label: "Litres out", value: fmtLitres(d.monthLitres) },
              { label: "Stock receipts", value: String(d.monthReceipts) },
            ].map((s, i) => (
              <div key={s.label} style={{ display: "flex", justifyContent: "space-between", padding: "9px 0", borderTop: i ? "1px solid var(--hf-border)" : "none", fontSize: 13 }}>
                <span style={{ color: "var(--hf-text-muted)" }}>{s.label}</span><b style={{ color: "var(--hf-text)" }}>{s.value}</b>
              </div>
            ))}
            {d.capacity > 0 && <div style={{ marginTop: 10 }}><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)", marginBottom: 6 }}>Stock vs capacity</div><ProgressBar percent={d.stockPercent} tone={d.stockPercent < 25 ? "red" : "blue"} /></div>}
          </Panel>
          <Panel title="Quick actions" icon={Activity}>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 10 }}>
              <ActionTile label="Receive stock" icon={PackagePlus} tone="green" onClick={() => onNavigate("tanks")} />
              <ActionTile label="Dispatch fuel" icon={Fuel} tone="blue" onClick={() => onNavigate("dispatches")} />
              <ActionTile label="Record dip" icon={Gauge} tone="amber" onClick={() => onNavigate("tanks")} />
              <ActionTile label="Schedule delivery" icon={CalendarPlus} tone="violet" onClick={() => onNavigate("deliveries")} />
            </div>
          </Panel>
        </div>
      </div>
    </div>
  )
}
