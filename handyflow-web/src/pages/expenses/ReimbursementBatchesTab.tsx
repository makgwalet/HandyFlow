// src/pages/expenses/ReimbursementBatchesTab.tsx
// Finance patch F18: approved claims owed to staff, grouped by employee; pay a chosen set as one batch.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { batchTotal } from "./expensePolicy.logic"

const unwrap = (r: any) => r.data?.data ?? r.data
const fmtR = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 12 }
const btn: React.CSSProperties = { padding: "7px 13px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontWeight: 600, fontSize: 12, cursor: "pointer" }
const input: React.CSSProperties = { padding: "7px 9px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function ReimbursementBatchesTab() {
  const qc = useQueryClient()
  const canPay = usePermission("EXPENSES_ADMIN")
  const [chosen, setChosen] = useState<Set<string>>(new Set())
  const [payFor, setPayFor] = useState<string | null>(null)
  const [bank, setBank] = useState("")
  const [reference, setReference] = useState("")
  const [error, setError] = useState<string | null>(null)

  const { data: owed = [] } = useQuery<any[]>({ queryKey: ["expense-owed"], queryFn: async () => unwrap(await apiClient.get("/api/v1/expenses/reimbursements/owed")) ?? [] })
  const { data: batches = [] } = useQuery<any[]>({ queryKey: ["expense-batches"], queryFn: async () => unwrap(await apiClient.get("/api/v1/expenses/reimbursements/batches")) ?? [] })
  const { data: banks = [] } = useQuery<any[]>({
    queryKey: ["expense-banks"], enabled: canPay,
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/bank-accounts")) ?? [],
  })

  const refresh = () => { setError(null); qc.invalidateQueries({ queryKey: ["expense-owed"] }); qc.invalidateQueries({ queryKey: ["expense-batches"] }) }
  const fail = (e: any) => setError(e?.response?.data?.message ?? "Something went wrong. Please try again.")
  const create = useMutation({
    mutationFn: () => apiClient.post("/api/v1/expenses/reimbursements/batches", { claimIds: [...chosen] }),
    onSuccess: () => { setChosen(new Set()); refresh() }, onError: fail,
  })
  const pay = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/expenses/reimbursements/batches/${id}/pay`, { bankAccountId: bank || null, reference: reference || null }),
    onSuccess: () => { setPayFor(null); setBank(""); setReference(""); refresh() }, onError: fail,
  })
  const cancel = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/expenses/reimbursements/batches/${id}/cancel`),
    onSuccess: refresh, onError: fail,
  })

  const claims = owed.flatMap(o => o.claims as any[])
  const toggle = (id: string) => setChosen(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n })
  const needsBank = banks.length > 0

  return (
    <div>
      {error && <div role="alert" style={{ ...card, color: "var(--hf-danger-text)", background: "var(--hf-danger-soft)" }}>{error}</div>}

      <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 8 }}>Approved and not yet paid</div>
      {owed.length === 0 ? <div style={{ color: "var(--hf-text-faint)", fontSize: 13, marginBottom: 12 }}>Nothing is owed to staff.</div> : owed.map(o => (
        <div key={o.employeeName} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between" }}><strong>{o.employeeName}</strong><strong>{fmtR(o.total)}</strong></div>
          {o.claims.map((c: any) => (
            <label key={c.claimId} style={{ display: "flex", gap: 8, fontSize: 12, marginTop: 6, alignItems: "center" }}>
              {canPay && <input type="checkbox" aria-label={`Include ${c.claimNumber}`} disabled={c.inBatch} checked={chosen.has(c.claimId)} onChange={() => toggle(c.claimId)} />}
              <span>{c.claimNumber} · {c.claimDate} · {c.category.replace(/_/g, " ")} · {fmtR(c.amount)}{c.inBatch ? " (in a batch)" : ""}</span>
            </label>
          ))}
        </div>
      ))}
      {canPay && chosen.size > 0 && (
        <div style={{ marginBottom: 16 }}>
          <button style={btn} disabled={create.isPending} onClick={() => create.mutate()}>Make a batch of {chosen.size} claim(s), {fmtR(batchTotal(claims, chosen))}</button>
        </div>
      )}

      <div style={{ fontWeight: 700, fontSize: 13, margin: "16px 0 8px" }}>Batches</div>
      {batches.length === 0 ? <div style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No batches yet.</div> : batches.map(b => (
        <div key={b.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <strong>{b.batchNumber}</strong><span>{b.status === "DRAFT" ? "Unpaid" : b.status === "PAID" ? "Paid" : "Cancelled"} · {fmtR(b.totalAmount)}</span>
          </div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0" }}>
            {b.items.map((i: any) => `${i.employeeName} ${i.claimNumber} ${fmtR(i.amount)}`).join(" · ")}
          </div>
          {b.reference && <div style={{ fontSize: 12 }}>Reference {b.reference}</div>}
          {canPay && b.status === "DRAFT" && payFor !== b.id && (
            <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
              <button style={btn} onClick={() => setPayFor(b.id)}>Pay batch</button>
              <button style={btn} onClick={() => cancel.mutate(b.id)}>Cancel batch</button>
            </div>
          )}
          {payFor === b.id && (
            <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
              {needsBank && (
                <select aria-label="Paid from" value={bank} onChange={e => setBank(e.target.value)} style={input}>
                  <option value="">Paid from…</option>
                  {banks.map((a: any) => <option key={a.id} value={a.id}>{a.bankName} {a.accountName}</option>)}
                </select>
              )}
              <input aria-label="Payment reference" placeholder={`Reference (default ${b.batchNumber})`} value={reference} onChange={e => setReference(e.target.value)} style={input} />
              <button style={btn} disabled={pay.isPending || (needsBank && !bank)} onClick={() => pay.mutate(b.id)}>Confirm payment</button>
              <button style={btn} onClick={() => setPayFor(null)}>Cancel</button>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}
