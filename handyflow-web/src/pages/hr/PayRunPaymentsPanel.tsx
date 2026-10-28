// src/pages/hr/PayRunPaymentsPanel.tsx
//
// The bank payment file of a finalised pay run: make up the payments, download the CSV, confirm it was paid, and record any payment the
// bank returned. Account numbers are masked here; the file has the full number.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { BANK_LAYOUTS, STATUS_LABEL, blobMessage, layoutFilename, layoutMime, nextStep, rand, returnProblem, type BankLayout, type PaymentsSummary } from "./payRunPayments.logic"

type ApiErr = { response?: { data?: { message?: string } } }
const messageOf = (e: unknown, fallback: string) => (e as ApiErr)?.response?.data?.message ?? fallback
const unwrap = (r: { data?: { data?: unknown } }) => (r.data?.data ?? r.data) as PaymentsSummary
const btn: React.CSSProperties = { padding: "7px 14px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", fontSize: 13, fontWeight: 600, cursor: "pointer", color: "var(--hf-text-secondary)" }
const solid: React.CSSProperties = { ...btn, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none" }
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }

export default function PayRunPaymentsPanel({ runId, canAct }: { runId: string; canAct: boolean }) {
  const qc = useQueryClient()
  const key = ["hr-run-payments", runId]
  const [error, setError] = useState("")
  const [returning, setReturning] = useState<{ id: string; reason: string } | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [note, setNote] = useState("")
  const [layout, setLayout] = useState<BankLayout>("GENERIC_CSV")
  const [left, setLeft] = useState<PaymentsSummary["skipped"]>([])

  const q = useQuery<PaymentsSummary>({ queryKey: key, queryFn: async () => unwrap(await apiClient.get(`/api/v1/hr/pay-runs/${runId}/payments`)) })
  const s = q.data

  const done = (res: { data?: { data?: unknown } }) => {
    const next = unwrap(res)
    qc.setQueryData(key, next); qc.invalidateQueries({ queryKey: ["hr-run-history", runId] })
    setError("")
    return next
  }
  const makeUp = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/hr/pay-runs/${runId}/payments/make-up`),
    onSuccess: res => setLeft(done(res).skipped ?? []),
    onError: e => setError(messageOf(e, "The payments could not be made up.")),
  })
  const confirm = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/hr/pay-runs/${runId}/payments/confirm-paid`, { note: note.trim() || null }),
    onSuccess: res => { done(res); setConfirming(false); setNote("") },
    onError: e => setError(messageOf(e, "The payments could not be confirmed.")),
  })
  const markReturned = useMutation({
    mutationFn: (v: { id: string; reason: string }) => apiClient.post(`/api/v1/hr/pay-run-payments/${v.id}/return`, { reason: v.reason.trim() }),
    onSuccess: res => { done(res); setReturning(null) },
    onError: e => setError(messageOf(e, "The payment could not be marked as returned.")),
  })
  const download = async () => {
    try {
      const res = await apiClient.get(`/api/v1/hr/pay-runs/${runId}/payments/file`, { responseType: "blob", params: { layout } })
      const url = URL.createObjectURL(new Blob([res.data], { type: layoutMime(layout) }))
      const a = document.createElement("a"); a.href = url; a.download = layoutFilename(layout, s?.payRunNumber); a.click()
      URL.revokeObjectURL(url)
      setError(""); qc.invalidateQueries({ queryKey: key }); qc.invalidateQueries({ queryKey: ["hr-run-history", runId] })
    } catch (e) { setError(await blobMessage(e, "The bank file could not be downloaded.")) }
  }

  if (q.isLoading) return <div style={{ padding: "8px 0", fontSize: 13, color: "var(--hf-text-faint)" }}>Loading bank payments…</div>
  if (q.isError || !s) return <div role="alert" style={{ padding: "8px 0", fontSize: 13, color: "var(--hf-danger-text)" }}>The bank payments could not be loaded.</div>

  const retProblem = returning ? returnProblem(returning.reason) : ""
  return (
    <section aria-label="Bank payments" style={{ marginBottom: 16, border: "1px solid var(--hf-border)", borderRadius: 9, padding: 12, background: "var(--hf-surface)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <div>
          <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-muted)", textTransform: "uppercase", letterSpacing: "0.06em" }}>Bank payments</div>
          <div style={{ fontSize: 13, color: "var(--hf-text-secondary)", marginTop: 2 }}>{nextStep(s)}</div>
          {s.count > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>
            {s.count} payments, {rand(s.total)} · {s.paid} paid · {s.pending} not yet paid · {s.returned} returned</div>}
        </div>
        {canAct && (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {s.canMakeUp && <button type="button" style={btn} disabled={makeUp.isPending} onClick={() => makeUp.mutate()}>
              {makeUp.isPending ? "Working…" : s.count ? "Make up again" : "Make up payments"}</button>}
            {s.pending > 0 && (
              <select aria-label="File layout" value={layout} onChange={e => setLayout(e.target.value as BankLayout)} style={{ ...btn, fontWeight: 500 }}>
                {BANK_LAYOUTS.map(l => <option key={l.value} value={l.value}>{l.label}</option>)}
              </select>
            )}
            {s.pending > 0 && <button type="button" style={btn} onClick={download}>Download bank file</button>}
            {s.pending > 0 && s.downloaded && !confirming && <button type="button" style={solid} onClick={() => setConfirming(true)}>Confirm paid</button>}
          </div>
        )}
      </div>

      {canAct && s.pending > 0 && <div style={{ marginTop: 6, fontSize: 12, color: "var(--hf-text-faint)" }}>{BANK_LAYOUTS.find(l => l.value === layout)?.hint}</div>}
      {error && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}

      {left.length > 0 && (
        <div role="status" style={{ marginTop: 10, padding: "8px 10px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-warning-text)" }}>
          <strong>{left.length === 1 ? "One person is" : `${left.length} people are`} not in the file:</strong>
          <ul style={{ margin: "4px 0 0", paddingLeft: 18 }}>
            {left.map(l => <li key={l.employeeId}>{l.employeeName ?? "Unknown employee"} ({rand(l.amount)}): {l.reason}</li>)}
          </ul>
        </div>
      )}

      {confirming && (
        <form onSubmit={e => { e.preventDefault(); confirm.mutate() }} style={{ marginTop: 10, border: "1px solid var(--hf-border)", borderRadius: 8, padding: 10 }}>
          <label htmlFor="pay-confirm-note" style={{ display: "block", fontSize: 12, fontWeight: 600, marginBottom: 4, color: "var(--hf-text-secondary)" }}>
            The bank accepted the file for {s.pending} payments ({rand(s.payments.filter(p => p.status === "PENDING").reduce((a, p) => a + p.amount, 0))}). Note (optional)</label>
          <input id="pay-confirm-note" style={input} maxLength={200} value={note} onChange={e => setNote(e.target.value)} />
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 8 }}>
            <button type="button" style={btn} onClick={() => setConfirming(false)}>Not yet</button>
            <button type="submit" style={solid} disabled={confirm.isPending}>{confirm.isPending ? "Saving…" : "Yes, it was paid"}</button>
          </div>
        </form>
      )}

      {s.payments.length > 0 && (
        <div style={{ overflowX: "auto", marginTop: 10 }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr style={{ textAlign: "left", color: "var(--hf-text-faint)", fontSize: 11, textTransform: "uppercase" }}>
              <th style={{ padding: 6 }}>Employee</th><th style={{ padding: 6 }}>Bank</th><th style={{ padding: 6 }}>Amount</th><th style={{ padding: 6 }}>Status</th><th style={{ padding: 6 }} /></tr></thead>
            <tbody>
              {s.payments.map(p => (
                <tr key={p.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <td style={{ padding: 6 }}>{p.beneficiaryName}<div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{p.employeeNumber} · {p.reference}</div></td>
                  <td style={{ padding: 6 }}>{p.bankName ?? "—"}<div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{p.branchCode} · {p.accountMasked}</div></td>
                  <td style={{ padding: 6, whiteSpace: "nowrap" }}>{rand(p.amount)}</td>
                  <td style={{ padding: 6 }}>{STATUS_LABEL[p.status]}
                    {p.returnReason && <div style={{ fontSize: 11, color: "var(--hf-danger-text)" }}>{p.returnReason}</div>}</td>
                  <td style={{ padding: 6 }}>
                    {canAct && p.canReturn && !returning && (
                      <button type="button" style={btn} aria-label={`Bank returned ${p.beneficiaryName}`} onClick={() => { setReturning({ id: p.id, reason: "" }); setError("") }}>Returned</button>)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {returning && (
        <form onSubmit={e => { e.preventDefault(); if (!retProblem) markReturned.mutate(returning) }} noValidate
          style={{ marginTop: 10, border: "1px solid var(--hf-border)", borderRadius: 8, padding: 10 }}>
          <label htmlFor="pay-return-reason" style={{ display: "block", fontSize: 12, fontWeight: 600, marginBottom: 4, color: "var(--hf-text-secondary)" }}>Why did the bank return it? *</label>
          <input id="pay-return-reason" style={input} maxLength={300} value={returning.reason} onChange={e => setReturning({ id: returning.id, reason: e.target.value })} />
          {returning.reason && retProblem && <div role="alert" style={{ marginTop: 6, fontSize: 13, color: "var(--hf-danger-text)" }}>{retProblem}</div>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 8 }}>
            <button type="button" style={btn} onClick={() => setReturning(null)}>Cancel</button>
            <button type="submit" style={solid} disabled={!!retProblem || markReturned.isPending}>{markReturned.isPending ? "Saving…" : "Mark as returned"}</button>
          </div>
        </form>
      )}
    </section>
  )
}
