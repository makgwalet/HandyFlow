// Stage 4D: the manager's first screen. Margin by customer is shown only to people with the margin permission, and is labelled as margin, not cost-to-serve.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost } from "../warehousing/whseUi"
import { CheckCircle2, ClipboardCheck, Layers, TrendingUp } from "lucide-react"
import { EmptyState, KpiGrid, KpiTile, Panel, ProgressBar } from "./fuelUi"
import { fmtL, fmtR } from "./orderOps"
import { attention, pct, type Overview, type Margin } from "./overviewOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const th: React.CSSProperties = { textAlign: "left", padding: "8px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)" }
const td: React.CSSProperties = { padding: "8px 12px", fontSize: 13, color: "var(--hf-text)" }

export default function OverviewTab({ onNavigate }: { onNavigate: (section: string) => void }) {
  const canMargin = usePermissions(["FUEL_MARGIN_READ"])
  const [days, setDays] = useState(30)
  const ov = useQuery<Overview>({ queryKey: ["fuel-overview"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/dashboard")) })
  const mg = useQuery<Margin>({ queryKey: ["fuel-margin-customers", days], enabled: canMargin, queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/dashboard/margin", { params: { days } })) })

  if (ov.isLoading) return <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>
  if (ov.isError || !ov.data) return <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load the overview.</p>
  const o = ov.data
  const todo = attention(o)
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Panel title="Needs attention" icon={ClipboardCheck}>
        {todo.length === 0 ? (
          <p style={{ margin: 0, fontSize: 13, color: "var(--hf-success-text-strong)", display: "flex", alignItems: "center", gap: 8 }}><CheckCircle2 size={16} aria-hidden="true" />Nothing needs attention right now.</p>
        ) : (
          <ul style={{ margin: 0, padding: 0, listStyle: "none", display: "flex", flexDirection: "column", gap: 8 }}>{todo.map(a => (
            <li key={a.key} style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 13, padding: "8px 12px", borderRadius: 10, background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", color: "var(--hf-warning-text-deep)" }}>
              <span style={{ flex: 1 }}>{a.text}</span>
              <button style={{ ...btnGhost, padding: "3px 10px", fontSize: 11.5, background: "var(--hf-surface)" }} onClick={() => onNavigate(a.section)}>Open</button>
            </li>))}
          </ul>
        )}
      </Panel>

      <KpiGrid min={200}>
        <KpiTile label="Open orders" tone="blue" value={o.confirmedOrders + o.partDeliveredOrders} hint={`${o.quotes} quotation${o.quotes === 1 ? "" : "s"} waiting`} onClick={() => onNavigate("orders")} />
        <KpiTile label="Delivered, not invoiced" tone="amber" value={fmtR(o.deliveredNotInvoicedValue)} hint={`${o.deliveredNotInvoicedCount} deliver${o.deliveredNotInvoicedCount === 1 ? "y" : "ies"}`} onClick={() => onNavigate("invoicing")} />
        <KpiTile label="Loads today" tone="violet" value={o.loadsToday} hint={`${o.loadsTodayUnallocated} without tanker or driver`} onClick={() => onNavigate("planning")} />
        <KpiTile label="On credit hold" tone={o.customersOnCreditHold > 0 ? "red" : "slate"} value={o.customersOnCreditHold} hint="customers" onClick={() => onNavigate("customers")} />
        <KpiTile label="Compliance" tone={o.complianceExpired > 0 ? "red" : o.complianceExpiring > 0 ? "amber" : "green"} value={o.complianceExpired + o.complianceExpiring} hint={`${o.complianceExpired} expired, ${o.complianceExpiring} expiring`} onClick={() => onNavigate("compliance")} />
        <KpiTile label="Supplier orders" tone="green" value={o.purchaseOrdersAwaitingDelivery} hint="awaiting delivery" onClick={() => onNavigate("purchase-orders")} />
      </KpiGrid>

      <Panel title="Stock by fuel" icon={Layers} pad={false}>
        {o.stock.length === 0 ? <EmptyState title="No tanks yet" /> : (
          <div style={{ overflowX: "auto" }}>
            <table>
              <thead><tr><th style={th}>Fuel</th><th style={th}>On hand</th><th style={th}>Reserved</th><th style={th}>Available</th><th style={th}>Capacity</th><th style={{ ...th, minWidth: 120 }}>Filled</th></tr></thead>
              <tbody>{o.stock.map(s => (
                <tr key={s.fuelType} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={{ ...td, fontWeight: 700 }}>{s.fuelType}</td><td style={td}>{fmtL(s.litresOnHand)}</td><td style={td}>{fmtL(s.litresReserved)}</td><td style={td}>{fmtL(s.litresAvailable)}</td><td style={td}>{fmtL(s.capacityLitres)}</td>
                  <td style={td}><ProgressBar percent={s.capacityLitres > 0 ? (s.litresOnHand / s.capacityLitres) * 100 : 0} tone={s.capacityLitres > 0 && s.litresOnHand / s.capacityLitres < 0.25 ? "red" : "blue"} /></td>
                </tr>))}</tbody>
            </table>
          </div>
        )}
      </Panel>

      {canMargin && (
        <Panel title="Delivery margin by customer" icon={TrendingUp} pad={false} action={
          <select aria-label="Period" value={days} onChange={e => setDays(Number(e.target.value))} style={{ padding: "3px 8px", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12 }}>
            <option value={30}>Last 30 days</option><option value={90}>Last 90 days</option><option value={365}>Last 12 months</option>
          </select>}>
          <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "6px 14px" }}>Revenue less the fuel's cost at the time of sale. This is margin, not full cost-to-serve: driver time, vehicle running costs and overheads are not included, so real profit is lower.</p>
          {mg.isLoading ? <p style={{ padding: "0 14px 12px", color: "var(--hf-text-muted)" }}>Loading…</p> : !mg.data || mg.data.customers.length === 0 ? <p style={{ padding: "0 14px 12px", color: "var(--hf-text-muted)", fontSize: 13 }}>No deliveries in this period.</p> : (
            <>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>Customer</th><th style={th}>Deliveries</th><th style={th}>Litres</th><th style={th}>Revenue</th><th style={th}>Margin</th><th style={th}>Margin %</th><th style={th}>Per litre</th></tr></thead>
                <tbody>{mg.data.customers.map(c => (
                  <tr key={c.customerId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={td}>{c.customerName}</td><td style={td}>{c.deliveries}</td><td style={td}>{fmtL(c.litres)}</td><td style={td}>{fmtR(c.revenue)}</td>
                    <td style={td}>{fmtR(c.margin)}</td><td style={td}>{pct(c.margin, c.revenue)}</td><td style={td}>{c.marginPerLitre == null ? "–" : fmtR(c.marginPerLitre)}</td>
                  </tr>))}</tbody>
              </table>
              <p style={{ fontSize: 12, margin: "8px 14px 12px", color: "var(--hf-text-muted)" }}>
                Total revenue {fmtR(mg.data.revenue)}, margin {fmtR(mg.data.margin)}.
                {mg.data.deliveriesWithoutCost > 0 && ` ${mg.data.deliveriesWithoutCost} deliver${mg.data.deliveriesWithoutCost === 1 ? "y has" : "ies have"} no recorded fuel cost, so their revenue is counted but no margin is shown for them.`}
              </p>
            </>
          )}
        </Panel>
      )}
    </div>
  )
}
