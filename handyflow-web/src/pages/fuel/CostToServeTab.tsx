import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { card, inputStyle, Chip } from "../warehousing/whseUi"
import { fmtL, fmtR } from "./orderOps"
import { rowCaveats, losesMoneyToServe, type CostToServe } from "./costToServeOps"
import RouteCostsPanel from "./RouteCostsPanel"

const unwrap = (r: any) => r.data?.data ?? r.data
const th: React.CSSProperties = { textAlign: "right", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)", textAlign: "right", whiteSpace: "nowrap" }
const PERIODS = [[7, "7 days"], [30, "30 days"], [90, "90 days"]] as const

export default function CostToServeTab({ onNavigate }: { onNavigate?: (section: string) => void }) {
  const [days, setDays] = useState(30)
  const q = useQuery<CostToServe>({ queryKey: ["fuel-cost-to-serve", days], queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/dashboard/cost-to-serve?days=${days}`)) })
  const d = q.data
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12, flexWrap: "wrap" }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1, minWidth: 260 }}>
          Fuel margin less what it cost to get the fuel there: driver time, vehicle running cost, a fixed cost per delivery and an overhead share. These are estimates from the rates in Settings and the site distances on each customer, not GPS or fuel-burn readings.
        </p>
        <select aria-label="Period" value={days} onChange={e => setDays(Number(e.target.value))} style={{ ...inputStyle, width: "auto" }}>
          {PERIODS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
      </div>

      {q.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : q.isError || !d ? <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load cost-to-serve.</p> : (
        <>
          {!d.ratesConfigured && (
            <div role="status" style={{ ...card, padding: "10px 14px", marginBottom: 12, background: "var(--hf-warning-soft)", color: "var(--hf-warning-text-strong)", fontSize: 13 }}>
              No cost rates are set, so net margin below is the same as plain margin.{" "}
              {onNavigate && <button onClick={() => onNavigate("settings")} style={{ background: "none", border: "none", padding: 0, color: "inherit", fontWeight: 700, textDecoration: "underline", cursor: "pointer" }}>Set your rates</button>}
            </div>
          )}
          {d.deliveriesWithoutCost > 0 && (
            <p role="status" style={{ fontSize: 12.5, color: "var(--hf-warning-text-strong)", margin: "0 0 10px" }}>
              {d.deliveriesWithoutCost} {d.deliveriesWithoutCost === 1 ? "delivery has" : "deliveries have"} no cost at sale and {d.deliveriesWithoutCost === 1 ? "is" : "are"} left out of every figure below.
            </p>
          )}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 10, marginBottom: 14 }}>
            <Stat label="Revenue" value={fmtR(d.revenue)} />
            <Stat label="Margin" value={fmtR(d.margin)} />
            <Stat label="Cost to serve" value={fmtR(d.driverCost + d.vehicleCost + d.fixedCost + d.overheadCost)} sub={`Driver ${fmtR(d.driverCost)} · Vehicle ${fmtR(d.vehicleCost)}`} />
            <Stat label="Net margin" value={fmtR(d.netMargin)} tone={d.netMargin < 0 ? "bad" : undefined} />
          </div>
          {(d.deliveriesWithoutDistance > 0 || d.deliveriesWithEstimatedHours > 0) && (
            <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>
              {d.deliveriesWithoutDistance > 0 && <>{d.deliveriesWithoutDistance} {d.deliveriesWithoutDistance === 1 ? "delivery goes" : "deliveries go"} to a site with no distance, so vehicle cost is understated. Add distances under Customers. </>}
              {d.deliveriesWithEstimatedHours > 0 && <>{d.deliveriesWithEstimatedHours} used the typical trip hours because loading or delivery time was missing.</>}
            </p>
          )}
          {d.customers.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No costed deliveries in this period.</p> : (
            <div style={{ ...card, overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr>
                  <th style={{ ...th, textAlign: "left" }}>Customer</th><th style={th}>Deliveries</th><th style={th}>Litres</th><th style={th}>Margin</th>
                  <th style={th}>Driver</th><th style={th}>Vehicle</th><th style={th}>Fixed</th><th style={th}>Overhead</th><th style={th}>Net margin</th><th style={th}>Net / L</th>
                </tr></thead>
                <tbody>
                  {d.customers.map(r => {
                    const caveats = rowCaveats(r)
                    return (
                      <tr key={r.customerId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                        <td style={{ ...td, textAlign: "left", whiteSpace: "normal" }}>
                          <div style={{ fontWeight: 600 }}>{r.customerName} {losesMoneyToServe(r) && <Chip bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)">Loses money to serve</Chip>}</div>
                          {caveats.length > 0 && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{caveats.join("; ")}</div>}
                        </td>
                        <td style={td}>{r.deliveries}</td><td style={td}>{fmtL(r.litres)}</td><td style={td}>{fmtR(r.margin)}</td>
                        <td style={td}>{fmtR(r.driverCost)}</td><td style={td}>{fmtR(r.vehicleCost)}</td><td style={td}>{fmtR(r.fixedCost)}</td><td style={td}>{fmtR(r.overheadCost)}</td>
                        <td style={{ ...td, fontWeight: 700, color: r.netMargin < 0 ? "var(--hf-danger-text)" : undefined }}>{fmtR(r.netMargin)}</td>
                        <td style={td}>{r.netPerLitre == null ? "–" : fmtR(r.netPerLitre)}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
      <RouteCostsPanel days={days} />
    </div>
  )
}

function Stat({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: "bad" }) {
  return (
    <div style={{ ...card, padding: "12px 14px" }}>
      <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)" }}>{label}</div>
      <div style={{ fontSize: 20, fontWeight: 800, color: tone === "bad" ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{value}</div>
      {sub && <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 2 }}>{sub}</div>}
    </div>
  )
}
