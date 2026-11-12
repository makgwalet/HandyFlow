// Phase 6a: read-only report across the group's legal entities. Money columns appear only when the server sends money.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { card, inputStyle, Chip } from "../warehousing/whseUi"
import { fmtL, fmtR } from "./orderOps"
import ForecourtSupplySection from "./ForecourtSupplySection"
import { changeText, caveats, type GroupReport, type GroupRow } from "./groupReportOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const th: React.CSSProperties = { textAlign: "right", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)", textAlign: "right", whiteSpace: "nowrap" }
const PERIODS = [[7, "7 days"], [30, "30 days"], [90, "90 days"]] as const

function Delta({ now, before }: { now: number | null; before: number | null }) {
  const t = changeText(now, before)
  return t ? <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{t}</div> : null
}

export default function GroupReportTab() {
  const [days, setDays] = useState(30)
  const q = useQuery<GroupReport>({ queryKey: ["fuel-group-report", days], queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/group-report?days=${days}`)) })
  const d = q.data
  const money = !!d?.moneyShown
  const line = (r: GroupRow, bold = false) => (
    <tr key={r.legalEntityId ?? r.name} style={{ borderTop: "1px solid var(--hf-border)", fontWeight: bold ? 700 : undefined }}>
      <td style={{ ...td, textAlign: "left" }}>{r.name}{r.unassigned && <> <Chip bg="var(--hf-warning-soft)" fg="var(--hf-warning-text)">Needs a legal entity</Chip></>}</td>
      <td style={td}>{r.deliveries}</td>
      <td style={td}>{fmtL(r.litres)}<Delta now={r.litres} before={r.previousLitres} /></td>
      {money && <td style={td}>{fmtR(r.revenue)}<Delta now={r.revenue} before={r.previousRevenue} /></td>}
      {money && <td style={td}>{fmtR(r.margin)}<Delta now={r.margin} before={r.previousMargin} /></td>}
      <td style={td}>{fmtL(r.stockLitres)}</td>
      {money && <td style={td}>{fmtR(r.stockValue)}</td>}
      <td style={td}>{r.openVariances > 0 ? <Chip bg="var(--hf-warning-soft)" fg="var(--hf-warning-text)">{r.openVariances}</Chip> : 0}</td>
    </tr>
  )
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12, flexWrap: "wrap" }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1, minWidth: 260 }}>
          Every legal entity side by side: fuel delivered, stock on hand and variances still open. Each figure sits with the entity that owns the depot the tank is in. Forecourt sales are shown separately below, grouped by the depot that supplies each forecourt. This page only reports; it changes nothing.
        </p>
        <select aria-label="Period" value={days} onChange={e => setDays(Number(e.target.value))} style={{ ...inputStyle, width: "auto" }}>
          {PERIODS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
      </div>
      {q.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : q.isError || !d ? <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load the group report.</p> : (
        <>
          <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>{d.from} to {d.to}, compared with {d.previousFrom} to {d.previousTo}. Stock and open variances are as at now.</p>
          {!money && <p role="status" style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>Revenue, margin and stock value are shown to people who can see margin.</p>}
          {caveats(d).map(c => <p key={c} role="status" style={{ fontSize: 12.5, color: "var(--hf-warning-text-strong)", margin: "0 0 6px" }}>{c}</p>)}
          <div style={{ ...card, overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr>
                <th style={{ ...th, textAlign: "left" }}>Legal entity</th><th style={th}>Deliveries</th><th style={th}>Litres</th>
                {money && <th style={th}>Revenue</th>}{money && <th style={th}>Margin</th>}
                <th style={th}>Stock</th>{money && <th style={th}>Stock value</th>}<th style={th}>Open variances</th>
              </tr></thead>
              <tbody>{d.entities.map(r => line(r))}{line(d.total, true)}</tbody>
            </table>
          </div>
          <ForecourtSupplySection days={days} />
        </>
      )}
    </div>
  )
}
