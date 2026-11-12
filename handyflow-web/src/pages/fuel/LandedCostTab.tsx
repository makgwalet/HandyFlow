// Phase 6c: landed cost per litre by route, tanker and depot. Read-only; shown to people who can see margin.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { card, inputStyle, Chip } from "../warehousing/whseUi"
import { fmtL, fmtR } from "./orderOps"
import { perLitre, flagText, leftOutNote, type LandedCostReport, type LandedRollup } from "./landedCostOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const th: React.CSSProperties = { textAlign: "right", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)", textAlign: "right", whiteSpace: "nowrap" }
const PERIODS = [[7, "7 days"], [30, "30 days"], [90, "90 days"]] as const
const iso = (d: Date) => d.toISOString().slice(0, 10)

function Rollups({ title, rows }: { title: string; rows: LandedRollup[] }) {
  if (rows.length === 0) return null
  return (
    <div style={{ ...card, overflowX: "auto", marginBottom: 14 }}>
      <div style={{ padding: "10px 12px", fontWeight: 700, fontSize: 13 }}>{title}</div>
      <table aria-label={title} style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead><tr><th style={{ ...th, textAlign: "left" }}>Name</th><th style={th}>Routes</th><th style={th}>Litres</th><th style={th}>Product</th><th style={th}>Transport</th><th style={th}>Landed per litre</th><th style={th}>Margin per litre</th><th style={th}>Margin</th></tr></thead>
        <tbody>{rows.map(r => (
          <tr key={r.key} style={{ borderTop: "1px solid var(--hf-border)" }}>
            <td style={{ ...td, textAlign: "left" }}>{r.label}</td><td style={td}>{r.routes}</td><td style={td}>{fmtL(r.litres)}</td>
            <td style={td}>{fmtR(r.productCost)}</td><td style={td}>{fmtR(r.transportCost)}</td><td style={td}>{perLitre(r.landedPerLitre)}</td><td style={td}>{perLitre(r.marginPerLitre)}</td><td style={td}>{fmtR(r.margin)}</td>
          </tr>))}</tbody>
      </table>
    </div>
  )
}

export default function LandedCostTab() {
  const [days, setDays] = useState(30)
  const to = iso(new Date()), from = iso(new Date(Date.now() - days * 86400000))
  const q = useQuery<LandedCostReport>({ queryKey: ["fuel-landed-cost", days], queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/landed-cost?from=${from}&to=${to}`)) })
  const d = q.data
  const note = d ? leftOutNote(d) : null
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12, flexWrap: "wrap" }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1, minWidth: 260 }}>
          What a litre really cost by the time it reached the customer: the cost of the fuel when it was sold (including inbound freight when it was entered at receipt), plus the vehicle, driver, toll and other costs recorded on the route. Only routes with every cost recorded are counted, so a missing cost is never read as zero. Depot figures share a route by litres delivered from each depot.
        </p>
        <select aria-label="Period" value={days} onChange={e => setDays(Number(e.target.value))} style={{ ...inputStyle, width: "auto" }}>
          {PERIODS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
      </div>
      {q.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : q.isError || !d ? <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load landed cost.</p> : (
        <>
          {note && <p role="status" style={{ fontSize: 12.5, color: "var(--hf-warning-text-strong)", margin: "0 0 10px" }}>{note}</p>}
          {d.routes.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No routes with deliveries in this period.</p> : (
            <>
              <div style={{ ...card, padding: "12px 14px", marginBottom: 14, display: "flex", gap: 24, flexWrap: "wrap" }}>
                <div><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Landed cost per litre</div><div style={{ fontSize: 20, fontWeight: 700 }}>{perLitre(d.total.landedPerLitre)}</div></div>
                <div><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Margin per litre</div><div style={{ fontSize: 20, fontWeight: 700 }}>{perLitre(d.total.marginPerLitre)}</div></div>
                <div><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Margin</div><div style={{ fontSize: 20, fontWeight: 700 }}>{fmtR(d.total.margin)}</div></div>
                <div><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Routes counted</div><div style={{ fontSize: 20, fontWeight: 700 }}>{d.total.routes}</div></div>
              </div>
              <Rollups title="By tanker" rows={d.byTanker} />
              <Rollups title="By depot" rows={d.byDepot} />
              <div style={{ ...card, overflowX: "auto" }}>
                <table aria-label="Routes" style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead><tr><th style={{ ...th, textAlign: "left" }}>Route</th><th style={th}>Litres</th><th style={th}>Product</th><th style={th}>Transport</th><th style={th}>Landed per litre</th><th style={th}>Margin per litre</th><th style={{ ...th, textAlign: "left" }}>Status</th></tr></thead>
                  <tbody>{d.routes.map(r => (
                    <tr key={r.routeId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                      <td style={{ ...td, textAlign: "left" }}>{r.routeDate} {r.label}{r.tankerRegistration ? ` · ${r.tankerRegistration}` : ""}</td>
                      <td style={td}>{fmtL(r.litres)}</td><td style={td}>{r.productCost == null ? "-" : fmtR(r.productCost)}</td><td style={td}>{fmtR(r.transportCost)}</td>
                      <td style={td}>{perLitre(r.landedPerLitre)}</td><td style={td}>{perLitre(r.marginPerLitre)}</td>
                      <td style={{ ...td, textAlign: "left", whiteSpace: "normal" }}>{r.complete ? <Chip bg="var(--hf-success-soft)" fg="var(--hf-success-text)">Complete</Chip> : r.flags.map(f => <div key={f}><Chip bg="var(--hf-warning-soft)" fg="var(--hf-warning-text)">{flagText(f)}</Chip></div>)}</td>
                    </tr>))}</tbody>
                </table>
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}
