import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { card } from "../warehousing/whseUi"
import { fmtL } from "./orderOps"
import { isFcRecon, reconNotes, type FcRecon } from "./forecourtSupplyOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const th: React.CSSProperties = { textAlign: "right", padding: "8px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "8px 12px", fontSize: 13, textAlign: "right", whiteSpace: "nowrap" }

/** Where each forecourt's fuel came from. Renders nothing when the Retail division is off or the server does not answer with a reconciliation. */
export default function ForecourtReconciliationSection({ days }: { days: number }) {
  const q = useQuery<FcRecon | null>({
    queryKey: ["fuel-forecourt-recon", days], retry: false,
    queryFn: async () => { const d = unwrap(await apiClient.get(`/api/v1/fuel/forecourt/supply-report/reconciliation?days=${days}`)); return isFcRecon(d) ? d : null },
  })
  const d = q.data
  if (!d) return null
  const notes = reconNotes(d)
  return (
    <section style={{ marginTop: 18 }} aria-label="Forecourt supply reconciliation">
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Forecourt supply: where the fuel came from</h3>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>{d.from} to {d.to}. Transfers and supplier receipts into forecourt tanks, against litres sold on closed shifts. Moves between tanks of the same depot are left out.</p>
      {notes.map(n => <p key={n} role="status" style={{ fontSize: 12.5, color: "var(--hf-warning-text-strong)", margin: "0 0 4px" }}>{n}</p>)}
      {d.forecourts.length === 0 ? <p style={{ fontSize: 13 }}>No forecourts with active pumps.</p> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={{ ...th, textAlign: "left" }}>Forecourt</th><th style={{ ...th, textAlign: "left" }}>Supplying depot</th><th style={th}>From supplying depot</th><th style={th}>From other depots</th><th style={th}>From suppliers</th><th style={th}>Total in</th><th style={th}>Share from supplying depot</th><th style={th}>Litres sold</th></tr></thead>
            <tbody>
              {d.forecourts.map(f => (
                <tr key={f.depotId} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <td style={{ ...td, textAlign: "left" }}>{f.name}</td><td style={{ ...td, textAlign: "left" }}>{f.supplierName ?? "Not set"}</td>
                  <td style={td}>{fmtL(f.fromSupplierDepot)}</td><td style={td}>{fmtL(f.fromOtherDepots)}</td><td style={td}>{fmtL(f.fromSuppliers)}</td>
                  <td style={td}>{fmtL(f.totalIn)}</td><td style={td}>{f.supplierDepotSharePct == null ? "–" : `${f.supplierDepotSharePct}%`}</td><td style={td}>{fmtL(f.litresSold)}</td>
                </tr>))}
              <tr style={{ borderTop: "1px solid var(--hf-border)", fontWeight: 700 }}>
                <td style={{ ...td, textAlign: "left" }}>All forecourts</td><td style={td}></td><td style={td}></td><td style={td}></td><td style={td}></td><td style={td}>{fmtL(d.totalIn)}</td><td style={td}></td><td style={td}>{fmtL(d.totalSold)}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
