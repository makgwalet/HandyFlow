// src/pages/accounting/BudgetsTab.tsx
//
// Finance F21: yearly budgets by income and expense account, month by month, and the budget against what actually happened.
// A draft can be edited and filled from last year; once activated it is fixed (make a new budget to change it).
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { fmtR, gridProblem, gridToLines, monthLabels, rowTotal, STATUS_LABEL, varianceText, type GridRow } from "./budget.logic"

interface Summary { id: string; name: string; startDate: string; endDate: string; status: string; income: number; expense: number; lines: number }
interface Account { id: string; accountCode: string; accountName: string; accountType: string }
interface Detail { summary: Summary; accounts: { accountId: string; code: string; name: string; type: string; months: number[] }[] }
interface VRow { accountId: string; code: string; name: string; type: string; budget: number; actual: number; variance: number; percent: number | null; favourable: boolean }
interface Report { name: string; from: string; through: string; monthsCounted: number; rows: VRow[]; budgetIncome: number; actualIncome: number; budgetExpense: number; actualExpense: number; budgetNet: number; actualNet: number }

const unwrap = (r: any) => r.data?.data ?? r.data
const errorOf = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14 }
const btn: React.CSSProperties = { padding: "7px 13px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontWeight: 600, fontSize: 12, cursor: "pointer" }
const cell: React.CSSProperties = { width: 64, padding: "4px 5px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 11, textAlign: "right", background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function BudgetsTab() {
  const qc = useQueryClient()
  const canManage = usePermission("ACCOUNTING_MANAGE")
  const canAdmin = usePermission("ACCOUNTING_ADMIN")
  const [openId, setOpenId] = useState<string | null>(null)
  const [name, setName] = useState("")
  const [start, setStart] = useState("")
  const [error, setError] = useState<string | null>(null)

  const { data: budgets = [] } = useQuery<Summary[]>({ queryKey: ["acc-budgets"], queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/budgets")) ?? [] })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["acc-budgets"] }); qc.invalidateQueries({ queryKey: ["acc-budget"] }); qc.invalidateQueries({ queryKey: ["acc-budget-vs"] }) }
  const create = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/budgets", { name: name.trim(), startDate: start }),
    onSuccess: r => { setError(null); setName(""); setStart(""); refresh(); setOpenId(unwrap(r).id) },
    onError: e => setError(errorOf(e)),
  })

  if (openId) return <BudgetEditor id={openId} onBack={() => { setOpenId(null); refresh() }} canManage={canManage} canAdmin={canAdmin} />

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Budgets</h2>
      {budgets.length === 0 && <p style={{ fontSize: 13 }}>No budgets yet.</p>}
      {budgets.map(b => (
        <div key={b.id} style={{ ...card, display: "flex", justifyContent: "space-between", alignItems: "center", cursor: "pointer" }} onClick={() => setOpenId(b.id)}>
          <div><b>{b.name}</b> <span style={{ fontSize: 12, marginLeft: 8 }}>{STATUS_LABEL[b.status] ?? b.status}</span>
            <div style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>{b.startDate} to {b.endDate}</div></div>
          <div style={{ fontSize: 12, textAlign: "right" }}>Income {fmtR(b.income)}<br />Expenses {fmtR(b.expense)}</div>
        </div>
      ))}
      {canManage && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>New budget</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <input aria-label="Budget name" placeholder="e.g. Budget 2027" value={name} onChange={e => setName(e.target.value)} style={{ ...cell, width: 220, textAlign: "left" }} />
            <input aria-label="Starts on" type="date" value={start} onChange={e => setStart(e.target.value)} style={{ ...cell, width: 140, textAlign: "left" }} />
            <button style={btn} disabled={!name.trim() || !start || create.isPending} onClick={() => create.mutate()}>Create</button>
          </div>
          <p style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginBottom: 0 }}>A budget covers 12 months and starts on the first day of a month.</p>
          {error && <div role="alert" style={{ color: "var(--hf-danger-text, #b91c1c)", fontSize: 12, marginTop: 6 }}>{error}</div>}
        </div>
      )}
    </div>
  )
}

function BudgetEditor({ id, onBack, canManage, canAdmin }: { id: string; onBack: () => void; canManage: boolean; canAdmin: boolean }) {
  const qc = useQueryClient()
  const [rows, setRows] = useState<GridRow[]>([])
  const [uplift, setUplift] = useState("0")
  const [add, setAdd] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const { data } = useQuery<Detail>({ queryKey: ["acc-budget", id], queryFn: async () => unwrap(await apiClient.get(`/api/v1/accounting/budgets/${id}`)) })
  const { data: accounts = [] } = useQuery<Account[]>({ queryKey: ["coa"], queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/accounts")) })
  const status = data?.summary.status
  const draft = status === "DRAFT"
  const { data: report } = useQuery<Report>({
    queryKey: ["acc-budget-vs", id], enabled: !!status && status !== "DRAFT",
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accounting/budgets/${id}/vs-actual`)),
  })

  useEffect(() => {
    if (data) setRows(data.accounts.map(a => ({ accountId: a.accountId, code: a.code, name: a.name, type: a.type, months: a.months.map(m => (m ? String(m) : "")) })))
  }, [data])

  const done = (msg?: string) => { setError(null); if (msg) setNotice(msg); qc.invalidateQueries({ queryKey: ["acc-budget", id] }); qc.invalidateQueries({ queryKey: ["acc-budgets"] }); qc.invalidateQueries({ queryKey: ["acc-budget-vs", id] }) }
  const fail = (e: any) => { setNotice(null); setError(errorOf(e)) }
  const save = useMutation({ mutationFn: () => apiClient.put(`/api/v1/accounting/budgets/${id}/lines`, { lines: gridToLines(rows) }), onSuccess: () => done("Saved."), onError: fail })
  const copy = useMutation({ mutationFn: () => apiClient.post(`/api/v1/accounting/budgets/${id}/copy-actuals`, { upliftPercent: Number(uplift) || 0 }), onSuccess: () => done("Filled from the 12 months before this budget starts."), onError: fail })
  const activate = useMutation({ mutationFn: () => apiClient.post(`/api/v1/accounting/budgets/${id}/activate`), onSuccess: () => done("The budget is active."), onError: fail })
  const close = useMutation({ mutationFn: () => apiClient.post(`/api/v1/accounting/budgets/${id}/close`), onSuccess: () => done("The budget is closed."), onError: fail })
  const del = useMutation({ mutationFn: () => apiClient.delete(`/api/v1/accounting/budgets/${id}`), onSuccess: onBack, onError: fail })

  if (!data) return <p>Loading…</p>
  const labels = monthLabels(data.summary.startDate)
  const problem = gridProblem(rows)
  const setCell = (r: number, m: number, v: string) => setRows(rows.map((x, i) => i === r ? { ...x, months: x.months.map((c, k) => k === m ? v : c) } : x))
  const addable = accounts.filter(a => (a.accountType === "INCOME" || a.accountType === "EXPENSE") && !rows.some(r => r.accountId === a.id))

  return (
    <div>
      <button style={btn} onClick={onBack}>← All budgets</button>
      <h2>{data.summary.name} <span style={{ fontSize: 13, fontWeight: 500 }}>{STATUS_LABEL[status!]} · {data.summary.startDate} to {data.summary.endDate}</span></h2>
      {notice && <div role="status" style={{ ...card, fontSize: 13 }}>{notice}</div>}
      {error && <div role="alert" style={{ ...card, fontSize: 13, color: "var(--hf-danger-text, #b91c1c)" }}>{error}</div>}

      {report && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>Budget against actual, {report.from} to {report.through}</h3>
          <div style={{ display: "flex", gap: 24, flexWrap: "wrap", fontSize: 13, marginBottom: 8 }}>
            <span>Income: budget {fmtR(report.budgetIncome)}, actual <b>{fmtR(report.actualIncome)}</b></span>
            <span>Expenses: budget {fmtR(report.budgetExpense)}, actual <b>{fmtR(report.actualExpense)}</b></span>
            <span>Profit: budget {fmtR(report.budgetNet)}, actual <b>{fmtR(report.actualNet)}</b></span>
          </div>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr style={{ textAlign: "right", color: "var(--hf-text-secondary)" }}><th style={{ textAlign: "left" }}>Account</th><th>Budget</th><th>Actual</th><th style={{ textAlign: "left", paddingLeft: 14 }}>Difference</th></tr></thead>
            <tbody>{report.rows.map(r => (
              <tr key={r.accountId} style={{ textAlign: "right", borderTop: "1px solid var(--hf-border)" }}>
                <td style={{ textAlign: "left", padding: "4px 0" }}>{r.code} {r.name}</td><td>{fmtR(r.budget)}</td><td>{fmtR(r.actual)}</td>
                <td style={{ textAlign: "left", paddingLeft: 14, color: r.favourable ? undefined : "var(--hf-danger-text, #b91c1c)" }}>{varianceText(r.variance, r.percent, r.favourable)}</td></tr>
            ))}</tbody>
          </table>
        </div>
      )}

      <div style={{ ...card, overflowX: "auto" }}>
        <table style={{ borderCollapse: "collapse", fontSize: 11 }}>
          <thead><tr><th style={{ textAlign: "left", paddingRight: 8 }}>Account</th>{labels.map(l => <th key={l}>{l}</th>)}<th style={{ paddingLeft: 8 }}>Year</th></tr></thead>
          <tbody>{rows.map((r, i) => (
            <tr key={r.accountId}>
              <td style={{ paddingRight: 8, whiteSpace: "nowrap" }}>{r.code} {r.name}</td>
              {r.months.map((v, m) => <td key={m}>{draft && canManage
                ? <input aria-label={`${r.code} ${labels[m]}`} value={v} onChange={e => setCell(i, m, e.target.value)} style={cell} />
                : <span style={{ display: "block", textAlign: "right", padding: "4px 5px" }}>{v ? Number(v).toLocaleString("en-ZA") : "–"}</span>}</td>)}
              <td style={{ paddingLeft: 8, textAlign: "right", fontWeight: 700 }}>{fmtR(rowTotal(r))}</td>
            </tr>
          ))}</tbody>
        </table>
        {rows.length === 0 && <p style={{ fontSize: 13 }}>No lines yet.{draft && canManage ? " Add an account below or fill from last year." : ""}</p>}
        {problem && <div role="alert" style={{ color: "var(--hf-danger-text, #b91c1c)", fontSize: 12, marginTop: 6 }}>{problem}</div>}
      </div>

      {draft && canManage && (
        <div style={card}>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 10 }}>
            <select aria-label="Add account" value={add} onChange={e => setAdd(e.target.value)} style={{ ...cell, width: 260, textAlign: "left" }}>
              <option value="">Add an income or expense account…</option>
              {addable.map(a => <option key={a.id} value={a.id}>{a.accountCode} {a.accountName}</option>)}
            </select>
            <button style={btn} disabled={!add} onClick={() => {
              const a = accounts.find(x => x.id === add); if (!a) return
              setRows([...rows, { accountId: a.id, code: a.accountCode, name: a.accountName, type: a.accountType, months: Array(12).fill("") }]); setAdd("")
            }}>Add</button>
          </div>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 10 }}>
            <label style={{ fontSize: 12 }}>Fill from last year, plus <input aria-label="Uplift percent" value={uplift} onChange={e => setUplift(e.target.value)} style={{ ...cell, width: 50 }} /> %</label>
            <button style={btn} disabled={copy.isPending} onClick={() => { if (rows.length === 0 || window.confirm("This replaces the lines you have now.")) copy.mutate() }}>Fill</button>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button style={btn} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>Save</button>
            {canAdmin && <button style={btn} disabled={activate.isPending} onClick={() => { if (window.confirm("Once active, a budget cannot be changed. Activate it?")) activate.mutate() }}>Activate</button>}
            <button style={btn} disabled={del.isPending} onClick={() => { if (window.confirm("Delete this draft budget?")) del.mutate() }}>Delete draft</button>
          </div>
          {!canAdmin && <p style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginBottom: 0 }}>Someone with accounting admin rights activates the budget.</p>}
        </div>
      )}
      {status === "ACTIVE" && canAdmin && <button style={btn} onClick={() => { if (window.confirm("Close this budget?")) close.mutate() }}>Close budget</button>}
    </div>
  )
}
