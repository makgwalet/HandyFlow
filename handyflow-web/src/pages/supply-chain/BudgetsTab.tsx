// src/pages/supply-chain/BudgetsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, Wallet } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { inp, TH, TD, Modal, ModalFooter, Field, ErrBox, Spinner, EmptyState, filterPill, fmtR, fmtDate } from "./scm.shared"
import { asList, errorText, SCOPES, MODES, MODE_HELP, emptyBudget, budgetProblem, utilisationTone, type BudgetForm } from "./scmOps"

interface Budget { id: string; name: string; scopeType: string; scopeValue: string; periodStart: string; periodEnd: string; amount: number; committed: number; actual: number; used: number; available: number; utilisationPct: number; state: string; controlMode: string }
interface Commitment { reference?: string; kind?: string; amount: number; description?: string }

const BASE = "/api/v1/supply-chain/budgets"
const TONE = { ok: "var(--hf-success-text-strong)", warn: "var(--hf-warning-text-deep)", bad: "var(--hf-danger-text)" }

export function BudgetsTab() {
  const qc = useQueryClient()
  const canManage = usePermissions(["SCM_BUDGET_MANAGE", "SCM_ADMIN"])
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState<BudgetForm>(emptyBudget())
  const [err, setErr] = useState("")
  const [open, setOpen] = useState<Budget | null>(null)

  const list = useQuery({ queryKey: ["scm-budgets"], queryFn: () => apiClient.get(BASE).then(asList<Budget>) })
  const commitments = useQuery({
    queryKey: ["scm-budget-commitments", open?.id], enabled: !!open,
    queryFn: () => apiClient.get(`${BASE}/${open!.id}/commitments`).then(asList<Commitment>),
  })

  const create = useMutation({
    mutationFn: () => apiClient.post(BASE, { name: form.name.trim(), scopeType: form.scopeType, scopeValue: form.scopeValue.trim(), periodStart: form.periodStart, periodEnd: form.periodEnd, amount: Number(form.amount), controlMode: form.controlMode, currency: "ZAR" }),
    onSuccess: () => { setCreating(false); setForm(emptyBudget()); setErr(""); qc.invalidateQueries({ queryKey: ["scm-budgets"] }) },
    // An overlapping budget for the same scope comes back as a 409 with a plain message; show it as is.
    onError: (e) => setErr(errorText(e)),
  })

  const submit = () => {
    const p = budgetProblem(form)
    if (p) { setErr(p); return }
    setErr(""); create.mutate()
  }

  const rows = list.data ?? []
  return (
    <div style={{ padding: 20 }}>
      <div style={{ display: "flex", marginBottom: 14 }}>
        <div style={{ flex: 1 }} />
        {canManage && <button onClick={() => setCreating(true)} style={{ ...filterPill(true), display: "flex", gap: 6, alignItems: "center" }}><Plus size={14} /> New budget</button>}
      </div>

      {list.isLoading ? <Spinner /> : list.isError ? <ErrBox msg={errorText(list.error, "Could not load budgets")} /> : rows.length === 0 ? (
        <EmptyState icon={Wallet} title="No budgets" sub="Set a budget for a department, cost centre or project to track what is committed and spent." />
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr>{["Budget", "Covers", "Period", "Amount", "Committed", "Actual", "Available", "Used", "Control"].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
          <tbody>
            {rows.map(b => (
              <tr key={b.id} onClick={() => setOpen(b)} style={{ cursor: "pointer" }}>
                <td style={TD}>{b.name}</td>
                <td style={TD}>{b.scopeType.replace("_", " ")}: {b.scopeValue}</td>
                <td style={TD}>{fmtDate(b.periodStart)} – {fmtDate(b.periodEnd)}</td>
                <td style={TD}>{fmtR(b.amount)}</td>
                <td style={TD}>{fmtR(b.committed)}</td>
                <td style={TD}>{fmtR(b.actual)}</td>
                <td style={TD}>{fmtR(b.available)}</td>
                <td style={{ ...TD, color: TONE[utilisationTone(b.state)], fontWeight: 700 }}>{Math.round(b.utilisationPct)}%{b.state === "EXCEEDED" ? " — exceeded" : b.state === "NEAR_LIMIT" ? " — near limit" : ""}</td>
                <td style={TD}>{b.controlMode.replace("_", " ")}</td>
              </tr>
            ))}
          </tbody>
        </table>
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

      {open && (
        <Modal title={`${open.name} — what is committed`} wide onClose={() => setOpen(null)}>
          {commitments.isLoading ? <Spinner /> : (commitments.data ?? []).length === 0 ? <p>Nothing is committed against this budget yet.</p> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr>{["Reference", "Kind", "Amount"].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
              <tbody>{(commitments.data ?? []).map((c, i) => <tr key={i}><td style={TD}>{c.reference ?? c.description ?? "—"}</td><td style={TD}>{c.kind ?? "—"}</td><td style={TD}>{fmtR(c.amount)}</td></tr>)}</tbody>
            </table>
          )}
        </Modal>
      )}
    </div>
  )
}
