// src/pages/accounting/CashForecastTab.tsx
//
// Finance F21: 13 weeks of expected cash in and out. Estimates from open invoices, approved bills and items added by hand;
// the assumptions are shown and can be changed to see a slower or worse case.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertTriangle } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { defaultQuery, fmtR, forecastParams, itemProblem, type ForecastQuery } from "./budget.logic"

interface Week { index: number; start: string; end: string; opening: number; in: number; out: number; closing: number }
interface Item { date: string; amount: number; direction: string; label: string; source: string }
interface Forecast {
  today: string; openingCash: number; weeks: Week[]; lowest: number; lowestWeekStart: string; firstNegativeWeek: number | null
  laterIn: number; laterOut: number; warnings: string[]; items: Item[]; receivablesTotal: number; payablesTotal: number
}
interface Manual { id: string; date: string; amount: number; direction: string; label: string }
const unwrap = (r: any) => r.data?.data ?? r.data
const errorOf = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14 }
const input: React.CSSProperties = { padding: "7px 9px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)", width: 90 }
const btn: React.CSSProperties = { padding: "7px 13px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontWeight: 600, fontSize: 12, cursor: "pointer" }

export default function CashForecastTab() {
  const qc = useQueryClient()
  const canManage = usePermission("ACCOUNTING_MANAGE")
  const [q, setQ] = useState<ForecastQuery>(defaultQuery())
  const [d, setD] = useState({ date: "", amount: "", direction: "OUT", label: "" })
  const [error, setError] = useState<string | null>(null)

  const { data, isLoading } = useQuery<Forecast>({
    queryKey: ["cash-forecast", forecastParams(q)],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accounting/cash-forecast?${forecastParams(q)}`)),
  })
  const { data: manual = [] } = useQuery<Manual[]>({
    queryKey: ["cash-forecast-items"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/cash-forecast/items")) ?? [],
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["cash-forecast"] }); qc.invalidateQueries({ queryKey: ["cash-forecast-items"] }) }
  const add = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/cash-forecast/items", { date: d.date, amount: Number(d.amount.replace(",", ".")), direction: d.direction, label: d.label.trim() }),
    onSuccess: () => { setError(null); setD({ date: "", amount: "", direction: "OUT", label: "" }); refresh() },
    onError: e => setError(errorOf(e)),
  })
  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/accounting/cash-forecast/items/${id}`),
    onSuccess: refresh, onError: e => setError(errorOf(e)),
  })
  const num = (k: keyof ForecastQuery) => (e: React.ChangeEvent<HTMLInputElement>) => setQ(p => ({ ...p, [k]: Number(e.target.value) }))
  const problem = itemProblem(d.date, d.amount, d.label)

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Cash forecast</h2>
      <p style={{ fontSize: 13, color: "var(--hf-text-secondary)", marginTop: 0 }}>
        The next 13 weeks, worked out from what customers owe, what suppliers are owed and the items you add below. These are estimates, not promises.
      </p>

      <div style={card}>
        <div style={{ display: "flex", gap: 14, flexWrap: "wrap", alignItems: "flex-end", fontSize: 12 }}>
          <label>Customers pay this many days late<input type="number" min={0} max={120} value={q.customerDelayDays} onChange={num("customerDelayDays")} style={{ ...input, display: "block", marginTop: 4 }} /></label>
          <label>Share of invoices collected (%)<input type="number" min={0} max={100} value={q.collectPercent} onChange={num("collectPercent")} style={{ ...input, display: "block", marginTop: 4 }} /></label>
          <label>Overdue invoices paid in (days)<input type="number" min={0} max={365} value={q.overdueReceiptInDays} onChange={num("overdueReceiptInDays")} style={{ ...input, display: "block", marginTop: 4 }} /></label>
          <label>Overdue bills paid in (days)<input type="number" min={0} max={120} value={q.overduePayInDays} onChange={num("overduePayInDays")} style={{ ...input, display: "block", marginTop: 4 }} /></label>
          <label>Warn below (R)<input value={q.lowBalance} onChange={e => setQ(p => ({ ...p, lowBalance: e.target.value }))} placeholder="optional" style={{ ...input, display: "block", marginTop: 4 }} /></label>
        </div>
      </div>

      {isLoading && <p>Loading…</p>}
      {data && (<>
        {data.warnings.map(w => (
          <div key={w} role="alert" style={{ ...card, display: "flex", gap: 8, borderColor: "var(--hf-warning-border, #f59e0b)", fontSize: 13 }}>
            <AlertTriangle size={16} /> {w}
          </div>
        ))}
        <div style={card}>
          <div style={{ display: "flex", gap: 28, flexWrap: "wrap", fontSize: 13, marginBottom: 10 }}>
            <span>Cash today: <b>{fmtR(data.openingCash)}</b></span>
            <span>Customers owe: <b>{fmtR(data.receivablesTotal)}</b></span>
            <span>Suppliers are owed: <b>{fmtR(data.payablesTotal)}</b></span>
            <span>Lowest point: <b>{fmtR(data.lowest)}</b></span>
          </div>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr style={{ textAlign: "right", color: "var(--hf-text-secondary)" }}>
              <th style={{ textAlign: "left" }}>Week from</th><th>Start</th><th>In</th><th>Out</th><th>End</th></tr></thead>
            <tbody>{data.weeks.map(w => (
              <tr key={w.index} style={{ textAlign: "right", borderTop: "1px solid var(--hf-border)", color: w.closing < 0 ? "var(--hf-danger-text, #b91c1c)" : undefined }}>
                <td style={{ textAlign: "left", padding: "5px 0" }}>{w.start}</td><td>{fmtR(w.opening)}</td><td>{fmtR(w.in)}</td><td>{fmtR(w.out)}</td><td><b>{fmtR(w.closing)}</b></td></tr>
            ))}</tbody>
          </table>
          {(data.laterIn > 0 || data.laterOut > 0) && (
            <p style={{ fontSize: 12, marginBottom: 0 }}>After the 13 weeks: {fmtR(data.laterIn)} in, {fmtR(data.laterOut)} out (not in the table above).</p>
          )}
        </div>
      </>)}

      <div style={card}>
        <h3 style={{ marginTop: 0 }}>Items you add</h3>
        <p style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginTop: 0 }}>
          Payroll, VAT payments, loan instalments, rent, expected deposits: anything that is not yet an invoice or an approved bill.
        </p>
        {manual.length === 0 && <p style={{ fontSize: 12 }}>None yet.</p>}
        {manual.map(m => (
          <div key={m.id} style={{ display: "flex", justifyContent: "space-between", fontSize: 13, padding: "4px 0" }}>
            <span>{m.date} · {m.label} · {m.direction === "IN" ? "money in" : "money out"}</span>
            <span>{fmtR(m.amount)} {canManage && <button style={{ ...btn, marginLeft: 8 }} onClick={() => remove.mutate(m.id)}>Remove</button>}</span>
          </div>
        ))}
        {canManage && (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 10, alignItems: "center" }}>
            <input aria-label="Item date" type="date" value={d.date} onChange={e => setD({ ...d, date: e.target.value })} style={{ ...input, width: 140 }} />
            <input aria-label="Item description" placeholder="What is it" value={d.label} onChange={e => setD({ ...d, label: e.target.value })} style={{ ...input, width: 200 }} />
            <input aria-label="Item amount" placeholder="Amount" value={d.amount} onChange={e => setD({ ...d, amount: e.target.value })} style={input} />
            <select aria-label="Item direction" value={d.direction} onChange={e => setD({ ...d, direction: e.target.value })} style={{ ...input, width: 120 }}>
              <option value="OUT">Money out</option><option value="IN">Money in</option></select>
            <button style={btn} disabled={!!problem || add.isPending} title={problem ?? ""} onClick={() => add.mutate()}>Add</button>
          </div>
        )}
        {error && <div role="alert" style={{ color: "var(--hf-danger-text, #b91c1c)", fontSize: 12, marginTop: 8 }}>{error}</div>}
      </div>
    </div>
  )
}
