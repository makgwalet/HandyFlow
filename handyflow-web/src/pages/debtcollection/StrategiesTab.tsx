// src/pages/debtcollection/StrategiesTab.tsx
//
// Finance F23: collection strategies. A strategy picks cases by balance and days overdue and lists steps (call, remind,
// demand, review). Each step raises ONE task in the work queue per case. Nothing is sent to a debtor by a strategy.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { ACTION_LABEL, DECISION_LABEL, emptyStrategy, formFromStrategy, segmentText, strategyProblem, strategyRequest, type StrategyForm } from "./strategies.logic"

const unwrap = (r: any) => r.data?.data ?? r.data
const errorOf = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."
const fmtR = (n: number) => `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14 }
const btn: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", fontSize: 12, fontWeight: 600, cursor: "pointer" }
const inp: React.CSSProperties = { padding: "6px 8px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }

export default function StrategiesTab() {
  const qc = useQueryClient()
  const isAdmin = usePermission("DEBTCOLLECTION_ADMIN")
  const base = "/api/v1/debtcollection/strategies"
  const [form, setForm] = useState<StrategyForm | null>(null)
  const [editing, setEditing] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [showPreview, setShowPreview] = useState(false)

  const { data: list = [] } = useQuery<any[]>({ queryKey: ["dc-strategies"], queryFn: async () => unwrap(await apiClient.get(base)) ?? [] })
  const { data: preview, isFetching } = useQuery<any>({
    queryKey: ["dc-strategy-preview", list.map(s => `${s.id}${s.active}`).join()], enabled: showPreview,
    queryFn: async () => unwrap(await apiClient.get(`${base}/preview`)),
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["dc-strategies"] }); qc.invalidateQueries({ queryKey: ["dc-strategy-preview"] }) }
  const fail = (e: any) => { setNotice(null); setError(errorOf(e)) }
  const save = useMutation({
    mutationFn: () => editing ? apiClient.put(`${base}/${editing}`, strategyRequest(form!)) : apiClient.post(base, strategyRequest(form!)),
    onSuccess: () => { setError(null); setForm(null); setEditing(null); refresh() }, onError: fail,
  })
  const toggle = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => apiClient.post(`${base}/${id}/active`, { active }),
    onSuccess: () => { setError(null); refresh() }, onError: fail,
  })
  const run = useMutation({
    mutationFn: () => apiClient.post(`${base}/run`),
    onSuccess: r => { setError(null); const x = unwrap(r); setNotice(x.raised ? `${x.raised} task(s) raised in the work queue.` : "Nothing needed raising."); refresh() }, onError: fail,
  })
  const problem = form ? strategyProblem(form) : null
  const set = (p: Partial<StrategyForm>) => setForm(f => ({ ...f!, ...p }))
  const setStep = (i: number, p: any) => setForm(f => ({ ...f!, steps: f!.steps.map((s, k) => k === i ? { ...s, ...p } : s) }))

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Collection strategies</h2>
      <p style={{ fontSize: 13, color: "var(--hf-text-secondary)", marginTop: 0 }}>
        A strategy decides what to do and when, by balance and days overdue. Each step puts one task in the collectors' work queue. A strategy never sends anything to a debtor,
        and it leaves alone cases that are disputed, on a payment plan, with legal, have an open promise to pay or were contacted recently.
      </p>
      {notice && <div role="status" style={{ ...card, fontSize: 13 }}>{notice}</div>}
      {error && <div role="alert" style={{ ...card, fontSize: 13, color: "var(--hf-danger-text, #b91c1c)" }}>{error}</div>}

      {list.length === 0 && <div style={card}>No strategies yet.</div>}
      {list.map(s => (
        <div key={s.id} style={{ ...card, opacity: s.active ? 1 : 0.6 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <span><b>{s.name}</b> <span style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>order {s.priority} · {segmentText(s)}{s.active ? "" : " · switched off"}</span></span>
            {isAdmin && <span>
              <button style={btn} onClick={() => { setForm(formFromStrategy(s)); setEditing(s.id) }}>Edit</button>
              <button style={{ ...btn, marginLeft: 6 }} onClick={() => toggle.mutate({ id: s.id, active: !s.active })}>{s.active ? "Switch off" : "Switch on"}</button>
            </span>}
          </div>
          {s.description && <div style={{ fontSize: 12, margin: "4px 0" }}>{s.description}</div>}
          <ol style={{ margin: "6px 0 0", paddingLeft: 20, fontSize: 13 }}>
            {s.steps.map((x: any) => <li key={x.id}>Day {x.afterDays}: {ACTION_LABEL[x.action]}{x.instruction ? `, ${x.instruction}` : ""}</li>)}
          </ol>
        </div>
      ))}

      {isAdmin && !form && <button style={btn} onClick={() => { setForm(emptyStrategy()); setEditing(null) }}>New strategy</button>}
      {form && (
        <div style={card}>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
            <label style={{ fontSize: 11 }}>Name<br /><input aria-label="Strategy name" value={form.name} onChange={e => set({ name: e.target.value })} style={{ ...inp, width: 220 }} /></label>
            <label style={{ fontSize: 11 }}>Order (1 is first)<br /><input aria-label="Order" value={form.priority} onChange={e => set({ priority: e.target.value })} style={{ ...inp, width: 70 }} /></label>
            <label style={{ fontSize: 11 }}>Balance from (R)<br /><input aria-label="Balance from" value={form.minBalance} onChange={e => set({ minBalance: e.target.value })} style={{ ...inp, width: 100 }} /></label>
            <label style={{ fontSize: 11 }}>Balance up to (R)<br /><input aria-label="Balance up to" value={form.maxBalance} onChange={e => set({ maxBalance: e.target.value })} style={{ ...inp, width: 100 }} /></label>
            <label style={{ fontSize: 11 }}>Days overdue from<br /><input aria-label="Days from" value={form.minDays} onChange={e => set({ minDays: e.target.value })} style={{ ...inp, width: 80 }} /></label>
            <label style={{ fontSize: 11 }}>Days overdue up to<br /><input aria-label="Days up to" value={form.maxDays} onChange={e => set({ maxDays: e.target.value })} style={{ ...inp, width: 80 }} /></label>
            <label style={{ fontSize: 11 }}>Wait after contact (days)<br /><input aria-label="Wait days" value={form.quietDays} onChange={e => set({ quietDays: e.target.value })} style={{ ...inp, width: 70 }} /></label>
          </div>
          <label style={{ fontSize: 11, display: "block", marginTop: 8 }}>Note (optional)<br /><input aria-label="Strategy note" value={form.description} onChange={e => set({ description: e.target.value })} style={{ ...inp, width: "100%" }} /></label>
          <h4 style={{ margin: "12px 0 6px" }}>Steps</h4>
          {form.steps.map((s, i) => (
            <div key={i} style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 6, alignItems: "center" }}>
              <span style={{ fontSize: 12 }}>After</span>
              <input aria-label={`Step ${i + 1} day`} value={s.afterDays} onChange={e => setStep(i, { afterDays: e.target.value })} style={{ ...inp, width: 60 }} />
              <span style={{ fontSize: 12 }}>days overdue:</span>
              <select aria-label={`Step ${i + 1} action`} value={s.action} onChange={e => setStep(i, { action: e.target.value })} style={inp}>
                {Object.entries(ACTION_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
              <input aria-label={`Step ${i + 1} instruction`} placeholder="What to say or do (optional)" value={s.instruction} onChange={e => setStep(i, { instruction: e.target.value })} style={{ ...inp, width: 260 }} />
              <button style={btn} onClick={() => set({ steps: form.steps.filter((_, k) => k !== i) })}>Remove</button>
            </div>
          ))}
          <button style={btn} onClick={() => set({ steps: [...form.steps, { afterDays: "", action: "CALL", instruction: "" }] })}>Add a step</button>
          {problem && <div style={{ fontSize: 12, color: "var(--hf-danger-text, #b91c1c)", marginTop: 8 }}>{problem}</div>}
          <div style={{ display: "flex", gap: 6, marginTop: 10 }}>
            <button style={btn} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>Save</button>
            <button style={btn} onClick={() => { setForm(null); setEditing(null) }}>Cancel</button>
          </div>
        </div>
      )}

      <div style={{ ...card, marginTop: 14 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
          <h3 style={{ margin: 0 }}>What would happen today</h3>
          <span>
            <button style={btn} onClick={() => setShowPreview(true)}>{showPreview ? "Refresh" : "Show"}</button>
            {isAdmin && <button style={{ ...btn, marginLeft: 6 }} disabled={run.isPending} onClick={() => { if (window.confirm("Raise today's tasks in the work queue now? Nothing is sent to debtors.")) run.mutate() }}>Raise tasks now</button>}
          </span>
        </div>
        {showPreview && isFetching && <p style={{ fontSize: 13 }}>Working it out…</p>}
        {showPreview && preview && (preview.rows.length === 0 ? <p style={{ fontSize: 13 }}>No live cases, or no strategy is switched on.</p> : (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12, marginTop: 8 }}>
            <thead><tr style={{ textAlign: "left", color: "var(--hf-text-secondary)" }}><th>Case</th><th>Balance</th><th>Days overdue</th><th>Strategy</th><th>Result</th></tr></thead>
            <tbody>{preview.rows.map((r: any) => (
              <tr key={r.caseId} style={{ borderTop: "1px solid var(--hf-border)", verticalAlign: "top" }}>
                <td style={{ padding: "5px 6px 5px 0" }}>{r.caseNumber} · {r.debtorName}</td><td>{fmtR(r.balance)}</td><td>{r.daysOverdue}</td><td>{r.strategyName ?? "–"}</td>
                <td><b>{DECISION_LABEL[r.decision]}</b>{r.title ? `: ${r.title}` : ""}{r.reason ? <div style={{ color: "var(--hf-text-secondary)" }}>{r.reason}</div> : null}</td></tr>))}</tbody>
          </table>))}
      </div>
    </div>
  )
}
