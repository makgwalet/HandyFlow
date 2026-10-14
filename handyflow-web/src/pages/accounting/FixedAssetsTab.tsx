// src/pages/accounting/FixedAssetsTab.tsx
//
// Finance F25: the fixed-asset register. Adding an asset does NOT post anything (its cost is already in the ledger);
// the register is what depreciation is worked out from, and it is checked against the ledger at the bottom.
// Disposals make a draft journal that a different person posts.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { emptyForm, formOf, formProblem, lifeText, METHOD_LABEL, money, suggestAccount, toRequest, type Account, type Asset, type FormState } from "./fixedAsset.logic"
import { labelOf, type CostCentre } from "./costCentre.logic"

interface Recon { accountId: string; code: string; name: string; role: string; ledger: number; register: number; difference: number }
const unwrap = (r: any) => r.data?.data ?? r.data
const errorOf = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14, overflowX: "auto" }
const btn: React.CSSProperties = { padding: "7px 13px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontWeight: 600, fontSize: 12, cursor: "pointer" }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function FixedAssetsTab() {
  const qc = useQueryClient()
  const canManage = usePermission("ACCOUNTING_MANAGE")
  const [form, setForm] = useState<FormState>(emptyForm())
  const [editing, setEditing] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [disposing, setDisposing] = useState<Asset | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const set = (k: keyof FormState, v: string) => setForm(f => ({ ...f, [k]: v }))

  const { data: assets = [] } = useQuery<Asset[]>({ queryKey: ["acc-fixed-assets"], queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/fixed-assets")) ?? [] })
  const { data: accounts = [] } = useQuery<Account[]>({ queryKey: ["coa"], queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/accounts")) ?? [] })
  const { data: centres = [] } = useQuery<CostCentre[]>({ queryKey: ["acc-cost-centres"], queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/cost-centres")) ?? [] })
  const { data: recon = [] } = useQuery<Recon[]>({ queryKey: ["acc-fixed-recon"], queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/fixed-assets/reconciliation")) ?? [] })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["acc-fixed-assets"] }); qc.invalidateQueries({ queryKey: ["acc-fixed-recon"] }); qc.invalidateQueries({ queryKey: ["journals"] }) }

  const save = useMutation({
    mutationFn: () => editing ? apiClient.put(`/api/v1/accounting/fixed-assets/${editing}`, toRequest(form)) : apiClient.post("/api/v1/accounting/fixed-assets", toRequest(form)),
    onSuccess: () => { setShowForm(false); setEditing(null); setError(null); refresh() },
    onError: e => setError(errorOf(e)),
  })

  const open = () => {
    setEditing(null); setError(null)
    setForm({ ...emptyForm(), costAccountId: suggestAccount(accounts, "cost"), accumulatedAccountId: suggestAccount(accounts, "accumulated"), expenseAccountId: suggestAccount(accounts, "expense") })
    setShowForm(true)
  }
  const problem = formProblem(form)
  const active = assets.filter(a => a.status === "ACTIVE")
  const totals = active.reduce((t, a) => ({ cost: t.cost + a.cost, acc: t.acc + a.accumulated, nbv: t.nbv + a.bookValue }), { cost: 0, acc: 0, nbv: 0 })
  const assetAccounts = accounts.filter(a => a.accountType === "ASSET"), expenseAccounts = accounts.filter(a => a.accountType === "EXPENSE")
  const opt = (a: Account) => <option key={a.id} value={a.id}>{a.accountCode} — {a.accountName}</option>

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Fixed assets</h2>
      {notice && <div role="status" style={{ ...card, background: "var(--hf-success-soft)" }}>{notice}</div>}
      {canManage && !showForm && <button style={{ ...btn, marginBottom: 12 }} onClick={open}>Add an asset</button>}
      {showForm && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>{editing ? "Edit asset" : "New asset"}</h3>
          <p style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginTop: 0 }}>
            This records the asset for depreciation. It does not post a journal: the purchase is already in your books through the bill or journal that paid for it.
          </p>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(190px, 1fr))", gap: 8 }}>
            <input aria-label="Name" placeholder="Name" value={form.name} onChange={e => set("name", e.target.value)} style={field} />
            <input aria-label="Acquired on" type="date" value={form.acquiredOn} onChange={e => set("acquiredOn", e.target.value)} style={field} />
            <input aria-label="Cost" type="number" placeholder="Cost" value={form.cost} onChange={e => set("cost", e.target.value)} style={field} />
            <input aria-label="Residual value" type="number" placeholder="Residual value" value={form.residualValue} onChange={e => set("residualValue", e.target.value)} style={field} />
            <select aria-label="Method" value={form.method} onChange={e => set("method", e.target.value)} style={field}>
              <option value="STRAIGHT_LINE">Straight line</option><option value="REDUCING_BALANCE">Reducing balance</option></select>
            {form.method === "STRAIGHT_LINE"
              ? <input aria-label="Useful life (months)" type="number" placeholder="Useful life in months" value={form.lifeMonths} onChange={e => set("lifeMonths", e.target.value)} style={field} />
              : <input aria-label="Yearly rate (%)" type="number" placeholder="Yearly rate %" value={form.ratePercent} onChange={e => set("ratePercent", e.target.value)} style={field} />}
            <select aria-label="Cost account" value={form.costAccountId} onChange={e => set("costAccountId", e.target.value)} style={field}><option value="">Cost account…</option>{assetAccounts.map(opt)}</select>
            <select aria-label="Accumulated depreciation account" value={form.accumulatedAccountId} onChange={e => set("accumulatedAccountId", e.target.value)} style={field}><option value="">Accumulated depreciation account…</option>{assetAccounts.map(opt)}</select>
            <select aria-label="Depreciation expense account" value={form.expenseAccountId} onChange={e => set("expenseAccountId", e.target.value)} style={field}><option value="">Depreciation expense account…</option>{expenseAccounts.map(opt)}</select>
            <select aria-label="Cost centre" value={form.costCentreId} onChange={e => set("costCentreId", e.target.value)} style={field}>
              <option value="">No cost centre</option>{centres.filter(c => c.active).map(c => <option key={c.id} value={c.id}>{labelOf(c)}</option>)}</select>
            <input aria-label="Serial number" placeholder="Serial / registration" value={form.serialNumber} onChange={e => set("serialNumber", e.target.value)} style={field} />
            <input aria-label="Location" placeholder="Location" value={form.location} onChange={e => set("location", e.target.value)} style={field} />
            <input aria-label="Description" placeholder="Description" value={form.description} onChange={e => set("description", e.target.value)} style={field} />
          </div>
          {problem && <div style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginTop: 6 }}>{problem}</div>}
          {error && <div role="alert" style={{ color: "var(--hf-danger-text, #b91c1c)", fontSize: 12, marginTop: 6 }}>{error}</div>}
          <div style={{ marginTop: 10, display: "flex", gap: 8 }}>
            <button style={btn} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>{editing ? "Save" : "Add asset"}</button>
            <button style={btn} onClick={() => { setShowForm(false); setEditing(null); setError(null) }}>Cancel</button>
          </div>
        </div>
      )}

      {assets.length === 0 ? <p style={{ fontSize: 13 }}>No assets in the register yet.</p> : (
        <div style={card}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)", fontSize: 11, textTransform: "uppercase" }}>
              <th>No.</th><th>Asset</th><th>Acquired</th><th>Method</th><th style={{ textAlign: "right" }}>Cost</th><th style={{ textAlign: "right" }}>Accumulated</th><th style={{ textAlign: "right" }}>Book value</th><th /></tr></thead>
            <tbody>
              {assets.map(a => (
                <tr key={a.id} style={{ borderTop: "1px solid var(--hf-border)", opacity: a.status === "ACTIVE" ? 1 : 0.6 }}>
                  <td style={{ fontFamily: "monospace" }}>{a.assetNumber}</td>
                  <td>{a.name}{a.status === "DISPOSED" && <span> (disposed {a.disposedOn})</span>}</td>
                  <td>{a.acquiredOn}</td>
                  <td>{METHOD_LABEL[a.method] ?? a.method}, {lifeText(a)}</td>
                  <td style={{ textAlign: "right" }}>{money(a.cost)}</td>
                  <td style={{ textAlign: "right" }}>{money(a.accumulated)}</td>
                  <td style={{ textAlign: "right", fontWeight: 600 }}>{money(a.bookValue)}</td>
                  <td style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                    {canManage && a.status === "ACTIVE" && <>
                      <button style={btn} onClick={() => { setEditing(a.id); setForm(formOf(a)); setShowForm(true); setError(null) }}>Edit</button>{" "}
                      <button style={btn} onClick={() => { setDisposing(a); setError(null) }}>Dispose</button></>}
                  </td>
                </tr>
              ))}
              <tr style={{ borderTop: "2px solid var(--hf-border)", fontWeight: 700 }}>
                <td colSpan={4}>Active assets</td><td style={{ textAlign: "right" }}>{money(totals.cost)}</td><td style={{ textAlign: "right" }}>{money(totals.acc)}</td><td style={{ textAlign: "right" }}>{money(totals.nbv)}</td><td /></tr>
            </tbody>
          </table>
        </div>
      )}

      {disposing && <Dispose asset={disposing} accounts={accounts} onClose={() => setDisposing(null)}
        onDone={msg => { setDisposing(null); setNotice(msg); refresh() }} />}

      {recon.length > 0 && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>Register against the ledger</h3>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)", fontSize: 11, textTransform: "uppercase" }}>
              <th>Account</th><th style={{ textAlign: "right" }}>Ledger</th><th style={{ textAlign: "right" }}>Register</th><th style={{ textAlign: "right" }}>Difference</th></tr></thead>
            <tbody>{recon.map(r => (
              <tr key={r.accountId + r.role} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td>{r.code} {r.name} <span style={{ color: "var(--hf-text-muted)" }}>({r.role === "COST" ? "cost" : "accumulated depreciation"})</span></td>
                <td style={{ textAlign: "right" }}>{money(r.ledger)}</td><td style={{ textAlign: "right" }}>{money(r.register)}</td>
                <td style={{ textAlign: "right", fontWeight: 600, color: Math.abs(r.difference) < 0.005 ? undefined : "var(--hf-danger-text, #b91c1c)" }}>{Math.abs(r.difference) < 0.005 ? "Agrees" : money(r.difference)}</td></tr>))}</tbody>
          </table>
          <p style={{ fontSize: 11, color: "var(--hf-text-secondary)", marginBottom: 0 }}>
            The ledger counts posted journals only, so depreciation waiting for a second person to post shows as a difference until it is posted.
          </p>
        </div>
      )}
    </div>
  )
}

function Dispose({ asset, accounts, onClose, onDone }: { asset: Asset; accounts: Account[]; onClose: () => void; onDone: (msg: string) => void }) {
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10))
  const [proceeds, setProceeds] = useState("0")
  const [proceedsAccount, setProceedsAccount] = useState("")
  const [gainLossAccount, setGainLossAccount] = useState("")
  const [error, setError] = useState<string | null>(null)
  const p = Number(proceeds) || 0
  const result = p - asset.bookValue
  const go = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/accounting/fixed-assets/${asset.id}/dispose`, { disposedOn: date, proceeds: p, proceedsAccountId: proceedsAccount || null, gainLossAccountId: gainLossAccount || null }),
    onSuccess: r => { const d = unwrap(r); onDone(`Disposal of ${asset.assetNumber} drafted as journal ${d.journalNumber}. A different person must post it.`) },
    onError: e => setError(errorOf(e)),
  })
  return (
    <div style={card} role="dialog" aria-label="Dispose of asset">
      <h3 style={{ marginTop: 0 }}>Dispose of {asset.assetNumber} — {asset.name}</h3>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <input aria-label="Disposal date" type="date" value={date} onChange={e => setDate(e.target.value)} style={field} />
        <input aria-label="Proceeds" type="number" value={proceeds} onChange={e => setProceeds(e.target.value)} style={field} />
        <select aria-label="Proceeds received into" value={proceedsAccount} onChange={e => setProceedsAccount(e.target.value)} style={field}>
          <option value="">Proceeds received into…</option>{accounts.filter(a => a.accountType === "ASSET").map(a => <option key={a.id} value={a.id}>{a.accountCode} — {a.accountName}</option>)}</select>
        <select aria-label="Gain or loss account" value={gainLossAccount} onChange={e => setGainLossAccount(e.target.value)} style={field}>
          <option value="">Gain or loss account…</option>{accounts.filter(a => a.accountType === "INCOME" || a.accountType === "EXPENSE").map(a => <option key={a.id} value={a.id}>{a.accountCode} — {a.accountName}</option>)}</select>
      </div>
      <p style={{ fontSize: 12 }}>Book value {money(asset.bookValue)}. {result >= 0 ? "Gain" : "Loss"} on disposal {money(Math.abs(result))}. No depreciation is charged in the month of disposal.</p>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text, #b91c1c)", fontSize: 12 }}>{error}</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button style={btn} disabled={go.isPending || !gainLossAccount || (p > 0 && !proceedsAccount)} onClick={() => go.mutate()}>Draft the disposal journal</button>
        <button style={btn} onClick={onClose}>Cancel</button>
      </div>
    </div>
  )
}
