// src/pages/accounting/MonthEndCloseTab.tsx
//
// Finance patch F13: the month-end checklist. The system checks drafts, posting problems, bank sign-offs, the trial balance
// and VAT; a person confirms accruals, payroll, depreciation, debtors and creditors. Closing the month sets the books'
// lock date (F5) and is only possible once every blocking item is done.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertCircle, CheckCircle } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { type ItemStatus, lastMonthEnd, recentMonthEnds, remaining, STATUS_COLOR, STATUS_TEXT } from "./monthEnd.logic"

interface Item { key: string; title: string; kind: "AUTO" | "MANUAL"; status: ItemStatus; detail: string; blocking: boolean
  confirmedBy: string | null; confirmedAt: string | null }
interface Checklist { periodEnd: string; lockedThrough: string | null; closable: boolean; closeProblem: string | null; items: Item[] }

const fmtDay = (d: string | null) => d ? new Date(`${d}T00:00:00`).toLocaleDateString("en-ZA", { day: "numeric", month: "long", year: "numeric" }) : "nothing"
const errorOf = (e: any, fallback: string) => e?.response?.data?.message ?? fallback
const unwrap = (r: any) => r.data?.data ?? r.data
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 18, marginBottom: 16 }
const btn: React.CSSProperties = { padding: "8px 14px", borderRadius: 8, border: "none", background: "var(--hf-primary, #2563eb)", color: "#fff", fontWeight: 600, fontSize: 13, cursor: "pointer" }
const input: React.CSSProperties = { display: "block", width: "100%", marginTop: 4, padding: "8px 10px", border: "1.5px solid var(--hf-border)",
  borderRadius: 8, fontSize: 13, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function MonthEndCloseTab() {
  const qc = useQueryClient()
  const canConfirm = usePermission("ACCOUNTING_MANAGE")
  const canClose = usePermission("ACCOUNTING_ADMIN")
  const [periodEnd, setPeriodEnd] = useState(lastMonthEnd())
  const [note, setNote] = useState("")
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")
  const months = recentMonthEnds()

  const { data, isLoading } = useQuery<Checklist>({
    queryKey: ["month-end", periodEnd],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accounting/month-end?periodEnd=${periodEnd}`)),
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["month-end"] }); qc.invalidateQueries({ queryKey: ["period-lock"] }) }
  const fail = (e: any, m: string) => { setNotice(""); setError(errorOf(e, m)) }

  const confirm = useMutation({
    mutationFn: (key: string) => apiClient.post("/api/v1/accounting/month-end/confirmations", { periodEnd, stepKey: key }),
    onSuccess: () => { setError(""); refresh() }, onError: e => fail(e, "Could not confirm the step."),
  })
  const undo = useMutation({
    mutationFn: (key: string) => apiClient.delete(`/api/v1/accounting/month-end/confirmations?periodEnd=${periodEnd}&stepKey=${key}`),
    onSuccess: () => { setError(""); refresh() }, onError: e => fail(e, "Could not take the confirmation back."),
  })
  const close = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/month-end/close", { periodEnd, note: note.trim() || undefined }),
    onSuccess: () => { setNote(""); setError(""); setNotice(`The books are closed up to and including ${fmtDay(periodEnd)}.`); refresh() },
    onError: e => fail(e, "Could not close the month."),
  })

  const closed = data?.lockedThrough != null && data.lockedThrough >= periodEnd

  return (
    <div style={{ maxWidth: 860 }}>
      <h2 style={{ margin: "0 0 4px" }}>Month-end close</h2>
      <p style={{ margin: "0 0 16px", color: "var(--hf-text-secondary)", fontSize: 13 }}>
        Work through the list, then close the month. Closing locks the books: nothing dated on or before the month end can be added or changed.
      </p>
      {error && <div role="alert" style={{ ...card, borderColor: "#dc2626", color: "#dc2626" }}><AlertCircle size={14} /> {error}</div>}
      {notice && <div role="status" style={{ ...card, borderColor: "#16a34a", color: "#16a34a" }}><CheckCircle size={14} /> {notice}</div>}

      <div style={card}>
        <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)" }}>Month
          <select aria-label="Month" style={input} value={periodEnd} onChange={e => { setPeriodEnd(e.target.value); setNotice(""); setError("") }}>
            {months.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        </label>
        {data && <p data-testid="summary" style={{ margin: "12px 0 0", fontSize: 13 }}>
          {closed ? `Closed. The books are locked up to ${fmtDay(data.lockedThrough)}.`
            : data.closable ? "Everything is done. Ready to close."
            : `${remaining(data.items)} item${remaining(data.items) === 1 ? "" : "s"} still to do.`}
        </p>}
      </div>

      {isLoading && <p>Loading…</p>}
      {data && (
        <div style={card}>
          {data.items.map(i => (
            <div key={i.key} data-testid={`item-${i.key}`} style={{ display: "flex", gap: 12, alignItems: "flex-start", padding: "10px 0", borderTop: "1px solid var(--hf-border)" }}>
              <span style={{ minWidth: 70, fontSize: 12, fontWeight: 700, color: STATUS_COLOR[i.status] }}>{STATUS_TEXT[i.status]}</span>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 14, fontWeight: 600 }}>{i.title}</div>
                <div style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>{i.detail}</div>
              </div>
              {i.kind === "MANUAL" && canConfirm && !closed && (
                i.status === "DONE"
                  ? <button style={{ ...btn, background: "#6b7280" }} onClick={() => undo.mutate(i.key)}>Undo</button>
                  : <button style={btn} onClick={() => confirm.mutate(i.key)}>Confirm</button>
              )}
            </div>
          ))}
        </div>
      )}

      {data && canClose && !closed && (
        <div style={card}>
          <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)" }}>Note (optional)
            <input aria-label="Close note" style={input} value={note} onChange={e => setNote(e.target.value)} />
          </label>
          {data.closeProblem && <p data-testid="close-problem" style={{ fontSize: 12, color: "#b45309" }}>{data.closeProblem}</p>}
          <button style={{ ...btn, marginTop: 8, opacity: data.closable ? 1 : 0.5 }} disabled={!data.closable || close.isPending}
            onClick={() => close.mutate()}>Close {new Date(`${periodEnd}T00:00:00`).toLocaleDateString("en-ZA", { month: "long", year: "numeric" })}</button>
        </div>
      )}
    </div>
  )
}
