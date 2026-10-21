// src/pages/fleet/FleetFuelLog.tsx
// Every fill-up across the fleet in one searchable, paged table, with totals for whatever the filters match.
import { useEffect, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { SearchBox } from "./ListControls"
import { unwrap } from "./driverScores.logic"
import { FUEL_LOG_PAGE_SIZE, defaultPeriod, litresText, logUrl, periodProblem, priceText, randText, summaryUrl, type FuelLogRow, type FuelLogTotals } from "./fuelLog.logic"

interface LogPage { content: FuelLogRow[]; totalElements: number; totalPages: number }
const th: React.CSSProperties = { padding: "10px 14px", textAlign: "left", fontWeight: 700, fontSize: 11, color: "var(--hf-text-muted)", letterSpacing: "0.05em", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 14px", fontSize: 13.5 }
const date: React.CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" }
const step: React.CSSProperties = { padding: "5px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)" }
const fmtDate = (d: string) => new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })

export default function FleetFuelLog() {
  const [period, setPeriod] = useState(defaultPeriod), [text, setText] = useState(""), [q, setQ] = useState(""), [page, setPage] = useState(0)
  // Search waits for a short pause in typing so it does not ask the server on every key.
  useEffect(() => { const t = setTimeout(() => { setQ(text); setPage(0) }, 300); return () => clearTimeout(t) }, [text])
  const problem = periodProblem(period.from, period.to), filter = { ...period, q }
  const rows = useQuery<LogPage>({ queryKey: ["fleet", "fuel-log", "rows", filter, page], enabled: !problem, queryFn: async () => unwrap<LogPage>(await apiClient.get(logUrl(filter, page))) })
  const totals = useQuery<FuelLogTotals>({ queryKey: ["fleet", "fuel-log", "totals", filter], enabled: !problem, queryFn: async () => unwrap<FuelLogTotals>(await apiClient.get(summaryUrl(filter))) })
  const list = rows.data?.content ?? [], total = rows.data?.totalElements ?? 0, pages = Math.max(1, rows.data?.totalPages ?? 1)
  const from = total === 0 ? 0 : page * FUEL_LOG_PAGE_SIZE + 1, to = Math.min(total, (page + 1) * FUEL_LOG_PAGE_SIZE)
  const t = totals.data
  const tiles: [string, string][] = t ? [["Fill-ups", String(t.fillups)], ["Litres", litresText(t.litres)], ["Spend", randText(t.spend)], ["Average price", t.fillups === 0 ? "—" : `${priceText(t.avgPricePerLitre)} / L`]] : []
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <SearchBox value={text} onChange={setText} label="Search fuel log" placeholder="Search registration, station or receipt" />
        <label style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>From <input aria-label="From date" type="date" style={date} value={period.from} onChange={e => { setPeriod(p => ({ ...p, from: e.target.value })); setPage(0) }} /></label>
        <label style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>To <input aria-label="To date" type="date" style={date} value={period.to} onChange={e => { setPeriod(p => ({ ...p, to: e.target.value })); setPage(0) }} /></label>
      </div>
      {problem && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>{problem}</p>}
      {tiles.length > 0 && <div aria-label="Fuel totals" style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 10, marginBottom: 14 }}>
        {tiles.map(([label, value]) => <div key={label} aria-label={label} style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 14px" }}><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{label}</div><div style={{ fontSize: 18, fontWeight: 700 }}>{value}</div></div>)}
      </div>}
      {rows.isError && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load the fuel log. <button type="button" onClick={() => rows.refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>}
      {rows.isLoading && !problem && <p style={{ fontSize: 13 }}>Loading…</p>}
      {rows.data && list.length === 0 && <p role="status" style={{ fontSize: 13, color: "var(--hf-text-muted)", border: "1px dashed var(--hf-border)", borderRadius: 12, padding: 16 }}>{q ? "No fill-ups match." : "No fill-ups in this period."}</p>}
      {list.length > 0 && <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr style={{ background: "var(--hf-surface-muted)", borderBottom: "1px solid var(--hf-border)" }}>{["Date", "Vehicle", "Odometer", "Litres", "Price/L", "Total", "Station", "Receipt", "Tank"].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
          <tbody>{list.map(r => (
            <tr key={r.id} aria-label={`${r.registration} ${r.filledAt}`} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
              <td style={td}>{fmtDate(r.filledAt)}</td><td style={{ ...td, fontWeight: 700 }}>{r.registration}</td>
              <td style={td}>{r.odometerAtFillup ? `${Number(r.odometerAtFillup).toLocaleString("en-ZA")} km` : "—"}</td>
              <td style={td}>{litresText(r.litres)}</td><td style={td}>{priceText(r.pricePerLitre)}</td><td style={{ ...td, fontWeight: 700 }}>{randText(r.totalCost)}</td>
              <td style={td}>{r.station || "—"}</td><td style={{ ...td, color: "var(--hf-text-faint)" }}>{r.receiptRef || "—"}</td>
              <td style={td}>{r.fullTank ? "Full" : <span style={{ color: "var(--hf-text-faint)" }}>Partial</span>}</td>
            </tr>))}</tbody>
        </table>
      </div>}
      {total > 0 && <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 10, fontSize: 12.5, color: "var(--hf-text-muted)", flexWrap: "wrap", gap: 8 }}>
        <span aria-label="Showing">Showing {from}–{to} of {total}</span>
        {pages > 1 && <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)} style={step}>Previous</button>
          <span>Page {page + 1} of {pages}</span>
          <button type="button" disabled={page >= pages - 1} onClick={() => setPage(page + 1)} style={step}>Next</button>
        </div>}
      </div>}
    </div>)
}
