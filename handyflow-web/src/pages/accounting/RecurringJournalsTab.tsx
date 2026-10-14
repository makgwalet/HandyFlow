// src/pages/accounting/RecurringJournalsTab.tsx
//
// Finance patch F17: recurring journals, accruals and prepayments. The system makes DRAFT journals on the due dates, in the
// name of whoever set the schedule up; a different person posts them from Journal Entries.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { type FormLine, type ScheduleForm, emptyForm, emptyLine, formProblem, FREQ_LABEL, KIND_LABEL, lineTotals, nextText, STATUS_LABEL, toRequest } from "./recurring.logic"

interface Account { id: string; accountCode: string; accountName: string }
const unwrap = (r: any) => r.data?.data ?? r.data
const errorOf = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."
const today = () => new Date().toISOString().slice(0, 10)
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 12 }
const btn: React.CSSProperties = { padding: "7px 13px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontWeight: 600, fontSize: 12, cursor: "pointer" }
const input: React.CSSProperties = { width: "100%", padding: "7px 9px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 12, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function RecurringJournalsTab() {
  const qc = useQueryClient()
  const canManage = usePermission("ACCOUNTING_MANAGE")
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState<ScheduleForm>(emptyForm(today()))
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const { data: schedules = [], isLoading } = useQuery<any[]>({
    queryKey: ["acc-recurring"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/recurring-journals")) ?? [],
  })
  const { data: accounts = [] } = useQuery<Account[]>({
    queryKey: ["coa"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/accounts")),
  })

  const refresh = () => qc.invalidateQueries({ queryKey: ["acc-recurring"] })
  const create = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/recurring-journals", toRequest(form)),
    onSuccess: () => { setError(null); setAdding(false); setForm(emptyForm(today())); refresh() },
    onError: e => setError(errorOf(e)),
  })
  const act = useMutation({
    mutationFn: ({ id, action }: { id: string; action: "pause" | "resume" | "end" | "run-now" }) =>
      apiClient.post(`/api/v1/accounting/recurring-journals/${id}/${action}`),
    onSuccess: (r: any, v) => {
      setError(null)
      if (v.action === "run-now") {
        const res = unwrap(r)
        setNotice(res?.problems?.length ? `Made ${res.made}. ${res.problems[0]}` : res?.made ? `Made ${res.made} draft journal(s). Post them from Journal Entries.` : "Nothing was due.")
      }
      refresh()
    },
    onError: e => setError(errorOf(e)),
  })

  const problem = formProblem(form)
  const set = (patch: Partial<ScheduleForm>) => setForm(f => ({ ...f, ...patch }))
  const setLine = (i: number, patch: Partial<FormLine>) => set({ lines: form.lines.map((l, k) => (k === i ? { ...l, ...patch } : l)) })
  const totals = lineTotals(form.lines)
  const prepay = form.kind === "PREPAYMENT"
  const acctSelect = (value: string, onChange: (v: string) => void, label: string) => (
    <select aria-label={label} value={value} onChange={e => onChange(e.target.value)} style={input}>
      <option value="">{label}…</option>
      {accounts.map(a => <option key={a.id} value={a.id}>{a.accountCode} {a.accountName}</option>)}
    </select>
  )

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)", maxWidth: 560 }}>
          Journals made for you on a schedule. They arrive as drafts in your name, so someone else posts them.
        </div>
        {canManage && <button style={btn} onClick={() => { setAdding(a => !a); setError(null) }}>{adding ? "Cancel" : "+ New schedule"}</button>}
      </div>

      {error && <div role="alert" style={{ ...card, color: "var(--hf-danger-text)", background: "var(--hf-danger-soft)" }}>{error}</div>}
      {notice && <div style={{ ...card, background: "var(--hf-info-soft)" }}>{notice}</div>}

      {adding && (
        <div style={card}>
          <div style={{ display: "grid", gridTemplateColumns: "2fr 2fr 1fr", gap: 8, marginBottom: 8 }}>
            <input aria-label="Name" placeholder="Name (e.g. Office rent)" value={form.name} onChange={e => set({ name: e.target.value })} style={input} />
            <select aria-label="Kind" value={form.kind} onChange={e => set({ kind: e.target.value })} style={input}>
              {Object.entries(KIND_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <select aria-label="Frequency" value={form.frequency} onChange={e => set({ frequency: e.target.value })} style={input}>
              {Object.entries(FREQ_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 2fr", gap: 8, marginBottom: 8 }}>
            <label style={{ fontSize: 11 }}>First date<input aria-label="First date" type="date" value={form.startDate} onChange={e => set({ startDate: e.target.value })} style={input} /></label>
            <label style={{ fontSize: 11 }}>Last date (optional)<input aria-label="Last date" type="date" value={form.endDate} onChange={e => set({ endDate: e.target.value })} style={input} /></label>
            <label style={{ fontSize: 11 }}>{prepay ? "Periods to spread over" : "Number of runs (optional)"}<input aria-label="Runs" type="number" value={form.runs} onChange={e => set({ runs: e.target.value })} style={input} /></label>
            <label style={{ fontSize: 11 }}>Journal description<input aria-label="Description" value={form.description} onChange={e => set({ description: e.target.value })} style={input} /></label>
          </div>
          {prepay ? (
            <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr 2fr", gap: 8, marginBottom: 8 }}>
              <input aria-label="Total amount" type="number" placeholder="Total amount" value={form.totalAmount} onChange={e => set({ totalAmount: e.target.value })} style={input} />
              {acctSelect(form.expenseAccountId, v => set({ expenseAccountId: v }), "Expense account")}
              {acctSelect(form.prepaidAccountId, v => set({ prepaidAccountId: v }), "Prepaid asset account")}
            </div>
          ) : (
            <div style={{ marginBottom: 8 }}>
              {form.lines.map((l, i) => (
                <div key={i} style={{ display: "grid", gridTemplateColumns: "3fr 2fr 1fr 1fr", gap: 6, marginBottom: 6 }}>
                  {acctSelect(l.accountId, v => setLine(i, { accountId: v }), `Account ${i + 1}`)}
                  <input aria-label={`Line text ${i + 1}`} placeholder="Line text" value={l.description} onChange={e => setLine(i, { description: e.target.value })} style={input} />
                  <input aria-label={`Debit ${i + 1}`} type="number" placeholder="Debit" value={l.debit} onChange={e => setLine(i, { debit: e.target.value })} style={input} />
                  <input aria-label={`Credit ${i + 1}`} type="number" placeholder="Credit" value={l.credit} onChange={e => setLine(i, { credit: e.target.value })} style={input} />
                </div>
              ))}
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12 }}>
                <button style={btn} onClick={() => set({ lines: [...form.lines, emptyLine()] })}>+ Line</button>
                <span>Debits {totals.debit.toFixed(2)} · Credits {totals.credit.toFixed(2)}</span>
              </div>
            </div>
          )}
          {problem && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 6 }}>{problem}</div>}
          <button style={btn} disabled={!!problem || create.isPending} onClick={() => create.mutate()}>{create.isPending ? "Saving…" : "Save schedule"}</button>
        </div>
      )}

      {isLoading ? <div style={{ color: "var(--hf-text-faint)" }}>Loading…</div> : schedules.length === 0 ? (
        <div style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No schedules yet.</div>
      ) : schedules.map(s => (
        <div key={s.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <strong>{s.name}</strong>
            <span style={{ fontWeight: 700, fontSize: 12 }}>{STATUS_LABEL[s.status] ?? s.status}</span>
          </div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0" }}>
            {KIND_LABEL[s.kind] ?? s.kind} · {FREQ_LABEL[s.frequency] ?? s.frequency} · {s.description}
            {s.totalAmount != null ? ` · R ${Number(s.totalAmount).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : ""}
          </div>
          <div style={{ fontSize: 12 }}>{nextText(s)}</div>
          {s.lastError && <div style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>Stopped: {s.lastError}</div>}
          {s.runs?.length > 0 && <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 4 }}>Made so far: {s.runs.map((r: any) => r.runDate).reverse().join(", ")}</div>}
          {canManage && s.status !== "ENDED" && (
            <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
              {s.status === "ACTIVE" && <button style={btn} disabled={act.isPending} onClick={() => act.mutate({ id: s.id, action: "run-now" })}>Make due drafts now</button>}
              {s.status === "ACTIVE" ? <button style={btn} onClick={() => act.mutate({ id: s.id, action: "pause" })}>Pause</button>
                : <button style={btn} onClick={() => act.mutate({ id: s.id, action: "resume" })}>Resume</button>}
              <button style={btn} onClick={() => act.mutate({ id: s.id, action: "end" })}>End</button>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}
