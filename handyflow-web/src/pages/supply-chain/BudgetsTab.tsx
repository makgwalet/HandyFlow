// src/pages/supply-chain/BudgetsTab.tsx — budgets: what is allocated, variance against it, what is committed, and a what-if check.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, Wallet } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { inp, TH, TD, Modal, ModalFooter, Field, ErrBox, Spinner, EmptyState, Banner, filterPill, fmtR, fmtDate } from "./scm.shared"
import { asList, asOne, errorText, SCOPES, MODES, MODE_HELP, emptyBudget, budgetProblem, utilisationTone, budgetTotals, allocationBy, budgetVariance, budgetBar,
  budgetCheckProblem, budgetCheckParams, type BudgetForm, type BudgetCheckForm } from "./scmOps"
import { StatCard, Panel, Pill, grid } from "./scmUi"

interface Budget { id: string; name: string; scopeType: string; scopeValue: string; periodStart: string; periodEnd: string; amount: number; committed: number; actual: number; used: number; available: number; utilisationPct: number; state: string; controlMode: string; status: string; currency?: string }
interface Commitment { purchaseOrderId: string; orderNumber: string; supplierName: string; poStatus: string; committed: number; actual: number; used: number; spendDate: string | null }
interface Check { hardStop: boolean; hasWarnings: boolean; findings: { budgetName: string; used: number; amount: number; requested: number; availableAfter: number; state: string; message: string; controlMode: string }[] }

const BASE = "/api/v1/supply-chain/budgets"
const BAR = { actual: "var(--hf-info)", committed: "var(--hf-warning)", over: "var(--hf-danger)" }
const scopeLabel = (s: string) => s.replace("_", " ").toLowerCase().replace(/^./, c => c.toUpperCase())

export function BudgetsTab() {
  const qc = useQueryClient()
  const canManage = usePermissions(["SCM_BUDGET_MANAGE", "SCM_ADMIN"])
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState<BudgetForm>(emptyBudget())
  const [err, setErr] = useState("")
  const [open, setOpen] = useState<Budget | null>(null)
  const [edit, setEdit] = useState<{ b: Budget; amount: string; controlMode: string; close: boolean } | null>(null)
  const [editErr, setEditErr] = useState("")
  const [view, setView] = useState<"variance" | "allocation" | "check">("variance")
  const [check, setCheck] = useState<BudgetCheckForm>({ department: "", costCentre: "", projectRef: "", amount: "" })
  const [checkErr, setCheckErr] = useState("")

  const list = useQuery({ queryKey: ["scm-budgets"], queryFn: () => apiClient.get(BASE).then(asList<Budget>) })
  const commitments = useQuery({ queryKey: ["scm-budget-commitments", open?.id], enabled: !!open, queryFn: () => apiClient.get(`${BASE}/${open!.id}/commitments`).then(asList<Commitment>) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["scm-budgets"] })

  const create = useMutation({
    mutationFn: () => apiClient.post(BASE, { name: form.name.trim(), scopeType: form.scopeType, scopeValue: form.scopeValue.trim(), periodStart: form.periodStart, periodEnd: form.periodEnd, amount: Number(form.amount), controlMode: form.controlMode, currency: "ZAR" }),
    onSuccess: () => { setCreating(false); setForm(emptyBudget()); setErr(""); refresh() },
    // An overlapping budget for the same scope comes back as a 409 with a plain message; show it as is.
    onError: (e) => setErr(errorText(e)),
  })
  const update = useMutation({
    mutationFn: () => apiClient.put(`${BASE}/${edit!.b.id}`, { amount: Number(edit!.amount), controlMode: edit!.controlMode, ...(edit!.close ? { status: "CLOSED" } : {}) }),
    onSuccess: () => { setEdit(null); setEditErr(""); refresh() }, onError: e => setEditErr(errorText(e)),
  })
  const run = useMutation({ mutationFn: () => apiClient.get(`${BASE}/check`, { params: budgetCheckParams(check) }).then(r => asOne<Check>(r)) })

  const submit = () => { const p = budgetProblem(form); if (p) { setErr(p); return } setErr(""); create.mutate() }
  const submitEdit = () => { if (!(Number(edit!.amount) > 0)) { setEditErr("The amount must be above zero"); return } setEditErr(""); update.mutate() }
  const runCheck = () => { const p = budgetCheckProblem(check); if (p) { setCheckErr(p); return } setCheckErr(""); run.mutate() }

  const rows = list.data ?? []
  const t = budgetTotals(rows)
  const groups = allocationBy(rows)

  return (
    <div style={{ padding: 20, display: "grid", gap: 14 }}>
      <div style={grid(170)}>
        <StatCard label="Allocated" value={fmtR(t.allocated)} sub={`${t.count} active budget${t.count === 1 ? "" : "s"}`} icon={Wallet} tone="info" />
        <StatCard label="Committed + spent" value={fmtR(t.used)} sub={`${fmtR(t.actual)} invoiced`} />
        <StatCard label="Available" value={fmtR(t.available)} tone={t.available < 0 ? "bad" : "ok"} />
        <StatCard label="Needs attention" value={t.exceeded + t.near} sub={`${t.exceeded} exceeded · ${t.near} near limit`} tone={t.exceeded ? "bad" : t.near ? "warn" : "neutral"} />
      </div>

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        {([["variance", "Variance"], ["allocation", "Allocation"], ["check", "What-if check"]] as const).map(([v, l]) => <button key={v} style={filterPill(view === v)} onClick={() => setView(v)}>{l}</button>)}
        <div style={{ flex: 1 }} />
        {canManage && <button onClick={() => setCreating(true)} style={{ ...filterPill(true), display: "flex", gap: 6, alignItems: "center" }}><Plus size={14} /> New budget</button>}
      </div>

      {list.isLoading ? <Spinner /> : list.isError ? <ErrBox msg={errorText(list.error, "Could not load budgets")} /> : rows.length === 0 && view !== "check" ? (
        <EmptyState icon={Wallet} title="No budgets" sub="Set a budget for a department, cost centre or project to track what is committed and spent." />
      ) : view === "variance" ? (
        <div style={{ overflowX: "auto", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr>{["Budget", "Period", "Allocated", "Committed", "Invoiced", "Variance", "Used", "Control", ""].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
            <tbody>{rows.map(b => { const v = budgetVariance(b), bar = budgetBar(b), tone = utilisationTone(b.state); return (
              <tr key={b.id} style={{ borderTop: "1px solid var(--hf-border)", cursor: "pointer", opacity: b.status === "CLOSED" ? 0.55 : 1 }} onClick={() => setOpen(b)}>
                <td style={TD}><strong>{b.name}</strong><div style={{ fontSize: 12 }}>{scopeLabel(b.scopeType)}: {b.scopeValue}</div></td>
                <td style={TD}>{fmtDate(b.periodStart)} – {fmtDate(b.periodEnd)}</td>
                <td style={TD}>{fmtR(b.amount)}</td><td style={TD}>{fmtR(b.committed)}</td><td style={TD}>{fmtR(b.actual)}</td>
                <td style={{ ...TD, color: v < 0 ? "var(--hf-danger-text)" : undefined, fontWeight: v < 0 ? 700 : 400 }}>{fmtR(v)}</td>
                <td style={{ ...TD, minWidth: 150 }}>
                  <div style={{ display: "flex", height: 8, borderRadius: 4, overflow: "hidden", background: "var(--hf-surface-sunken)" }} role="img" aria-label={`${Math.round(b.utilisationPct)}% used`}>
                    <span style={{ width: `${bar.actualPct}%`, background: BAR.actual }} /><span style={{ width: `${bar.committedPct}%`, background: BAR.committed }} /><span style={{ width: `${Math.min(bar.overPct, 100)}%`, background: BAR.over }} />
                  </div>
                  <div style={{ fontSize: 12, marginTop: 3, color: tone === "ok" ? undefined : `var(--hf-${tone === "bad" ? "danger" : "warning"}-text${tone === "bad" ? "" : "-deep"})`, fontWeight: 700 }}>{Math.round(b.utilisationPct)}%{b.state === "EXCEEDED" ? " — exceeded" : b.state === "NEAR_LIMIT" ? " — near limit" : ""}</div>
                </td>
                <td style={TD}><Pill tone={b.controlMode === "HARD_STOP" ? "bad" : b.controlMode === "WARN" ? "warn" : "neutral"}>{b.controlMode.replace("_", " ")}</Pill></td>
                <td style={TD} onClick={e => e.stopPropagation()}>{canManage && b.status !== "CLOSED" && <button style={filterPill(false)} onClick={() => { setEdit({ b, amount: String(b.amount), controlMode: b.controlMode, close: false }); setEditErr("") }}>Edit</button>}</td>
              </tr>) })}</tbody>
          </table>
          <div style={{ display: "flex", gap: 14, padding: "8px 14px", fontSize: 11, color: "var(--hf-text-tertiary)" }}>
            <span><i style={{ display: "inline-block", width: 9, height: 9, background: BAR.actual, marginRight: 5 }} />Invoiced</span><span><i style={{ display: "inline-block", width: 9, height: 9, background: BAR.committed, marginRight: 5 }} />Committed, not yet invoiced</span><span><i style={{ display: "inline-block", width: 9, height: 9, background: BAR.over, marginRight: 5 }} />Over budget</span>
          </div>
        </div>
      ) : view === "allocation" ? (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 14 }}>
          {groups.map(g => (
            <Panel key={g.scopeType} title={`By ${scopeLabel(g.scopeType).toLowerCase()}`} right={<span style={{ fontSize: 12 }}>{fmtR(g.allocated)}</span>}>
              <div style={{ display: "grid", gap: 10 }}>{g.items.map(i => (
                <div key={i.id}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 3 }}><span>{i.scopeValue}</span><span>{fmtR(i.amount)} · {i.sharePct.toFixed(0)}%</span></div>
                  <div style={{ background: "var(--hf-surface-sunken)", borderRadius: 4, height: 8 }}><div style={{ width: `${Math.max(2, i.sharePct)}%`, height: 8, borderRadius: 4, background: "var(--hf-primary)" }} /></div>
                </div>))}</div>
            </Panel>))}
          <Banner variant="info">Budgets for different kinds of scope (department, cost centre, project) can overlap, so these totals are not added together.</Banner>
        </div>
      ) : (
        <Panel title="Would this spend fit?">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 10 }}>
            <Field label="Department"><input style={inp} aria-label="Department" value={check.department} onChange={e => setCheck({ ...check, department: e.target.value })} /></Field>
            <Field label="Cost centre"><input style={inp} aria-label="Cost centre" value={check.costCentre} onChange={e => setCheck({ ...check, costCentre: e.target.value })} /></Field>
            <Field label="Project"><input style={inp} aria-label="Project" value={check.projectRef} onChange={e => setCheck({ ...check, projectRef: e.target.value })} /></Field>
            <Field label="Amount (R)"><input style={inp} aria-label="Amount to test" value={check.amount} onChange={e => setCheck({ ...check, amount: e.target.value })} /></Field>
          </div>
          {checkErr && <ErrBox msg={checkErr} />}{run.isError && <ErrBox msg={errorText(run.error)} />}
          <div style={{ margin: "10px 0" }}><button style={filterPill(true)} disabled={run.isPending} onClick={runCheck}>Check</button> <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Changes nothing.</span></div>
          {run.data && (run.data.findings.length === 0 ? <Banner variant="info">No budget applies to this spend.</Banner> : <>
            {run.data.hardStop ? <Banner variant="error">This would be blocked unless someone with override permission approves it with a reason.</Banner> : run.data.hasWarnings ? <Banner variant="warning">This would go through with a warning.</Banner> : <Banner variant="info">This fits.</Banner>}
            <ul style={{ margin: "10px 0 0", padding: 0, listStyle: "none", display: "grid", gap: 6, fontSize: 13 }}>{run.data.findings.map((f, i) => <li key={i}><strong>{f.budgetName}</strong> · {f.message} · available after {fmtR(f.availableAfter)}</li>)}</ul></>)}
        </Panel>
      )}

      {creating && (
        <Modal title="New budget" onClose={() => setCreating(false)}>
          {err && <ErrBox msg={err} />}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Name" span={2}><input style={inp} aria-label="Name" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field>
            <Field label="Covers"><select style={inp} value={form.scopeType} onChange={e => setForm({ ...form, scopeType: e.target.value })}>{SCOPES.map(s => <option key={s} value={s}>{s.replace("_", " ")}</option>)}</select></Field>
            <Field label="Which one"><input style={inp} aria-label="Scope value" value={form.scopeValue} onChange={e => setForm({ ...form, scopeValue: e.target.value })} /></Field>
            <Field label="From"><input style={inp} type="date" aria-label="From" value={form.periodStart} onChange={e => setForm({ ...form, periodStart: e.target.value })} /></Field>
            <Field label="To"><input style={inp} type="date" aria-label="To" value={form.periodEnd} onChange={e => setForm({ ...form, periodEnd: e.target.value })} /></Field>
            <Field label="Amount (R)"><input style={inp} aria-label="Amount" value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} /></Field>
            <Field label="When it runs out"><select style={inp} value={form.controlMode} onChange={e => setForm({ ...form, controlMode: e.target.value })}>{MODES.map(m => <option key={m} value={m}>{m.replace("_", " ")}</option>)}</select></Field>
          </div>
          <p style={{ fontSize: 12, opacity: 0.8 }}>{MODE_HELP[form.controlMode]}</p>
          <ModalFooter onCancel={() => setCreating(false)} onConfirm={submit} label="Create budget" loading={create.isPending} />
        </Modal>
      )}

      {edit && (
        <Modal title={`Edit ${edit.b.name}`} onClose={() => setEdit(null)}>
          {editErr && <ErrBox msg={editErr} />}
          <Field label="Amount (R)"><input style={inp} aria-label="New amount" value={edit.amount} onChange={e => setEdit({ ...edit, amount: e.target.value })} /></Field>
          <Field label="When it runs out"><select style={{ ...inp, marginTop: 8 }} value={edit.controlMode} onChange={e => setEdit({ ...edit, controlMode: e.target.value })}>{MODES.map(m => <option key={m} value={m}>{m.replace("_", " ")}</option>)}</select></Field>
          <label style={{ display: "block", fontSize: 13, marginTop: 10 }}><input type="checkbox" checked={edit.close} onChange={e => setEdit({ ...edit, close: e.target.checked })} /> Close this budget (it stops applying to new spend)</label>
          <ModalFooter onCancel={() => setEdit(null)} onConfirm={submitEdit} label="Save changes" loading={update.isPending} />
        </Modal>
      )}

      {open && (
        <Modal title={`${open.name} — what is committed`} wide onClose={() => setOpen(null)}>
          {commitments.isLoading ? <Spinner /> : (commitments.data ?? []).length === 0 ? <p>Nothing is committed against this budget yet.</p> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr>{["Order", "Supplier", "Status", "Committed", "Invoiced", "Counts as", "Date"].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
              <tbody>{(commitments.data ?? []).map(c => <tr key={c.purchaseOrderId} style={{ borderTop: "1px solid var(--hf-border)" }}><td style={TD}><strong>{c.orderNumber}</strong></td><td style={TD}>{c.supplierName}</td><td style={TD}>{c.poStatus.replace(/_/g, " ").toLowerCase()}</td>
                <td style={TD}>{fmtR(c.committed)}</td><td style={TD}>{fmtR(c.actual)}</td><td style={TD}>{fmtR(c.used)}</td><td style={TD}>{fmtDate(c.spendDate)}</td></tr>)}</tbody>
            </table>
          )}
          <p style={{ fontSize: 12, color: "var(--hf-text-tertiary)" }}>Counts as = the larger of committed and invoiced, so an invoice above its order uses the invoiced amount.</p>
        </Modal>
      )}
    </div>
  )
}
