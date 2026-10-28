// src/pages/hr/PayAdjustmentsModal.tsx
//
// Bonuses and back pay for one employee. Each is paid by the next pay run whose period ends on or after its date, and is taxed in that month
// by the annual-payment method (the extra tax the year bears once it is added to the annualised month).
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { X } from "lucide-react"
import { apiClient } from "../../api/client"
import { EMPTY_PAY_FORM, PAY_KINDS, canEnterPay, cancelProblem, money, payBody, payProblem, type PayAdjustment, type PayForm } from "./payAdjustments.logic"

type ApiErr = { response?: { data?: { message?: string } } }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const input: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }
const today = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })

export default function PayAdjustmentsModal({ employee, permissions, onClose }: {
  employee: { id: string; fullName: string; employeeNumber: string; startDate?: string | null }; permissions: string[]; onClose: () => void
}) {
  const qc = useQueryClient()
  const canEdit = canEnterPay(permissions)
  const [form, setForm] = useState<PayForm>(EMPTY_PAY_FORM)
  const [cancelling, setCancelling] = useState<{ id: string; reason: string } | null>(null)
  const [error, setError] = useState("")
  const key = ["hr-pay-adjustments", employee.id]

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose() }
    document.addEventListener("keydown", onKey)
    return () => document.removeEventListener("keydown", onKey)
  }, [onClose])

  const list = useQuery<PayAdjustment[]>({
    queryKey: key,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/hr/employees/${employee.id}/pay-adjustments`); return (r.data?.data ?? r.data) as PayAdjustment[] },
  })
  const items = list.data ?? []

  const onErr = (fallback: string) => (e: unknown) => setError((e as ApiErr)?.response?.data?.message ?? fallback)
  const add = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/hr/employees/${employee.id}/pay-adjustments`, payBody(form)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setForm(EMPTY_PAY_FORM); setError("") },
    onError: onErr("The payment could not be saved."),
  })
  const cancel = useMutation({
    mutationFn: (v: { id: string; reason: string }) => apiClient.post(`/api/v1/hr/pay-adjustments/${v.id}/cancel`, { reason: v.reason.trim() }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setCancelling(null); setError("") },
    onError: onErr("The payment could not be cancelled."),
  })

  const problem = payProblem(form, today(), employee.startDate)
  const set = (k: keyof PayForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setForm(f => ({ ...f, [k]: e.target.value }))
  const cancelErr = cancelling ? cancelProblem(cancelling.reason) : ""
  const cancellingItem = cancelling ? items.find(i => i.id === cancelling.id) : undefined

  const status = (i: PayAdjustment) => i.status === "CANCELLED" ? { text: "Cancelled", bg: "var(--hf-danger-soft)", fg: "var(--hf-danger-text)" }
    : i.payRunNumber ? { text: `In ${i.payRunNumber}`, bg: "var(--hf-success-soft-strong)", fg: "var(--hf-success-text-strong)" }
    : { text: "Waiting for a pay run", bg: "var(--hf-info-soft)", fg: "var(--hf-info-text)" }

  return (
    <div onMouseDown={e => { if (e.target === e.currentTarget) onClose() }}
      style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", zIndex: 200, display: "flex", alignItems: "center", justifyContent: "center", padding: "clamp(8px, 3vw, 24px)" }}>
      <div role="dialog" aria-modal="true" aria-label={`Bonuses and back pay for ${employee.fullName}`}
        style={{ background: "var(--hf-surface)", borderRadius: 16, width: "100%", maxWidth: 760, maxHeight: "92vh", overflowY: "auto", padding: "clamp(16px, 4vw, 24px)", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>Bonuses and back pay</h3>
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{employee.fullName} · {employee.employeeNumber}</div>
          </div>
          <button type="button" aria-label="Close" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
        </div>

        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 14px" }}>
          The next pay run whose period ends on or after the date pays it. It is taxed in that month: PAYE is the extra tax the year bears once it is added to the
          annualised month, and UIF and SDL count it. You cannot enter a payment for yourself. Have the tax treatment checked by a payroll specialist before relying on it.
        </p>

        {list.isLoading ? <div style={{ color: "var(--hf-text-faint)", padding: 12 }}>Loading…</div>
          : list.isError ? <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>The payments could not be loaded.</div>
          : items.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)", padding: "8px 0 14px" }}>No bonuses, back pay or recoveries are recorded.</div>
          : (
            <div style={{ overflowX: "auto", marginBottom: 14 }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
                <thead><tr style={{ textAlign: "left", color: "var(--hf-text-faint)", fontSize: 11, textTransform: "uppercase" }}>
                  <th style={{ padding: 6 }}>Payment</th><th style={{ padding: 6 }}>Amount</th><th style={{ padding: 6 }}>Pay from</th><th style={{ padding: 6 }}>Status</th><th style={{ padding: 6 }} /></tr></thead>
                <tbody>
                  {items.map(i => {
                    const s = status(i)
                    return (
                      <tr key={i.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                        <td style={{ padding: 6 }}>{i.kindLabel}: {i.description}
                          {i.kind === "BACK_PAY" && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Covers {fmtDate(i.relatesFrom)} to {fmtDate(i.relatesTo)}</div>}
                          <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Reason: {i.reason}</div>
                          {i.cancelReason && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Cancelled: {i.cancelReason}</div>}</td>
                        <td style={{ padding: 6, whiteSpace: "nowrap" }}>{money(i.amount)}</td>
                        <td style={{ padding: 6 }}>{fmtDate(i.payFrom)}</td>
                        <td style={{ padding: 6 }}><span style={{ background: s.bg, color: s.fg, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{s.text}</span>
                          {canEdit && i.canCancel && !cancelling && (
                            <button type="button" aria-label={`Cancel ${i.kindLabel} ${i.description}`} onClick={() => { setCancelling({ id: i.id, reason: "" }); setError("") }}
                              style={{ marginLeft: 6, padding: "3px 10px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", fontSize: 12, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
                          )}</td>
                        <td />
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}

        {cancelling && cancellingItem && (
          <form onSubmit={e => { e.preventDefault(); if (!cancelErr) cancel.mutate(cancelling) }} noValidate
            style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12, marginBottom: 14 }}>
            <label htmlFor="pa-cancel" style={label}>Why cancel {cancellingItem.kindLabel.toLowerCase()} of {money(cancellingItem.amount)}? *</label>
            <input id="pa-cancel" style={input} value={cancelling.reason} maxLength={300} onChange={e => setCancelling({ id: cancelling.id, reason: e.target.value })} />
            {cancelling.reason && cancelErr && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{cancelErr}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 10 }}>
              <button type="button" onClick={() => setCancelling(null)} style={{ padding: "7px 14px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 13, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Keep it</button>
              <button type="submit" disabled={!!cancelErr || cancel.isPending}
                style={{ padding: "7px 16px", background: cancelErr || cancel.isPending ? "var(--hf-text-faint)" : "var(--hf-danger-text)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 700, cursor: cancelErr ? "not-allowed" : "pointer" }}>
                {cancel.isPending ? "Saving…" : "Cancel payment"}
              </button>
            </div>
          </form>
        )}

        {canEdit ? (
          <form onSubmit={e => { e.preventDefault(); if (!problem) add.mutate() }} noValidate>
            <h4 style={{ margin: "0 0 8px", fontSize: 14, color: "var(--hf-text)" }}>Enter a payment</h4>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10 }}>
              <div><label htmlFor="pa-kind" style={label}>Kind *</label>
                <select id="pa-kind" style={input} value={form.kind} onChange={set("kind")}>
                  <option value="">Choose…</option>
                  {PAY_KINDS.map(k => <option key={k.value} value={k.value}>{k.label}</option>)}
                </select></div>
              <div><label htmlFor="pa-amount" style={label}>Amount (R) *</label><input id="pa-amount" style={input} inputMode="decimal" value={form.amount} onChange={set("amount")} /></div>
              <div><label htmlFor="pa-from" style={label}>Pay from *</label><input id="pa-from" style={input} type="date" value={form.payFrom} onChange={set("payFrom")} /></div>
            </div>
            {form.kind === "BACK_PAY" && (
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10, marginTop: 10 }}>
                <div><label htmlFor="pa-rf" style={label}>Period covered: from *</label><input id="pa-rf" style={input} type="date" value={form.relatesFrom} onChange={set("relatesFrom")} /></div>
                <div><label htmlFor="pa-rt" style={label}>Period covered: to *</label><input id="pa-rt" style={input} type="date" value={form.relatesTo} onChange={set("relatesTo")} /></div>
              </div>
            )}
            <div style={{ marginTop: 10 }}><label htmlFor="pa-desc" style={label}>Description *</label><input id="pa-desc" style={input} value={form.description} onChange={set("description")} maxLength={200} placeholder="For example, Annual performance bonus 2026" /></div>
            <div style={{ marginTop: 10 }}><label htmlFor="pa-reason" style={label}>Reason *</label><input id="pa-reason" style={input} value={form.reason} onChange={set("reason")} maxLength={300} /></div>
            {(error || (problem && form.kind)) && <div role="alert" style={{ marginTop: 10, fontSize: 13, color: "var(--hf-danger-text)" }}>{error || problem}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
              <button type="button" onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Close</button>
              <button type="submit" disabled={!!problem || add.isPending}
                style={{ padding: "9px 20px", background: problem || add.isPending ? "var(--hf-text-faint)" : "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: problem ? "not-allowed" : "pointer" }}>
                {add.isPending ? "Saving…" : "Save payment"}
              </button>
            </div>
          </form>
        ) : (
          <div style={{ display: "flex", justifyContent: "flex-end" }}>
            {error && <div role="alert" style={{ marginRight: "auto", fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
            <button type="button" onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Close</button>
          </div>
        )}
      </div>
    </div>
  )
}
