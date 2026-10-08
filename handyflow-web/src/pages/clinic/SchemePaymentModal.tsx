// One scheme payment spread over many claims (CLINIC-DEC-006): oldest first by default, or your own split with a reason.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import ModalShell from "./ModalShell"
import { allocationProblem, owed, rand } from "./claimMoney"
import { parseAmount } from "./claimPayment"

interface OpenClaim { id: string; patientName?: string; referenceNumber?: string; schemeName: string; status: string; schemePortion: number; schemeOutstanding?: number; createdAt: string }
interface Share { claimId: string; patientName?: string; claimReference?: string; outstandingBefore: number; amount: number; outstandingAfter: number }
interface Plan { recorded: boolean; method: string; amount: number; shares: Share[] }

const BASE = "/api/v1/clinic/billing/claims"
const rows = (r: any) => { const p = r?.data?.data ?? r?.data ?? []; return Array.isArray(p) ? p : (p.content ?? []) }

export default function SchemePaymentModal({ schemes, onClose }: { schemes: string[]; onClose: () => void }) {
  const qc = useQueryClient()
  const [scheme, setScheme] = useState(schemes[0] ?? "")
  const [amountText, setAmountText] = useState("")
  const [reference, setReference] = useState("")
  const [manual, setManual] = useState(false)
  const [amounts, setAmounts] = useState<Record<string, string>>({})
  const [reason, setReason] = useState("")
  const [plan, setPlan] = useState<Plan | null>(null)
  const [error, setError] = useState("")
  const [done, setDone] = useState(false)

  const { data: open = [] } = useQuery<OpenClaim[]>({
    queryKey: ["clinic-open-scheme-claims", scheme], enabled: !!scheme,
    queryFn: async () => {
      const [a, p] = await Promise.all(["ACCEPTED", "PARTIAL"].map(s => apiClient.get(`${BASE}?status=${s}`)))
      return [...rows(a), ...rows(p)].filter((c: OpenClaim) => (c.schemeName ?? "").toLowerCase() === scheme.toLowerCase() && owed(c as any) > 0)
        .sort((x: OpenClaim, y: OpenClaim) => x.createdAt.localeCompare(y.createdAt))
    },
  })
  const owedById = Object.fromEntries(open.map(c => [c.id, owed(c as any)]))
  const problem = allocationProblem(scheme, amountText, manual, amounts, owedById, reason)

  const body = (preview: boolean) => ({
    schemeName: scheme, amount: parseAmount(amountText), reference: reference.trim() || undefined, preview,
    shares: manual ? Object.entries(amounts).filter(([, v]) => v.trim()).map(([claimId, v]) => ({ claimId, amount: parseAmount(v) })) : undefined,
    overrideReason: manual ? reason.trim() : undefined,
  })
  const send = useMutation({
    mutationFn: async (preview: boolean) => (await apiClient.post(`${BASE}/scheme-payments`, body(preview))).data?.data as Plan,
    onSuccess: p => { setError(""); setPlan(p); if (p.recorded) { setDone(true); qc.invalidateQueries({ queryKey: ["clinic-claims"] }) } },
    onError: (e: any) => { setPlan(null); setError(e?.response?.data?.message ?? "Could not allocate the payment") },
  })

  return (
    <ModalShell title="Record a scheme payment" onClose={onClose} width={600}
      footer={done ? <button type="button" onClick={onClose} style={{ padding: "9px 20px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontWeight: 600, cursor: "pointer" }}>Close</button> : <>
        <button type="button" onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", cursor: "pointer" }}>Cancel</button>
        <button type="button" disabled={!!problem || send.isPending} onClick={() => send.mutate(true)}
          style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", cursor: "pointer", opacity: problem ? 0.6 : 1 }}>Preview</button>
        <button type="button" disabled={!plan || send.isPending} onClick={() => send.mutate(false)}
          style={{ padding: "9px 20px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontWeight: 600, cursor: "pointer", opacity: plan ? 1 : 0.6 }}>Record payment</button></>}>
      {done && plan ? (
        <div role="status" style={{ fontSize: 14 }}>Recorded {rand(plan.amount)} over {plan.shares.length} claim{plan.shares.length === 1 ? "" : "s"}.</div>
      ) : (<>
        <label style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 5 }}>Scheme *
          <select aria-label="Scheme" value={scheme} onChange={e => { setScheme(e.target.value); setPlan(null); setAmounts({}) }}
            style={{ width: "100%", padding: 8, marginTop: 5, border: "1.5px solid var(--hf-border)", borderRadius: 8 }}>
            {schemes.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        </label>
        <label style={{ display: "block", fontSize: 13, fontWeight: 600, margin: "10px 0 5px" }}>Amount received (R) *
          <input aria-label="Amount received" inputMode="decimal" value={amountText} onChange={e => { setAmountText(e.target.value); setPlan(null) }}
            style={{ width: "100%", padding: 8, marginTop: 5, boxSizing: "border-box", border: "1.5px solid var(--hf-border)", borderRadius: 8 }} />
        </label>
        <label style={{ display: "block", fontSize: 13, fontWeight: 600, margin: "10px 0 5px" }}>Remittance reference
          <input aria-label="Reference" value={reference} onChange={e => setReference(e.target.value)}
            style={{ width: "100%", padding: 8, marginTop: 5, boxSizing: "border-box", border: "1.5px solid var(--hf-border)", borderRadius: 8 }} />
        </label>
        <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, margin: "12px 0" }}>
          <input type="checkbox" checked={manual} onChange={e => { setManual(e.target.checked); setPlan(null) }}/>
          Choose the split myself (otherwise the oldest claims are paid first)
        </label>
        {manual && (
          <div style={{ border: "1px solid var(--hf-border)", borderRadius: 8, marginBottom: 10 }}>
            {open.length === 0 && <div style={{ padding: 10, fontSize: 13 }}>No open claims for this scheme.</div>}
            {open.map(c => (
              <div key={c.id} style={{ display: "flex", gap: 10, alignItems: "center", padding: "6px 10px", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
                <span style={{ flex: 1 }}>{c.patientName ?? "Patient"} · {c.referenceNumber ?? c.id.slice(0, 8)}</span>
                <span>owes {rand(owed(c as any))}</span>
                <input aria-label={`Amount for ${c.patientName ?? c.id}`} inputMode="decimal" value={amounts[c.id] ?? ""} placeholder="0"
                  onChange={e => { setAmounts(a => ({ ...a, [c.id]: e.target.value })); setPlan(null) }}
                  style={{ width: 90, padding: 6, border: "1px solid var(--hf-border)", borderRadius: 6 }} />
              </div>
            ))}
            <div style={{ padding: 10 }}>
              <label style={{ display: "block", fontSize: 13, fontWeight: 600 }}>Why this split? *
                <textarea aria-label="Reason for the split" rows={2} value={reason} onChange={e => { setReason(e.target.value); setPlan(null) }}
                  style={{ width: "100%", padding: 8, marginTop: 5, boxSizing: "border-box", border: "1.5px solid var(--hf-border)", borderRadius: 8 }} />
              </label>
            </div>
          </div>
        )}
        {amountText.trim() && problem && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginBottom: 8 }}>{problem}</div>}
        {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginBottom: 8 }}>{error}</div>}
        {plan && (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }} aria-label="Allocation preview">
            <thead><tr><th align="left">Claim</th><th align="right">Owed</th><th align="right">Pays</th><th align="right">Left</th></tr></thead>
            <tbody>{plan.shares.map(s => (
              <tr key={s.claimId} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                <td>{s.patientName ?? "Patient"} · {s.claimReference ?? s.claimId.slice(0, 8)}</td>
                <td align="right">{rand(s.outstandingBefore)}</td><td align="right">{rand(s.amount)}</td><td align="right">{rand(s.outstandingAfter)}</td>
              </tr>))}</tbody>
          </table>
        )}
      </>)}
    </ModalShell>
  )
}
