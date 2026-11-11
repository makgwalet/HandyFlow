// src/pages/events/EventPaymentsTab.tsx
//
// Money for the selected event: what was asked and received, confirming money that arrived outside a provider (EFT, cash, card machine), cancelling an unpaid
// checkout, refunds (raise, then mark paid out or failed) and a reconciliation CSV. Needs EVENTS_PAYMENTS_READ; each action needs its own permission.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import {
  canCancelCheckout, canConfirm, canRefund, canRefundAll, confirmProblem, confirmWarning, PAYMENT_STATUSES, paymentStatusText, paymentStatusTone, rand, refundableNow, refundProblem,
  refundStatusText, totals, type Payment, type Refund,
} from "./paymentOps"

interface PageOf<T> { content: T[] }
const field: React.CSSProperties = { ...inputStyle, width: "100%" }
const TONE = { ok: ["var(--hf-success-soft-strong)", "var(--hf-success-text-strong)"], warn: ["var(--hf-warning-soft)", "var(--hf-warning-text)"], bad: ["var(--hf-danger-soft)", "var(--hf-danger-text)"], muted: ["var(--hf-surface-sunken)", "var(--hf-text-muted)"] } as const

function Dialog({ title, problem, warning, pending, error, label, onClose, onSubmit, children }: {
  title: string; problem: string | null; warning?: string | null; pending: boolean; error: unknown; label: string; onClose: () => void; onSubmit: () => void; children: React.ReactNode
}) {
  return (
    <Modal title={title} onClose={onClose}>
      {children}
      {warning && <p role="note" style={{ fontSize: 12.5, color: "var(--hf-warning-text)", margin: "10px 0 0" }}>{warning}</p>}
      <ErrorLine error={error} fallback="That could not be completed" />
      {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{problem}</p>}
      <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
        <button type="button" style={{ ...btnGhost, flex: 1, justifyContent: "center" }} onClick={onClose}>Back</button>
        <button type="button" style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }} disabled={!!problem || pending} onClick={onSubmit}>{pending ? "Working…" : label}</button>
      </div>
    </Modal>
  )
}

type Action = { kind: "confirm"; p: Payment } | { kind: "cancel"; p: Payment } | { kind: "refund"; p: Payment } | { kind: "complete"; r: Refund } | { kind: "fail"; r: Refund } | null

export default function EventPaymentsTab({ eventId, eventTitle, onChangeEvent }: { eventId: string | null; eventTitle: string; onChangeEvent: () => void }) {
  const qc = useQueryClient()
  const canRead = usePermissions(["EVENTS_PAYMENTS_READ", "EVENTS_MANAGE"])
  const canManage = usePermissions(["EVENTS_PAYMENTS_MANAGE", "EVENTS_MANAGE"])
  const canRefundPerm = usePermissions(["EVENTS_REFUND", "EVENTS_MANAGE"])
  const [status, setStatus] = useState("")
  const [action, setAction] = useState<Action>(null)
  const [a, setA] = useState(""); const [b, setB] = useState(""); const [reason, setReason] = useState(""); const [flag, setFlag] = useState(false)
  const open = (x: Action, init: Partial<{ a: string }> = {}) => { setAction(x); setA(init.a ?? ""); setB(""); setReason(""); setFlag(false) }

  const base = `/api/v1/events/${eventId}`
  const { data: event } = useQuery<any>({ queryKey: ["event-one", eventId], enabled: !!eventId && canRead, queryFn: async () => { const r = await apiClient.get(base); return r.data?.data ?? r.data } })
  const { data: payments, isLoading, error } = useQuery<PageOf<Payment>>({
    queryKey: ["event-payments", eventId, status], enabled: !!eventId && canRead,
    queryFn: async () => { const r = await apiClient.get(`${base}/payments`, { params: { status: status || undefined, size: 100 } }); return r.data?.data ?? r.data },
  })
  const { data: refunds = [] } = useQuery<Refund[]>({ queryKey: ["event-refunds", eventId], enabled: !!eventId && canRead, queryFn: async () => { const r = await apiClient.get(`${base}/refunds`); return r.data?.data ?? r.data ?? [] } })

  const done = () => { setAction(null); qc.invalidateQueries({ queryKey: ["event-payments"] }); qc.invalidateQueries({ queryKey: ["event-refunds"] }); qc.invalidateQueries({ queryKey: ["event-guests", eventId] }); qc.invalidateQueries({ queryKey: ["event-stats", eventId] }) }
  const run = useMutation({
    mutationFn: async () => {
      if (!action) return
      if (action.kind === "confirm") return apiClient.post(`${base}/payments/${action.p.id}/confirm`, { amountReceived: Number(a), reference: b.trim() || null, fee: reason.trim() === "" ? null : Number(reason) })
      if (action.kind === "cancel") return apiClient.post(`${base}/payments/${action.p.id}/cancel`, { reason: a.trim() })
      if (action.kind === "refund") return apiClient.post(`${base}/payments/${action.p.id}/refunds`, { amount: a.trim() === "" ? null : Number(a), reason: reason.trim(), cancelGuest: flag })
      if (action.kind === "complete") return apiClient.post(`${base}/refunds/${action.r.id}/complete`, { reference: a.trim() || null })
      return apiClient.post(`${base}/refunds/${action.r.id}/fail`, { reason: a.trim() })
    },
    onSuccess: done,
  })
  const refundAll = useMutation({ mutationFn: async () => (await apiClient.post(`${base}/payments/refund-all`)).data?.data, onSuccess: done })
  const csv = useMutation({
    mutationFn: async () => {
      const r = await apiClient.get(`${base}/reports/payments.csv`, { responseType: "blob" })
      const url = URL.createObjectURL(r.data as Blob)
      const el = document.createElement("a"); el.href = url; el.download = `event-payments.csv`; el.click(); URL.revokeObjectURL(url)
    },
  })

  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={inputStyle} onClick={onChangeEvent}>Choose event</button></div>
  if (!canRead) return <p style={{ padding: 20, color: "var(--hf-text-muted)" }}>You do not have access to the payments of events.</p>

  const rows = payments?.content ?? []
  const t = totals(rows)
  const refundsFor = (id: string) => refunds.filter(r => r.paymentId === id)

  let problem: string | null = null; let warning: string | null = null; let title = ""; let label = ""; let body: React.ReactNode = null
  if (action?.kind === "confirm") {
    title = "Confirm payment received"; label = "Confirm payment"; problem = confirmProblem(a, reason); warning = confirmWarning(a, action.p.amount)
    body = <>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>Ticket {action.p.ticketNumber} · asked for {rand(action.p.amount)}</p>
      <label style={labelStyle} htmlFor="pc-amt">Amount received (R) *</label><input id="pc-amt" style={field} inputMode="decimal" value={a} onChange={e => setA(e.target.value)} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="pc-ref">Bank or receipt reference</label><input id="pc-ref" style={field} maxLength={100} value={b} onChange={e => setB(e.target.value)} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="pc-fee">Fee charged (R)</label><input id="pc-fee" style={field} inputMode="decimal" value={reason} onChange={e => setReason(e.target.value)} />
    </>
  } else if (action?.kind === "cancel") {
    title = "Cancel this checkout"; label = "Cancel checkout"; problem = a.trim().length >= 3 ? null : "Give a reason."
    body = <><p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>The held seat for ticket {action.p.ticketNumber} goes back to sale.</p>
      <label style={labelStyle} htmlFor="pc-why">Reason *</label><input id="pc-why" style={field} maxLength={500} value={a} onChange={e => setA(e.target.value)} /></>
  } else if (action?.kind === "refund") {
    title = "Refund payment"; label = "Raise refund"; problem = refundProblem(a, refundableNow(action.p, refunds), reason)
    body = <>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>Ticket {action.p.ticketNumber} · up to {rand(refundableNow(action.p, refunds))} can be refunded. The money is paid out by your finance team; this records the request.</p>
      <label style={labelStyle} htmlFor="pr-amt">Amount (R), empty = everything refundable</label><input id="pr-amt" style={field} inputMode="decimal" value={a} onChange={e => setA(e.target.value)} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="pr-why">Reason *</label><input id="pr-why" style={field} maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />
      <label style={{ display: "block", fontSize: 13, marginTop: 10 }}><input type="checkbox" checked={flag} onChange={e => setFlag(e.target.checked)} /> Also cancel the ticket and return the seat</label>
    </>
  } else if (action?.kind === "complete") {
    title = "Mark refund paid out"; label = "Mark paid out"; problem = null
    body = <><p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>{rand(action.r.amount)} refunded. Only mark this once the money has left your account.</p>
      <label style={labelStyle} htmlFor="rc-ref">Bank or payout reference</label><input id="rc-ref" style={field} maxLength={100} value={a} onChange={e => setA(e.target.value)} /></>
  } else if (action?.kind === "fail") {
    title = "Mark refund failed"; label = "Mark failed"; problem = a.trim().length >= 3 ? null : "Say why it failed."
    body = <><p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>The {rand(action.r.amount)} becomes refundable again so it can be raised once more.</p>
      <label style={labelStyle} htmlFor="rf-why">Reason *</label><input id="rf-why" style={field} maxLength={500} value={a} onChange={e => setA(e.target.value)} /></>
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Payments for {eventTitle}</p>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <select aria-label="Status" style={inputStyle} value={status} onChange={e => setStatus(e.target.value)}>
            <option value="">All statuses</option>{PAYMENT_STATUSES.map(s => <option key={s} value={s}>{paymentStatusText(s)}</option>)}
          </select>
          <button type="button" style={btnGhost} disabled={csv.isPending} onClick={() => csv.mutate()}>{csv.isPending ? "Preparing…" : "Download CSV"}</button>
          {canRefundPerm && canRefundAll(event?.status, rows, refunds) && <button type="button" style={btnPrimary} disabled={refundAll.isPending} onClick={() => refundAll.mutate()}>Refund everyone (cancelled event)</button>}
        </div>
      </div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 12 }}>
        {[["Received", rand(t.received)], ["Refunded", rand(t.refunded)], ["Fees", rand(t.fees)], ["Waiting on payment", rand(t.held)], ["Need a refund", String(t.needReview)]].map(([k, v]) => (
          <div key={k} style={{ ...card, padding: "8px 14px", minWidth: 120 }}><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{k}</div><div style={{ fontWeight: 800 }}>{v}</div></div>
        ))}
      </div>
      <ErrorLine error={error ?? csv.error ?? refundAll.error} fallback="That could not be completed" />
      {refundAll.isSuccess && refundAll.data && <p role="status" style={{ fontSize: 12.5 }}>{refundAll.data.requested} refunds raised{refundAll.data.failed ? `, ${refundAll.data.failed} could not be` : ""}.{refundAll.data.moreRemaining ? " There are more; run it again for the rest." : ""}</p>}
      {isLoading ? <p>Loading…</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No payments.</p> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {rows.map(p => {
            const [bg, fg] = TONE[paymentStatusTone(p.status)]
            return (
              <div key={p.id} style={{ ...card, padding: "10px 14px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 13 }}>{p.ticketNumber ?? "—"} · {rand(p.amount)} <span style={{ background: bg, color: fg, padding: "1px 8px", borderRadius: 20, fontSize: 11, marginLeft: 6 }}>{paymentStatusText(p.status)}</span></div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                      {p.provider}{p.providerRef ? ` · ${p.providerRef}` : ""}{p.receivedAmount != null ? ` · received ${rand(p.receivedAmount)}` : ""}{p.refundedAmount ? ` · refunded ${rand(p.refundedAmount)}` : ""}
                    </div>
                    {p.reviewReason && <div style={{ fontSize: 12, color: "var(--hf-danger-text)" }}>{p.reviewReason}</div>}
                    {p.failureReason && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{p.failureReason}</div>}
                  </div>
                  <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                    {canManage && canConfirm(p) && <button type="button" style={btnPrimary} onClick={() => open({ kind: "confirm", p }, { a: String(p.amount) })}>Confirm payment</button>}
                    {canManage && canCancelCheckout(p) && <button type="button" style={btnGhost} onClick={() => open({ kind: "cancel", p })}>Cancel checkout</button>}
                    {canRefundPerm && canRefund(p, refunds) && <button type="button" style={btnGhost} onClick={() => open({ kind: "refund", p })}>Refund</button>}
                  </div>
                </div>
                {refundsFor(p.id).map(r => (
                  <div key={r.id} style={{ marginTop: 6, paddingTop: 6, borderTop: "1px dashed var(--hf-border)", display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", fontSize: 12 }}>
                    <span>Refund {rand(r.amount)} · {refundStatusText(r.status)} · {r.reason}{r.failureReason ? ` (${r.failureReason})` : ""}{r.providerRef ? ` · ${r.providerRef}` : ""}</span>
                    {canRefundPerm && r.status === "REQUESTED" && (
                      <span style={{ display: "flex", gap: 6 }}>
                        <button type="button" style={btnPrimary} onClick={() => open({ kind: "complete", r })}>Mark paid out</button>
                        <button type="button" style={btnGhost} onClick={() => open({ kind: "fail", r })}>Mark failed</button>
                      </span>
                    )}
                  </div>
                ))}
              </div>
            )
          })}
        </div>
      )}
      {action && <Dialog title={title} problem={problem} warning={warning} pending={run.isPending} error={run.error} label={label} onClose={() => setAction(null)} onSubmit={() => run.mutate()}>{body}</Dialog>}
    </div>
  )
}
