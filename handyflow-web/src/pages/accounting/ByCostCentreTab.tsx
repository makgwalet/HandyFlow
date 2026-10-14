// src/pages/accounting/ByCostCentreTab.tsx
//
// Finance F24: profit and loss with a column per cost centre, and the journal lines behind any one of them.
// Only posted (and reversed) journals count, the same as the other reports.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { KINDS, marginPercent, money, yearStart } from "./costCentre.logic"

interface Column { id: string | null; code: string; name: string; kind: string | null }
interface Row { accountId: string; code: string; name: string; type: string; amounts: number[]; total: number }
interface Report {
  from: string; to: string; rollUp: boolean; columns: Column[]; income: Row[]; expenses: Row[]
  incomeTotals: number[]; expenseTotals: number[]; resultTotals: number[]; incomeTotal: number; expenseTotal: number; resultTotal: number
}
interface Line { date: string; entryNumber: string; entryDescription: string; accountCode: string; accountName: string; lineDescription: string | null; amount: number }
interface Detail { centre: { name: string; code: string }; income: number; expenses: number; result: number; lines: Line[]; truncated: boolean }

const unwrap = (r: any) => r.data?.data ?? r.data
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14, overflowX: "auto" }
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)" }
const num: React.CSSProperties = { textAlign: "right", whiteSpace: "nowrap", padding: "3px 8px" }

export default function ByCostCentreTab() {
  const today = new Date()
  const [from, setFrom] = useState(yearStart(today))
  const [to, setTo] = useState(today.toISOString().slice(0, 10))
  const [rollUp, setRollUp] = useState(true)
  const [kind, setKind] = useState("")
  const [open, setOpen] = useState<string | null>(null)

  const { data, isLoading } = useQuery<Report>({
    queryKey: ["acc-by-centre", from, to, rollUp, kind],
    enabled: !!from && !!to && to >= from,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accounting/cost-centres/report?from=${from}&to=${to}&rollUp=${rollUp}${kind ? `&kind=${kind}` : ""}`)),
  })

  const heads = (r: Report) => (
    <tr style={{ fontSize: 11, color: "var(--hf-text-muted)", textTransform: "uppercase" }}>
      <th style={{ textAlign: "left", padding: "3px 8px" }}>Account</th>
      {r.columns.map(c => (
        <th key={c.id ?? "none"} style={num}>
          {c.id ? <button onClick={() => setOpen(c.id)} style={{ all: "unset", cursor: "pointer", textDecoration: "underline" }} title="Show the lines">{c.code}</button> : "Unallocated"}
          <div style={{ fontWeight: 400, textTransform: "none" }}>{c.id ? c.name : ""}</div>
        </th>
      ))}
      <th style={num}>Total</th>
    </tr>
  )
  const body = (rows: Row[]) => rows.map(x => (
    <tr key={x.accountId} style={{ borderTop: "1px solid var(--hf-border)" }}>
      <td style={{ padding: "3px 8px" }}><span style={{ fontFamily: "monospace" }}>{x.code}</span> {x.name}</td>
      {x.amounts.map((a, i) => <td key={i} style={num}>{a ? money(a) : "—"}</td>)}
      <td style={{ ...num, fontWeight: 600 }}>{money(x.total)}</td>
    </tr>
  ))
  const totalRow = (label: string, vals: number[], total: number, strong = false) => (
    <tr style={{ borderTop: "2px solid var(--hf-border)", fontWeight: strong ? 700 : 600 }}>
      <td style={{ padding: "4px 8px" }}>{label}</td>
      {vals.map((v, i) => <td key={i} style={num}>{money(v)}</td>)}
      <td style={num}>{money(total)}</td>
    </tr>
  )

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Profit and loss by cost centre</h2>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 12, fontSize: 12 }}>
        <label>From <input aria-label="From" type="date" value={from} onChange={e => setFrom(e.target.value)} style={field} /></label>
        <label>To <input aria-label="To" type="date" value={to} onChange={e => setTo(e.target.value)} style={field} /></label>
        <label>Show <select aria-label="Kind" value={kind} onChange={e => setKind(e.target.value)} style={field}>
          <option value="">All kinds</option>{KINDS.map(k => <option key={k.value} value={k.value}>{k.label}s</option>)}</select></label>
        <label><input aria-label="Include everything inside" type="checkbox" checked={rollUp} onChange={e => setRollUp(e.target.checked)} /> Include everything inside each one</label>
      </div>
      {to < from && <p role="alert" style={{ fontSize: 12 }}>The period must end on or after it starts.</p>}
      {isLoading && <p style={{ fontSize: 13 }}>Loading…</p>}
      {data && (
        <div style={card}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead>{heads(data)}</thead>
            <tbody>
              <tr><td colSpan={data.columns.length + 2} style={{ fontWeight: 700, padding: "6px 8px" }}>Income</td></tr>
              {body(data.income)}
              {totalRow("Total income", data.incomeTotals, data.incomeTotal)}
              <tr><td colSpan={data.columns.length + 2} style={{ fontWeight: 700, padding: "6px 8px" }}>Expenses</td></tr>
              {body(data.expenses)}
              {totalRow("Total expenses", data.expenseTotals, data.expenseTotal)}
              {totalRow("Profit / (loss)", data.resultTotals, data.resultTotal, true)}
            </tbody>
          </table>
          <p style={{ fontSize: 11, color: "var(--hf-text-secondary)", marginBottom: 0 }}>
            {data.rollUp ? "A parent's column includes everything inside it, so columns can overlap; the Total column counts every line once. " : ""}
            Overall margin {marginPercent(data.incomeTotal, data.resultTotal) ?? "—"}{marginPercent(data.incomeTotal, data.resultTotal) != null ? "%" : ""}.
            Journal lines without a tag are in Unallocated.
          </p>
        </div>
      )}
      {open && <CentreLines id={open} from={from} to={to} onClose={() => setOpen(null)} />}
    </div>
  )
}

function CentreLines({ id, from, to, onClose }: { id: string; from: string; to: string; onClose: () => void }) {
  const { data } = useQuery<Detail>({
    queryKey: ["acc-by-centre-lines", id, from, to],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accounting/cost-centres/${id}/lines?from=${from}&to=${to}`)),
  })
  if (!data) return null
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between" }}>
        <h3 style={{ marginTop: 0 }}>{data.centre.code} — {data.centre.name}</h3>
        <button onClick={onClose} style={{ ...field, cursor: "pointer" }}>Close</button>
      </div>
      <div style={{ fontSize: 12, marginBottom: 8 }}>Income {money(data.income)} · Expenses {money(data.expenses)} · Result {money(data.result)}</div>
      {data.lines.length === 0 && <p style={{ fontSize: 12 }}>No income or expense lines in this period.</p>}
      <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
        <tbody>
          {data.lines.map((l, i) => (
            <tr key={i} style={{ borderTop: "1px solid var(--hf-border)" }}>
              <td style={{ padding: "3px 8px", whiteSpace: "nowrap" }}>{l.date}</td>
              <td style={{ padding: "3px 8px" }}>{l.entryNumber}</td>
              <td style={{ padding: "3px 8px" }}>{l.accountCode} {l.accountName}</td>
              <td style={{ padding: "3px 8px" }}>{l.lineDescription || l.entryDescription}</td>
              <td style={num}>{money(l.amount)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {data.truncated && <p style={{ fontSize: 11 }}>Showing the newest 500 lines. The totals above cover every line.</p>}
    </div>
  )
}
