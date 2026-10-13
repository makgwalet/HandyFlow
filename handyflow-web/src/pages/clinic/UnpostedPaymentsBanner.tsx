// Top of the Claims tab: scheme payments received and write-offs that have not reached the accounts yet (patch 0185).
// Write-offs post on their own; a scheme payment waits here until someone says which bank account it was paid into.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { bannerSummary, needsBankChoice, postBody, postingTitle, type BankChoice, type Posting } from "./postingView"

const KEY = ["clinic-money-postings"]
const unwrap = (r: any) => { const p = r?.data?.data ?? r?.data; return Array.isArray(p) ? p : (p?.content ?? []) }

export default function UnpostedPaymentsBanner() {
  const qc = useQueryClient()
  const canPost = usePermission("CLINIC_PAYMENT_ALLOCATE")
  const [bank, setBank] = useState("")
  const [error, setError] = useState("")
  const { data: items = [] } = useQuery<Posting[]>({ queryKey: KEY, queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/billing/money-postings")) })
  const { data: banks = [] } = useQuery<BankChoice[]>({
    queryKey: [...KEY, "banks"], enabled: canPost && items.some(i => i.needsBank),
    queryFn: async () => unwrap(await apiClient.get("/api/v1/clinic/billing/money-postings/banks")),
  })
  const post = useMutation({
    mutationFn: async () => {
      const r = postBody(items, bank)
      if (r.error) throw new Error(r.error)
      const res: any = await apiClient.post("/api/v1/clinic/billing/money-postings/post", r.body)
      return res?.data?.data ?? res?.data
    },
    onSuccess: (r: any) => {
      setError(r?.failed?.length ? `${r.failed.length} could not be posted: ${r.failed[0].reason}` : "")
      qc.invalidateQueries({ queryKey: KEY }); qc.invalidateQueries({ queryKey: ["clinic-claims"] })
    },
    onError: (e: any) => setError(e?.message ?? "Could not post."),
  })

  if (items.length === 0) return null
  return (
    <div role="status" style={{ border: "1px solid var(--hf-warning-border, #d4a017)", background: "var(--hf-warning-bg, #fff8e1)", borderRadius: 12, padding: "12px 16px", marginBottom: 16 }}>
      <div style={{ fontWeight: 700, fontSize: 14 }}>{bannerSummary(items)}</div>
      <ul style={{ margin: "6px 0 10px", paddingLeft: 18, fontSize: 13 }}>
        {items.map(i => <li key={i.id}>{postingTitle(i)}{i.status === "FAILED" && i.reason ? ` — ${i.reason}` : ""}</li>)}
      </ul>
      {canPost
        ? <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            {needsBankChoice(items) && <>
              <label htmlFor="mp-bank" style={{ fontSize: 13 }}>Paid into</label>
              <select id="mp-bank" value={bank} onChange={e => setBank(e.target.value)} style={{ padding: "6px 8px" }}>
                <option value="">Choose a bank account</option>
                {banks.map(b => <option key={b.id} value={b.id}>{b.label}</option>)}
              </select></>}
            <button type="button" disabled={post.isPending} onClick={() => post.mutate()} style={{ padding: "6px 14px", fontWeight: 600 }}>
              {post.isPending ? "Posting…" : "Post to accounts"}</button>
          </div>
        : <div style={{ fontSize: 12 }}>Someone who may allocate payments needs to post these.</div>}
      {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
    </div>
  )
}
