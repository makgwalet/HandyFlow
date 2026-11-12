import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { card } from "../warehousing/whseUi"
import { fmtL, fmtR } from "./orderOps"
import { isFcReport, supplyCaveat, type FcReport } from "./forecourtSupplyOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const th: React.CSSProperties = { textAlign: "right", padding: "8px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "8px 12px", fontSize: 13, textAlign: "right", whiteSpace: "nowrap" }

/** Forecourt sales by supplying depot. Renders nothing when the Retail division is off or the server does not answer with a report. */
export default function ForecourtSupplySection({ days }: { days: number }) {
  const q = useQuery<FcReport | null>({
    queryKey: ["fuel-forecourt-supply", days], retry: false,
    queryFn: async () => { const d = unwrap(await apiClient.get(`/api/v1/fuel/forecourt/supply-report?days=${days}`)); return isFcReport(d) ? d : null },
  })
  const d = q.data
  if (!d) return null
  const money = d.moneyShown
  const caveat = supplyCaveat(d)
  return (
    <section style={{ marginTop: 18 }} aria-label="Forecourt sales by supplying depot">
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Forecourt sales by supplying depot</h3>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>Closed shifts, {d.from} to {d.to}. Each forecourt is the depot that holds its pump tanks.</p>
      {caveat && <p role="status" style={{ fontSize: 12.5, color: "var(--hf-warning-text-strong)", margin: "0 0 6px" }}>{caveat}</p>}
      {d.suppliers.length === 0 ? <p style={{ fontSize: 13 }}>No closed forecourt shifts in this period.</p> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={{ ...th, textAlign: "left" }}>Supplying depot / forecourt</th><th style={th}>Shifts</th><th style={th}>Litres sold</th>{money && <th style={th}>Sales</th>}</tr></thead>
            <tbody>
              {d.suppliers.map(s => [
                <tr key={`s-${s.supplierId ?? "none"}`} style={{ borderTop: "1px solid var(--hf-border)", fontWeight: 700 }}>
                  <td style={{ ...td, textAlign: "left" }}>{s.name}</td><td style={td}></td><td style={td}>{fmtL(s.litresSold)}</td>{money && <td style={td}>{fmtR(s.salesValue ?? 0)}</td>}
                </tr>,
                ...s.forecourts.map(f => (
                  <tr key={`f-${s.supplierId ?? "none"}-${f.depotId ?? "none"}`} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                    <td style={{ ...td, textAlign: "left", paddingLeft: 28 }}>{f.name}</td><td style={td}>{f.shifts}</td><td style={td}>{fmtL(f.litresSold)}</td>{money && <td style={td}>{fmtR(f.salesValue ?? 0)}</td>}
                  </tr>)),
              ])}
              <tr style={{ borderTop: "1px solid var(--hf-border)", fontWeight: 700 }}>
                <td style={{ ...td, textAlign: "left" }}>All forecourts</td><td style={td}></td><td style={td}>{fmtL(d.totalLitres)}</td>{money && <td style={td}>{fmtR(d.totalSales ?? 0)}</td>}
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
