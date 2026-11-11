// Stage 4D: the manager's first screen. Margin by customer is shown only to people with the margin permission, and is labelled as margin, not cost-to-serve.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, card } from "../warehousing/whseUi"
import { fmtL, fmtR } from "./orderOps"
import { attention, pct, type Overview, type Margin } from "./overviewOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const th: React.CSSProperties = { textAlign: "left", padding: "8px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)" }
const td: React.CSSProperties = { padding: "8px 12px", fontSize: 13, color: "var(--hf-text)" }

function Tile({ label, value, sub, onClick }: { label: string; value: React.ReactNode; sub?: string; onClick?: () => void }) {
  return (
    <button onClick={onClick} style={{ ...card, padding: "14px 16px", textAlign: "left", cursor: onClick ? "pointer" : "default", background: "var(--hf-surface)" }}>
      <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)", fontWeight: 600 }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 800, color: "var(--hf-text)", marginTop: 4 }}>{value}</div>
      {sub && <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)", marginTop: 2 }}>{sub}</div>}
    </button>
  )
}

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
      <div style={{ ...card, padding: 14 }}>
        <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 6 }}>Needs attention</div>
        {todo.length === 0 ? <p style={{ margin: 0, fontSize: 13, color: "var(--hf-success-text-strong)" }}>Nothing needs attention right now.</p> : (
          <ul style={{ margin: 0, paddingLeft: 18 }}>{todo.map(a => <li key={a.key} style={{ fontSize: 13, marginBottom: 3 }}>{a.text} <button style={{ ...btnGhost, padding: "2px 8px", fontSize: 11.5 }} onClick={() => onNavigate(a.section)}>Open</button></li>)}</ul>
        )}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 12 }}>
        <Tile label="Open orders" value={o.confirmedOrders + o.partDeliveredOrders} sub={`${o.quotes} quotation${o.quotes === 1 ? "" : "s"} waiting`} onClick={() => onNavigate("orders")} />
        <Tile label="Delivered, not invoiced" value={fmtR(o.deliveredNotInvoicedValue)} sub={`${o.deliveredNotInvoicedCount} deliver${o.deliveredNotInvoicedCount === 1 ? "y" : "ies"}`} onClick={() => onNavigate("invoicing")} />
        <Tile label="Loads today" value={o.loadsToday} sub={`${o.loadsTodayUnallocated} without tanker or driver`} onClick={() => onNavigate("planning")} />
        <Tile label="On credit hold" value={o.customersOnCreditHold} sub="customers" onClick={() => onNavigate("customers")} />
        <Tile label="Compliance" value={o.complianceExpired + o.complianceExpiring} sub={`${o.complianceExpired} expired, ${o.complianceExpiring} expiring`} onClick={() => onNavigate("compliance")} />
        <Tile label="Supplier orders" value={o.purchaseOrdersAwaitingDelivery} sub="awaiting delivery" onClick={() => onNavigate("purchase-orders")} />
      </div>

      <div style={{ ...card, overflowX: "auto" }}>
        <div style={{ padding: "12px 14px 0", fontWeight: 700, fontSize: 13 }}>Stock by fuel</div>
        {o.stock.length === 0 ? <p style={{ padding: "0 14px 12px", color: "var(--hf-text-muted)", fontSize: 13 }}>No tanks yet.</p> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Fuel</th><th style={th}>On hand</th><th style={th}>Reserved</th><th style={th}>Available</th><th style={th}>Capacity</th></tr></thead>
            <tbody>{o.stock.map(s => <tr key={s.fuelType} style={{ borderTop: "1px solid var(--hf-border)" }}><td style={td}>{s.fuelType}</td><td style={td}>{fmtL(s.litresOnHand)}</td><td style={td}>{fmtL(s.litresReserved)}</td><td style={td}>{fmtL(s.litresAvailable)}</td><td style={td}>{fmtL(s.capacityLitres)}</td></tr>)}</tbody>
          </table>
        )}
      </div>

      {canMargin && (
        <div style={{ ...card, overflowX: "auto" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "12px 14px 0" }}>
            <div style={{ fontWeight: 700, fontSize: 13, flex: 1 }}>Delivery margin by customer</div>
            <select aria-label="Period" value={days} onChange={e => setDays(Number(e.target.value))} style={{ padding: "4px 8px", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)" }}>
              <option value={30}>Last 30 days</option><option value={90}>Last 90 days</option><option value={365}>Last 12 months</option>
            </select>
          </div>
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
        </div>
      )}
    </div>
  )
}
