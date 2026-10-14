// src/pages/accounting/ProfitabilityTab.tsx
//
// Finance F28: which sites, vehicles, people, projects and branches make money. Built on cost centres: only lines carrying a
// cost centre are attributed; the rest show as Unallocated, so a large Unallocated row means the tagging rules need work.
// Costs are kept by type so you decide which types count as direct (for a guarding company, wages are; for a shop they are not).
import { useEffect, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { KINDS } from "./costCentre.logic"
import { cell, lossMakers, marginText, money, monthLabel, split, subtypeLabel, yearStart, type Row, type Trend, type View } from "./profitability.logic"

const unwrap = (r: any) => r.data?.data ?? r.data
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14, overflowX: "auto" }
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)" }
const num: React.CSSProperties = { textAlign: "right", whiteSpace: "nowrap", padding: "3px 8px" }
const DEFAULT_DIRECT = ["COS", "PAYROLL"]

export default function ProfitabilityTab() {
  const today = new Date()
  const [from, setFrom] = useState(yearStart(today))
  const [to, setTo] = useState(today.toISOString().slice(0, 10))
  const [kind, setKind] = useState("SITE")
  const [rollUp, setRollUp] = useState(false)
  const [picked, setPicked] = useState<Set<string> | null>(null)
  const [open, setOpen] = useState<Row | null>(null)

  const { data, isLoading } = useQuery<View>({
    queryKey: ["acc-profitability", from, to, kind, rollUp], enabled: !!from && !!to && to >= from,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accounting/profitability?from=${from}&to=${to}&rollUp=${rollUp}${kind ? `&kind=${kind}` : ""}`)),
  })
  // First time the cost types are known, the usual direct ones are ticked.
  useEffect(() => { if (data && picked === null) setPicked(new Set(DEFAULT_DIRECT.filter(s => data.subtypes.includes(s)))) }, [data, picked])
  const direct = picked ?? new Set<string>()
  const toggle = (s: string) => setPicked(p => { const n = new Set(p ?? []); n.has(s) ? n.delete(s) : n.add(s); return n })

  const losers = data ? lossMakers(data.rows) : []
  const unallocShare = data && data.total.income + data.total.expenseTotal > 0
    ? Math.round(((data.unallocated.income + data.unallocated.expenseTotal) / (data.total.income + data.total.expenseTotal)) * 100) : 0

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Profitability</h2>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 12, fontSize: 12 }}>
        <label>Show <select aria-label="Kind" value={kind} onChange={e => { setKind(e.target.value); setOpen(null) }} style={field}>
          <option value="">Everything</option>{KINDS.map(k => <option key={k.value} value={k.value}>{k.label}s</option>)}</select></label>
        <label>From <input aria-label="From" type="date" value={from} onChange={e => setFrom(e.target.value)} style={field} /></label>
        <label>To <input aria-label="To" type="date" value={to} onChange={e => setTo(e.target.value)} style={field} /></label>
        <label><input aria-label="Include everything inside" type="checkbox" checked={rollUp} onChange={e => setRollUp(e.target.checked)} /> Include everything inside each one</label>
      </div>
      {isLoading && <p style={{ fontSize: 13 }}>Loading…</p>}
      {data && (
        <>
          <div style={card}>
            <div style={{ fontSize: 12, marginBottom: 6 }}><b>Direct costs</b> (the rest are overheads):{" "}
              {data.subtypes.length === 0 && <span>no costs in this period</span>}
              {data.subtypes.map(s => (
                <label key={s} style={{ marginRight: 10 }}><input type="checkbox" aria-label={subtypeLabel(s)} checked={direct.has(s)} onChange={() => toggle(s)} /> {subtypeLabel(s)}</label>
              ))}
            </div>
          </div>
          {losers.length > 0 && (
            <div role="status" style={{ ...card, background: "var(--hf-warning-soft, #fffbeb)", fontSize: 13 }}>
              {losers.length} {losers.length === 1 ? "is" : "are"} losing money: {losers.slice(0, 5).map(r => `${r.code} (${money(r.profit)})`).join(", ")}{losers.length > 5 ? "…" : ""}
            </div>
          )}
          {unallocShare >= 20 && (
            <div role="status" style={{ ...card, fontSize: 13 }}>
              {unallocShare}% of income and costs in this period carry no cost centre, so they are not in any row below. Add tagging rules to attribute them.
            </div>
          )}
          <div style={card}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
              <thead><tr style={{ fontSize: 11, color: "var(--hf-text-muted)", textTransform: "uppercase" }}>
                <th style={{ textAlign: "left", padding: "3px 8px" }}>Name</th><th style={num}>Income</th><th style={num}>Direct costs</th><th style={num}>Contribution</th>
                <th style={num}>Overheads</th><th style={num}>Profit</th><th style={num}>Margin</th></tr></thead>
              <tbody>
                {data.rows.map(r => <Line key={r.centreId} r={r} direct={direct} onOpen={() => setOpen(r)} />)}
                <Line r={data.unallocated} label="Unallocated" direct={direct} muted />
                <Line r={data.total} label="Total" direct={direct} strong />
              </tbody>
            </table>
            {data.rows.length === 0 && <p style={{ fontSize: 12 }}>No cost centres of this kind yet. Add them under Cost centres.</p>}
          </div>
        </>
      )}
      {open && <TrendView row={open} from={from} to={to} direct={direct} onClose={() => setOpen(null)} />}
    </div>
  )
}

function Line({ r, direct, label, onOpen, muted, strong }: { r: Row; direct: Set<string>; label?: string; onOpen?: () => void; muted?: boolean; strong?: boolean }) {
  const s = split(r, direct)
  return (
    <tr style={{ borderTop: strong ? "2px solid var(--hf-border)" : "1px solid var(--hf-border)", fontWeight: strong ? 700 : 400, opacity: muted ? 0.75 : 1 }}>
      <td style={{ padding: "3px 8px" }}>{onOpen
        ? <button onClick={onOpen} style={{ all: "unset", cursor: "pointer", textDecoration: "underline" }} title="Month by month"><span style={{ fontFamily: "monospace" }}>{r.code}</span> {r.name}</button>
        : label}</td>
      <td style={num}>{cell(s.income)}</td><td style={num}>{cell(s.direct)}</td><td style={num}>{cell(s.contribution)}</td><td style={num}>{cell(s.overhead)}</td>
      <td style={{ ...num, fontWeight: 600, color: s.profit < -0.004 ? "var(--hf-danger-text, #b91c1c)" : undefined }}>{cell(s.profit)}</td>
      <td style={num}>{marginText(s.margin)}</td>
    </tr>
  )
}

function TrendView({ row, from, to, direct, onClose }: { row: Row; from: string; to: string; direct: Set<string>; onClose: () => void }) {
  const { data } = useQuery<Trend>({
    queryKey: ["acc-profit-trend", row.centreId, from, to],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accounting/profitability/${row.centreId}/trend?from=${from}&to=${to}`)),
  })
  if (!data) return null
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between" }}>
        <h3 style={{ marginTop: 0 }}>{data.code} — {data.name}, month by month</h3>
        <button onClick={onClose} style={{ ...field, cursor: "pointer" }}>Close</button>
      </div>
      <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
        <thead><tr style={{ fontSize: 11, color: "var(--hf-text-muted)", textTransform: "uppercase" }}>
          <th style={{ textAlign: "left", padding: "3px 8px" }}>Month</th><th style={num}>Income</th><th style={num}>Direct costs</th><th style={num}>Overheads</th><th style={num}>Profit</th></tr></thead>
        <tbody>
          {data.months.map(m => {
            const d = Object.entries(m.expenses).reduce((t, [k, v]) => (direct.has(k) ? t + v : t), 0)
            return (
              <tr key={m.month} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={{ padding: "3px 8px" }}>{monthLabel(m.month)}</td><td style={num}>{cell(m.income)}</td><td style={num}>{cell(d)}</td>
                <td style={num}>{cell(m.expenseTotal - d)}</td>
                <td style={{ ...num, fontWeight: 600, color: m.profit < -0.004 ? "var(--hf-danger-text, #b91c1c)" : undefined }}>{cell(m.profit)}</td>
              </tr>)
          })}
        </tbody>
      </table>
    </div>
  )
}
